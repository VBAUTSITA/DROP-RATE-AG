package com.ranadvisor.pci.planner;

/**
 * The PCI/RSI plan of a single cell as the planning backend sees it.
 *
 * <p>{@code rsi} is nullable on purpose: the local mirror does not model RSI at all
 * (the real engine derives it from a lookup table), and saying "unknown" is more honest
 * than inventing a value.
 */
public record CellPlan(
        String cellName,
        String site,
        Integer pci,
        Integer rsi,
        String carrier,
        Integer azimuthDeg,
        boolean found) {

    public static CellPlan notFound(String cellName) {
        return new CellPlan(cellName, null, null, null, null, null, false);
    }

    /** PSS group (PCI mod 3), or -1 when the PCI is unknown. */
    public int pssGroup() {
        return pci == null ? -1 : pci % 3;
    }
}
