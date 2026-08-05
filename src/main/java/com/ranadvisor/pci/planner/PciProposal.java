package com.ranadvisor.pci.planner;

import java.util.List;

/**
 * Result of a RE-PLAN pass: a candidate PCI (and RSI, where the backend knows it) plus
 * the reasoning that produced it.
 *
 * <p>{@code auditTrail} is the point of this type. A bare integer is not actionable — an
 * RF engineer needs to see which PCIs were excluded and why before touching a live cell.
 * The real engine already emits this (its {@code AUDITORIA_PCI} per cell); the local
 * backend reconstructs an equivalent trail from its own search.
 *
 * <p>A proposal is <b>always a simulation</b>: nothing on this path writes to the
 * network. Applying it stays a human action in the planning tool.
 */
public record PciProposal(
        String cellName,
        Integer currentPci,
        Integer proposedPci,
        Integer proposedRsi,
        List<String> auditTrail,
        List<String> warnings,
        String source) {

    /** True when the backend could compute a candidate. */
    public boolean available() {
        return proposedPci != null;
    }

    /** Never true. Present so callers cannot mistake a proposal for an applied change. */
    public boolean applied() {
        return false;
    }

    public int proposedPssGroup() {
        return proposedPci == null ? -1 : proposedPci % 3;
    }

    public static PciProposal unavailable(String cellName, Integer currentPci, String reason, String source) {
        return new PciProposal(cellName, currentPci, null, null, List.of(), List.of(reason), source);
    }
}
