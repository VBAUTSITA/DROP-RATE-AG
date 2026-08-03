package com.ranadvisor.pci;

import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import com.ranadvisor.pci.entity.PciCell;
import com.ranadvisor.pci.entity.PciNeighbor;
import com.ranadvisor.pci.repository.PciCellRepository;
import com.ranadvisor.pci.repository.PciNeighborRepository;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PCI (Physical Cell Identity) planning + conflict diagnostics.
 *
 * <p>Detection is fully deterministic (no LLM). The {@code @Tool} methods expose it
 * to the PCI agent as formatted strings; {@link #detectForCell(String)} exposes the
 * same logic as typed {@link PciConflict}s for the orchestrator (via {@code PciModule}).
 *
 * <p>Neighbour set of a cell = explicit ANR relations (either direction) ∪ co-sited
 * cells (same gNodeB). mod-3 (PSS) conflicts are only meaningful within the same NR-ARFCN.
 */
@Component
public class PciAnalysisTool {

    @Autowired private PciCellRepository cellRepo;
    @Autowired private PciNeighborRepository neighborRepo;
    @Autowired private AgentLogRepository logRepo;

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
     * Suggest a conflict-free PCI for a cell: avoids any neighbour's PCI (collision),
     * any same-carrier neighbour's PSS group (mod-3), and any PCI already used by a
     * second-tier cell that shares a neighbour with the target (would create confusion).
     */
    public Integer suggestPci(String cellName) {
        PciCell self = cellRepo.findByCellName(cellName);
        if (self == null) return null;
        List<PciCell> neighbors = neighborsOf(cellName);

        Set<Integer> bannedExact = new LinkedHashSet<>();   // collision + confusion sources
        Set<Integer> bannedMod3  = new LinkedHashSet<>();   // same-carrier PSS groups
        for (PciCell n : neighbors) {
            if (n.getPci() == null) continue;
            bannedExact.add(n.getPci());
            if (sameArfcn(self, n)) bannedMod3.add(n.getPci() % 3);
            // second tier: cells sharing a neighbour with us must not equal our new PCI
            for (PciCell m : neighborsOf(n.getCellName())) {
                if (m.getPci() != null && !m.getCellName().equals(cellName)) bannedExact.add(m.getPci());
            }
        }
        // Pass 1 (ideal): avoid collision + confusion AND mod-3.
        for (int pci = 0; pci <= MAX_PCI; pci++) {
            if (bannedExact.contains(pci)) continue;
            if (bannedMod3.contains(pci % 3)) continue;
            return pci;
        }
        // Pass 2 (fallback): the same-carrier neighbourhood occupies all 3 PSS groups,
        // so mod-3 cannot be fully avoided. Return a collision/confusion-free PCI anyway —
        // resolving the fatal conflicts matters more than a residual mod-3.
        for (int pci = 0; pci <= MAX_PCI; pci++) {
            if (!bannedExact.contains(pci)) return pci;
        }
        return null;
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

    // ─── @Tool surface for the PCI agent ────────────────────────────────────────

    @Tool("Get the PCI plan for a specific cell: its PCI, carrier (NR-ARFCN), azimuth, gNodeB and neighbour count. Use when the user asks about the PCI/identity/plan of a cell.")
    public String getCellPci(String cellName) {
        long start = System.currentTimeMillis();
        PciCell c = cellRepo.findByCellName(cellName);
        String result;
        if (c == null) {
            result = "Cell not found in PCI plan: " + cellName;
        } else {
            List<PciCell> ns = neighborsOf(cellName);
            String neighborList = ns.stream()
                    .map(n -> n.getCellName() + "(PCI " + n.getPci() + ")")
                    .collect(Collectors.joining(", "));
            result = String.format(
                    "Cell: %s%ngNodeB: %s%nPCI: %d (PSS group %d)%nNR-ARFCN: %s%nAzimuth: %s°%nNeighbours (%d): %s",
                    c.getCellName(), c.getGnodebName(), c.getPci(), c.pssGroup(),
                    String.valueOf(c.getNrArfcn()), String.valueOf(c.getAzimuthDeg()),
                    ns.size(), neighborList.isEmpty() ? "(none)" : neighborList);
        }
        log("getCellPci", cellName, result, start);
        return result;
    }

    @Tool("Check PCI conflicts (collision, confusion, mod-3/PSS) for one specific cell and its neighbours. Use when the user asks whether a cell has a PCI problem, or as a root-cause check for a cell with high drops or handover/RACH failures.")
    public String checkCellPciConflicts(String cellName) {
        long start = System.currentTimeMillis();
        PciCell c = cellRepo.findByCellName(cellName);
        String result;
        if (c == null) {
            result = "Cell not found in PCI plan: " + cellName;
        } else {
            List<PciConflict> conflicts = detectForCell(cellName);
            if (conflicts.isEmpty()) {
                result = "No PCI conflicts found for " + cellName + " (PCI " + c.getPci() + ").";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("PCI conflicts for ").append(cellName)
                  .append(" (PCI ").append(c.getPci()).append("):\n");
                for (PciConflict cf : conflicts) sb.append(" - ").append(cf).append('\n');
                Integer suggestion = suggestPci(cellName);
                if (suggestion != null) {
                    sb.append("Suggested conflict-free PCI: ").append(suggestion)
                      .append(" (PSS group ").append(suggestion % 3).append(").");
                }
                result = sb.toString();
            }
        }
        log("checkCellPciConflicts", cellName, result, start);
        return result;
    }

    @Tool("List all PCI conflicts across the whole network (collision, confusion, mod-3). Use when the user asks for a network-wide PCI audit or which cells have PCI problems.")
    public String listPciConflicts() {
        long start = System.currentTimeMillis();
        List<PciConflict> conflicts = detectNetworkWide();
        StringBuilder sb = new StringBuilder();
        if (conflicts.isEmpty()) {
            sb.append("No PCI conflicts detected across the network.");
        } else {
            long collisions = conflicts.stream().filter(c -> c.type().equals(PciConflict.COLLISION)).count();
            long confusions = conflicts.stream().filter(c -> c.type().equals(PciConflict.CONFUSION)).count();
            long mod3 = conflicts.stream().filter(c -> c.type().equals(PciConflict.MOD3)).count();
            sb.append(String.format("Network PCI audit: %d conflict(s) — %d collision, %d confusion, %d mod-3.%n",
                    conflicts.size(), collisions, confusions, mod3));
            conflicts.stream()
                    .sorted(Comparator.comparing(PciConflict::type))
                    .forEach(cf -> sb.append(" - ").append(cf).append('\n'));
        }
        String result = sb.toString();
        log("listPciConflicts", "(none)", result, start);
        return result;
    }

    @Tool("Suggest a conflict-free PCI for a cell that avoids collision, confusion and mod-3 with its neighbours. Use when the user asks how to fix or re-plan a cell's PCI.")
    public String suggestPciTool(String cellName) {
        long start = System.currentTimeMillis();
        PciCell c = cellRepo.findByCellName(cellName);
        String result;
        if (c == null) {
            result = "Cell not found in PCI plan: " + cellName;
        } else {
            Integer suggestion = suggestPci(cellName);
            result = suggestion == null
                    ? "No conflict-free PCI available in 0..503 for " + cellName + " (neighbour set too dense)."
                    : String.format("Re-plan %s from PCI %d to PCI %d (PSS group %d): avoids collision, confusion and mod-3 with all current neighbours.",
                        cellName, c.getPci(), suggestion, suggestion % 3);
        }
        log("suggestPci", cellName, result, start);
        return result;
    }

    // ─── logging helper (mirrors DropAnalysisTool) ──────────────────────────────

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
            System.err.println("[PciAnalysisTool] Failed to save log entry: " + e.getMessage());
        }
    }
}
