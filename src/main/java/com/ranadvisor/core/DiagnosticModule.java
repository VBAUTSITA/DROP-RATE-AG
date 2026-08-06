package com.ranadvisor.core;

/**
 * Service Provider Interface (SPI) implemented by every RAN diagnostic module.
 *
 * <p>This is the seam that makes the platform extensible: to add a new diagnostic
 * capability (PCI planning, coverage/overshoot, PRACH RSI planning, accessibility …)
 * you implement this interface as a Spring {@code @Component}. The orchestrator
 * autowires {@code List<DiagnosticModule>}, so a newly added module is picked up
 * automatically — no orchestrator code changes required.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #analyze(String)} must be deterministic and side-effect free
 *       (safe to fan-out across every module for the same cell).</li>
 *   <li>It must never throw for an unknown cell — return
 *       {@link ModuleFinding#none(String, String)} instead.</li>
 *   <li>Populate {@link ModuleFinding#tags()} with stable, documented tags so the
 *       correlator can reason across modules.</li>
 * </ul>
 */
public interface DiagnosticModule {

    /** Stable module id, e.g. "drop-rate". Also used as the finding's {@code module}. */
    String id();

    /** Human-readable domain, e.g. "Call-drop / retainability". */
    String domain();

    /** Analyze a single cell and return a typed finding (never null). */
    ModuleFinding analyze(String cellName);
}
