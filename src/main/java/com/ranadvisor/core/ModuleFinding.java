package com.ranadvisor.core;

import java.util.Set;

/**
 * The single, typed unit of output every diagnostic module returns for a cell.
 *
 * <p>This is the contract that lets the orchestrator treat all modules uniformly:
 * it never parses a module's free-text answer — it reads {@code severity} and the
 * machine-readable {@code tags}, and correlates those across modules. Human-facing
 * prose stays in {@code headline}/{@code detail}; orchestration logic keys off tags.
 *
 * @param module   stable module id, e.g. "drop-rate" or "pci-planning"
 * @param cellName the cell this finding is about
 * @param severity CRITICAL | WARNING | OK | UNKNOWN
 * @param headline one-line, human-readable summary
 * @param tags     machine-readable signals for cross-module correlation
 *                 (e.g. HIGH_DROP, RACH_FAILURE, PCI_COLLISION)
 * @param detail   longer human-readable explanation / evidence
 */
public record ModuleFinding(
        String module,
        String cellName,
        String severity,
        String headline,
        Set<String> tags,
        String detail) {

    public static final String CRITICAL = "CRITICAL";
    public static final String WARNING  = "WARNING";
    public static final String OK       = "OK";
    public static final String UNKNOWN  = "UNKNOWN";

    /** A "module has no data for this cell" finding. */
    public static ModuleFinding none(String module, String cellName) {
        return new ModuleFinding(module, cellName, UNKNOWN,
                module + ": no data for cell " + cellName, Set.of(), "");
    }

    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

    public boolean isActionable() {
        return CRITICAL.equals(severity) || WARNING.equals(severity);
    }
}
