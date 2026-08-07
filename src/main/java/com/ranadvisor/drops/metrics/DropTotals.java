package com.ranadvisor.drops.metrics;

import com.ranadvisor.drops.entity.NrCellDrops;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Summed drop counters over a set of samples, plus everything derived from them.
 *
 * <p>Extracted out of {@code DropAnalysisTool} because three callers now need the same
 * arithmetic and none of them should re-implement it: the drop tools, the site-level
 * aggregation, and the PCI track (which reports the drop rate of the cell on the other
 * side of a conflict). Counter semantics — which column counts as which cause, and where
 * the CRITICAL/WARNING thresholds sit — live here and nowhere else.
 */
public class DropTotals {

    public long menbScgfail;
    public long menbScgfailRaproblem;
    public long menbScgfailRlcmaxnumretx;
    public long menbScgfailRecfgfail;
    public long menbScgfailSyncrecfgfail;
    public long menbScgfailT310expiry;
    public long sgnbAbnrel;
    public long sgnbAbnrelRadio;
    public long sgnbAbnrelRadioUelost;
    public long sgnbAbnrelRadioUlsyncfail;
    public long sgnbAbnrelTrans;
    public long sgnbRelTotal;

    /** Number of hourly samples summed, and the window they span. */
    public int samples;
    public LocalDateTime firstSample;
    public LocalDateTime lastSample;

    public static DropTotals of(List<NrCellDrops> rows) {
        DropTotals t = new DropTotals();
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

            LocalDateTime ts = r.getSampleTime();
            if (ts != null) {
                if (t.firstSample == null || ts.isBefore(t.firstSample)) t.firstSample = ts;
                if (t.lastSample == null || ts.isAfter(t.lastSample)) t.lastSample = ts;
            }
        }
        t.samples = rows.size();
        return t;
    }

    private static long nz(Long v) { return v == null ? 0L : v; }

    public long totalAbnormal() { return menbScgfail + sgnbAbnrel; }

    public double dropRate() {
        return sgnbRelTotal == 0 ? 0.0 : (totalAbnormal() * 100.0) / sgnbRelTotal;
    }

    /**
     * Hours actually covered. Uses the sample count rather than the wall-clock span,
     * because a gap in the export would otherwise deflate every per-hour rate.
     */
    public long spanHours() {
        if (samples > 0) return samples;
        if (firstSample == null || lastSample == null) return 0;
        return Math.max(1, Duration.between(firstSample, lastSample).toHours());
    }

    public double releasesPerHour() {
        long h = spanHours();
        return h == 0 ? 0.0 : (double) sgnbRelTotal / h;
    }

    public double abnormalPerHour() {
        long h = spanHours();
        return h == 0 ? 0.0 : (double) totalAbnormal() / h;
    }

    public double abnormalPerDay() { return abnormalPerHour() * 24.0; }

    public String severity() {
        double r = dropRate();
        return r > 15 ? "CRITICAL" : r > 5 ? "WARNING" : "OK";
    }

    public String dominantColumn() {
        String dominant = "menbScgfailRaproblem";
        long max = menbScgfailRaproblem;

        if (menbScgfailRlcmaxnumretx > max) { dominant = "menbScgfailRlcmaxnumretx"; max = menbScgfailRlcmaxnumretx; }
        if (menbScgfailRecfgfail > max) { dominant = "menbScgfailRecfgfail"; max = menbScgfailRecfgfail; }
        if (menbScgfailSyncrecfgfail > max) { dominant = "menbScgfailSyncrecfgfail"; max = menbScgfailSyncrecfgfail; }
        if (menbScgfailT310expiry > max) { dominant = "menbScgfailT310expiry"; max = menbScgfailT310expiry; }
        if (sgnbAbnrelRadio > max) { dominant = "sgnbAbnrelRadio"; max = sgnbAbnrelRadio; }
        if (sgnbAbnrelTrans > max) { dominant = "sgnbAbnrelTrans"; }

        return dominant;
    }

    public long dominantCount() {
        return switch (dominantColumn()) {
            case "menbScgfailRaproblem" -> menbScgfailRaproblem;
            case "menbScgfailRlcmaxnumretx" -> menbScgfailRlcmaxnumretx;
            case "menbScgfailRecfgfail" -> menbScgfailRecfgfail;
            case "menbScgfailSyncrecfgfail" -> menbScgfailSyncrecfgfail;
            case "menbScgfailT310expiry" -> menbScgfailT310expiry;
            case "sgnbAbnrelRadio" -> sgnbAbnrelRadio;
            case "sgnbAbnrelTrans" -> sgnbAbnrelTrans;
            default -> 0L;
        };
    }

    public String dominantLabel() { return labelFor(dominantColumn()); }

    /** Short form for tables where the full sentence would not fit. */
    public String dominantShort() {
        return switch (dominantColumn()) {
            case "menbScgfailRaproblem" -> "RA Problem";
            case "menbScgfailRlcmaxnumretx" -> "RLC Max Retx";
            case "menbScgfailRecfgfail" -> "Recfg Fail";
            case "menbScgfailSyncrecfgfail" -> "Sync Recfg Fail";
            case "menbScgfailT310expiry" -> "T310 Expiry";
            case "sgnbAbnrelRadio" -> "SgNB Radio";
            case "sgnbAbnrelTrans" -> "Transport";
            default -> "Unknown";
        };
    }

    public static String labelFor(String column) {
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
}
