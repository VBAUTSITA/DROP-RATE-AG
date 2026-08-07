package com.ranadvisor.pci.planner;

import com.ranadvisor.pci.PciAnalysisTool;
import com.ranadvisor.pci.PciConflict;
import com.ranadvisor.pci.entity.PciCell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default backend: answers from the local PCI mirror in PostgreSQL ({@code pci_cell} /
 * {@code pci_neighbor}), so the whole diagnose → identify → re-plan flow runs today
 * without the external planning tool being reachable.
 *
 * <p>Two things it deliberately does <b>not</b> pretend to do, because the real engine
 * does them and this does not:
 * <ul>
 *   <li><b>RSI</b> — the real engine derives it from a lookup table keyed by coverage and
 *       PCI. Here it stays {@code null}, and the proposal says so in its warnings rather
 *       than emitting a made-up number.</li>
 *   <li><b>Geometry</b> — the real engine picks a PCI from wedge-intersection areas and
 *       timing-advance–derived cell radii. This searches the first value that satisfies the
 *       constraint set. That is a correct answer to "which PCIs are free", not to "which
 *       PCI is best".</li>
 * </ul>
 * Both limits are stated in every proposal's audit trail — an operator must be able to see
 * which backend answered before acting on a live cell.
 */
@Component
@ConditionalOnProperty(name = "pci.planner.backend", havingValue = "local", matchIfMissing = true)
public class LocalPciPlannerAdapter implements PciPlannerPort {

    static final String SOURCE = "local-mirror";

    @Autowired private PciAnalysisTool detection;

    @Override
    public String backendName() {
        return "local PCI mirror (seeded PostgreSQL tables) — conflict detection only, no RF geometry, no RSI";
    }

    @Override
    public CellPlan plan(String cellName) {
        PciCell c = detection.findCell(cellName);
        if (c == null) return CellPlan.notFound(cellName);
        return new CellPlan(
                c.getCellName(),
                c.getGnodebName(),
                c.getPci(),
                null,                                   // RSI is not modelled by the mirror
                c.getNrArfcn() == null ? null : String.valueOf(c.getNrArfcn()),
                c.getAzimuthDeg(),
                true);
    }

    @Override
    public PciAudit audit(String cellName) {
        if (detection.findCell(cellName) == null) {
            return PciAudit.clean(cellName, SOURCE);
        }
        return new PciAudit(cellName, detection.detectForCell(cellName), SOURCE);
    }

    @Override
    public PciAudit auditNetwork() {
        return new PciAudit("(network)", detection.detectNetworkWide(), SOURCE);
    }

    @Override
    public PciProposal propose(String cellName) {
        PciCell self = detection.findCell(cellName);
        if (self == null) {
            return PciProposal.unavailable(cellName, null,
                    "Cell is not present in the PCI mirror, so no re-plan can be computed.", SOURCE);
        }

        PciAnalysisTool.PciSearchSpace space = detection.searchSpace(cellName);
        Integer proposed = detection.firstFreePci(space);

        List<String> trail = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        trail.add(String.format("Step 1 — neighbourhood: %d neighbour(s), %d of them on the same carrier "
                        + "(mod-3 only constrains same-carrier cells).",
                space.neighbourCount(), space.sameCarrierCount()));
        trail.add(String.format("Step 2 — PCIs excluded (collision with a neighbour, or confusion via a "
                        + "second-tier cell): %s.", asList(space.bannedExact())));
        trail.add(String.format("Step 3 — PSS groups excluded (occupied by a same-carrier neighbour): %s.",
                asList(space.bannedMod3())));

        if (proposed == null) {
            warnings.add("No PCI in 0.." + PciAnalysisTool.MAX_PCI + " satisfies the constraint set — "
                    + "the neighbourhood is saturated.");
            trail.add("Step 4 — search over 0.." + PciAnalysisTool.MAX_PCI + " found no candidate.");
            return new PciProposal(cellName, self.getPci(), null, null, trail, warnings, SOURCE);
        }

        boolean mod3Cleared = !space.bannedMod3().contains(proposed % 3);
        trail.add(String.format("Step 4 — first value in 0..%d clearing those constraints: PCI %d (PSS group %d).",
                PciAnalysisTool.MAX_PCI, proposed, proposed % 3));

        if (!mod3Cleared) {
            warnings.add(String.format(
                    "mod-3 could not be cleared: the same-carrier neighbourhood already occupies all three PSS "
                    + "groups, so PCI %d still shares PSS group %d with a neighbour. It does resolve the "
                    + "collision/confusion, which is the fatal part.", proposed, proposed % 3));
        }

        warnings.add("RSI is not modelled by this backend — the real planning engine derives it from its "
                + "lookup table. Treat the RSI as unknown until the engine is consulted.");
        warnings.add("This candidate satisfies the identity constraints but is not RF-optimised: the real "
                + "engine also weighs sector geometry and timing-advance–derived cell radii.");

        trail.add("Step 5 — not applied. This is a simulation; assigning the PCI stays a human action in the "
                + "planning tool.");

        return new PciProposal(cellName, self.getPci(), proposed, null, trail, warnings, SOURCE);
    }

    /** Renders a constraint set compactly, capped so a dense neighbourhood cannot flood the answer. */
    private static String asList(java.util.Set<Integer> values) {
        if (values.isEmpty()) return "(none)";
        List<Integer> sorted = values.stream().sorted().collect(Collectors.toList());
        if (sorted.size() <= 12) {
            return sorted.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        String head = sorted.subList(0, 12).stream().map(String::valueOf).collect(Collectors.joining(", "));
        return head + ", … (" + sorted.size() + " values in total)";
    }

    /** Exposed for the tool layer so a clean cell can still be described precisely. */
    public static boolean isFatal(PciConflict c) {
        return PciConflict.COLLISION.equals(c.type()) || PciConflict.CONFUSION.equals(c.type());
    }
}
