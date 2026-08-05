package com.ranadvisor.drops;

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
import com.ranadvisor.pci.PciTrackWorkflow;

import java.util.Comparator;
import java.util.List;
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
