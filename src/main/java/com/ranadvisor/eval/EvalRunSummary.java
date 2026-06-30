/*
 * Package: com.ranadvisor.eval
 * Purpose: Immutable summary of a full eval run, returned to the caller of EvalRunnerService.runAll.
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import java.util.List;

public record EvalRunSummary(
    String runLabel,
    int total,
    int passed,
    int failed,
    double passRate,
    List<Long> failedCaseIds
) {}
