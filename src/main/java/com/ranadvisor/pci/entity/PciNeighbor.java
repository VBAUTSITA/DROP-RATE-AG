package com.ranadvisor.pci.entity;

import jakarta.persistence.*;

/**
 * A directed neighbour relation (e.g. discovered by ANR / configured as an X2/Xn
 * neighbour). Stored one row per ordered pair; the analysis layer treats the
 * relation as undirected and also unions in co-sited cells.
 */
@Entity
@Table(name = "pci_neighbor",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cell_name", "neighbor_cell_name"}))
public class PciNeighbor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cell_name", nullable = false)
    private String cellName;

    @Column(name = "neighbor_cell_name", nullable = false)
    private String neighborCellName;

    public PciNeighbor() {}

    public PciNeighbor(String cellName, String neighborCellName) {
        this.cellName = cellName;
        this.neighborCellName = neighborCellName;
    }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getCellName() { return cellName; }
    public void setCellName(String v) { this.cellName = v; }
    public String getNeighborCellName() { return neighborCellName; }
    public void setNeighborCellName(String v) { this.neighborCellName = v; }
}
