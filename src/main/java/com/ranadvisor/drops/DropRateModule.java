package com.ranadvisor.drops;

import com.ranadvisor.core.DiagnosticModule;
import com.ranadvisor.core.ModuleFinding;
import com.ranadvisor.drops.entity.NrCellDrops;
import com.ranadvisor.drops.repository.NrCellDropsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exposes the existing drop-rate data through the shared {@link DiagnosticModule} SPI
 * so the orchestrator can consume it as typed findings (rather than parsing the
 * drop agent's free-text answers). Uses the same drop-rate formula as
 * {@code DropAnalysisTool} for consistency.
 *
 * <p>Emitted tags include a severity tag ({@code HIGH_DROP} / {@code ELEVATED_DROP})
 * and a dominant-cause tag ({@code RACH_FAILURE}, {@code COVERAGE_DEGRADATION},
 * {@code DL_RADIO_QUALITY}, {@code RECONFIG_FAILURE}, {@code UL_RADIO_FAILURE},
 * {@code TRANSPORT_FAILURE}).
 */
@Component
public class DropRateModule implements DiagnosticModule {

    @Autowired private NrCellDropsRepository dropsRepo;

    @Override public String id() { return "drop-rate"; }

    @Override public String domain() { return "Call-drop / retainability"; }

    @Override
    public ModuleFinding analyze(String cellName) {
        List<NrCellDrops> rows = dropsRepo.findByCellNameOrderBySampleTimeAsc(cellName);
        if (rows.isEmpty()) return ModuleFinding.none(id(), cellName);

        long menbScgfail = 0, sgnbAbnrel = 0, relTotal = 0;
        long raproblem = 0, rlc = 0, recfg = 0, syncRecfg = 0, t310 = 0, sgnbRadio = 0, sgnbTrans = 0;
        for (NrCellDrops r : rows) {
            menbScgfail += nz(r.getMenbScgfail());
            sgnbAbnrel  += nz(r.getSgnbAbnrel());
            relTotal    += nz(r.getSgnbRelTotal());
            raproblem   += nz(r.getMenbScgfailRaproblem());
            rlc         += nz(r.getMenbScgfailRlcmaxnumretx());
            recfg       += nz(r.getMenbScgfailRecfgfail());
            syncRecfg   += nz(r.getMenbScgfailSyncrecfgfail());
            t310        += nz(r.getMenbScgfailT310expiry());
            sgnbRadio   += nz(r.getSgnbAbnrelRadio());
            sgnbTrans   += nz(r.getSgnbAbnrelTrans());
        }
        long abnormal = menbScgfail + sgnbAbnrel;
        double dropRate = relTotal == 0 ? 0.0 : (abnormal * 100.0) / relTotal;

        // dominant cause among the seven cause counters
        long[] counts = {raproblem, rlc, recfg, syncRecfg, t310, sgnbRadio, sgnbTrans};
        String[] tags = {"RACH_FAILURE", "DL_RADIO_QUALITY", "RECONFIG_FAILURE", "RECONFIG_FAILURE",
                         "COVERAGE_DEGRADATION", "UL_RADIO_FAILURE", "TRANSPORT_FAILURE"};
        String[] labels = {"RA Problem (RACH failure during access or handover)",
                           "RLC Max Retransmissions (DL radio quality)",
                           "Reconfiguration Failure (UE config issue)",
                           "Sync Reconfiguration Failure",
                           "T310 Expiry (DL coverage/quality degradation)",
                           "SgNB Radio Failure (UE lost or UL sync fail)",
                           "Transport Failure (X2-U interface issue)"};
        int dom = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[dom]) dom = i;

        String severity = dropRate > 15 ? ModuleFinding.CRITICAL
                        : dropRate > 5  ? ModuleFinding.WARNING
                        : ModuleFinding.OK;

        Set<String> outTags = new LinkedHashSet<>();
        if (ModuleFinding.CRITICAL.equals(severity)) outTags.add("HIGH_DROP");
        else if (ModuleFinding.WARNING.equals(severity)) outTags.add("ELEVATED_DROP");
        if (abnormal > 0) outTags.add(tags[dom]);

        String headline = String.format("Drop rate %.2f%% (%s); dominant cause: %s.",
                dropRate, severity, labels[dom]);
        String detail = String.format(
                "Total releases: %d | abnormal: %d | dominant %s = %d events.",
                relTotal, abnormal, labels[dom], counts[dom]);

        return new ModuleFinding(id(), cellName, severity, headline, outTags, detail);
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
}
