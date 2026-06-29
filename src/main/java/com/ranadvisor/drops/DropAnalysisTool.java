package com.ranadvisor.drops;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ranadvisor.drops.entity.NrCellDrops;
import com.ranadvisor.drops.repository.NrCellDropsRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DropAnalysisTool {

    @Autowired
    private NrCellDropsRepository dropsRepo;

    @Tool("Get drop rate summary for a specific 5G NSA cell by cell name. Use this when the user asks about drops, call drops, or retainability for a specific cell.")
    public String getCellDropSummary(String cellName) {
        List<NrCellDrops> rows = dropsRepo.findByCellNameOrderBySampleTimeAsc(cellName);
        if (rows.isEmpty()) {
            return "Cell not found: " + cellName;
        }
        return buildSummary(cellName, rows);
    }

    @Tool("List all available 5G NSA cells in the database. Use this when the user asks which cells are available, or before analyzing all cells.")
    public String listAllCells() {
        List<String> cells = dropsRepo.findDistinctCellNames();
        StringBuilder sb = new StringBuilder();
        sb.append("Available cells (").append(cells.size()).append("):\n");
        sb.append(String.join("\n", cells));
        return sb.toString();
    }

    @Tool("Return the N cells with the highest drop rates. Use when the user asks which cells are worst, most problematic, or have the highest drops.")
    public String getWorstCells(int topN) {
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
        return sb.toString();
    }

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
