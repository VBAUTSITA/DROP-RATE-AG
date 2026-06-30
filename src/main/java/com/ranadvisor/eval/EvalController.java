/*
 * Package: com.ranadvisor.eval
 * Purpose: REST endpoints to trigger an eval run and inspect cases and results.
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/eval")
public class EvalController {

    private final EvalRunnerService runnerService;
    private final EvalCaseRepository caseRepo;
    private final EvalResultRepository resultRepo;

    public EvalController(EvalRunnerService runnerService,
                          EvalCaseRepository caseRepo,
                          EvalResultRepository resultRepo) {
        this.runnerService = runnerService;
        this.caseRepo = caseRepo;
        this.resultRepo = resultRepo;
    }

    /** Run all eval cases and return the aggregate summary. e.g. POST /eval/run?label=baseline */
    @PostMapping("/run")
    public EvalRunSummary run(@RequestParam(defaultValue = "baseline") String label) {
        return runnerService.runAll(label);
    }

    /** All per-case results for a given run label. e.g. GET /eval/results?label=baseline */
    @GetMapping("/results")
    public List<EvalResult> results(@RequestParam(defaultValue = "baseline") String label) {
        return resultRepo.findByRunLabel(label);
    }

    /** All eval cases, for inspection of the question set. GET /eval/cases */
    @GetMapping("/cases")
    public List<EvalCase> cases() {
        return caseRepo.findAll();
    }
}
