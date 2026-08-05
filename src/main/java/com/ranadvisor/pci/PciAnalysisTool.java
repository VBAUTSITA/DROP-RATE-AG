package com.ranadvisor.pci;

import com.ranadvisor.pci.entity.PciCell;
import com.ranadvisor.pci.entity.PciNeighbor;
import com.ranadvisor.pci.repository.PciCellRepository;
import com.ranadvisor.pci.repository.PciNeighborRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PCI (Physical Cell Identity) conflict detection over the local PCI mirror.
 *
 * <p>This class is the <b>local detection engine</b>, not a tool surface: it is
 * deterministic (no LLM) and knows nothing about agents. The {@code @Tool} methods that
 * used to live here moved to {@link com.ranadvisor.pci.PciPlannerTools}, which talks to a
 * {@link com.ranadvisor.pci.planner.PciPlannerPort} so the same tools work against either
 * this mirror or the real multi-technology planner.
 *
 * <p>Neighbour set of a cell = explicit ANR relations (either direction) ∪ co-sited
 * cells (same gNodeB). mod-3 (PSS) conflicts are only meaningful within the same NR-ARFCN.
 */
@Component
public class PciAnalysisTool {

    @Autowired private PciCellRepository cellRepo;
    @Autowired private PciNeighborRepository neighborRepo;

    public static final int MAX_PCI = 503;

    // ─── typed detection (used by @Tool methods AND by PciModule/orchestrator) ──

    /** Undirected neighbours of a cell: explicit ANR relations ∪ co-sited cells. */
    public List<PciCell> neighborsOf(String cellName) {
        PciCell self = cellRepo.findByCellName(cellName);
        Set<String> names = new LinkedHashSet<>();
        for (PciNeighbor n : neighborRepo.findByCellName(cellName)) names.add(n.getNeighborCellName());
        for (PciNeighbor n : neighborRepo.findByNeighborCellName(cellName)) names.add(n.getCellName());
        if (self != null && self.getGnodebName() != null) {
            for (PciCell c : cellRepo.findByGnodebName(self.getGnodebName())) names.add(c.getCellName());
        }
        names.remove(cellName);
        List<PciCell> result = new ArrayList<>();
        for (String nm : names) {
            PciCell c = cellRepo.findByCellName(nm);
            if (c != null) result.add(c);
        }
        return result;
    }

    private static boolean sameArfcn(PciCell a, PciCell b) {
        return a.getNrArfcn() != null && a.getNrArfcn().equals(b.getNrArfcn());
    }

    /** All PCI conflicts observed from {@code cellName}'s perspective. */
    public List<PciConflict> detectForCell(String cellName) {
        List<PciConflict> out = new ArrayList<>();
        PciCell self = cellRepo.findByCellName(cellName);
        if (self == null || self.getPci() == null) return out;
        List<PciCell> neighbors = neighborsOf(cellName);

        // COLLISION + MOD3 (pairwise vs each neighbour, same carrier)
        for (PciCell n : neighbors) {
            if (n.getPci() == null || !sameArfcn(self, n)) continue;
            if (n.getPci().equals(self.getPci())) {
                out.add(new PciConflict(PciConflict.COLLISION, self.getCellName(), self.getPci(),
                        n.getCellName(), n.getPci(), "Re-plan one of the two cells."));
            } else if (n.getPci() % 3 == self.getPci() % 3) {
                out.add(new PciConflict(PciConflict.MOD3, self.getCellName(), self.getPci(),
                        n.getCellName(), n.getPci(), "PSS/sync interference at cell edge."));
            }
        }

        // CONFUSION: two distinct neighbours sharing a PCI
        Map<Integer, List<String>> byPci = new LinkedHashMap<>();
        for (PciCell n : neighbors) {
            if (n.getPci() == null) continue;
            byPci.computeIfAbsent(n.getPci(), k -> new ArrayList<>()).add(n.getCellName());
        }
        for (Map.Entry<Integer, List<String>> e : byPci.entrySet()) {
            List<String> cells = e.getValue();
            if (cells.size() >= 2) {
                out.add(new PciConflict(PciConflict.CONFUSION, self.getCellName(), e.getKey(),
                        cells.get(0), e.getKey(), cells.get(1)));
            }
        }
        return out;
    }

    /** Whole-network scan, de-duplicated (symmetric COLLISION/MOD3 pairs counted once). */
    public List<PciConflict> detectNetworkWide() {
        List<PciConflict> all = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PciCell c : cellRepo.findAll()) {
            for (PciConflict cf : detectForCell(c.getCellName())) {
                String key = switch (cf.type()) {
                    case PciConflict.CONFUSION -> "CONF|" + cf.cellA() + "|" + cf.pciA();
                    default -> {
                        // unordered pair key so A-B and B-A collapse
                        String x = cf.cellA(), y = cf.cellB();
                        yield cf.type() + "|" + (x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x);
                    }
                };
                if (seen.add(key)) all.add(cf);
            }
        }
        return all;
    }

    /**
     * The constraint set the PCI search runs against, kept as a value so a caller can
     * explain <i>why</i> a value was chosen instead of only reporting the number.
     *
     * @param bannedExact     PCIs excluded outright — a neighbour's PCI (collision) or a
     *                        second-tier cell's PCI (would create confusion)
     * @param bannedMod3      PSS groups excluded because a same-carrier neighbour occupies them
     * @param neighbourCount  size of the neighbour set considered
     * @param sameCarrierCount how many of those share the cell's NR-ARFCN (mod-3 only applies there)
     */
    public record PciSearchSpace(
            Set<Integer> bannedExact,
            Set<Integer> bannedMod3,
            int neighbourCount,
            int sameCarrierCount) {}

    /**
     * Build the constraint set for a cell: any neighbour's PCI (collision), any
     * same-carrier neighbour's PSS group (mod-3), and any PCI already used by a
     * second-tier cell that shares a neighbour with the target (would create confusion).
     */
    public PciSearchSpace searchSpace(String cellName) {
        PciCell self = cellRepo.findByCellName(cellName);
        if (self == null) return null;
        List<PciCell> neighbors = neighborsOf(cellName);

        Set<Integer> bannedExact = new LinkedHashSet<>();   // collision + confusion sources
        Set<Integer> bannedMod3  = new LinkedHashSet<>();   // same-carrier PSS groups
        int sameCarrier = 0;
        for (PciCell n : neighbors) {
            if (n.getPci() == null) continue;
            bannedExact.add(n.getPci());
            if (sameArfcn(self, n)) {
                bannedMod3.add(n.getPci() % 3);
                sameCarrier++;
            }
            // second tier: cells sharing a neighbour with us must not equal our new PCI
            for (PciCell m : neighborsOf(n.getCellName())) {
                if (m.getPci() != null && !m.getCellName().equals(cellName)) bannedExact.add(m.getPci());
            }
        }
        return new PciSearchSpace(bannedExact, bannedMod3, neighbors.size(), sameCarrier);
    }

    /**
     * Suggest a conflict-free PCI for a cell: avoids any neighbour's PCI (collision),
     * any same-carrier neighbour's PSS group (mod-3), and any PCI already used by a
     * second-tier cell that shares a neighbour with the target (would create confusion).
     */
    public Integer suggestPci(String cellName) {
        PciSearchSpace space = searchSpace(cellName);
        return space == null ? null : firstFreePci(space);
    }

    /**
     * First acceptable PCI for a constraint set, or {@code null} if none exists.
     *
     * <p>Pass 1 (ideal) avoids collision, confusion AND mod-3. Pass 2 is the fallback for
     * when the same-carrier neighbourhood already occupies all three PSS groups: it
     * returns a collision/confusion-free PCI anyway, because resolving the fatal conflicts
     * matters more than a residual mod-3.
     */
    public Integer firstFreePci(PciSearchSpace space) {
        for (int pci = 0; pci <= MAX_PCI; pci++) {
            if (space.bannedExact().contains(pci)) continue;
            if (space.bannedMod3().contains(pci % 3)) continue;
            return pci;
        }
        for (int pci = 0; pci <= MAX_PCI; pci++) {
            if (!space.bannedExact().contains(pci)) return pci;
        }
        return null;
    }

    /** True when {@link #firstFreePci} had to fall back to pass 2 (mod-3 unavoidable). */
    public boolean mod3Unavoidable(PciSearchSpace space) {
        return space.bannedMod3().size() >= 3;
    }

    /** Read-through to the mirror, so adapters do not need their own repository handle. */
    public PciCell findCell(String cellName) {
        return cellRepo.findByCellName(cellName);
    }

    /** Every cell in the mirror — used by the network-wide audit. */
    public List<PciCell> allCells() {
        return cellRepo.findAll();
    }

    /** True if {@link #suggestPci} can also avoid mod-3 (pass 1), false if only collision/confusion. */
    public boolean canAvoidMod3(String cellName) {
        Integer s = suggestPci(cellName);
        if (s == null) return false;
        PciCell self = cellRepo.findByCellName(cellName);
        if (self == null) return false;
        for (PciCell n : neighborsOf(cellName)) {
            if (n.getPci() != null && sameArfcn(self, n) && n.getPci() % 3 == s % 3) return false;
        }
        return true;
    }
}
