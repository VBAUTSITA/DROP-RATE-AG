package com.ranadvisor.pci.entity;

import jakarta.persistence.*;

/**
 * PCI (Physical Cell Identity) plan record for a single 5G cell.
 *
 * <p>PCI is an integer 0..503 broadcast by each cell (PCI = 3·SSS + PSS).
 * It is the identity a UE uses to distinguish cells; a bad plan (two neighbours
 * sharing a PCI, or a cell seeing two neighbours with the same PCI) causes
 * handover and RACH failures — which surface downstream as call drops.
 */
@Entity
@Table(name = "pci_cell",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cell_name"}))
public class PciCell {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cell_name", nullable = false)
    private String cellName;

    @Column(name = "gnodeb_name")
    private String gnodebName;

    /** Physical Cell Identity, 0..503. */
    @Column(name = "pci")
    private Integer pci;

    /** NR-ARFCN (carrier frequency channel). mod-3 (PSS) interference only matters within the same ARFCN. */
    @Column(name = "nr_arfcn")
    private Integer nrArfcn;

    /** Sector azimuth in degrees (metadata / RF context). */
    @Column(name = "azimuth_deg")
    private Integer azimuthDeg;

    public PciCell() {}

    public PciCell(String cellName, String gnodebName, Integer pci, Integer nrArfcn, Integer azimuthDeg) {
        this.cellName = cellName;
        this.gnodebName = gnodebName;
        this.pci = pci;
        this.nrArfcn = nrArfcn;
        this.azimuthDeg = azimuthDeg;
    }

    /** PSS group = PCI mod 3. Two cells on the same ARFCN with equal PSS interfere on sync signals. */
    public int pssGroup() { return pci == null ? -1 : pci % 3; }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getCellName() { return cellName; }
    public void setCellName(String v) { this.cellName = v; }
    public String getGnodebName() { return gnodebName; }
    public void setGnodebName(String v) { this.gnodebName = v; }
    public Integer getPci() { return pci; }
    public void setPci(Integer v) { this.pci = v; }
    public Integer getNrArfcn() { return nrArfcn; }
    public void setNrArfcn(Integer v) { this.nrArfcn = v; }
    public Integer getAzimuthDeg() { return azimuthDeg; }
    public void setAzimuthDeg(Integer v) { this.azimuthDeg = v; }
}
