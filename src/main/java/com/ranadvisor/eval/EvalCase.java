/*
 * Package: com.ranadvisor.eval
 * Purpose: JPA entity mapping a single ground-truth evaluation case to the eval_cases table.
 * Part of Phase 0 eval harness.
 */
package com.ranadvisor.eval;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eval_cases")
public class EvalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent", nullable = false, length = 20)
    private String agent;            // 'drops' or 'telecom'

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "expected", nullable = false, columnDefinition = "TEXT")
    private String expected;         // ground-truth answer fragment

    @Column(name = "score_type", nullable = false, length = 20)
    private String scoreType;        // 'exact', 'contains', or 'llm_judge'

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }
    public String getScoreType() { return scoreType; }
    public void setScoreType(String scoreType) { this.scoreType = scoreType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
