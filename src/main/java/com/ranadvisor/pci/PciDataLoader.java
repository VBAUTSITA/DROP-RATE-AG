package com.ranadvisor.pci;

import com.ranadvisor.pci.entity.PciCell;
import com.ranadvisor.pci.entity.PciNeighbor;
import com.ranadvisor.pci.repository.PciCellRepository;
import com.ranadvisor.pci.repository.PciNeighborRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a small but realistic PCI plan for the same 28 cells that appear in the
 * drops dataset, so cross-module (drops ⇄ PCI) lookups resolve by cell name.
 *
 * <p>The plan is deterministic and contains three <b>deliberate</b> conflicts used to
 * demonstrate cross-module root-cause analysis, plus one realistic incidental mod-3:
 * <ul>
 *   <li>COLLISION — worst drop cell {@code ARR40312C1_Moran_Uribe} (PCI 168) with its
 *       neighbour {@code ARR39931C1_Puente_Quinones} (PCI 168). This is the crafted
 *       root cause of that cell's RACH-driven drops.</li>
 *   <li>CONFUSION — {@code ARR39091C1_Azangaro} sees two neighbours (its co-sited
 *       {@code ARR39092C1} and inter-site {@code ARR38892C1_Melgar}) both on PCI 110.</li>
 *   <li>MOD3 — {@code ARR18911C1_Jm_Cuadros} (PCI 10) and neighbour
 *       {@code ARR38551C1_Parra} (PCI 40) share PSS group 1.</li>
 * </ul>
 */
@Component
public class PciDataLoader {

    private static final int ARFCN_C1 = 504990;   // low band carrier
    private static final int ARFCN_C3 = 520110;   // high band carrier

    private final PciCellRepository cellRepo;
    private final PciNeighborRepository neighborRepo;

    public PciDataLoader(PciCellRepository cellRepo, PciNeighborRepository neighborRepo) {
        this.cellRepo = cellRepo;
        this.neighborRepo = neighborRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        if (cellRepo.count() > 0) {
            System.out.println("[PciDataLoader] pci_cell already populated (" +
                    cellRepo.count() + " rows). Skipping seed.");
            return;
        }

        // cellName, gNodeB, PCI  — ARFCN and azimuth are derived from the name.
        String[][] plan = {
            {"ARR18911C1_Jm_Cuadros", "MBTS_AR1891_JM_CUADROS", "10"},
            {"ARR18912C1_Jm_Cuadros", "MBTS_AR1891_JM_CUADROS", "20"},
            {"ARR18913C1_Jm_Cuadros", "MBTS_AR1891_JM_CUADROS", "30"},
            {"ARR38551C1_Parra", "MBTS_AR3855_PARRA", "40"},
            {"ARR38552C1_Parra", "MBTS_AR3855_PARRA", "50"},
            {"ARR38554C1_Parra", "MBTS_AR3855_PARRA", "60"},
            {"ARR38891C1_Melgar", "MBTS_AR3889_MELGAR", "70"},
            {"ARR38892C1_Melgar", "MBTS_AR3889_MELGAR", "110"},
            {"ARR38893C1_Melgar", "MBTS_AR3889_MELGAR", "90"},
            {"ARR39091C1_Azangaro", "MBTS_AR3909_AZANGARO", "100"},
            {"ARR39092C1_Azangaro", "MBTS_AR3909_AZANGARO", "110"},
            {"ARR39093C1_Azangaro", "MBTS_AR3909_AZANGARO", "120"},
            {"ARR39361C1_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", "130"},
            {"ARR39361C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", "131"},
            {"ARR39362C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", "137"},
            {"ARR39363C1_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", "150"},
            {"ARR39363C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", "152"},
            {"ARR39364C1_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", "155"},
            {"ARR39364C3_Residencial_La_Lomada", "MBTS_AR3936_RESIDENCIAL_LA_LOMADA", "163"},
            {"ARR39931C1_Puente_Quinones", "MBTS_AR3993_PUENTE_QUINONES", "168"},
            {"ARR39932C1_Puente_Quinones", "MBTS_AR3993_PUENTE_QUINONES", "175"},
            {"ARR39933C1_Puente_Quinones", "MBTS_AR3993_PUENTE_QUINONES", "185"},
            {"ARR40311C1_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", "190"},
            {"ARR40311C3_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", "191"},
            {"ARR40312C1_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", "168"},   // worst drop cell — collides with 39931C1
            {"ARR40312C3_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", "201"},
            {"ARR40313C1_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", "209"},
            {"ARR40313C3_Moran_Uribe", "MBTS_AR4031_MORAN_URIBE", "211"},
        };

        for (String[] row : plan) {
            String name = row[0];
            PciCell c = new PciCell(name, row[1], Integer.parseInt(row[2]), arfcnOf(name), azimuthOf(name));
            cellRepo.save(c);
        }

        // Explicit inter-site ANR neighbour relations (co-sited cells are neighbours implicitly).
        List<String[]> edges = List.of(
            new String[]{"ARR40312C1_Moran_Uribe", "ARR39931C1_Puente_Quinones"}, // -> COLLISION (PCI 168)
            new String[]{"ARR39091C1_Azangaro",    "ARR38892C1_Melgar"},          // -> CONFUSION (PCI 110)
            new String[]{"ARR18911C1_Jm_Cuadros",  "ARR38551C1_Parra"},           // -> MOD3 (PSS group 1)
            new String[]{"ARR38891C1_Melgar",      "ARR39093C1_Azangaro"},        // benign adjacency
            new String[]{"ARR38552C1_Parra",       "ARR38891C1_Melgar"}           // benign adjacency
        );
        for (String[] e : edges) neighborRepo.save(new PciNeighbor(e[0], e[1]));

        System.out.println("[PciDataLoader] Seeded " + plan.length + " PCI cells and "
                + edges.size() + " inter-site neighbour relations.");
    }

    /** C3 cells sit on the high-band carrier; everything else on the low-band carrier. */
    private static int arfcnOf(String cellName) {
        int c = cellName.indexOf('C', 3);
        return (c >= 0 && c + 1 < cellName.length() && cellName.charAt(c + 1) == '3') ? ARFCN_C3 : ARFCN_C1;
    }

    /** Sector = the digit before the carrier token; azimuth = (sector-1)·120° mod 360. */
    private static int azimuthOf(String cellName) {
        int c = cellName.indexOf('C', 3);
        int sector = (c > 0) ? (cellName.charAt(c - 1) - '0') : 1;
        if (sector < 1) sector = 1;
        return ((sector - 1) * 120) % 360;
    }
}
