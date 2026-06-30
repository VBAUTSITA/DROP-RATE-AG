/*
 * Package: com.ranadvisor.eval
 * Purpose: Decides whether an agent response passes a case, using one of three scoring strategies.
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EvalScorer {

    private static final Logger log = LoggerFactory.getLogger(EvalScorer.class);

    /**
     * Returns true if the actual response satisfies the expected value under the given strategy.
     *
     * @param scoreType "exact", "contains", or "llm_judge"
     * @param expected  the ground-truth fragment from the EvalCase
     * @param actualResponse the raw agent output
     */
    public boolean score(String scoreType, String expected, String actualResponse) {
        if (actualResponse == null || expected == null || scoreType == null) {
            return false;
        }

        return switch (scoreType.toLowerCase()) {
            case "exact" -> actualResponse.trim().equalsIgnoreCase(expected.trim());

            case "contains" -> actualResponse.toLowerCase().contains(expected.toLowerCase());

            case "llm_judge" -> {
                // TODO (Phase 1): implement LLM-as-judge scoring. This requires a second LLM
                // call to a (preferably stronger) model that reads the question, the expected
                // answer, and the agent's free-text response, and returns a pass/fail verdict.
                // Until then we fail closed so an unimplemented strategy can never silently
                // inflate the pass rate.
                log.warn("score_type 'llm_judge' is not yet implemented (Phase 1). " +
                         "Returning false. expected='{}'", expected);
                yield false;
            }

            default -> {
                log.warn("Unknown score_type '{}'. Returning false.", scoreType);
                yield false;
            }
        };
    }
}
