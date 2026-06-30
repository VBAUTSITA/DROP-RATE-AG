/*
 * Package: com.ranadvisor.eval
 * Purpose: Spring Data JPA repository for EvalResult rows, with a lookup by run label.
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvalResultRepository extends JpaRepository<EvalResult, Long> {
    List<EvalResult> findByRunLabel(String runLabel);
}
