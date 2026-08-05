package com.ranadvisor.orchestrator;

import com.ranadvisor.core.ModuleFinding;
import com.ranadvisor.core.RootCauseHypothesis;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The deterministic brain of the orchestrator.
 *
 * <p>It does NOT call an LLM. It reads the machine-readable {@code tags} that each
 * {@link ModuleFinding} carries and applies a small, auditable rule set to produce a
 * single ranked {@link RootCauseHypothesis}. This is what "connecting the modules"
 * actually means: the drop-rate signal on its own says <i>what</i> is wrong (RACH-driven
 * drops); the PCI signal on its own says a collision exists; correlating them yields the
 * <i>why</i> — the PCI collision is the likely root cause of the drops.
 *
 * <p>Rules are ordered most-specific first; the first match wins. Adding a new module
 * means adding new tag-combination rules here (or a new correlator) — the modules
 * themselves stay decoupled and never reference each other.
 */
@Component
public class CrossModuleCorrelator {

    public RootCauseHypothesis correlate(String cellName, List<ModuleFinding> findings) {
        ModuleFinding drop = find(findings, "drop-rate");
        ModuleFinding pci  = find(findings, "pci-planning");

        boolean dropsActionable = drop != null && drop.isActionable();
        boolean rach            = drop != null && drop.hasTag("RACH_FAILURE");
        boolean coverage        = drop != null && drop.hasTag("COVERAGE_DEGRADATION");

        boolean pciFatal = pci != null && (pci.hasTag("PCI_COLLISION") || pci.hasTag("PCI_CONFUSION"));
        boolean pciMod3  = pci != null && pci.hasTag("PCI_MOD3");

        // "Not checked" and "checked, clean" are different facts and must not collapse into the
        // same verdict. A module reports UNKNOWN when its backend was unreachable — it carries no
        // tags, so without this distinction the absence of conflict tags would read as a clean
        // plan and the correlator would rule identity out on the strength of a check that never
        // ran. Same for a PCI module that is not registered at all.
        boolean pciUnchecked = pci == null || ModuleFinding.UNKNOWN.equals(pci.severity());
        boolean pciConfirmedClean = !pciUnchecked && !pciFatal && !pciMod3;

        String  pciKind  = pci == null ? "" :
                pci.hasTag("PCI_COLLISION") ? "collision" :
                pci.hasTag("PCI_CONFUSION") ? "confusion" :
                pci.hasTag("PCI_MOD3")      ? "mod-3 conflict" : "";

        // Rule 1 — RACH-driven drops co-occurring with a fatal PCI conflict: strongest signal.
        if (dropsActionable && rach && pciFatal) {
            return new RootCauseHypothesis(
                String.format("PCI %s is the likely root cause of the RACH-driven drops on %s.", pciKind, cellName),
                RootCauseHypothesis.HIGH,
                "The drop domain reports RACH/access-failure–dominated drops, and the PCI domain reports a "
                    + pciKind + " among this cell's neighbours. PCI ambiguity makes the UE unable to resolve "
                    + "the correct cell during access/handover, which manifests exactly as RACH failures and drops. "
                    + "The two independent signals line up on the same cell.",
                List.of(
                    "Re-plan the PCI to the suggested conflict-free value (see the PCI finding below).",
                    "After the change, re-measure the drop rate for this cell over the next 24h.",
                    "Only if drops persist after the PCI fix, escalate to RF coverage/parameter tuning."),
                findings);
        }

        // Rule 2 — RACH drops with only a mod-3 (softer) PCI issue.
        if (dropsActionable && rach && pciMod3) {
            return new RootCauseHypothesis(
                String.format("A PCI mod-3 (PSS) conflict may be degrading RACH on %s.", cellName),
                RootCauseHypothesis.MEDIUM,
                "RACH-dominated drops coincide with a mod-3 (PSS group) conflict on the same carrier. This degrades "
                    + "sync-signal SINR at the cell edge and can lower RACH success, though it is a weaker cause than a "
                    + "collision/confusion.",
                List.of(
                    "Re-plan the PCI to clear the mod-3 conflict where feasible (see the PCI finding).",
                    "Check PRACH configuration and cell-edge coverage in parallel."),
                findings);
        }

        // Rule 3 — coverage-driven drops, PCI confirmed clean: hand off to RF/coverage domain.
        if (dropsActionable && coverage && pciConfirmedClean) {
            return new RootCauseHypothesis(
                String.format("Coverage/quality degradation (T310) on %s with a clean PCI plan.", cellName),
                RootCauseHypothesis.MEDIUM,
                "Drops are dominated by T310 expiry (DL coverage/quality) and no PCI conflict was found, so the cause "
                    + "is most likely RF coverage/overshoot rather than identity planning.",
                List.of(
                    "Investigate DL coverage: tilt/power/overshoot for this cell.",
                    "(Future) route to a dedicated coverage/overshoot module for confirmation."),
                findings);
        }

        // Rule 4 — drops elevated, PCI confirmed clean and non-RACH cause.
        if (dropsActionable && pciConfirmedClean) {
            return new RootCauseHypothesis(
                String.format("Elevated drops on %s are not explained by PCI — cause is within the drop domain.", cellName),
                RootCauseHypothesis.LOW,
                "The drop domain is actionable but the PCI plan is clean, so the dominant drop cause should be pursued "
                    + "directly (see the drop finding).",
                List.of("Follow the drop module's dominant-cause guidance for this cell."),
                findings);
        }

        // Rule 4b — drops elevated but the PCI domain never answered. The honest verdict is a
        // partial one: report what the drop domain found and say plainly that identity is still
        // open, rather than implying it was cleared.
        if (dropsActionable && pciUnchecked) {
            return new RootCauseHypothesis(
                String.format("Elevated drops on %s, with the PCI domain unchecked.", cellName),
                RootCauseHypothesis.LOW,
                "The drop domain is actionable, but the PCI plan could not be verified for this cell — the "
                    + "PCI backend did not answer. Identity conflicts are therefore neither confirmed nor ruled "
                    + "out, and this diagnosis rests on the drop domain alone.",
                List.of(
                    "Follow the drop module's dominant-cause guidance for this cell.",
                    "Re-run the diagnosis once the PCI planning backend is reachable, so identity can be "
                        + "confirmed or ruled out."),
                findings);
        }

        // Rule 5 — PCI conflict exists but drops are fine: proactive fix.
        if ((pciFatal || pciMod3) && (drop == null || !drop.isActionable())) {
            return new RootCauseHypothesis(
                String.format("PCI %s on %s is not yet degrading drops — fix it proactively.", pciKind, cellName),
                RootCauseHypothesis.MEDIUM,
                "A PCI conflict is present but drops are currently within tolerance. Conflicts of this kind tend to "
                    + "degrade service under load or after mobility changes, so it is worth clearing pre-emptively.",
                List.of("Schedule a PCI re-plan (see the PCI finding) during the next optimization window."),
                findings);
        }

        // Rule 6 — nothing actionable.
        return new RootCauseHypothesis(
            String.format("No cross-module issue detected for %s.", cellName),
            RootCauseHypothesis.NONE,
            "No module reported an actionable (CRITICAL/WARNING) finding for this cell.",
            List.of("No action required."),
            findings);
    }

    private static ModuleFinding find(List<ModuleFinding> findings, String moduleId) {
        return findings.stream().filter(f -> moduleId.equals(f.module())).findFirst().orElse(null);
    }
}
