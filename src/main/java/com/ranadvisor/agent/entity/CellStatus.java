package com.ranadvisor.agent.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cell_counters")
public class CellStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_counter")
    private Long idCounter;

    @Column(name = "cell_id", unique = true)
    private String cellId;

    @Column(name = "fecha_carga")
    private String fechaCarga;

    @Column(name = "rrc_att")  private Long rrcAtt;
    @Column(name = "rrc_succ") private Long rrcSucc;
    @Column(name = "erab_att") private Long erabAtt;
    @Column(name = "erab_succ")private Long erabSucc;
    @Column(name = "ho_att")   private Long hoAtt;
    @Column(name = "ho_succ")  private Long hoSucc;

    @Column(name = "avail_minutes") private Long availMinutes;
    @Column(name = "total_minutes") private Long totalMinutes;

    @Column(name = "pag_att")     private Long pagAtt;
    @Column(name = "pag_succ")    private Long pagSucc;
    @Column(name = "prb_used_dl") private Long prbUsedDl;
    @Column(name = "prb_avail_dl")private Long prbAvailDl;
    @Column(name = "rach_att")    private Long rachAtt;
    @Column(name = "rach_succ")   private Long rachSucc;
    @Column(name = "volte_att")   private Long volteAtt;
    @Column(name = "volte_drop")  private Long volteDrop;

    // Getters
    public Long getIdCounter()    { return idCounter; }
    public String getCellId()     { return cellId; }
    public String getFechaCarga() { return fechaCarga; }
    public Long getRrcAtt()       { return rrcAtt; }
    public Long getRrcSucc()      { return rrcSucc; }
    public Long getErabAtt()      { return erabAtt; }
    public Long getErabSucc()     { return erabSucc; }
    public Long getHoAtt()        { return hoAtt; }
    public Long getHoSucc()       { return hoSucc; }
    public Long getAvailMinutes() { return availMinutes; }
    public Long getTotalMinutes() { return totalMinutes; }
    public Long getPagAtt()       { return pagAtt; }
    public Long getPagSucc()      { return pagSucc; }
    public Long getPrbUsedDl()    { return prbUsedDl; }
    public Long getPrbAvailDl()   { return prbAvailDl; }
    public Long getRachAtt()      { return rachAtt; }
    public Long getRachSucc()     { return rachSucc; }
    public Long getVolteAtt()     { return volteAtt; }
    public Long getVolteDrop()    { return volteDrop; }
}
