package com.ranadvisor.drops.timerange;

import com.ranadvisor.drops.repository.NrCellDropsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The parser is pure logic over two repository bounds, so it is tested without Spring
 * or a database. The dataset is pinned to May–June 2026 while "today" is later, which is
 * the whole point: relative phrases must resolve against the data, not the clock.
 */
class TimeRangeParserTest {

    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 5, 1, 0, 0);
    private static final LocalDateTime LAST  = LocalDateTime.of(2026, 6, 30, 23, 0);

    private TimeRangeParser parser;

    @BeforeEach
    void setUp() {
        NrCellDropsRepository repo = mock(NrCellDropsRepository.class);
        when(repo.findEarliestSampleTime()).thenReturn(FIRST);
        when(repo.findLatestSampleTime()).thenReturn(LAST);
        when(repo.findDistinctCellNames()).thenReturn(List.of("CELL_A"));

        parser = new TimeRangeParser();
        ReflectionTestUtils.setField(parser, "dropsRepo", repo);
    }

    @Test
    void relativePeriodsAnchorToTheDataNotToToday() {
        TimeRange r = parser.parse("última semana");

        // Anchored to the newest sample: the window ends where the data ends. Anchored to
        // now() it would sit in the future relative to the dataset and return nothing.
        assertEquals(LAST, r.end());
        assertEquals(LAST.minusWeeks(1), r.start());
        assertTrue(r.start().isAfter(FIRST));
    }

    @Test
    void lastNDaysIsUnderstoodInBothLanguages() {
        assertEquals(LAST.minusDays(7), parser.parse("ultimos 7 dias").start());
        assertEquals(LAST.minusDays(7), parser.parse("last 7 days").start());
    }

    @Test
    void explicitRangeIsParsedAndOrderedRegardlessOfInputOrder() {
        TimeRange a = parser.parse("2026-06-01 a 2026-06-15");
        TimeRange b = parser.parse("2026-06-15 to 2026-06-01");

        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), a.start());
        assertEquals(LocalDateTime.of(2026, 6, 15, 23, 59, 59), a.end());
        assertEquals(a.start(), b.start());
        assertEquals(a.end(), b.end());
    }

    @Test
    void singleDateBecomesThatWholeDay() {
        TimeRange r = parser.parse("2026-06-15");
        assertEquals(LocalDateTime.of(2026, 6, 15, 0, 0), r.start());
        assertEquals(LocalDateTime.of(2026, 6, 15, 23, 59, 59), r.end());
    }

    @Test
    void monthNameResolvesToThatCalendarMonthClampedToTheData() {
        TimeRange r = parser.parse("junio");
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), r.start());
        // June ends on the 30th at 23:59:59, but the data stops at 23:00 — must be trimmed.
        assertEquals(LAST, r.end());
        assertTrue(r.clamped());
    }

    @Test
    void windowOutsideTheDataFallsBackAndSaysSo() {
        TimeRange r = parser.parse("2027-01-01 a 2027-01-31");

        assertTrue(r.clamped());
        assertEquals(FIRST, r.start());
        assertEquals(LAST, r.end());
        assertTrue(r.label().contains("outside"),
                "the label must tell the agent the requested window had no data: " + r.label());
    }

    @Test
    void unparseablePhraseFallsBackToFullDatasetAndSaysSo() {
        TimeRange r = parser.parse("cuando estaba lloviendo");

        assertEquals(FIRST, r.start());
        assertEquals(LAST, r.end());
        assertTrue(r.label().contains("could not interpret"));
    }

    @Test
    void emptyOrAllMeansTheWholeDataset() {
        for (String phrase : new String[] {"", "   ", "todo", "all"}) {
            TimeRange r = parser.parse(phrase);
            assertEquals(FIRST, r.start(), "phrase: " + phrase);
            assertEquals(LAST, r.end(), "phrase: " + phrase);
        }
    }
}
