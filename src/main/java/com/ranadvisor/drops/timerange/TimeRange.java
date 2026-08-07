package com.ranadvisor.drops.timerange;

import java.time.LocalDateTime;

/**
 * A resolved analysis window, plus the label describing how it was resolved.
 *
 * <p>The label exists so the agent can tell the user which window it actually used.
 * "Última semana" is ambiguous — the user means one thing, the data may only support
 * another — and silently analysing a different period than the one asked for is the
 * kind of error nobody notices until a decision has been made on it.
 *
 * @param start     inclusive lower bound
 * @param end       inclusive upper bound
 * @param label     human-readable description of the resolved window
 * @param clamped   true when the requested window had to be trimmed to the data that exists
 */
public record TimeRange(LocalDateTime start, LocalDateTime end, String label, boolean clamped) {

    public TimeRange withLabel(String newLabel) {
        return new TimeRange(start, end, newLabel, clamped);
    }
}
