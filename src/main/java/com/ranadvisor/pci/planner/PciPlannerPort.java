package com.ranadvisor.pci.planner;

/**
 * The seam between the agent and whatever actually knows about PCI planning.
 *
 * <p>Two capabilities matter for the diagnosis flow, and they are deliberately separate
 * because they cost very different amounts:
 *
 * <ul>
 *   <li><b>{@link #audit(String)} — IDENTIFY.</b> Cheap, called on every cross-module
 *       diagnosis (see {@code PciModule}). It must stay in the millisecond range, so a
 *       remote implementation is expected to read a pre-computed conflict table rather
 *       than run the planning engine per call.</li>
 *   <li><b>{@link #propose(String)} — RE-PLAN.</b> Expensive and user-initiated. It runs
 *       (or asks the tool to run) the real assignment algorithm and returns the reasoning
 *       alongside the number.</li>
 * </ul>
 *
 * <p><b>No method on this port writes to the network.</b> {@code propose} is always a
 * simulation; applying a PCI stays a human action inside the planning tool, which is why
 * there is no {@code apply(...)} here and no {@code @Tool} can reach one.
 *
 * <p>Implementations: {@link LocalPciPlannerAdapter} (default — the seeded PCI mirror in
 * PostgreSQL, so the flow runs today) and {@link RestPciPlannerAdapter} (the real
 * multi-technology planner, reached over HTTP because it runs on a different JVM).
 */
public interface PciPlannerPort {

    /** Human-readable backend id, surfaced in tool output so the user knows what answered. */
    String backendName();

    /** Current PCI/RSI plan of a cell. {@link CellPlan#found()} is false when unknown. */
    CellPlan plan(String cellName);

    /** IDENTIFY: conflicts affecting one cell (collision / confusion / mod-3). */
    PciAudit audit(String cellName);

    /** IDENTIFY, network-wide. Used for "which cells have PCI problems". */
    PciAudit auditNetwork();

    /** RE-PLAN: a conflict-free PCI/RSI proposal with the reasoning that produced it. Never persists. */
    PciProposal propose(String cellName);
}
