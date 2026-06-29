package com.ranadvisor.agent;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ranadvisor.agent.entity.CellStatus;
import com.ranadvisor.agent.repository.CellStatusRepository;
import com.ranadvisor.agent.repository.KpiDefinitionRepository;
import com.ranadvisor.agent.repository.TelecomCommandRepository;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TelecomTools {

    @Autowired private CellStatusRepository    cellRepo;
    @Autowired private KpiDefinitionRepository kpiRepo;
    @Autowired private TelecomCommandRepository commandRepo;

    @Tool("Get raw counters for a specific cell by cell_id. Use this first when the user asks about a specific cell.")
    public String getCellStatus(String cellId) {
        return cellRepo.findByCellId(cellId)
            .map(c -> String.format(
                "Cell: %s | Fecha: %s | RRC(att/succ): %d/%d | ERAB(att/succ): %d/%d | " +
                "HO(att/succ): %d/%d | Avail: %d/%d min | PRB DL: %d/%d | " +
                "RACH(att/succ): %d/%d | VoLTE(att/drop): %d/%d",
                c.getCellId(), c.getFechaCarga(),
                c.getRrcAtt(),  c.getRrcSucc(),
                c.getErabAtt(), c.getErabSucc(),
                c.getHoAtt(),   c.getHoSucc(),
                c.getAvailMinutes(), c.getTotalMinutes(),
                c.getPrbUsedDl(), c.getPrbAvailDl(),
                c.getRachAtt(), c.getRachSucc(),
                c.getVolteAtt(), c.getVolteDrop()
            ))
            .orElse("Cell not found: " + cellId);
    }

    @Tool("Return all cells with degraded KPI performance. Calculates RRC Success Rate for every cell and returns those below warning threshold.")
    public List<String> getDegradedCells() {
        return cellRepo.findAll().stream()
            .filter(c -> c.getRrcAtt() != null && c.getRrcAtt() > 0)
            .filter(c -> (double) c.getRrcSucc() / c.getRrcAtt() < 0.95)
            .map(c -> {
                double rrcSr = (double) c.getRrcSucc() / c.getRrcAtt() * 100;
                return String.format("%s | RRC SR: %.2f%% | Fecha: %s",
                    c.getCellId(), rrcSr, c.getFechaCarga());
            })
            .collect(Collectors.toList());
    }

    @Tool("Calculate a specific KPI for a cell. Fetches formula from kpi_definitions, applies it to cell counters, compares to threshold. Returns result with severity level.")
    public String calculateKpi(String cellId, String kpiName) {
        var cellOpt = cellRepo.findByCellId(cellId);
        var kpiOpt  = kpiRepo.findByKpiName(kpiName);

        if (cellOpt.isEmpty()) return "Cell not found: " + cellId;
        if (kpiOpt.isEmpty())  return "KPI not found: " + kpiName;

        var cell = cellOpt.get();
        var kpi  = kpiOpt.get();

        try {
            double numerator   = resolveField(cell, kpi.getNumeratorField());
            double denominator = resolveField(cell, kpi.getDenominatorField());

            if (denominator == 0) return kpiName + ": N/A (denominator is zero)";

            double value = (numerator / denominator) * 100;

            // thresholds are NUMERIC(5,4) scale 0-1, multiply by 100 to compare
            String severity = "OK";
            if (value <= kpi.getCriticalThreshold() * 100) severity = "CRITICAL";
            else if (value <= kpi.getWarningThreshold() * 100) severity = "WARNING";

            return String.format("%s: %.2f%% — %s (warning: %.0f%%, critical: %.0f%%)",
                kpiName, value, severity,
                kpi.getWarningThreshold() * 100, kpi.getCriticalThreshold() * 100);

        } catch (Exception e) {
            return "Error calculating " + kpiName + ": " + e.getMessage();
        }
    }

    @Tool("Find telecom commands by keyword, category, or vendor. Use when user asks how to diagnose or fix an issue, or when a KPI is CRITICAL.")
    public List<String> findCommands(String keyword) {
        return commandRepo.findByKeyword(keyword.toLowerCase())
            .stream()
            .map(cmd -> String.format("[%s][%s] %s — %s",
                cmd.getVendor(), cmd.getCategory(),
                cmd.getCommandCode(), cmd.getDescription()))
            .collect(Collectors.toList());
    }

    private double resolveField(Object cell, String fieldName) throws Exception {
        var method = cell.getClass().getMethod(
            "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)
        );
        return ((Number) method.invoke(cell)).doubleValue();
    }
}
