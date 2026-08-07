package com.ranadvisor.pci;

import com.ranadvisor.drops.entity.NrCellDrops;
import com.ranadvisor.drops.metrics.DropTotals;
import com.ranadvisor.drops.repository.NrCellDropsRepository;
import com.ranadvisor.pci.PciConflict;
import com.ranadvisor.pci.planner.PciAudit;
import com.ranadvisor.pci.planner.PciPlannerPort;
import com.ranadvisor.pci.planner.PciPlannerUnavailableException;
import com.ranadvisor.pci.planner.PciProposal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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
    @Autowired private NrCellDropsRepository dropsRepo;
    @Autowired private PciTimelineCorrelator timeline;

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

        String impact = renderBothSides(audit, cellName);
        if (!impact.isEmpty()) sb.append(impact).append('\n');

        String when = timeline.correlate(cellName, audit);
        if (!when.isEmpty()) sb.append(when).append('\n');

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

    /**
     * A collision or confusion has two victims, not one. The conflict list names the partner
     * cell but says nothing about how badly it is doing, so the two ends read as unrelated
     * problems and get two tickets — and after the re-plan, nobody checks whether the second
     * one recovered.
     *
     * <p>This block pulls the partner's drop rate over the whole dataset and prints both sides
     * together. It is deliberately whole-history rather than windowed: this is an impact
     * statement about a configuration fault, not a trend, and the caller may not have supplied
     * a period at all.
     */
    private String renderBothSides(PciAudit audit, String cellName) {
        StringBuilder sb = new StringBuilder();

        for (PciConflict c : audit.conflicts()) {
            if (!PciConflict.COLLISION.equals(c.type()) && !PciConflict.CONFUSION.equals(c.type())) {
                continue;   // mod-3 degrades quality; it does not make two cells indistinguishable
            }
            String partner = cellName.equals(c.cellA()) ? c.cellB() : c.cellA();
            if (partner == null || partner.equals(cellName)) continue;

            String self = dropRateOf(cellName);
            String other = dropRateOf(partner);
            if (self == null && other == null) continue;

            sb.append("Both sides of this ").append(c.type().toLowerCase()).append(":\n");
            sb.append("  ").append(cellName).append("  ").append(self == null ? "no drop data" : self).append('\n');
            sb.append("  ").append(partner).append("  ").append(other == null ? "no drop data" : other).append('\n');
            sb.append("  These are one fault with two victims. Track them as a single change: the "
                    + "re-plan should improve both, and if only one recovers the conflict was not the "
                    + "whole story.\n");
        }
        return sb.toString();
    }

    /** Drop rate of a cell over all loaded samples, or null when the cell has no drop data. */
    private String dropRateOf(String cellName) {
        List<NrCellDrops> rows = dropsRepo.findByCellNameOrderBySampleTimeAsc(cellName);
        if (rows.isEmpty()) return null;
        DropTotals t = DropTotals.of(rows);
        return String.format("drop=%.2f%% (%s), dominant=%s", t.dropRate(), t.severity(), t.dominantShort());
    }
}
