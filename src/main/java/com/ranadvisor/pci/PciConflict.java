package com.ranadvisor.pci;

/**
 * A single detected PCI planning problem.
 *
 * <ul>
 *   <li>COLLISION — two neighbouring cells on the same carrier share the same PCI.
 *       Fatal: the UE cannot tell them apart → handover/RACH failures.</li>
 *   <li>CONFUSION — one cell has two distinct neighbours that share a PCI.
 *       The source cell cannot resolve a handover target → wrong-cell HO, drops.</li>
 *   <li>MOD3 — two neighbouring cells on the same carrier have PCI mod 3 equal
 *       (same PSS group) → sync-signal interference, degraded cell-edge RACH/SINR.</li>
 * </ul>
 */
public record PciConflict(
        String type,
        String cellA, Integer pciA,
        String cellB, Integer pciB,
        String note) {

    public static final String COLLISION = "COLLISION";
    public static final String CONFUSION = "CONFUSION";
    public static final String MOD3      = "MOD3";

    @Override
    public String toString() {
        return switch (type) {
            case COLLISION -> String.format("COLLISION: %s and %s both use PCI %d (neighbours, same carrier). %s",
                    cellA, cellB, pciA, note);
            case CONFUSION -> String.format("CONFUSION at %s: neighbours %s and %s both use PCI %d.",
                    cellA, cellB, note, pciA);
            case MOD3 -> String.format("MOD3 conflict: %s (PCI %d) and %s (PCI %d) share PSS group %d (neighbours, same carrier). %s",
                    cellA, pciA, cellB, pciB, pciA % 3, note);
            default -> type + ": " + cellA + " / " + cellB;
        };
    }
}
