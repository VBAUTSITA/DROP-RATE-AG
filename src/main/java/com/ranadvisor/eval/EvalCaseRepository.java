/*
 * Package: com.ranadvisor.eval
 * Purpose: Spring Data JPA repository for EvalCase rows, with a lookup by agent.
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvalCaseRepository extends JpaRepository<EvalCase, Long> {
    List<EvalCase> findByAgent(String agent);
}
