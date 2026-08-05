package com.ranadvisor.core;

import java.util.List;

/**
 * The orchestrator's cross-module verdict for a cell: a single ranked root-cause
 * hypothesis synthesized by correlating the {@link ModuleFinding}s from every module.
 *
 * @param headline    the leading hypothesis in one line
 * @param confidence  HIGH | MEDIUM | LOW | NONE
 * @param rationale   why the correlator reached this hypothesis (which signals lined up)
 * @param recommendedActions ordered next steps for the operator
 * @param contributing the module findings that fed this hypothesis (evidence trail)
 */
public record RootCauseHypothesis(
        String headline,
        String confidence,
        String rationale,
        List<String> recommendedActions,
        List<ModuleFinding> contributing) {

    public static final String HIGH   = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW    = "LOW";
    public static final String NONE   = "NONE";
}
