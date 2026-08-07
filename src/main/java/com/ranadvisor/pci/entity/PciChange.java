package com.ranadvisor.pci.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One recorded change of a cell's PCI.
 *
 * <p>Why this table exists: without it the agent can say "this cell has a PCI collision"
 * and nothing more, and it will present that as the root cause of the drops. But a
 * collision that has been in place for two years cannot explain a degradation that began
 * last Tuesday — it is a pre-existing condition, and something else changed. Distinguishing
 * the two needs the date the conflicting PCI was introduced, which {@code pci_cell} (a
 * snapshot of current state) does not carry.
 *
 * <p>A change log is used rather than {@code valid_from}/{@code valid_to} columns on
 * {@code pci_cell} because the question is "when did this value appear", not "what was the
 * plan on date X". The log answers the first directly and keeps the snapshot table simple.
 */
@Entity
@Table(name = "pci_change",
       indexes = @Index(name = "idx_pci_change_cell", columnList = "cell_name"))
public class PciChange {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cell_name", nullable = false)
    private String cellName;

    /** Null when this row records the cell's first known assignment. */
    @Column(name = "old_pci")
    private Integer oldPci;

    @Column(name = "new_pci", nullable = false)
    private Integer newPci;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Where the record came from — planning tool, manual entry, seed. Shown in tool output. */
    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "reason", length = 200)
    private String reason;

    public PciChange() {}

    public PciChange(String cellName, Integer oldPci, Integer newPci,
                     LocalDateTime changedAt, String source, String reason) {
        this.cellName = cellName;
        this.oldPci = oldPci;
        this.newPci = newPci;
        this.changedAt = changedAt;
        this.source = source;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public String getCellName() { return cellName; }
    public Integer getOldPci() { return oldPci; }
    public Integer getNewPci() { return newPci; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public String getSource() { return source; }
    public String getReason() { return reason; }
}
