package com.ranadvisor.pci.planner;

/**
 * The planning backend could not be reached or refused the request.
 *
 * <p>Tools catch this and return the reason as text. A tool that throws aborts the
 * agent turn and the user gets nothing; a tool that says "the planner is unreachable"
 * lets the agent report the real situation.
 */
public class PciPlannerUnavailableException extends RuntimeException {

    public PciPlannerUnavailableException(String message) {
        super(message);
    }

    public PciPlannerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
