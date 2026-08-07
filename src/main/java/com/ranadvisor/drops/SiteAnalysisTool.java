package com.ranadvisor.drops;

import com.ranadvisor.drops.entity.NrCellDrops;
import com.ranadvisor.drops.metrics.DropTotals;
import com.ranadvisor.drops.repository.NrCellDropsRepository;
import com.ranadvisor.drops.timerange.TimeRange;
import com.ranadvisor.drops.timerange.TimeRangeParser;
import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Site-level view of the drop data.
 *
 * <p>Why this exists: the cell-level tools answer "which cells are worst" and an operator
 * reading that list has to notice by eye that several entries share a gNodeB. In the
 * current dataset the nine worst cells are five sites, and four of those five have
 * <b>two sectors</b> degrading with the same dominant cause.
 *
 * <p>That distinction changes the diagnosis. A single sector failing points at that
 * sector — RF, a neighbour relation, its PCI. Two or three sectors of the same site
 * failing the same way points at something they share: transport, baseband, synchronisation,
 * power, licensing. Those are different teams and different fixes, and the cell-level list
 * cannot express it.
 *
 * <p>The multi-sector flag here is a <b>hypothesis, not a verdict</b>. This module reads
 * PM counters only; it has no visibility of alarms, transport KPIs or uplink interference,
 * so it can say "these sectors fail together" but never "the transport is at fault". The
 * tool output says so, because an agent that overstates this would send someone to the
 * wrong team.
 */
@Component
public class SiteAnalysisTool {

    /** A site is called multi-sector-affected when at least this many of its cells are degraded. */
    private static final int MULTI_SECTOR_THRESHOLD = 2;

    /** Drop rate above which a sector counts as "degraded" for the pattern check. */
    private static final double DEGRADED_PCT = 5.0;

    @Autowired private NrCellDropsRepository dropsRepo;
    @Autowired private AgentLogRepository logRepo;
    @Autowired private TimeRangeParser timeRanges;

    // ─── tools ────────────────────────────────────────────────────────────────

    @Tool("Rank SITES (gNodeBs) by drop rate rather than individual cells, and flag sites where "
        + "two or more sectors are degraded with the same dominant cause. Use this for network-wide "
        + "questions — 'which are the worst', 'where are the problems', 'what should we prioritise' — "
        + "BEFORE listing individual cells, because several bad cells usually belong to the same site "
        + "and a shared site-level cause is a different fix from several independent cell faults.")
    public String getWorstSites(
            @P("How many sites to return, max 10") int topN,
            @P("Period phrase, e.g. 'ultima semana', 'junio', 'todo'") String period) {

        long start = System.currentTimeMillis();
        TimeRange r = timeRanges.parse(period);
        int cap = Math.min(Math.max(topN, 1), 10);

        List<SiteStat> stats = new ArrayList<>();
        for (String site : dropsRepo.findDistinctSiteNames()) {
            SiteStat s = statFor(site, r);
            if (s != null) stats.add(s);
        }
        stats.sort(Comparator.comparingDouble(SiteStat::dropRate).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("Top ").append(cap).append(" sites by drop rate — ").append(r.label()).append('\n');
        if (stats.isEmpty()) {
            sb.append("No sites have samples in this period.");
        } else {
            for (int i = 0; i < Math.min(cap, stats.size()); i++) {
                SiteStat s = stats.get(i);
                sb.append(String.format("%d. %s  drop=%.2f%%  sectors=%d (degraded %d)  dominant=%s%n",
                        i + 1, s.site(), s.dropRate(), s.sectors(), s.degradedSectors(), s.dominant()));
                if (s.multiSector()) {
                    sb.append("     ⚠ MULTI-SECTOR: ").append(s.degradedSectors())
                      .append(" sectors degraded with the same dominant cause (")
                      .append(s.dominant()).append("). Sectors sharing a failure mode usually share "
                      + "a cause at the site — transport, baseband, sync, power — rather than each "
                      + "having its own RF or PCI problem.\n");
                }
            }
            sb.append("\nNote: this ranking comes from PM counters only. A multi-sector pattern is a "
                    + "hypothesis to check against alarms, transport KPIs and uplink interference, "
                    + "none of which this module can see.");
        }

        String result = sb.toString();
        log("getWorstSites", "topN=" + topN + " | " + period, result, start);
        return result;
    }

    @Tool("Full breakdown of one SITE (gNodeB): every sector with its drop rate and dominant cause, "
        + "plus whether the sectors fail the same way. Use after getWorstSites, or when the user asks "
        + "about a site, a gNodeB, or 'the whole node'. Accepts either the site name or any cell name "
        + "belonging to it.")
    public String getSiteDropSummary(
            @P("Site (gNodeB) name, or any cell name belonging to it") String siteOrCell,
            @P("Period phrase, e.g. 'ultima semana', 'junio', 'todo'") String period) {

        long start = System.currentTimeMillis();
        TimeRange r = timeRanges.parse(period);

        String site = resolveSite(siteOrCell);
        String result;

        if (site == null) {
            result = "Unknown site or cell: " + siteOrCell;
        } else {
            SiteStat s = statFor(site, r);
            if (s == null) {
                result = "No samples for site " + site + " in period: " + r.label();
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("Site: ").append(site).append('\n');
                sb.append("Period: ").append(r.label()).append('\n');
                sb.append(String.format("Site drop rate: %.2f%%  (%s)%n", s.dropRate(), s.severity()));
                sb.append(String.format("Releases: %.0f/hour, %.0f abnormal/day%n",
                        s.releasesPerHour(), s.abnormalPerDay()));
                sb.append("Sectors: ").append(s.sectors())
                  .append(", degraded (>").append((long) DEGRADED_PCT).append("%): ")
                  .append(s.degradedSectors()).append('\n');
                sb.append("Dominant cause at site level: ").append(s.dominant()).append("\n\n");
                sb.append("Per sector:\n");
                for (CellStat c : s.cells()) {
                    sb.append(String.format("  %-32s drop=%6.2f%%  dominant=%s%n",
                            c.cell(), c.dropRate(), c.dominant()));
                }
                if (s.multiSector()) {
                    sb.append("\n⚠ MULTI-SECTOR PATTERN: ").append(s.degradedSectors())
                      .append(" sectors degraded, all dominated by ").append(s.dominant()).append(".\n")
                      .append("Independent per-sector RF or PCI faults rarely align this way. Check what "
                      + "the sectors share before treating them as separate problems: transport link, "
                      + "baseband board, GPS/sync, power, licensing.\n")
                      .append("This module sees PM counters only — it cannot confirm any of those.");
                } else if (s.degradedSectors() == 1) {
                    sb.append("\nOnly one sector is degraded, so the cause is most likely specific to it "
                            + "(its RF, its neighbour relations, its PCI) rather than shared by the site.");
                }
                result = sb.toString();
            }
        }

        log("getSiteDropSummary", siteOrCell + " | " + period, result, start);
        return result;
    }

    // ─── internals ────────────────────────────────────────────────────────────

    /** Accepts a site name directly, or resolves the site a cell belongs to. */
    private String resolveSite(String siteOrCell) {
        if (siteOrCell == null || siteOrCell.isBlank()) return null;
        List<String> sites = dropsRepo.findDistinctSiteNames();
        if (sites.contains(siteOrCell)) return siteOrCell;
        return dropsRepo.findSiteOfCell(siteOrCell);
    }

    private SiteStat statFor(String site, TimeRange r) {
        List<String> cells = dropsRepo.findCellNamesBySite(site);
        List<NrCellDrops> allRows = new ArrayList<>();
        List<CellStat> cellStats = new ArrayList<>();

        for (String cell : cells) {
            List<NrCellDrops> rows = dropsRepo
                    .findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(cell, r.start(), r.end());
            if (rows.isEmpty()) continue;
            allRows.addAll(rows);
            DropTotals t = DropTotals.of(rows);
            cellStats.add(new CellStat(cell, t.dropRate(), t.dominantShort()));
        }
        if (allRows.isEmpty()) return null;

        DropTotals siteTotals = DropTotals.of(allRows);
        cellStats.sort(Comparator.comparingDouble(CellStat::dropRate).reversed());

        String dominant = siteTotals.dominantShort();
        long degraded = cellStats.stream().filter(c -> c.dropRate() > DEGRADED_PCT).count();

        // The pattern only counts when the degraded sectors fail the SAME way. Two sectors
        // failing for different reasons is a coincidence, not a shared cause.
        long degradedSameCause = cellStats.stream()
                .filter(c -> c.dropRate() > DEGRADED_PCT && c.dominant().equals(dominant))
                .count();
        boolean multi = degradedSameCause >= MULTI_SECTOR_THRESHOLD;

        return new SiteStat(site, siteTotals.dropRate(), siteTotals.severity(),
                siteTotals.releasesPerHour(), siteTotals.abnormalPerDay(),
                cellStats.size(), (int) degraded, dominant, multi, cellStats);
    }

    private record CellStat(String cell, double dropRate, String dominant) {}

    private record SiteStat(String site, double dropRate, String severity,
                            double releasesPerHour, double abnormalPerDay,
                            int sectors, int degradedSectors, String dominant,
                            boolean multiSector, List<CellStat> cells) {}

    private void log(String tool, String input, String output, long startMs) {
        try {
            AgentLog entry = new AgentLog();
            entry.setAgentName("drops");
            entry.setToolCalled(tool);
            entry.setToolInput(input);
            entry.setToolOutput(output);
            entry.setLatencyMs(System.currentTimeMillis() - startMs);
            logRepo.save(entry);
        } catch (Exception e) {
            System.err.println("[SiteAnalysisTool] log failed: " + e.getMessage());
        }
    }
}
