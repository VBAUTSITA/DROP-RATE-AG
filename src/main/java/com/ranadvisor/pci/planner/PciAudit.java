package com.ranadvisor.pci.planner;

import com.ranadvisor.pci.PciConflict;

import java.util.List;

/**
 * Result of an IDENTIFY pass: every PCI conflict the backend attributes to a cell
 * (or to the whole network, for {@link PciPlannerPort#auditNetwork()}).
 *
 * <p>{@code source} names the backend that produced it so the answer can say whether the
 * verdict came from the real planning engine or from the local mirror — an operator will
 * not act on a re-plan without knowing that.
 */
public record PciAudit(
        String cellName,
        List<PciConflict> conflicts,
        String source) {

    public static PciAudit clean(String cellName, String source) {
        return new PciAudit(cellName, List.of(), source);
    }

    public boolean isClean() {
        return conflicts.isEmpty();
    }

    /** Collision or confusion — the UE genuinely cannot resolve the cell. Drives CRITICAL. */
    public boolean hasFatal() {
        return has(PciConflict.COLLISION) || has(PciConflict.CONFUSION);
    }

    /** mod-3 only — degraded sync SINR at cell edge, not fatal. Drives WARNING. */
    public boolean hasMod3() {
        return has(PciConflict.MOD3);
    }

    public boolean has(String type) {
        return conflicts.stream().anyMatch(c -> type.equals(c.type()));
    }

    public long count(String type) {
        return conflicts.stream().filter(c -> type.equals(c.type())).count();
    }

    /** Most severe conflict kind present, as a phrase for prose. Empty when clean. */
    public String worstKind() {
        if (has(PciConflict.COLLISION)) return "collision";
        if (has(PciConflict.CONFUSION)) return "confusion";
        if (has(PciConflict.MOD3)) return "mod-3 conflict";
        return "";
    }
}
