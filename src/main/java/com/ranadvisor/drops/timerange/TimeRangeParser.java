package com.ranadvisor.drops.timerange;

import com.ranadvisor.drops.repository.NrCellDropsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the period phrase a user types into a concrete window over the drop dataset.
 *
 * <p><b>Relative phrases are anchored to the newest sample in the database, not to the
 * system clock.</b> The counters are a historical export: the dataset ends in June 2026,
 * so resolving "última semana" against {@code now()} returns an empty window and the
 * agent reports "no data" for a cell that has plenty. Anchoring to
 * {@code MAX(sample_time)} makes "last week" mean the last week that was actually
 * measured, which is what someone looking at this data means.
 *
 * <p>Every result is clamped to the dataset bounds and carries a label saying what was
 * resolved, so the agent can state the window it used instead of implying it answered
 * the question as asked.
 *
 * <p>Understood forms (Spanish and English):
 * <ul>
 *   <li>empty, {@code todo}, {@code all}, {@code histórico} — the full dataset</li>
 *   <li>{@code 2026-06-15} — that single day</li>
 *   <li>{@code 2026-06-01..2026-06-15}, {@code ... a ...}, {@code ... to ...} — explicit range</li>
 *   <li>{@code últimos 7 días}, {@code last 14 days}, {@code últimas 48 horas}</li>
 *   <li>{@code última semana}, {@code last week}, {@code último mes}, {@code last month}</li>
 *   <li>{@code ayer}, {@code yesterday}, {@code hoy}, {@code today} — relative to the newest sample</li>
 *   <li>{@code junio}, {@code june}, {@code mayo 2026} — that calendar month</li>
 * </ul>
 */
@Component
public class TimeRangeParser {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern LAST_N = Pattern.compile(
            "(?:ultim[oa]s?|last|past)\\s+(\\d{1,4})\\s*(dias?|days?|horas?|hours?|semanas?|weeks?|mes(?:es)?|months?)");
    private static final Pattern YEAR = Pattern.compile("(20\\d{2})");

    private static final Map<String, Integer> MONTHS = new LinkedHashMap<>();
    static {
        MONTHS.put("enero", 1);      MONTHS.put("january", 1);
        MONTHS.put("febrero", 2);    MONTHS.put("february", 2);
        MONTHS.put("marzo", 3);      MONTHS.put("march", 3);
        MONTHS.put("abril", 4);      MONTHS.put("april", 4);
        MONTHS.put("mayo", 5);       MONTHS.put("may", 5);
        MONTHS.put("junio", 6);      MONTHS.put("june", 6);
        MONTHS.put("julio", 7);      MONTHS.put("july", 7);
        MONTHS.put("agosto", 8);     MONTHS.put("august", 8);
        MONTHS.put("septiembre", 9); MONTHS.put("september", 9);
        MONTHS.put("octubre", 10);   MONTHS.put("october", 10);
        MONTHS.put("noviembre", 11); MONTHS.put("november", 11);
        MONTHS.put("diciembre", 12); MONTHS.put("december", 12);
    }

    @Autowired private NrCellDropsRepository dropsRepo;

    /** Earliest and latest sample present, or null when the table is empty. */
    public TimeRange datasetBounds() {
        LocalDateTime min = dropsRepo.findEarliestSampleTime();
        LocalDateTime max = dropsRepo.findLatestSampleTime();
        if (min == null || max == null) return null;
        return new TimeRange(min, max, "full dataset (" + min + " to " + max + ")", false);
    }

    public TimeRange parse(String phrase) {
        TimeRange bounds = datasetBounds();
        if (bounds == null) {
            LocalDateTime now = LocalDateTime.now();
            return new TimeRange(now, now, "no data loaded", false);
        }
        if (phrase == null || phrase.isBlank()) return bounds;

        String p = phrase.toLowerCase(Locale.ROOT)
                         .replace('á', 'a').replace('é', 'e').replace('í', 'i')
                         .replace('ó', 'o').replace('ú', 'u')
                         .trim();

        if (p.equals("todo") || p.equals("all") || p.contains("historic") || p.contains("complet")) {
            return bounds;
        }

        TimeRange r = explicitDates(p);
        if (r == null) r = lastN(p, bounds);
        if (r == null) r = namedRelative(p, bounds);
        if (r == null) r = calendarMonth(p, bounds);
        if (r == null) return bounds.withLabel(
                "could not interpret \"" + phrase + "\", using the full dataset ("
                + bounds.start() + " to " + bounds.end() + ")");

        return clamp(r, bounds);
    }

    // ─── forms ────────────────────────────────────────────────────────────────

    private TimeRange explicitDates(String p) {
        Matcher m = ISO_DATE.matcher(p);
        LocalDate first = null, second = null;
        while (m.find()) {
            try {
                LocalDate d = LocalDate.parse(m.group(1), ISO);
                if (first == null) first = d; else if (second == null) second = d;
            } catch (DateTimeParseException ignored) {
                // a malformed date is treated as "not this form" so the next parser can try
            }
        }
        if (first == null) return null;

        if (second == null) {
            return new TimeRange(first.atStartOfDay(), first.atTime(23, 59, 59),
                    "day " + first, false);
        }
        LocalDate lo = first.isAfter(second) ? second : first;
        LocalDate hi = first.isAfter(second) ? first : second;
        return new TimeRange(lo.atStartOfDay(), hi.atTime(23, 59, 59),
                lo + " to " + hi, false);
    }

    private TimeRange lastN(String p, TimeRange bounds) {
        Matcher m = LAST_N.matcher(p);
        if (!m.find()) return null;

        long n = Long.parseLong(m.group(1));
        String unit = m.group(2);
        LocalDateTime end = bounds.end();
        LocalDateTime start;
        String what;

        if (unit.startsWith("hora") || unit.startsWith("hour")) {
            start = end.minusHours(n);  what = n + " hour(s)";
        } else if (unit.startsWith("semana") || unit.startsWith("week")) {
            start = end.minusWeeks(n);  what = n + " week(s)";
        } else if (unit.startsWith("mes") || unit.startsWith("month")) {
            start = end.minusMonths(n); what = n + " month(s)";
        } else {
            start = end.minusDays(n);   what = n + " day(s)";
        }
        return new TimeRange(start, end, "last " + what + " of data (up to " + end + ")", false);
    }

    private TimeRange namedRelative(String p, TimeRange bounds) {
        LocalDateTime end = bounds.end();

        if (p.contains("ultima semana") || p.contains("last week") || p.contains("semana pasada")) {
            return new TimeRange(end.minusWeeks(1), end, "last week of data (up to " + end + ")", false);
        }
        if (p.contains("ultimo mes") || p.contains("last month") || p.contains("mes pasado")) {
            return new TimeRange(end.minusMonths(1), end, "last month of data (up to " + end + ")", false);
        }
        if (p.contains("ayer") || p.contains("yesterday")) {
            LocalDate d = end.toLocalDate().minusDays(1);
            return new TimeRange(d.atStartOfDay(), d.atTime(23, 59, 59),
                    "the day before the last day of data (" + d + ")", false);
        }
        if (p.contains("hoy") || p.contains("today") || p.contains("ultimo dia") || p.contains("last day")) {
            LocalDate d = end.toLocalDate();
            return new TimeRange(d.atStartOfDay(), d.atTime(23, 59, 59),
                    "the last day of data (" + d + ")", false);
        }
        return null;
    }

    private TimeRange calendarMonth(String p, TimeRange bounds) {
        for (Map.Entry<String, Integer> e : MONTHS.entrySet()) {
            if (!p.contains(e.getKey())) continue;

            Matcher ym = YEAR.matcher(p);
            int year = ym.find() ? Integer.parseInt(ym.group(1)) : bounds.end().getYear();

            LocalDate first = LocalDate.of(year, e.getValue(), 1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            return new TimeRange(first.atStartOfDay(), last.atTime(23, 59, 59),
                    e.getKey() + " " + year, false);
        }
        return null;
    }

    // ─── clamping ─────────────────────────────────────────────────────────────

    /**
     * Trims a window to the data that exists. A request reaching outside the dataset is
     * reported rather than silently narrowed: an empty or truncated window changes what
     * the numbers mean, and the user has to know that before reading them.
     */
    private TimeRange clamp(TimeRange r, TimeRange bounds) {
        LocalDateTime s = r.start().isBefore(bounds.start()) ? bounds.start() : r.start();
        LocalDateTime e = r.end().isAfter(bounds.end()) ? bounds.end() : r.end();

        boolean trimmed = !s.equals(r.start()) || !e.equals(r.end());

        if (e.isBefore(s)) {
            return new TimeRange(bounds.start(), bounds.end(),
                    "requested window (" + r.label() + ") lies entirely outside the loaded data ("
                    + bounds.start() + " to " + bounds.end() + "); using the full dataset instead",
                    true);
        }
        if (trimmed) {
            return new TimeRange(s, e,
                    r.label() + " — trimmed to available data (" + s + " to " + e + ")", true);
        }
        return r;
    }
}
