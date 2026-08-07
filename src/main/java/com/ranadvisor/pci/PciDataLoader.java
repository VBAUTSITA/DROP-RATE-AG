package com.ranadvisor.pci;

import com.ranadvisor.pci.entity.PciCell;
import com.ranadvisor.pci.entity.PciChange;
import com.ranadvisor.pci.entity.PciNeighbor;
import com.ranadvisor.pci.repository.PciCellRepository;
import com.ranadvisor.pci.repository.PciChangeRepository;
import com.ranadvisor.pci.repository.PciNeighborRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
    private final PciChangeRepository changeRepo;

    public PciDataLoader(PciCellRepository cellRepo, PciNeighborRepository neighborRepo,
                         PciChangeRepository changeRepo) {
        this.cellRepo = cellRepo;
        this.neighborRepo = neighborRepo;
        this.changeRepo = changeRepo;
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

        seedChangeHistory();

        System.out.println("[PciDataLoader] Seeded " + plan.length + " PCI cells, "
                + edges.size() + " inter-site neighbour relations and "
                + changeRepo.count() + " PCI change records.");
    }

    /**
     * Seeds when each conflicting PCI was introduced, so the agent can test whether a
     * conflict actually precedes a degradation instead of assuming it does.
     *
     * <p>Two of the three seeded conflicts are deliberately old and one is recent. That is
     * the point: an agent that reports every conflict as the cause of every degradation is
     * not diagnosing, and the only way to see the difference is to have both cases present.
     *
     * <ul>
     *   <li>ARR40312C1 took PCI 168 on <b>2026-06-18</b> — the drop data runs to 30 June, so
     *       this change sits inside the observed window and can be correlated.</li>
     *   <li>ARR39092C1 has had PCI 110 since <b>2024</b>; its confusion is a long-standing
     *       condition and cannot explain anything that started this year.</li>
     *   <li>ARR18911C1 has had PCI 10 since <b>2023</b>; the mod-3 is equally old.</li>
     * </ul>
     */
    private void seedChangeHistory() {
        List<PciChange> history = List.of(
            // The one that matters: introduced mid-window, right before the observed jump.
            new PciChange("ARR40312C1_Moran_Uribe", 205, 168,
                    LocalDateTime.of(2026, 6, 18, 3, 20),
                    "seed:planning-tool", "Capacity re-plan of the Moran Uribe cluster"),
            new PciChange("ARR40312C1_Moran_Uribe", null, 205,
                    LocalDateTime.of(2024, 3, 11, 9, 0),
                    "seed:initial", "Initial commissioning"),

            // The partner has held 168 since commissioning, so the collision starts on the
            // date above, not on this one.
            new PciChange("ARR39931C1_Puente_Quinones", null, 168,
                    LocalDateTime.of(2023, 11, 2, 10, 30),
                    "seed:initial", "Initial commissioning"),

            // Long-standing confusion: predates the drop dataset by well over a year.
            new PciChange("ARR39092C1_Azangaro", null, 110,
                    LocalDateTime.of(2024, 1, 15, 8, 0),
                    "seed:initial", "Initial commissioning"),
            new PciChange("ARR38892C1_Melgar", null, 110,
                    LocalDateTime.of(2024, 2, 20, 8, 0),
                    "seed:initial", "Initial commissioning"),

            // Long-standing mod-3.
            new PciChange("ARR18911C1_Jm_Cuadros", null, 10,
                    LocalDateTime.of(2023, 5, 4, 12, 0),
                    "seed:initial", "Initial commissioning"),
            new PciChange("ARR38551C1_Parra", null, 40,
                    LocalDateTime.of(2023, 6, 18, 12, 0),
                    "seed:initial", "Initial commissioning")
        );
        changeRepo.saveAll(history);
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
