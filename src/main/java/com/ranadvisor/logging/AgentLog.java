package com.ranadvisor.logging;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_logs")
public class AgentLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_name")
    private String agentName;

    @Column(name = "tool_called")
    private String toolCalled;

    @Column(name = "tool_input", columnDefinition = "TEXT")
    private String toolInput;

    @Column(name = "tool_output", columnDefinition = "TEXT")
    private String toolOutput;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getToolCalled() { return toolCalled; }
    public void setToolCalled(String toolCalled) { this.toolCalled = toolCalled; }
    public String getToolInput() { return toolInput; }
    public void setToolInput(String toolInput) { this.toolInput = toolInput; }
    public String getToolOutput() { return toolOutput; }
    public void setToolOutput(String toolOutput) { this.toolOutput = toolOutput; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
