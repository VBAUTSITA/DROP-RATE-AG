package com.ranadvisor.pci;

import com.ranadvisor.core.DiagnosticModule;
import com.ranadvisor.core.ModuleFinding;
import com.ranadvisor.pci.planner.CellPlan;
import com.ranadvisor.pci.planner.PciAudit;
import com.ranadvisor.pci.planner.PciPlannerPort;
import com.ranadvisor.pci.planner.PciPlannerUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adapts PCI diagnostics into the shared {@link DiagnosticModule} SPI so the orchestrator
 * can fan out to it uniformly and correlate its tags.
 *
 * <p>Emitted tags: {@code PCI_COLLISION}, {@code PCI_CONFUSION}, {@code PCI_MOD3}.
 *
 * <p><b>This module only audits — it never asks for a re-plan.</b> {@code analyze} runs on
 * every {@code diagnoseCell} call, alongside every other module, so it has to stay cheap.
 * Computing a proposal means running the assignment engine over the surrounding network,
 * which takes seconds; doing that inside a fan-out would make cross-module diagnosis
 * unusable. The proposal is a separate, user-initiated step
 * ({@code OrchestratorTools.tacklePciIssue}).
 */
@Component
public class PciModule implements DiagnosticModule {

    @Autowired private PciPlannerPort planner;

    @Override public String id() { return "pci-planning"; }

    @Override public String domain() { return "PCI planning / identity conflicts"; }

    @Override
    public ModuleFinding analyze(String cellName) {
        CellPlan plan;
        PciAudit audit;
        try {
            plan = planner.plan(cellName);
            if (!plan.found()) return ModuleFinding.none(id(), cellName);
            audit = planner.audit(cellName);
        } catch (PciPlannerUnavailableException e) {
            // A module that throws would abort the whole cross-module diagnosis, so the outage
            // is reported as a finding instead. It must be UNKNOWN, never OK: OK means "checked,
            // no conflict", and the correlator's rules read a non-conflicting PCI finding as
            // evidence that identity is ruled out. An unreachable backend proves nothing.
            return new ModuleFinding(id(), cellName, ModuleFinding.UNKNOWN,
                    "PCI backend unavailable — identity conflicts could not be checked.",
                    Set.of(), e.getMessage());
        }

        Set<String> tags = new LinkedHashSet<>();
        for (PciConflict cf : audit.conflicts()) {
            switch (cf.type()) {
                case PciConflict.COLLISION -> tags.add("PCI_COLLISION");
                case PciConflict.CONFUSION -> tags.add("PCI_CONFUSION");
                case PciConflict.MOD3      -> tags.add("PCI_MOD3");
                default -> { }
            }
        }

        String severity = audit.hasFatal() ? ModuleFinding.CRITICAL
                        : audit.hasMod3() ? ModuleFinding.WARNING
                        : ModuleFinding.OK;

        String headline = audit.isClean()
                ? String.format("PCI %s on %s: no conflicts.", nvl(plan.pci()), nvl(plan.site()))
                : String.format("PCI %s on %s: %d conflict(s) [%s].",
                        nvl(plan.pci()), nvl(plan.site()), audit.conflicts().size(), String.join(",", tags));

        StringBuilder detail = new StringBuilder();
        for (PciConflict cf : audit.conflicts()) detail.append(cf).append('\n');
        if (!audit.isClean()) {
            detail.append("A re-plan proposal is available on request — it runs the planning engine, "
                    + "so it is not computed as part of this diagnosis.");
        }

        return new ModuleFinding(id(), cellName, severity, headline, tags, detail.toString().trim());
    }

    private static String nvl(Object v) {
        return v == null ? "unknown" : String.valueOf(v);
    }
}
