package com.ranadvisor.pci;

import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import com.ranadvisor.pci.planner.CellPlan;
import com.ranadvisor.pci.planner.PciAudit;
import com.ranadvisor.pci.planner.PciPlannerPort;
import com.ranadvisor.pci.planner.PciPlannerUnavailableException;
import com.ranadvisor.pci.planner.PciProposal;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * The PCI tool surface exposed to the agent.
 *
 * <p>Every method goes through {@link PciPlannerPort}, so the same four tools work
 * whether the answer comes from the local mirror or from the real planning engine —
 * only the backend named in the output changes.
 *
 * <p><b>There is no tool that assigns a PCI.</b> Anything annotated {@code @Tool} can be
 * invoked by the model on its own initiative from a sentence in a chat; the write path is
 * exactly the thing that must not have that property. {@link #proposePciReplan} simulates
 * and returns the reasoning; applying it stays a human action in the planning tool.
 */
@Component
public class PciPlannerTools {

    @Autowired private PciPlannerPort planner;
    @Autowired private AgentLogRepository logRepo;

    @Tool("Get the current PCI/RSI plan of a cell: its PCI, PSS group, RSI, carrier and azimuth. Use when the user asks what PCI a cell currently has.")
    public String getCellPci(String cellName) {
        long start = System.currentTimeMillis();
        String result;
        try {
            CellPlan p = planner.plan(cellName);
            result = p.found()
                    ? String.format("Cell: %s%nSite: %s%nPCI: %s (PSS group %d)%nRSI: %s%nCarrier: %s%nAzimuth: %s%nSource: %s",
                        p.cellName(), nvl(p.site()), nvl(p.pci()), p.pssGroup(), nvl(p.rsi()),
                        nvl(p.carrier()), nvl(p.azimuthDeg()), planner.backendName())
                    : "Cell not found in the PCI plan: " + cellName;
        } catch (PciPlannerUnavailableException e) {
            result = unreachable(e);
        }
        log("getCellPci", cellName, result, start);
        return result;
    }

    @Tool("Audit one cell for PCI conflicts (collision, confusion, mod-3/PSS). Use to check whether a cell has a PCI problem, or as a root-cause check for a cell with high drops or RACH/handover failures. This only reads — it changes nothing.")
    public String auditCellPci(String cellName) {
        long start = System.currentTimeMillis();
        String result;
        try {
            PciAudit audit = planner.audit(cellName);
            result = audit.isClean()
                    ? String.format("No PCI conflicts found for %s.%nChecked by: %s.%n"
                        + "A clean PCI plan is itself a result: it rules identity out as the cause and points the "
                        + "investigation at another domain.", cellName, planner.backendName())
                    : renderAudit(audit, cellName);
        } catch (PciPlannerUnavailableException e) {
            result = unreachable(e);
        }
        log("auditCellPci", cellName, result, start);
        return result;
    }

    @Tool("Audit the whole network for PCI conflicts and list the affected cells. Use when the user asks for a network-wide PCI audit or which cells have PCI problems.")
    public String auditNetworkPci() {
        long start = System.currentTimeMillis();
        String result;
        try {
            PciAudit audit = planner.auditNetwork();
            if (audit.isClean()) {
                result = "No PCI conflicts detected across the network. Checked by: " + planner.backendName() + ".";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Network PCI audit: %d conflict(s) — %d collision, %d confusion, %d mod-3.%n",
                        audit.conflicts().size(),
                        audit.count(PciConflict.COLLISION),
                        audit.count(PciConflict.CONFUSION),
                        audit.count(PciConflict.MOD3)));
                audit.conflicts().stream()
                        .sorted(Comparator.comparing(PciConflict::type))
                        .forEach(c -> sb.append("  - ").append(c).append('\n'));
                sb.append("Checked by: ").append(planner.backendName()).append('.');
                result = sb.toString();
            }
        } catch (PciPlannerUnavailableException e) {
            result = unreachable(e);
        }
        log("auditNetworkPci", "(network)", result, start);
        return result;
    }

    @Tool("Compute a conflict-free PCI/RSI re-plan proposal for a cell, with the planning engine's reasoning for why that value was chosen. Use when the user asks how to fix or re-plan a cell's PCI. This is a simulation only: it never writes anything to the network, and the proposal has to be applied by a person in the planning tool.")
    public String proposePciReplan(String cellName) {
        long start = System.currentTimeMillis();
        String result;
        try {
            result = renderProposal(planner.propose(cellName));
        } catch (PciPlannerUnavailableException e) {
            result = unreachable(e);
        }
        log("proposePciReplan", cellName, result, start);
        return result;
    }

    // ─── rendering (shared with the orchestrator's PCI hand-off) ────────────────

    /** Formats an audit that has at least one conflict. */
    public String renderAudit(PciAudit audit, String cellName) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("PCI audit for %s — %d conflict(s):%n", cellName, audit.conflicts().size()));
        for (PciConflict c : audit.conflicts()) sb.append("  - ").append(c).append('\n');
        sb.append(audit.hasFatal()
                ? "Severity: fatal. A collision or confusion means the UE cannot resolve the cell during "
                  + "access or handover, which shows up as RACH failures and drops.\n"
                : "Severity: degrading, not fatal. A mod-3 conflict lowers sync-signal SINR at the cell edge.\n");
        sb.append("Checked by: ").append(audit.source()).append('.');
        return sb.toString();
    }

    /** Formats a re-plan proposal, including its audit trail and its limits. */
    public String renderProposal(PciProposal p) {
        StringBuilder sb = new StringBuilder();
        if (!p.available()) {
            sb.append(String.format("No re-plan could be computed for %s.%n", p.cellName()));
        } else {
            sb.append(String.format("Re-plan proposal for %s — SIMULATION, nothing was written to the network.%n%n",
                    p.cellName()));
            sb.append(String.format("  Current PCI:  %s%n", nvl(p.currentPci())));
            sb.append(String.format("  Proposed PCI: %d (PSS group %d)%n", p.proposedPci(), p.proposedPssGroup()));
            sb.append(String.format("  Proposed RSI: %s%n%n", p.proposedRsi() == null ? "unknown" : p.proposedRsi()));
        }
        if (!p.auditTrail().isEmpty()) {
            sb.append("How the engine got there:\n");
            for (String line : p.auditTrail()) sb.append("  ").append(line).append('\n');
            sb.append('\n');
        }
        if (!p.warnings().isEmpty()) {
            sb.append("Caveats:\n");
            for (String w : p.warnings()) sb.append("  - ").append(w).append('\n');
            sb.append('\n');
        }
        sb.append("Computed by: ").append(p.source()).append('\n');
        sb.append("Next step: a person reviews this in the planning tool and applies it there. "
                + "Then re-measure the cell's drop rate 24-48h later to confirm the fix.");
        return sb.toString();
    }

    private String unreachable(PciPlannerUnavailableException e) {
        return "The PCI planning backend is unavailable, so this could not be checked: " + e.getMessage()
                + "\nNo conclusion should be drawn about the cell's PCI from this.";
    }

    private static String nvl(Object v) {
        return v == null ? "unknown" : String.valueOf(v);
    }

    private void log(String tool, String input, String output, long startMs) {
        try {
            AgentLog entry = new AgentLog();
            entry.setAgentName("pci");
            entry.setToolCalled(tool);
            entry.setToolInput(input);
            entry.setToolOutput(output != null && output.length() > 2000
                    ? output.substring(0, 2000) + "...[truncated]" : output);
            entry.setLatencyMs(System.currentTimeMillis() - startMs);
            logRepo.save(entry);
        } catch (Exception e) {
            System.err.println("[PciPlannerTools] Failed to save log entry: " + e.getMessage());
        }
    }

    /** Exposed so the orchestrator can reuse the same wording in its PCI hand-off. */
    public List<PciConflict> conflictsOf(PciAudit audit) {
        return audit.conflicts();
    }
}
