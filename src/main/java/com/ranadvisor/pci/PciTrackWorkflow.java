package com.ranadvisor.pci;

import com.ranadvisor.pci.planner.PciAudit;
import com.ranadvisor.pci.planner.PciPlannerPort;
import com.ranadvisor.pci.planner.PciPlannerUnavailableException;
import com.ranadvisor.pci.planner.PciProposal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The identify → re-plan sequence, in one place because two callers need it: the
 * supervisor agent ({@code OrchestratorTools.tacklePciIssue}) and the drop-rate agent
 * ({@code DropAnalysisTool.suggestPciFixForCell}), which reaches it directly so a user
 * already talking about drops can pursue a PCI cause without switching agents.
 *
 * <p>The order is deterministic on purpose and is not left to the model to chain: a
 * proposal is only computed when the audit actually found something. Re-planning a cell
 * whose identity is already clean would move a PCI for no reason and hand the user a
 * change to apply that fixes nothing — and, because the audit is cheap and the proposal
 * is not, it would also pay for the assignment engine to answer a question nobody asked.
 *
 * <p>Nothing here writes to the network: {@code propose} is always a simulation, and
 * applying a PCI stays a human action inside the planning tool.
 */
@Component
public class PciTrackWorkflow {

    @Autowired private PciPlannerPort pciPlanner;
    @Autowired private PciPlannerTools pciTools;

    public String run(String cellName) {
        PciAudit audit;
        try {
            audit = pciPlanner.audit(cellName);
        } catch (PciPlannerUnavailableException e) {
            return "Could not audit the PCI of " + cellName + ": " + e.getMessage()
                 + "\nNo conclusion should be drawn about this cell's PCI.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("PCI track for ").append(cellName).append("\n\n");
        sb.append("Step 1 — identify\n");

        if (audit.isClean()) {
            sb.append("  No PCI conflict found for this cell (checked by ")
              .append(audit.source()).append(").\n\n");
            sb.append("Step 2 — re-plan: not applicable.\n");
            sb.append("  There is nothing to correct here, so no PCI change is proposed. Moving a PCI on a "
                    + "cell with a clean identity plan would be a change with no fault to fix.\n");
            sb.append("  This also narrows the diagnosis: identity is ruled out, so the cause of the drops "
                    + "lies in another domain (coverage, transport, or a parameter). Follow the dominant "
                    + "cause reported by the drop module.");
            return sb.toString();
        }

        sb.append("  ").append(pciTools.renderAudit(audit, cellName).replace("\n", "\n  ")).append("\n\n");
        sb.append("Step 2 — re-plan\n");

        PciProposal proposal;
        try {
            proposal = pciPlanner.propose(cellName);
        } catch (PciPlannerUnavailableException e) {
            sb.append("  The conflict above is confirmed, but the planning engine could not be reached to "
                    + "compute a new PCI: ").append(e.getMessage()).append('\n');
            sb.append("  The identification still stands — the re-plan has to be done in the planning tool.");
            return sb.toString();
        }

        sb.append("  ").append(pciTools.renderProposal(proposal).replace("\n", "\n  "));
        return sb.toString();
    }
}
