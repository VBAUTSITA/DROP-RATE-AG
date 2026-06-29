package com.ranadvisor.agent.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "kpi_definitions")
public class KpiDefinition {

    @Id
    @Column(name = "kpi_name")
    private String kpiName;

    @Column(name = "numerator_field")
    private String numeratorField;

    @Column(name = "denominator_field")
    private String denominatorField;

    @Column(name = "warning_threshold")
    private Double warningThreshold;

    @Column(name = "critical_threshold")
    private Double criticalThreshold;

    @Column(name = "is_higher_better")
    private Boolean isHigherBetter;

    // Getters
    public String getKpiName()           { return kpiName; }
    public String getNumeratorField()    { return numeratorField; }
    public String getDenominatorField()  { return denominatorField; }
    public Double getWarningThreshold()  { return warningThreshold; }
    public Double getCriticalThreshold() { return criticalThreshold; }
    public Boolean getIsHigherBetter()   { return isHigherBetter; }
}
