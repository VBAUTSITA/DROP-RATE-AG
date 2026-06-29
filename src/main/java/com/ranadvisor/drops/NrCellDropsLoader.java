package com.ranadvisor.drops;

import com.ranadvisor.drops.entity.NrCellDrops;
import com.ranadvisor.drops.repository.NrCellDropsRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class NrCellDropsLoader {

    private static final int PREAMBLE_LINES = 3;
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm");
    private static final String CSV_PATH = "data/nsa_drops.csv";
    private static final int EXPECTED_COLS = 72;

    private final NrCellDropsRepository repo;

    public NrCellDropsLoader(NrCellDropsRepository repo) {
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() throws Exception {
        if (repo.count() > 0) {
            System.out.println("[NrCellDropsLoader] Table already populated (" +
                    repo.count() + " rows). Skipping import.");
            return;
        }

        List<NrCellDrops> batch = new ArrayList<>();
        int lineNo = 0, loaded = 0, skipped = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource(CSV_PATH).getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (lineNo <= PREAMBLE_LINES + 1) continue;
                if (line.isBlank()) continue;
                if (line.startsWith("Total")) continue;

                String[] f = line.split("\t", -1);
                if (f.length < EXPECTED_COLS) { skipped++; continue; }

                try {
                    NrCellDrops e = parseRow(f);
                    batch.add(e);
                    loaded++;
                } catch (Exception ex) {
                    skipped++;
                }

                if (batch.size() >= 1000) {
                    repo.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) repo.saveAll(batch);
        }

        System.out.println("[NrCellDropsLoader] Import complete. Loaded=" +
                loaded + " Skipped=" + skipped);
    }

    private NrCellDrops parseRow(String[] f) {
        NrCellDrops e = new NrCellDrops();
        e.setSampleTime(LocalDateTime.parse(f[0].trim(), FMT));
        e.setGnodebName(f[1].trim());
        e.setCellName(f[2].trim());
        e.setNrCellId(parseInt(f[3]));
        e.setGnodebFunctionName(f[4].trim());
        e.setIntegrity(f[5].trim());
        e.setMenbScgfail(parseLong(f[6]));
        e.setMenbScgfailRaproblem(parseLong(f[7]));
        e.setMenbScgfailRlcmaxnumretx(parseLong(f[8]));
        e.setMenbScgfailRecfgfail(parseLong(f[9]));
        e.setMenbScgfailSyncrecfgfail(parseLong(f[10]));
        e.setMenbScgfailT310expiry(parseLong(f[11]));
        e.setSgnbAbnrel(parseLong(f[12]));
        e.setSgnbAbnrelNoreply(parseLong(f[13]));
        e.setSgnbAbnrelRadio(parseLong(f[14]));
        e.setSgnbAbnrelRadioUelost(parseLong(f[15]));
        e.setSgnbAbnrelRadioUlsyncfail(parseLong(f[16]));
        e.setSgnbAbnrelRadioSul(parseLong(f[17]));
        e.setSgnbAbnrelRnlPreempt(parseLong(f[18]));
        e.setSgnbAbnrelTrans(parseLong(f[19]));
        e.setRaTaUeIdx0(parseLong(f[20]));
        e.setRaTaUeIdx1(parseLong(f[21]));
        e.setRaTaUeIdx2(parseLong(f[22]));
        e.setRaTaUeIdx3(parseLong(f[23]));
        e.setRaTaUeIdx4(parseLong(f[24]));
        e.setRaTaUeIdx5(parseLong(f[25]));
        e.setRaTaUeIdx6(parseLong(f[26]));
        e.setRaTaUeIdx7(parseLong(f[27]));
        e.setRaTaUeIdx8(parseLong(f[28]));
        e.setRaTaUeIdx9(parseLong(f[29]));
        e.setRaTaUeIdx10(parseLong(f[30]));
        e.setRaTaUeIdx11(parseLong(f[31]));
        e.setRaTaUeIdx12(parseLong(f[32]));
        e.setRaTaUeIdx13(parseLong(f[33]));
        e.setRaTaUeIdx14(parseLong(f[34]));
        e.setRaTaUeIdx15(parseLong(f[35]));
        e.setMeasrptRsrpIdx0(parseLong(f[36]));
        e.setMeasrptRsrpIdx1(parseLong(f[37]));
        e.setMeasrptRsrpIdx2(parseLong(f[38]));
        e.setMeasrptRsrpIdx3(parseLong(f[39]));
        e.setMeasrptRsrpIdx4(parseLong(f[40]));
        e.setMeasrptRsrpIdx5(parseLong(f[41]));
        e.setMeasrptRsrpIdx6(parseLong(f[42]));
        e.setMeasrptRsrpIdx7(parseLong(f[43]));
        e.setMeasrptRsrpIdx8(parseLong(f[44]));
        e.setMeasrptRsrpIdx9(parseLong(f[45]));
        e.setUlPuschRsrpIdx0(parseLong(f[46]));
        e.setUlPuschRsrpIdx1(parseLong(f[47]));
        e.setUlPuschRsrpIdx2(parseLong(f[48]));
        e.setUlPuschRsrpIdx3(parseLong(f[49]));
        e.setUlPuschRsrpIdx4(parseLong(f[50]));
        e.setUlPuschRsrpIdx5(parseLong(f[51]));
        e.setUlPuschRsrpIdx6(parseLong(f[52]));
        e.setUlPuschRsrpIdx7(parseLong(f[53]));
        e.setUlPuschRsrpIdx8(parseLong(f[54]));
        e.setUlPuschRsrpIdx9(parseLong(f[55]));
        e.setUlPuschRsrpIdx10(parseLong(f[56]));
        e.setUlPuschRsrpIdx11(parseLong(f[57]));
        e.setMeasrptSinrIdx0(parseLong(f[58]));
        e.setMeasrptSinrIdx1(parseLong(f[59]));
        e.setMeasrptSinrIdx2(parseLong(f[60]));
        e.setMeasrptSinrIdx3(parseLong(f[61]));
        e.setMeasrptSinrIdx4(parseLong(f[62]));
        e.setUlPuschSinrIdx0(parseLong(f[63]));
        e.setUlPuschSinrIdx1(parseLong(f[64]));
        e.setUlPuschSinrIdx2(parseLong(f[65]));
        e.setUlPuschSinrIdx3(parseLong(f[66]));
        e.setUlPuschSinrIdx4(parseLong(f[67]));
        e.setUlPuschSinrIdx5(parseLong(f[68]));
        e.setUlPuschSinrIdx6(parseLong(f[69]));
        e.setUlPuschSinrIdx7(parseLong(f[70]));
        e.setSgnbRelTotal(parseLong(f[71]));
        return e;
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return 0L;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
}
