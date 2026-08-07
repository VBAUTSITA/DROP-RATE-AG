package com.ranadvisor.drops.metrics;

import com.ranadvisor.drops.entity.NrCellDrops;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The counter arithmetic moved out of DropAnalysisTool so that the site aggregation and the
 * PCI track could reuse it. These pin the parts three callers now depend on.
 */
class DropTotalsTest {

    private static NrCellDrops sample(LocalDateTime at, long relTotal, long scgFail,
                                      long raProblem, long t310) {
        NrCellDrops r = new NrCellDrops();
        r.setSampleTime(at);
        r.setSgnbRelTotal(relTotal);
        r.setMenbScgfail(scgFail);
        r.setMenbScgfailRaproblem(raProblem);
        r.setMenbScgfailT310expiry(t310);
        return r;
    }

    @Test
    void dropRateIsAbnormalOverTotal() {
        DropTotals t = DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 1000, 100, 90, 10),
                sample(LocalDateTime.of(2026, 6, 1, 1, 0), 1000, 100, 90, 10)));

        assertEquals(10.0, t.dropRate(), 0.001);
        assertEquals(200, t.totalAbnormal());
    }

    @Test
    void ratesUseTheSampleCountNotTheWallClockSpan() {
        // Two samples an hour apart, but also two samples: a gap in the export must not
        // inflate the per-hour figure by shrinking the divisor.
        DropTotals t = DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 600, 60, 60, 0),
                sample(LocalDateTime.of(2026, 6, 3, 0, 0), 600, 60, 60, 0)));

        assertEquals(2, t.spanHours());
        assertEquals(600.0, t.releasesPerHour(), 0.001);
        assertEquals(60.0, t.abnormalPerHour(), 0.001);
        assertEquals(1440.0, t.abnormalPerDay(), 0.001);
    }

    @Test
    void dominantCauseIsTheLargestCounter() {
        DropTotals raDriven = DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 1000, 100, 90, 10)));
        assertEquals("RA Problem", raDriven.dominantShort());
        assertEquals(90, raDriven.dominantCount());

        DropTotals coverageDriven = DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 1000, 100, 10, 90)));
        assertEquals("T310 Expiry", coverageDriven.dominantShort());
    }

    @Test
    void severityThresholds() {
        assertEquals("OK", DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 1000, 20, 20, 0))).severity());
        assertEquals("WARNING", DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 1000, 100, 100, 0))).severity());
        assertEquals("CRITICAL", DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 1000, 300, 300, 0))).severity());
    }

    @Test
    void emptyInputIsSafe() {
        DropTotals t = DropTotals.of(List.of());
        assertEquals(0.0, t.dropRate(), 0.001);
        assertEquals(0.0, t.releasesPerHour(), 0.001);
        assertEquals("OK", t.severity());
        assertNull(t.firstSample);
    }

    @Test
    void windowBoundsAreTrackedRegardlessOfInputOrder() {
        DropTotals t = DropTotals.of(List.of(
                sample(LocalDateTime.of(2026, 6, 10, 5, 0), 100, 1, 1, 0),
                sample(LocalDateTime.of(2026, 6, 1, 0, 0), 100, 1, 1, 0),
                sample(LocalDateTime.of(2026, 6, 20, 9, 0), 100, 1, 1, 0)));

        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), t.firstSample);
        assertEquals(LocalDateTime.of(2026, 6, 20, 9, 0), t.lastSample);
        assertEquals(3, t.samples);
    }
}
