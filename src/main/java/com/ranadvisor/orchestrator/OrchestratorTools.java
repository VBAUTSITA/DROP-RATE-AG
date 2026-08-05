package com.ranadvisor.orchestrator;

import com.ranadvisor.core.DiagnosticModule;
import com.ranadvisor.core.ModuleFinding;
import com.ranadvisor.core.RootCauseHypothesis;
import com.ranadvisor.drops.DropRateAgent;
import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import com.ranadvisor.pci.PciPlanningAgent;
import com.ranadvisor.pci.PciTrackWorkflow;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The tools the supervisor agent can call. Two kinds of "connection" live here:
 *
 * <ol>
 *   <li><b>Agent-as-tool routing</b> — {@link #routeToDropSpecialist}/{@link #routeToPciSpecialist}
 *       delegate a natural-language question to a specialist agent, so the supervisor can hand off
 *       self-contained questions.</li>
 *   <li><b>Deterministic cross-module RCA</b> — {@link #diagnoseCell} fans out over every registered
 *       {@link DiagnosticModule} (autowired as a list, so new modules join automatically), then runs
 *       the {@link CrossModuleCorrelator} to synthesize one root-cause hypothesis. No LLM involved,
 *       so it is cheap, reproducible, and fully logged.</li>
 * </ol>
 */
@Component
public class OrchestratorTools {

    @Autowired private List<DiagnosticModule> modules;   // Spring injects every @Component module
    @Autowired private CrossModuleCorrelator correlator;
    @Autowired private DropRateAgent dropRateAgent;
    @Autowired private PciPlanningAgent pciPlanningAgent;
    @Autowired private PciTrackWorkflow pciTrack;   // shared with DropAnalysisTool
    @Autowired private AgentLogRepository logRepo;

    @Tool("List the diagnostic modules available to the platform (id and domain). Use to see what problems can be diagnosed.")
    public String listModules() {
        long start = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("Available diagnostic modules:\n");
        for (DiagnosticModule m : modules) {
            sb.append(" - ").append(m.id()).append(": ").append(m.domain()).append('\n');
        }
        String result = sb.toString();
        log("listModules", "(none)", result, start);
        return result;
    }

    @Tool("Run a full cross-module diagnosis for one cell: every diagnostic module analyzes the cell and the results are correlated into a single root-cause hypothesis. Use this whenever the user asks WHY a cell has a problem, or for a root-cause / holistic analysis of a specific cell.")
    public String diagnoseCell(String cellName) {
        long start = System.currentTimeMillis();

        List<ModuleFinding> findings = new ArrayList<>();
        for (DiagnosticModule m : modules) {
            try {
                ModuleFinding f = m.analyze(cellName);
                if (f != null) findings.add(f);
            } catch (Exception e) {
                System.err.println("[OrchestratorTools] module " + m.id() + " failed: " + e.getMessage());
            }
        }

        RootCauseHypothesis h = correlator.correlate(cellName, findings);

        StringBuilder sb = new StringBuilder();
        sb.append("Cross-module diagnosis for ").append(cellName).append("\n\n");
        sb.append("Module findings:\n");
        for (ModuleFinding f : findings) {
            sb.append(String.format("  [%s] %s — %s%n", f.module(), f.severity(), f.headline()));
            if (f.detail() != null && !f.detail().isBlank()) {
                sb.append("      ").append(f.detail().replace("\n", "\n      ")).append('\n');
            }
        }
        sb.append("\n=== Root-cause hypothesis ===\n");
        sb.append(h.headline()).append("  [confidence: ").append(h.confidence()).append("]\n");
        sb.append("Rationale: ").append(h.rationale()).append('\n');
        sb.append("Recommended actions:\n");
        for (String a : h.recommendedActions()) sb.append("  - ").append(a).append('\n');

        String result = sb.toString();
        log("diagnoseCell", cellName, result, start);
        return result;
    }

    @Tool("Pursue the PCI track for a cell: audit its PCI plan for conflicts and, only if a conflict is found, compute a re-plan proposal with the engine's reasoning. Use this after a diagnosis has listed the possible causes and the user decides to go after the PCI/identity one. This only reads and simulates — it never changes the network.")
    public String tacklePciIssue(String cellName) {
        long start = System.currentTimeMillis();
        String result = pciTrack.run(cellName);
        log("tacklePciIssue", cellName, result, start);
        return result;
    }

    @Tool("Ask the call-drop / retainability specialist a natural-language question (drop rates, worst cells, dominant drop causes). Use for questions specifically about drops.")
    public String routeToDropSpecialist(String question) {
        long start = System.currentTimeMillis();
        String result = dropRateAgent.chat(question);
        log("routeToDropSpecialist", question, result, start);
        return result;
    }

    @Tool("Ask the PCI-planning specialist a natural-language question (a cell's PCI, PCI collisions/confusions/mod-3, network PCI audit, PCI re-plan suggestions). Use for questions specifically about PCI.")
    public String routeToPciSpecialist(String question) {
        long start = System.currentTimeMillis();
        String result = pciPlanningAgent.chat(question);
        log("routeToPciSpecialist", question, result, start);
        return result;
    }

    private void log(String tool, String input, String output, long startMs) {
        try {
            AgentLog entry = new AgentLog();
            entry.setAgentName("orchestrator");
            entry.setToolCalled(tool);
            entry.setToolInput(input);
            entry.setToolOutput(output != null && output.length() > 2000
                    ? output.substring(0, 2000) + "...[truncated]" : output);
            entry.setLatencyMs(System.currentTimeMillis() - startMs);
            logRepo.save(entry);
        } catch (Exception e) {
            System.err.println("[OrchestratorTools] Failed to save log entry: " + e.getMessage());
        }
    }
}
