package com.ranadvisor.pci;

import com.ranadvisor.core.DiagnosticModule;
import com.ranadvisor.core.ModuleFinding;
import com.ranadvisor.pci.entity.PciCell;
import com.ranadvisor.pci.repository.PciCellRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapts the PCI diagnostics into the shared {@link DiagnosticModule} SPI so the
 * orchestrator can fan out to it uniformly and correlate its tags.
 *
 * <p>Emitted tags: {@code PCI_COLLISION}, {@code PCI_CONFUSION}, {@code PCI_MOD3}.
 */
@Component
public class PciModule implements DiagnosticModule {

    @Autowired private PciAnalysisTool pci;
    @Autowired private PciCellRepository cellRepo;

    @Override public String id() { return "pci-planning"; }

    @Override public String domain() { return "PCI planning / identity conflicts"; }

    @Override
    public ModuleFinding analyze(String cellName) {
        PciCell c = cellRepo.findByCellName(cellName);
        if (c == null) return ModuleFinding.none(id(), cellName);

        List<PciConflict> conflicts = pci.detectForCell(cellName);
        Set<String> tags = new LinkedHashSet<>();
        boolean collision = false, confusion = false, mod3 = false;
        for (PciConflict cf : conflicts) {
            switch (cf.type()) {
                case PciConflict.COLLISION -> { tags.add("PCI_COLLISION"); collision = true; }
                case PciConflict.CONFUSION -> { tags.add("PCI_CONFUSION"); confusion = true; }
                case PciConflict.MOD3      -> { tags.add("PCI_MOD3");      mod3 = true; }
            }
        }

        String severity = (collision || confusion) ? ModuleFinding.CRITICAL
                        : mod3 ? ModuleFinding.WARNING
                        : ModuleFinding.OK;

        String headline = conflicts.isEmpty()
                ? String.format("PCI %d on %s: no conflicts.", c.getPci(), c.getGnodebName())
                : String.format("PCI %d on %s: %d conflict(s) [%s].",
                        c.getPci(), c.getGnodebName(), conflicts.size(), String.join(",", tags));

        StringBuilder detail = new StringBuilder();
        for (PciConflict cf : conflicts) detail.append(cf).append('\n');
        if (!conflicts.isEmpty()) {
            Integer suggestion = pci.suggestPci(cellName);
            if (suggestion != null) {
                detail.append("Suggested conflict-free PCI: ").append(suggestion)
                      .append(" (PSS group ").append(suggestion % 3).append(").");
            }
        }
        return new ModuleFinding(id(), cellName, severity, headline, tags, detail.toString().trim());
    }
}
