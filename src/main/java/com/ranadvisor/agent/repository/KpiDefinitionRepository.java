package com.ranadvisor.agent.repository;

import com.ranadvisor.agent.entity.KpiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface KpiDefinitionRepository extends JpaRepository<KpiDefinition, String> {

    Optional<KpiDefinition> findByKpiName(String kpiName);
}
