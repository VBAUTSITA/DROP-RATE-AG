package com.ranadvisor.orchestrator;

import com.ranadvisor.guardrail.GuardrailResult;
import com.ranadvisor.guardrail.InputGuardrail;
import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * The unified front door for the multi-module platform.
 *
 * <ul>
 *   <li>{@code GET /agent/ran?message=...} — natural-language, LLM-orchestrated: the
 *       supervisor agent routes to specialists / cross-module RCA and answers in prose.</li>
 *   <li>{@code GET /agent/ran/diagnose?cell=...} — deterministic: runs the module fan-out
 *       + correlator directly with no LLM. Ideal for dashboards, tests, and eval.</li>
 * </ul>
 *
 * Uses the deterministic {@link InputGuardrail} only (the LLM guardrail is drop-scoped).
 */
@RestController
public class SupervisorController {

    private static final String CANNED_RESPONSE =
        "Lo siento, solo puedo ayudarte con diagnóstico de la red 5G RAN: caídas de " +
        "llamadas (retenibilidad) y planificación de PCI. Reformula tu pregunta sobre " +
        "una celda, tasas de caída, o conflictos de PCI.";

    @Autowired private RanSupervisorAgent  supervisor;
    @Autowired private OrchestratorTools   orchestratorTools;
    @Autowired private InputGuardrail      inputGuardrail;
    @Autowired private AgentLogRepository  logRepo;

    /** LLM-orchestrated entry point. */
    @GetMapping("/agent/ran")
    public String chat(@RequestParam String message) {
        GuardrailResult fast = inputGuardrail.check(message);
        if (!fast.allowed()) {
            saveBlock(message, fast.reason());
            return CANNED_RESPONSE;
        }
        return supervisor.chat(message);
    }

    /** Deterministic cross-module diagnosis (no LLM) — one line per module + a correlated verdict. */
    @GetMapping("/agent/ran/diagnose")
    public String diagnose(@RequestParam String cell) {
        return orchestratorTools.diagnoseCell(cell);
    }

    private void saveBlock(String message, String reason) {
        AgentLog entry = new AgentLog();
        entry.setAgentName("orchestrator");
        entry.setToolCalled("GUARDRAIL_BLOCK");
        entry.setToolInput(message != null && message.length() > 500
            ? message.substring(0, 500) + "...[truncated]" : message);
        entry.setToolOutput("Blocked: " + reason);
        entry.setLatencyMs(0L);
        logRepo.save(entry);
    }
}
