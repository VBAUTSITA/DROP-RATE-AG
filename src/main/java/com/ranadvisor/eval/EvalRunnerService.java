/*
 * Package: com.ranadvisor.eval
 * Purpose: Runs every eval case against the correct agent, scores it, persists the result, and summarizes the run.
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import com.ranadvisor.agent.TelecomAgent;
import com.ranadvisor.drops.DropRateAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvalRunnerService {

    private static final Logger log = LoggerFactory.getLogger(EvalRunnerService.class);

    private final EvalCaseRepository caseRepo;
    private final EvalResultRepository resultRepo;
    private final EvalScorer scorer;
    private final TelecomAgent telecomAgent;
    private final DropRateAgent dropRateAgent;

    public EvalRunnerService(EvalCaseRepository caseRepo,
                             EvalResultRepository resultRepo,
                             EvalScorer scorer,
                             TelecomAgent telecomAgent,
                             DropRateAgent dropRateAgent) {
        this.caseRepo = caseRepo;
        this.resultRepo = resultRepo;
        this.scorer = scorer;
        this.telecomAgent = telecomAgent;
        this.dropRateAgent = dropRateAgent;
    }

    /**
     * Loads all eval cases, runs each against its agent, scores and persists every result,
     * and returns an aggregate summary.
     *
     * KNOWN LIMITATION: the agents are invoked through their normal chat(String) interface,
     * which is backed by a single shared MessageWindowChatMemory bean (see AiConfig /
     * DropAgentConfig). That means eval calls share conversational memory both with each
     * other and with any live user traffic hitting /agent/* at the same time. Earlier cases
     * can therefore influence later ones, and results are not perfectly reproducible. For a
     * clean baseline, run the harness against an otherwise-idle instance. A proper fix would
     * give the eval runner its own memory-less agent instances (Phase 1).
     */
    public EvalRunSummary runAll(String runLabel) {
        List<EvalCase> cases = caseRepo.findAll();
        log.info("[Eval] Starting run '{}' over {} cases", runLabel, cases.size());

        int passed = 0;
        int failed = 0;
        List<Long> failedCaseIds = new ArrayList<>();

        for (EvalCase c : cases) {
            String response;
            boolean casePassed;
            long start = System.currentTimeMillis();

            try {
                response = callAgent(c.getAgent(), c.getQuestion());
                long latency = System.currentTimeMillis() - start;
                casePassed = scorer.score(c.getScoreType(), c.getExpected(), response);
                saveResult(c, response, casePassed, latency, runLabel);
            } catch (Exception e) {
                // Never let one failing case abort the whole run.
                long latency = System.currentTimeMillis() - start;
                response = "ERROR: " + e.getMessage();
                casePassed = false;
                saveResult(c, response, false, latency, runLabel);
                log.warn("[Eval] Case {} ({}) threw: {}", c.getId(), c.getAgent(), e.getMessage());
            }

            if (casePassed) {
                passed++;
            } else {
                failed++;
                failedCaseIds.add(c.getId());
            }
        }

        int total = cases.size();
        double passRate = total == 0 ? 0.0 : (double) passed / total;

        log.info("[Eval] Run '{}' complete: {}/{} passed ({}%)",
            runLabel, passed, total, String.format("%.1f", passRate * 100));

        return new EvalRunSummary(runLabel, total, passed, failed, passRate, failedCaseIds);
    }

    private String callAgent(String agent, String question) {
        if (agent == null) {
            throw new IllegalArgumentException("case.agent is null");
        }
        return switch (agent.toLowerCase()) {
            case "drops"   -> dropRateAgent.chat(question);
            case "telecom" -> telecomAgent.chat(question);
            default -> throw new IllegalArgumentException("Unknown agent: " + agent);
        };
    }

    private void saveResult(EvalCase c, String response, boolean passed, long latencyMs, String runLabel) {
        EvalResult r = new EvalResult();
        r.setEvalCaseId(c.getId());
        r.setAgentResponse(response);
        r.setPassed(passed);
        r.setScoreType(c.getScoreType());
        r.setLatencyMs(latencyMs);
        r.setRunLabel(runLabel);
        resultRepo.save(r);
    }
}
