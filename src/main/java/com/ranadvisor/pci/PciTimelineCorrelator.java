package com.ranadvisor.pci;

import com.ranadvisor.drops.entity.NrCellDrops;
import com.ranadvisor.drops.metrics.DropTotals;
import com.ranadvisor.drops.repository.NrCellDropsRepository;
import com.ranadvisor.pci.entity.PciChange;
import com.ranadvisor.pci.planner.PciAudit;
import com.ranadvisor.pci.repository.PciChangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests whether a PCI conflict can actually explain a degradation, by comparing when the
 * conflicting PCI was introduced against when the drops rose.
 *
 * <p>This is the difference between a correlation and a claim. "This cell has a PCI
 * collision and a high drop rate" is two facts side by side; an agent that joins them with
 * "therefore" has added something it did not measure. A collision in place since 2023
 * cannot have caused a degradation that started three days ago — it is a pre-existing
 * condition, and reporting it as the root cause sends someone to change a PCI while the
 * real fault stays.
 *
 * <p>Three verdicts, and the middle one matters most:
 * <ul>
 *   <li><b>SUPPORTED</b> — the conflict appeared at or shortly before the onset.</li>
 *   <li><b>PRE-EXISTING</b> — the conflict is older than the data, or clearly predates the
 *       onset. It is still worth fixing, but it does not explain this degradation.</li>
 *   <li><b>UNKNOWN</b> — no change history, or no detectable onset. Says so rather than
 *       defaulting to either of the other two.</li>
 * </ul>
 */
@Component
public class PciTimelineCorrelator {

    /** A conflict introduced within this many days before the onset is treated as supporting. */
    private static final int SUPPORT_WINDOW_DAYS = 7;

    /** Minimum jump, in percentage points, for a day to count as the onset of a degradation. */
    private static final double ONSET_STEP_PP = 5.0;

    /** Days of history used as the baseline the onset is measured against. */
    private static final int BASELINE_DAYS = 5;

    @Autowired private PciChangeRepository changeRepo;
    @Autowired private NrCellDropsRepository dropsRepo;

    public String correlate(String cellName, PciAudit audit) {
        if (audit == null || audit.isClean()) return "";

        LocalDateTime conflictSince = conflictStart(cellName, audit);
        LocalDate onset = detectOnset(cellName);

        StringBuilder sb = new StringBuilder();
        sb.append("Timeline check\n");

        if (conflictSince == null) {
            sb.append("  No PCI change history for the cells in this conflict, so it is not known when "
                    + "the conflicting PCI was introduced.\n");
            sb.append("  VERDICT: UNKNOWN — the conflict is real, but nothing here shows whether it "
                    + "predates the degradation. Treat it as a candidate, not the cause.\n");
            return sb.toString();
        }

        sb.append("  Conflicting PCI in place since: ").append(conflictSince).append('\n');

        if (onset == null) {
            sb.append("  No clear onset in the drop data: the rate has no step change of at least ")
              .append((long) ONSET_STEP_PP).append(" points, so the degradation looks continuous "
              + "rather than triggered.\n");
            sb.append("  VERDICT: UNKNOWN — a long-standing conflict alongside a long-standing "
                    + "degradation is consistent with causation but does not demonstrate it.\n");
            return sb.toString();
        }

        sb.append("  Drop rate stepped up on: ").append(onset).append('\n');

        LocalDate conflictDay = conflictSince.toLocalDate();
        long daysBefore = java.time.temporal.ChronoUnit.DAYS.between(conflictDay, onset);

        if (daysBefore >= 0 && daysBefore <= SUPPORT_WINDOW_DAYS) {
            sb.append("  The conflict appeared ").append(daysBefore).append(" day(s) before the drops rose.\n");
            sb.append("  VERDICT: SUPPORTED — the timing is consistent with the conflict causing the "
                    + "degradation. This is the strongest evidence available from these two data sources; "
                    + "it is still correlation, not proof.\n");
        } else if (daysBefore > SUPPORT_WINDOW_DAYS) {
            sb.append("  The conflict predates the degradation by ").append(daysBefore).append(" days.\n");
            sb.append("  VERDICT: PRE-EXISTING — the cell ran with this conflict for ").append(daysBefore)
              .append(" days before the drops rose, so the conflict alone does not explain the change. "
              + "Something else moved on ").append(onset).append(". Fix the conflict anyway, but keep "
              + "looking for what changed.\n");
        } else {
            sb.append("  The conflict was introduced ").append(-daysBefore)
              .append(" day(s) AFTER the drops rose.\n");
            sb.append("  VERDICT: PRE-EXISTING (inverted) — the degradation started first, so the "
                    + "conflict cannot be its cause. Both are real; they are separate problems.\n");
        }
        return sb.toString();
    }

    // ─── internals ────────────────────────────────────────────────────────────

    /**
     * When the conflict became possible: the later of the two cells' most recent changes to
     * the PCI values now in conflict. A collision only exists once BOTH ends hold the value.
     */
    private LocalDateTime conflictStart(String cellName, PciAudit audit) {
        List<String> involved = new ArrayList<>();
        involved.add(cellName);
        for (PciConflict c : audit.conflicts()) {
            if (c.cellA() != null && !involved.contains(c.cellA())) involved.add(c.cellA());
            if (c.cellB() != null && !involved.contains(c.cellB())) involved.add(c.cellB());
        }

        LocalDateTime latest = null;
        for (String cell : involved) {
            List<PciChange> history = changeRepo.findByCellNameOrderByChangedAtAsc(cell);
            if (history.isEmpty()) continue;
            LocalDateTime last = history.get(history.size() - 1).getChangedAt();
            if (latest == null || last.isAfter(latest)) latest = last;
        }
        return latest;
    }

    /**
     * First day whose drop rate exceeds the preceding baseline by {@link #ONSET_STEP_PP}
     * points. Deliberately a plain step test rather than anything statistical: an operator
     * has to be able to check the verdict by looking at the daily series, and a threshold
     * they can see beats a model they cannot.
     */
    private LocalDate detectOnset(String cellName) {
        List<NrCellDrops> rows = dropsRepo.findByCellNameOrderBySampleTimeAsc(cellName);
        if (rows.isEmpty()) return null;

        Map<LocalDate, List<NrCellDrops>> byDay = new LinkedHashMap<>();
        for (NrCellDrops r : rows) {
            if (r.getSampleTime() == null) continue;
            byDay.computeIfAbsent(r.getSampleTime().toLocalDate(), d -> new ArrayList<>()).add(r);
        }

        List<LocalDate> days = new ArrayList<>(byDay.keySet());
        days.sort(LocalDate::compareTo);
        if (days.size() <= BASELINE_DAYS) return null;

        List<Double> rates = new ArrayList<>();
        for (LocalDate d : days) rates.add(DropTotals.of(byDay.get(d)).dropRate());

        for (int i = BASELINE_DAYS; i < days.size(); i++) {
            double baseline = 0;
            for (int j = i - BASELINE_DAYS; j < i; j++) baseline += rates.get(j);
            baseline /= BASELINE_DAYS;

            if (rates.get(i) - baseline >= ONSET_STEP_PP) return days.get(i);
        }
        return null;
    }
}
