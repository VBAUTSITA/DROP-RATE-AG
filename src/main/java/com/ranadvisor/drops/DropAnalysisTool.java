package com.ranadvisor.drops;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ranadvisor.drops.entity.NrCellDrops;
import com.ranadvisor.drops.repository.NrCellDropsRepository;
import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import com.ranadvisor.drops.timerange.TimeRange;
import com.ranadvisor.drops.timerange.TimeRangeParser;
import com.ranadvisor.pci.PciTrackWorkflow;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DropAnalysisTool {

    @Autowired
    private NrCellDropsRepository dropsRepo;

    @Autowired
    private AgentLogRepository logRepo;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Autowired(required = false)
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private PciTrackWorkflow pciTrack;

    @Autowired
    private TimeRangeParser timeRanges;

    // ─── existing tools (unchanged logic, timing + log wrapper added) ─────────

    @Tool("Get drop rate summary for a specific 5G NSA cell by cell name. Use this when the user asks about drops, call drops, or retainability for a specific cell.")
    public String getCellDropSummary(String cellName) {
        long start = System.currentTimeMillis();
        List<NrCellDrops> rows = dropsRepo.findByCellNameOrderBySampleTimeAsc(cellName);
        String result = rows.isEmpty() ? "Cell not found: " + cellName : buildSummary(cellName, rows);
        log("getCellDropSummary", cellName, result, start);
        return result;
    }

    @Tool("List all available 5G NSA cells in the database. Use this when the user asks which cells are available, or before analyzing all cells.")
    public String listAllCells() {
        long start = System.currentTimeMillis();
        List<String> cells = dropsRepo.findDistinctCellNames();
        StringBuilder sb = new StringBuilder();
        sb.append("Available cells (").append(cells.size()).append("):\n");
        sb.append(String.join("\n", cells));
        String result = sb.toString();
        log("listAllCells", "(none)", result, start);
        return result;
    }

    @Tool("Return the N cells with the highest drop rates. Use when the user asks which cells are worst, most problematic, or have the highest drops.")
    public String getWorstCells(int topN) {
        long start = System.currentTimeMillis();
        int cap = Math.min(topN, 10);
        List<String> cells = dropsRepo.findDistinctCellNames();

        List<CellRate> rates = cells.stream()
            .map(cellName -> {
                List<NrCellDrops> rows = dropsRepo.findByCellNameOrderBySampleTimeAsc(cellName);
                Totals t = sumTotals(rows);
                double dropRate = t.sgnbRelTotal == 0 ? 0.0 : (t.totalAbnormal() * 100.0) / t.sgnbRelTotal;
                return new CellRate(cellName, dropRate, dominantCauseLabel(t));
            })
            .sorted(Comparator.comparingDouble(CellRate::dropRate).reversed())
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("Top ").append(cap).append(" cells by drop rate:\n");
        for (int i = 0; i < Math.min(cap, rates.size()); i++) {
            CellRate r = rates.get(i);
            sb.append(String.format("%d. %s  Drop Rate: %.2f%%  Dominant: %s%n",
                i + 1, r.cellName(), r.dropRate(), r.dominantLabel()));
        }
        String result = sb.toString();
        log("getWorstCells", "topN=" + topN, result, start);
        return result;
    }

    // ─── time-range tools ─────────────────────────────────────────────────────

    @Tool("Report which period the drop data actually covers (earliest and latest sample, and how many cells). "
        + "Call this FIRST whenever the user asks about a date, a month, or a relative period such as 'last week', "
        + "so the answer is framed against the data that exists instead of assuming today's date.")
    public String getDataCoverage() {
        long start = System.currentTimeMillis();
        TimeRange bounds = timeRanges.datasetBounds();
        String result;
        if (bounds == null) {
            result = "No drop data is loaded.";
        } else {
            result = "Drop data covers " + bounds.start() + " to " + bounds.end()
                   + " (" + dropsRepo.findDistinctCellNames().size() + " cells, hourly samples).\n"
                   + "Relative periods such as 'last week' are resolved against the END of this data, "
                   + "not against today's date.";
        }
        log("getDataCoverage", "(none)", result, start);
        return result;
    }

    @Tool("Drop rate summary for one cell restricted to a period. Use this instead of getCellDropSummary "
        + "whenever the user names a date, a month, or a relative window. The period accepts natural language: "
        + "'2026-06-15', '2026-06-01 a 2026-06-15', 'ultimos 7 dias', 'ultima semana', 'junio', or 'todo'.")
    public String getCellDropSummaryForPeriod(
            @P("Exact cell name") String cellName,
            @P("Period phrase, e.g. 'ultima semana', 'junio', '2026-06-01 a 2026-06-15'") String period) {

        long start = System.currentTimeMillis();
        TimeRange r = timeRanges.parse(period);
        List<NrCellDrops> rows = dropsRepo
                .findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(cellName, r.start(), r.end());

        String result = rows.isEmpty()
                ? "No samples for " + cellName + " in period: " + r.label()
                : "Period requested: " + r.label() + "\n" + buildSummary(cellName, rows);

        log("getCellDropSummaryForPeriod", cellName + " | " + period, result, start);
        return result;
    }

    @Tool("Day-by-day drop rate for one cell over a period. Use when the user asks WHEN a cell started "
        + "degrading, for a trend, an evolution, or whether a problem is recent — a single averaged number "
        + "hides the day it changed.")
    public String getCellDailyTrend(
            @P("Exact cell name") String cellName,
            @P("Period phrase, e.g. 'ultimos 14 dias', 'junio', 'todo'") String period) {

        long start = System.currentTimeMillis();
        TimeRange r = timeRanges.parse(period);
        List<NrCellDrops> rows = dropsRepo
                .findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(cellName, r.start(), r.end());

        String result;
        if (rows.isEmpty()) {
            result = "No samples for " + cellName + " in period: " + r.label();
        } else {
            Map<LocalDate, List<NrCellDrops>> byDay = new LinkedHashMap<>();
            for (NrCellDrops row : rows) {
                byDay.computeIfAbsent(row.getSampleTime().toLocalDate(), d -> new java.util.ArrayList<>()).add(row);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Daily drop rate for ").append(cellName).append(" — ").append(r.label()).append('\n');
            for (Map.Entry<LocalDate, List<NrCellDrops>> e : byDay.entrySet()) {
                Totals t = sumTotals(e.getValue());
                double rate = t.sgnbRelTotal == 0 ? 0.0 : (t.totalAbnormal() * 100.0) / t.sgnbRelTotal;
                sb.append(String.format("%s  drop=%.2f%%  releases=%d  dominant=%s%n",
                        e.getKey(), rate, t.sgnbRelTotal, dominantCauseLabel(t)));
            }
            result = sb.toString();
        }
        log("getCellDailyTrend", cellName + " | " + period, result, start);
        return result;
    }

    @Tool("Compare one cell between two periods, reporting the change in drop rate and whether the dominant "
        + "cause changed. Use for before/after questions: after a change, after a date, 'did it get worse', "
        + "'compare this week with last week'.")
    public String compareCellPeriods(
            @P("Exact cell name") String cellName,
            @P("Baseline period, the earlier one") String periodA,
            @P("Comparison period, the later one") String periodB) {

        long start = System.currentTimeMillis();
        TimeRange ra = timeRanges.parse(periodA);
        TimeRange rb = timeRanges.parse(periodB);

        List<NrCellDrops> rowsA = dropsRepo
                .findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(cellName, ra.start(), ra.end());
        List<NrCellDrops> rowsB = dropsRepo
                .findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(cellName, rb.start(), rb.end());

        String result;
        if (rowsA.isEmpty() || rowsB.isEmpty()) {
            result = "Cannot compare " + cellName + ": "
                   + (rowsA.isEmpty() ? "no samples in A (" + ra.label() + "). " : "")
                   + (rowsB.isEmpty() ? "no samples in B (" + rb.label() + ")." : "");
        } else {
            Totals ta = sumTotals(rowsA);
            Totals tb = sumTotals(rowsB);
            double rateA = ta.sgnbRelTotal == 0 ? 0.0 : (ta.totalAbnormal() * 100.0) / ta.sgnbRelTotal;
            double rateB = tb.sgnbRelTotal == 0 ? 0.0 : (tb.totalAbnormal() * 100.0) / tb.sgnbRelTotal;
            double delta = rateB - rateA;
            String direction = delta > 0.5 ? "WORSE" : delta < -0.5 ? "BETTER" : "STABLE";
            String causeA = dominantCauseLabel(ta);
            String causeB = dominantCauseLabel(tb);

            StringBuilder sb = new StringBuilder();
            sb.append("Comparison for ").append(cellName).append('\n');
            sb.append(String.format("A: %s -> drop=%.2f%%  releases=%d  dominant=%s%n",
                    ra.label(), rateA, ta.sgnbRelTotal, causeA));
            sb.append(String.format("B: %s -> drop=%.2f%%  releases=%d  dominant=%s%n",
                    rb.label(), rateB, tb.sgnbRelTotal, causeB));
            sb.append(String.format("Change: %+.2f percentage points -> %s%n", delta, direction));
            sb.append(causeA.equals(causeB)
                    ? "Dominant cause unchanged (" + causeA + ")."
                    : "Dominant cause CHANGED: " + causeA + " -> " + causeB
                      + ". A different failure mode usually means a different fix.");
            result = sb.toString();
        }
        log("compareCellPeriods", cellName + " | " + periodA + " vs " + periodB, result, start);
        return result;
    }

    @Tool("Return the N cells with the highest drop rates within a period. Use instead of getWorstCells "
        + "whenever the user restricts the question to a date, month or relative window.")
    public String getWorstCellsForPeriod(
            @P("How many cells to return, max 10") int topN,
            @P("Period phrase, e.g. 'ultima semana', 'junio', 'todo'") String period) {

        long start = System.currentTimeMillis();
        TimeRange r = timeRanges.parse(period);
        int cap = Math.min(topN, 10);

        List<CellRate> rates = dropsRepo.findDistinctCellNames().stream()
            .map(name -> {
                List<NrCellDrops> rows = dropsRepo
                        .findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(name, r.start(), r.end());
                if (rows.isEmpty()) return null;
                Totals t = sumTotals(rows);
                double rate = t.sgnbRelTotal == 0 ? 0.0 : (t.totalAbnormal() * 100.0) / t.sgnbRelTotal;
                return new CellRate(name, rate, dominantCauseLabel(t));
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparingDouble(CellRate::dropRate).reversed())
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("Top ").append(cap).append(" cells by drop rate — ").append(r.label()).append('\n');
        for (int i = 0; i < Math.min(cap, rates.size()); i++) {
            CellRate c = rates.get(i);
            sb.append(String.format("%d. %s  Drop Rate: %.2f%%  Dominant: %s%n",
                    i + 1, c.cellName(), c.dropRate(), c.dominantLabel()));
        }
        if (rates.isEmpty()) sb.append("No cells have samples in this period.");
        String result = sb.toString();
        log("getWorstCellsForPeriod", "topN=" + topN + " | " + period, result, start);
        return result;
    }

    // ─── cross-domain tool: PCI ────────────────────────────────────────────────

    @Tool("Pursue the PCI (Physical Cell Identity) track for a cell: audit its PCI plan for "
        + "collision / confusion / mod-3 conflicts and, only when a conflict is found, compute a "
        + "conflict-free PCI proposal with the planner's reasoning. Call this when getCellDropSummary "
        + "reports an access/RACH-dominated cause such as 'RA Problem', or whenever the user asks "
        + "whether PCI is behind the drops or asks for a new PCI. A PCI collision or confusion makes "
        + "the UE unable to resolve which cell it is talking to, which shows up precisely as RACH and "
        + "handover failures. This only reads and simulates — it never changes the network.")
    public String suggestPciFixForCell(String cellName) {
        long start = System.currentTimeMillis();
        String result = pciTrack.run(cellName);
        log("suggestPciFixForCell", cellName, result, start);
        return result;
    }

    // ─── RAG tool ─────────────────────────────────────────────────────────────

    @Tool("Retrieve expert knowledge about a 5G NSA drop root cause. Use this AFTER getCellDropSummary identifies the dominant cause, to explain what it means and what to tune to fix it. Pass the cause name as the query, e.g. 'T310Expiry root cause and fix' or 'RA Problem RACH failure troubleshooting'.")
    public String getKnowledgeForCause(String query) {
        long start = System.currentTimeMillis();

        if (embeddingModel == null || embeddingStore == null) {
            String msg = "Knowledge base not available (RAG not configured — pgvector setup required).";
            log("getKnowledgeForCause", query, msg, start);
            return msg;
        }

        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(2)
                    .build()
            ).matches();

            if (matches.isEmpty()) {
                String msg = "No relevant knowledge found for: " + query;
                log("getKnowledgeForCause", query, msg, start);
                return msg;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Knowledge base findings for '").append(query).append("':\n\n");
            for (EmbeddingMatch<TextSegment> match : matches) {
                sb.append(match.embedded().text()).append("\n\n---\n\n");
            }

            String result = sb.toString();
            log("getKnowledgeForCause", query, result.substring(0, Math.min(300, result.length())), start);
            return result;
        } catch (Exception e) {
            String msg = "Knowledge retrieval error: " + e.getMessage();
            log("getKnowledgeForCause", query, msg, start);
            return msg;
        }
    }

    // ─── logging helper ───────────────────────────────────────────────────────

    private void log(String tool, String input, String output, long startMs) {
        try {
            AgentLog entry = new AgentLog();
            entry.setAgentName("drops");
            entry.setToolCalled(tool);
            entry.setToolInput(input);
            entry.setToolOutput(output != null && output.length() > 2000
                ? output.substring(0, 2000) + "...[truncated]"
                : output);
            entry.setLatencyMs(System.currentTimeMillis() - startMs);
            logRepo.save(entry);
        } catch (Exception e) {
            System.err.println("[DropAnalysisTool] Failed to save log entry: " + e.getMessage());
        }
    }

    // ─── internal helpers (unchanged) ─────────────────────────────────────────

    private record CellRate(String cellName, double dropRate, String dominantLabel) {}

    private static class Totals {
        long menbScgfail;
        long menbScgfailRaproblem;
        long menbScgfailRlcmaxnumretx;
        long menbScgfailRecfgfail;
        long menbScgfailSyncrecfgfail;
        long menbScgfailT310expiry;
        long sgnbAbnrel;
        long sgnbAbnrelRadio;
        long sgnbAbnrelRadioUelost;
        long sgnbAbnrelRadioUlsyncfail;
        long sgnbAbnrelTrans;
        long sgnbRelTotal;

        long totalAbnormal() { return menbScgfail + sgnbAbnrel; }
    }

    private Totals sumTotals(List<NrCellDrops> rows) {
        Totals t = new Totals();
        for (NrCellDrops r : rows) {
            t.menbScgfail += nz(r.getMenbScgfail());
            t.menbScgfailRaproblem += nz(r.getMenbScgfailRaproblem());
            t.menbScgfailRlcmaxnumretx += nz(r.getMenbScgfailRlcmaxnumretx());
            t.menbScgfailRecfgfail += nz(r.getMenbScgfailRecfgfail());
            t.menbScgfailSyncrecfgfail += nz(r.getMenbScgfailSyncrecfgfail());
            t.menbScgfailT310expiry += nz(r.getMenbScgfailT310expiry());
            t.sgnbAbnrel += nz(r.getSgnbAbnrel());
            t.sgnbAbnrelRadio += nz(r.getSgnbAbnrelRadio());
            t.sgnbAbnrelRadioUelost += nz(r.getSgnbAbnrelRadioUelost());
            t.sgnbAbnrelRadioUlsyncfail += nz(r.getSgnbAbnrelRadioUlsyncfail());
            t.sgnbAbnrelTrans += nz(r.getSgnbAbnrelTrans());
            t.sgnbRelTotal += nz(r.getSgnbRelTotal());
        }
        return t;
    }

    private long nz(Long v) {
        return v == null ? 0L : v;
    }

    private String dominantCauseColumn(Totals t) {
        String dominant = "menbScgfailRaproblem";
        long max = t.menbScgfailRaproblem;

        if (t.menbScgfailRlcmaxnumretx > max) { dominant = "menbScgfailRlcmaxnumretx"; max = t.menbScgfailRlcmaxnumretx; }
        if (t.menbScgfailRecfgfail > max) { dominant = "menbScgfailRecfgfail"; max = t.menbScgfailRecfgfail; }
        if (t.menbScgfailSyncrecfgfail > max) { dominant = "menbScgfailSyncrecfgfail"; max = t.menbScgfailSyncrecfgfail; }
        if (t.menbScgfailT310expiry > max) { dominant = "menbScgfailT310expiry"; max = t.menbScgfailT310expiry; }
        if (t.sgnbAbnrelRadio > max) { dominant = "sgnbAbnrelRadio"; max = t.sgnbAbnrelRadio; }
        if (t.sgnbAbnrelTrans > max) { dominant = "sgnbAbnrelTrans"; max = t.sgnbAbnrelTrans; }

        return dominant;
    }

    private long dominantCauseCount(Totals t, String column) {
        return switch (column) {
            case "menbScgfailRaproblem" -> t.menbScgfailRaproblem;
            case "menbScgfailRlcmaxnumretx" -> t.menbScgfailRlcmaxnumretx;
            case "menbScgfailRecfgfail" -> t.menbScgfailRecfgfail;
            case "menbScgfailSyncrecfgfail" -> t.menbScgfailSyncrecfgfail;
            case "menbScgfailT310expiry" -> t.menbScgfailT310expiry;
            case "sgnbAbnrelRadio" -> t.sgnbAbnrelRadio;
            case "sgnbAbnrelTrans" -> t.sgnbAbnrelTrans;
            default -> 0L;
        };
    }

    private String dominantCauseLabel(Totals t) {
        return labelFor(dominantCauseColumn(t));
    }

    private String labelFor(String column) {
        return switch (column) {
            case "menbScgfailRaproblem" -> "RA Problem (RACH failure during access or handover)";
            case "menbScgfailRlcmaxnumretx" -> "RLC Max Retransmissions (DL radio quality)";
            case "menbScgfailRecfgfail" -> "Reconfiguration Failure (UE config issue)";
            case "menbScgfailSyncrecfgfail" -> "Sync Reconfiguration Failure";
            case "menbScgfailT310expiry" -> "T310 Expiry (DL coverage/quality degradation)";
            case "sgnbAbnrelRadio" -> "SgNB Radio Failure (UE lost or UL sync fail)";
            case "sgnbAbnrelTrans" -> "Transport Failure (X2-U interface issue)";
            default -> "Unknown";
        };
    }

    private String buildSummary(String cellName, List<NrCellDrops> rows) {
        Totals t = sumTotals(rows);
        long totalAbnormal = t.totalAbnormal();
        double dropRate = t.sgnbRelTotal == 0 ? 0.0 : (totalAbnormal * 100.0) / t.sgnbRelTotal;

        String dominantColumn = dominantCauseColumn(t);
        long dominantCount = dominantCauseCount(t, dominantColumn);
        String dominantLabel = labelFor(dominantColumn);

        String severity = dropRate > 15 ? "CRITICAL" : dropRate > 5 ? "WARNING" : "OK";

        var minTime = rows.get(0).getSampleTime();
        var maxTime = rows.get(rows.size() - 1).getSampleTime();

        StringBuilder sb = new StringBuilder();
        sb.append("Cell: ").append(cellName).append('\n');
        sb.append("Period: ").append(minTime).append(" to ").append(maxTime).append('\n');
        sb.append("Total Releases: ").append(t.sgnbRelTotal).append('\n');
        sb.append("Abnormal Releases: ").append(totalAbnormal).append('\n');
        sb.append(String.format("Drop Rate: %.2f%%%n", dropRate));
        sb.append("Dominant Cause: ").append(dominantLabel).append(" (").append(dominantCount).append(" events)\n");
        sb.append(String.format("MeNB ScgFail breakdown → RAProblem:%d | RlcMaxRetx:%d | RecfgFail:%d | SyncRecfgFail:%d | T310Expiry:%d%n",
            t.menbScgfailRaproblem, t.menbScgfailRlcmaxnumretx, t.menbScgfailRecfgfail,
            t.menbScgfailSyncrecfgfail, t.menbScgfailT310expiry));
        sb.append(String.format("SgNB AbnRel breakdown  → Radio:%d (UeLost:%d ULSyncFail:%d) | Transport:%d%n",
            t.sgnbAbnrelRadio, t.sgnbAbnrelRadioUelost, t.sgnbAbnrelRadioUlsyncfail, t.sgnbAbnrelTrans));
        sb.append("Severity: ").append(severity);

        return sb.toString();
    }
}
