package com.ranadvisor.orchestrator;

import com.ranadvisor.core.ModuleFinding;
import com.ranadvisor.core.RootCauseHypothesis;
import com.ranadvisor.pci.PciAnalysisTool;
import com.ranadvisor.pci.PciConflict;
import com.ranadvisor.pci.PciModule;
import com.ranadvisor.pci.entity.PciCell;
import com.ranadvisor.pci.entity.PciNeighbor;
import com.ranadvisor.pci.repository.PciCellRepository;
import com.ranadvisor.pci.repository.PciNeighborRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-logic verification of the deterministic core (PCI detection + cross-module
 * correlation) against the same topology the loader seeds. No Spring context, no DB.
 */
class OrchestrationLogicTest {

    private static final String WORST = "ARR40312C1_Moran_Uribe";

    private final Map<String, PciCell> cells = new LinkedHashMap<>();
    private final List<PciNeighbor> edges = new ArrayList<>();

    private PciAnalysisTool tool() {
        seed();
        PciCellRepository cellRepo = mock(PciCellRepository.class);
        PciNeighborRepository nRepo = mock(PciNeighborRepository.class);
        when(cellRepo.findByCellName(anyString())).thenAnswer(i -> cells.get(i.<String>getArgument(0)));
        when(cellRepo.findByGnodebName(anyString())).thenAnswer(i -> {
            String g = i.getArgument(0);
            List<PciCell> out = new ArrayList<>();
            for (PciCell c : cells.values()) if (g.equals(c.getGnodebName())) out.add(c);
            return out;
        });
        when(cellRepo.findAll()).thenReturn(new ArrayList<>(cells.values()));
        when(nRepo.findByCellName(anyString())).thenAnswer(i -> {
            String c = i.getArgument(0);
            List<PciNeighbor> out = new ArrayList<>();
            for (PciNeighbor e : edges) if (e.getCellName().equals(c)) out.add(e);
            return out;
        });
        when(nRepo.findByNeighborCellName(anyString())).thenAnswer(i -> {
            String c = i.getArgument(0);
            List<PciNeighbor> out = new ArrayList<>();
            for (PciNeighbor e : edges) if (e.getNeighborCellName().equals(c)) out.add(e);
            return out;
        });
        PciAnalysisTool t = new PciAnalysisTool();
        ReflectionTestUtils.setField(t, "cellRepo", cellRepo);
        ReflectionTestUtils.setField(t, "neighborRepo", nRepo);
        return t;
    }

    @Test
    void worstCell_hasExactlyOneCollisionWithPuente() {
        List<PciConflict> conflicts = tool().detectForCell(WORST);
        long collisions = conflicts.stream().filter(c -> c.type().equals(PciConflict.COLLISION)).count();
        assertEquals(1, collisions, "worst cell should have exactly one PCI collision");
        assertTrue(conflicts.stream().anyMatch(c ->
                c.type().equals(PciConflict.COLLISION) && c.cellB().equals("ARR39931C1_Puente_Quinones")),
                "collision should be with ARR39931C1_Puente_Quinones on PCI 168");
        assertTrue(conflicts.stream().noneMatch(c -> c.type().equals(PciConflict.CONFUSION)),
                "worst cell should not have a confusion");
    }

    @Test
    void azangaro_hasConfusionOnPci110() {
        List<PciConflict> conflicts = tool().detectForCell("ARR39091C1_Azangaro");
        assertTrue(conflicts.stream().anyMatch(c ->
                c.type().equals(PciConflict.CONFUSION) && c.pciA() == 110),
                "ARR39091C1 should see a confusion on PCI 110");
    }

    @Test
    void networkAudit_findsCollisionConfusionAndMod3() {
        List<PciConflict> all = tool().detectNetworkWide();
        assertTrue(all.stream().anyMatch(c -> c.type().equals(PciConflict.COLLISION)), "expected a collision");
        assertTrue(all.stream().anyMatch(c -> c.type().equals(PciConflict.CONFUSION)), "expected a confusion");
        assertTrue(all.stream().anyMatch(c ->
                c.type().equals(PciConflict.MOD3)
                    && ((c.pciA() == 10 && c.pciB() == 40) || (c.pciA() == 40 && c.pciB() == 10))),
                "expected the JM_CUADROS↔PARRA mod-3 (PCI 10 vs 40)");
    }

    @Test
    void suggestPci_forWorstCell_isCollisionFree() {
        PciAnalysisTool t = tool();
        Integer suggestion = t.suggestPci(WORST);
        assertNotNull(suggestion);
        assertNotEquals(168, suggestion.intValue(), "must move off the colliding PCI 168");
        // no neighbour may share the suggested PCI
        assertTrue(t.neighborsOf(WORST).stream().noneMatch(n -> suggestion.equals(n.getPci())),
                "suggested PCI must not collide with any neighbour");
    }

    @Test
    void pciModule_reportsCriticalCollisionForWorstCell() {
        PciAnalysisTool t = tool();
        PciModule module = new PciModule();
        ReflectionTestUtils.setField(module, "pci", t);
        ReflectionTestUtils.setField(module, "cellRepo", ReflectionTestUtils.getField(t, "cellRepo"));
        ModuleFinding f = module.analyze(WORST);
        assertEquals(ModuleFinding.CRITICAL, f.severity());
        assertTrue(f.hasTag("PCI_COLLISION"));
    }

    @Test
    void correlator_linksRachDropsToPciCollision_withHighConfidence() {
        ModuleFinding drop = new ModuleFinding("drop-rate", WORST, ModuleFinding.CRITICAL,
                "Drop rate 30.13% (CRITICAL); dominant cause RA Problem.",
                Set.of("HIGH_DROP", "RACH_FAILURE"), "");
        ModuleFinding pci = new ModuleFinding("pci-planning", WORST, ModuleFinding.CRITICAL,
                "PCI 168: 1 conflict [PCI_COLLISION].", Set.of("PCI_COLLISION"), "");
        RootCauseHypothesis h = new CrossModuleCorrelator().correlate(WORST, List.of(drop, pci));
        assertEquals(RootCauseHypothesis.HIGH, h.confidence());
        assertTrue(h.headline().toLowerCase().contains("collision"),
                "hypothesis should name the PCI collision as root cause");
    }

    @Test
    void correlator_cleanPci_doesNotBlamePci() {
        ModuleFinding drop = new ModuleFinding("drop-rate", "X", ModuleFinding.CRITICAL,
                "Drop rate 20% (CRITICAL); dominant cause T310.",
                Set.of("HIGH_DROP", "COVERAGE_DEGRADATION"), "");
        ModuleFinding pci = new ModuleFinding("pci-planning", "X", ModuleFinding.OK,
                "PCI ok.", Set.of(), "");
        RootCauseHypothesis h = new CrossModuleCorrelator().correlate("X", List.of(drop, pci));
        assertNotEquals(RootCauseHypothesis.HIGH, h.confidence());
        assertFalse(h.headline().toLowerCase().contains("collision"));
    }

    // ── seed: same 28 cells + 5 inter-site edges as PciDataLoader ────────────────
    private void seed() {
        if (!cells.isEmpty()) return;
        add("ARR18911C1_Jm_Cuadros", "MBTS_AR1891_JM_CUADROS", 10);
        add("ARR18912C1_Jm_Cuadros", "MBTS_AR1891_JM_CUADROS", 20);
        add("ARR18913C1_Jm_Cuadros", "MBTS_AR1891_JM_CUADROS", 30);
        add("ARR38551C1_Parra", "MBTS_AR3855_PARRA", 40);
        add("ARR38552C1_Parra", "MBTS_AR3855_PARRA", 50);
        add("ARR38554C1_Parra", "MBTS_AR3855_PARRA", 60);
        add("ARR38891C1_Melgar", "MBTS_AR3889_MELGAR", 70);
        add("ARR38892C1_Melgar", "MBTS_AR3889_MELGAR", 110);
        add("ARR38893C1_Melgar", "MBTS_AR3889_MELGAR", 90);
        add("ARR39091C1_Azangaro", "MBTS_AR3909_AZANGARO", 100);
        add("ARR39092C1_Azangaro", "MBTS_AR3909_AZANGARO", 110);
        add("ARR39093C1_Azangaro", "MBTS_AR3909_AZANGARO", 120);
        add("ARR39361C1_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", 130);
        add("ARR39361C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", 131);
        add("ARR39362C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", 137);
        add("ARR39363C1_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", 150);
        add("ARR39363C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", 152);
        add("ARR39364C1_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", 155);
        add("ARR39364C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", 163);
        add("ARR39931C1_Puente_Quinones", "MBTS_AR3993_PUENTE_QUINONES", 168);
        add("ARR39932C1_Puente_Quinones", "MBTS_AR3993_PUENTE_QUINONES", 175);
        add("ARR39933C1_Puente_Quinones", "MBTS_AR3993_PUENTE_QUINONES", 185);
        add("ARR40311C1_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", 190);
        add("ARR40311C3_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", 191);
        add(WORST, "MBTS_AR4031_MORAN_URIBE", 168);
        add("ARR40312C3_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", 201);
        add("ARR40313C1_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", 209);
        add("ARR40313C3_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", 211);
        edges.add(new PciNeighbor(WORST, "ARR39931C1_Puente_Quinones"));
        edges.add(new PciNeighbor("ARR39091C1_Azangaro", "ARR38892C1_Melgar"));
        edges.add(new PciNeighbor("ARR18911C1_Jm_Cuadros", "ARR38551C1_Parra"));
        edges.add(new PciNeighbor("ARR38891C1_Melgar", "ARR39093C1_Azangaro"));
        edges.add(new PciNeighbor("ARR38552C1_Parra", "ARR38891C1_Melgar"));
    }

    private void add(String name, String gnodeb, int pci) {
        int arfcn = name.charAt(name.indexOf('C', 3) + 1) == '3' ? 520110 : 504990;
        cells.put(name, new PciCell(name, gnodeb, pci, arfcn, 0));
    }
}
