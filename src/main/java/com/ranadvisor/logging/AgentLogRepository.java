package com.ranadvisor.logging;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentLogRepository extends JpaRepository<AgentLog, Long> {
    // findAll(Pageable) and findAll(Sort) inherited from JpaRepository
}
