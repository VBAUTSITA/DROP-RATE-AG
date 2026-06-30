/*
 * Package: com.ranadvisor.eval
 * Purpose: JPA entity recording the outcome of running one EvalCase against an agent (eval_results table).
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eval_results")
public class EvalResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "eval_case_id")
    private Long evalCaseId;         // FK reference to eval_cases.id

    @Column(name = "agent_response", columnDefinition = "TEXT")
    private String agentResponse;    // full raw response from the agent

    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "score_type", length = 20)
    private String scoreType;        // copied from the case at run time

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "run_label")
    private String runLabel;         // e.g. "baseline-2026-06-29"

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEvalCaseId() { return evalCaseId; }
    public void setEvalCaseId(Long evalCaseId) { this.evalCaseId = evalCaseId; }
    public String getAgentResponse() { return agentResponse; }
    public void setAgentResponse(String agentResponse) { this.agentResponse = agentResponse; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public String getScoreType() { return scoreType; }
    public void setScoreType(String scoreType) { this.scoreType = scoreType; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getRunLabel() { return runLabel; }
    public void setRunLabel(String runLabel) { this.runLabel = runLabel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
