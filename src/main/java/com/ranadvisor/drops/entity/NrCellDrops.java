package com.ranadvisor.drops.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nr_cell_drops",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cell_name", "sample_time"}))
public class NrCellDrops {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sample_time")
    private LocalDateTime sampleTime;
    @Column(name = "gnodeb_name")
    private String gnodebName;
    @Column(name = "cell_name")
    private String cellName;
    @Column(name = "nr_cell_id")
    private Integer nrCellId;
    @Column(name = "gnodeb_function_name")
    private String gnodebFunctionName;
    @Column(name = "integrity")
    private String integrity;
    @Column(name = "menb_scgfail")
    private Long menbScgfail;
    @Column(name = "menb_scgfail_raproblem")
    private Long menbScgfailRaproblem;
    @Column(name = "menb_scgfail_rlcmaxnumretx")
    private Long menbScgfailRlcmaxnumretx;
    @Column(name = "menb_scgfail_recfgfail")
    private Long menbScgfailRecfgfail;
    @Column(name = "menb_scgfail_syncrecfgfail")
    private Long menbScgfailSyncrecfgfail;
    @Column(name = "menb_scgfail_t310expiry")
    private Long menbScgfailT310expiry;
    @Column(name = "sgnb_abnrel")
    private Long sgnbAbnrel;
    @Column(name = "sgnb_abnrel_noreply")
    private Long sgnbAbnrelNoreply;
    @Column(name = "sgnb_abnrel_radio")
    private Long sgnbAbnrelRadio;
    @Column(name = "sgnb_abnrel_radio_uelost")
    private Long sgnbAbnrelRadioUelost;
    @Column(name = "sgnb_abnrel_radio_ulsyncfail")
    private Long sgnbAbnrelRadioUlsyncfail;
    @Column(name = "sgnb_abnrel_radio_sul")
    private Long sgnbAbnrelRadioSul;
    @Column(name = "sgnb_abnrel_rnl_preempt")
    private Long sgnbAbnrelRnlPreempt;
    @Column(name = "sgnb_abnrel_trans")
    private Long sgnbAbnrelTrans;
    @Column(name = "ra_ta_ue_idx0")
    private Long raTaUeIdx0;
    @Column(name = "ra_ta_ue_idx1")
    private Long raTaUeIdx1;
    @Column(name = "ra_ta_ue_idx2")
    private Long raTaUeIdx2;
    @Column(name = "ra_ta_ue_idx3")
    private Long raTaUeIdx3;
    @Column(name = "ra_ta_ue_idx4")
    private Long raTaUeIdx4;
    @Column(name = "ra_ta_ue_idx5")
    private Long raTaUeIdx5;
    @Column(name = "ra_ta_ue_idx6")
    private Long raTaUeIdx6;
    @Column(name = "ra_ta_ue_idx7")
    private Long raTaUeIdx7;
    @Column(name = "ra_ta_ue_idx8")
    private Long raTaUeIdx8;
    @Column(name = "ra_ta_ue_idx9")
    private Long raTaUeIdx9;
    @Column(name = "ra_ta_ue_idx10")
    private Long raTaUeIdx10;
    @Column(name = "ra_ta_ue_idx11")
    private Long raTaUeIdx11;
    @Column(name = "ra_ta_ue_idx12")
    private Long raTaUeIdx12;
    @Column(name = "ra_ta_ue_idx13")
    private Long raTaUeIdx13;
    @Column(name = "ra_ta_ue_idx14")
    private Long raTaUeIdx14;
    @Column(name = "ra_ta_ue_idx15")
    private Long raTaUeIdx15;
    @Column(name = "measrpt_rsrp_idx0")
    private Long measrptRsrpIdx0;
    @Column(name = "measrpt_rsrp_idx1")
    private Long measrptRsrpIdx1;
    @Column(name = "measrpt_rsrp_idx2")
    private Long measrptRsrpIdx2;
    @Column(name = "measrpt_rsrp_idx3")
    private Long measrptRsrpIdx3;
    @Column(name = "measrpt_rsrp_idx4")
    private Long measrptRsrpIdx4;
    @Column(name = "measrpt_rsrp_idx5")
    private Long measrptRsrpIdx5;
    @Column(name = "measrpt_rsrp_idx6")
    private Long measrptRsrpIdx6;
    @Column(name = "measrpt_rsrp_idx7")
    private Long measrptRsrpIdx7;
    @Column(name = "measrpt_rsrp_idx8")
    private Long measrptRsrpIdx8;
    @Column(name = "measrpt_rsrp_idx9")
    private Long measrptRsrpIdx9;
    @Column(name = "ul_pusch_rsrp_idx0")
    private Long ulPuschRsrpIdx0;
    @Column(name = "ul_pusch_rsrp_idx1")
    private Long ulPuschRsrpIdx1;
    @Column(name = "ul_pusch_rsrp_idx2")
    private Long ulPuschRsrpIdx2;
    @Column(name = "ul_pusch_rsrp_idx3")
    private Long ulPuschRsrpIdx3;
    @Column(name = "ul_pusch_rsrp_idx4")
    private Long ulPuschRsrpIdx4;
    @Column(name = "ul_pusch_rsrp_idx5")
    private Long ulPuschRsrpIdx5;
    @Column(name = "ul_pusch_rsrp_idx6")
    private Long ulPuschRsrpIdx6;
    @Column(name = "ul_pusch_rsrp_idx7")
    private Long ulPuschRsrpIdx7;
    @Column(name = "ul_pusch_rsrp_idx8")
    private Long ulPuschRsrpIdx8;
    @Column(name = "ul_pusch_rsrp_idx9")
    private Long ulPuschRsrpIdx9;
    @Column(name = "ul_pusch_rsrp_idx10")
    private Long ulPuschRsrpIdx10;
    @Column(name = "ul_pusch_rsrp_idx11")
    private Long ulPuschRsrpIdx11;
    @Column(name = "measrpt_sinr_idx0")
    private Long measrptSinrIdx0;
    @Column(name = "measrpt_sinr_idx1")
    private Long measrptSinrIdx1;
    @Column(name = "measrpt_sinr_idx2")
    private Long measrptSinrIdx2;
    @Column(name = "measrpt_sinr_idx3")
    private Long measrptSinrIdx3;
    @Column(name = "measrpt_sinr_idx4")
    private Long measrptSinrIdx4;
    @Column(name = "ul_pusch_sinr_idx0")
    private Long ulPuschSinrIdx0;
    @Column(name = "ul_pusch_sinr_idx1")
    private Long ulPuschSinrIdx1;
    @Column(name = "ul_pusch_sinr_idx2")
    private Long ulPuschSinrIdx2;
    @Column(name = "ul_pusch_sinr_idx3")
    private Long ulPuschSinrIdx3;
    @Column(name = "ul_pusch_sinr_idx4")
    private Long ulPuschSinrIdx4;
    @Column(name = "ul_pusch_sinr_idx5")
    private Long ulPuschSinrIdx5;
    @Column(name = "ul_pusch_sinr_idx6")
    private Long ulPuschSinrIdx6;
    @Column(name = "ul_pusch_sinr_idx7")
    private Long ulPuschSinrIdx7;
    @Column(name = "sgnb_rel_total")
    private Long sgnbRelTotal;

    public LocalDateTime getSampleTime() { return sampleTime; }
    public void setSampleTime(LocalDateTime v) { this.sampleTime = v; }
    public String getGnodebName() { return gnodebName; }
    public void setGnodebName(String v) { this.gnodebName = v; }
    public String getCellName() { return cellName; }
    public void setCellName(String v) { this.cellName = v; }
    public Integer getNrCellId() { return nrCellId; }
    public void setNrCellId(Integer v) { this.nrCellId = v; }
    public String getGnodebFunctionName() { return gnodebFunctionName; }
    public void setGnodebFunctionName(String v) { this.gnodebFunctionName = v; }
    public String getIntegrity() { return integrity; }
    public void setIntegrity(String v) { this.integrity = v; }
    public Long getMenbScgfail() { return menbScgfail; }
    public void setMenbScgfail(Long v) { this.menbScgfail = v; }
    public Long getMenbScgfailRaproblem() { return menbScgfailRaproblem; }
    public void setMenbScgfailRaproblem(Long v) { this.menbScgfailRaproblem = v; }
    public Long getMenbScgfailRlcmaxnumretx() { return menbScgfailRlcmaxnumretx; }
    public void setMenbScgfailRlcmaxnumretx(Long v) { this.menbScgfailRlcmaxnumretx = v; }
    public Long getMenbScgfailRecfgfail() { return menbScgfailRecfgfail; }
    public void setMenbScgfailRecfgfail(Long v) { this.menbScgfailRecfgfail = v; }
    public Long getMenbScgfailSyncrecfgfail() { return menbScgfailSyncrecfgfail; }
    public void setMenbScgfailSyncrecfgfail(Long v) { this.menbScgfailSyncrecfgfail = v; }
    public Long getMenbScgfailT310expiry() { return menbScgfailT310expiry; }
    public void setMenbScgfailT310expiry(Long v) { this.menbScgfailT310expiry = v; }
    public Long getSgnbAbnrel() { return sgnbAbnrel; }
    public void setSgnbAbnrel(Long v) { this.sgnbAbnrel = v; }
    public Long getSgnbAbnrelNoreply() { return sgnbAbnrelNoreply; }
    public void setSgnbAbnrelNoreply(Long v) { this.sgnbAbnrelNoreply = v; }
    public Long getSgnbAbnrelRadio() { return sgnbAbnrelRadio; }
    public void setSgnbAbnrelRadio(Long v) { this.sgnbAbnrelRadio = v; }
    public Long getSgnbAbnrelRadioUelost() { return sgnbAbnrelRadioUelost; }
    public void setSgnbAbnrelRadioUelost(Long v) { this.sgnbAbnrelRadioUelost = v; }
    public Long getSgnbAbnrelRadioUlsyncfail() { return sgnbAbnrelRadioUlsyncfail; }
    public void setSgnbAbnrelRadioUlsyncfail(Long v) { this.sgnbAbnrelRadioUlsyncfail = v; }
    public Long getSgnbAbnrelRadioSul() { return sgnbAbnrelRadioSul; }
    public void setSgnbAbnrelRadioSul(Long v) { this.sgnbAbnrelRadioSul = v; }
    public Long getSgnbAbnrelRnlPreempt() { return sgnbAbnrelRnlPreempt; }
    public void setSgnbAbnrelRnlPreempt(Long v) { this.sgnbAbnrelRnlPreempt = v; }
    public Long getSgnbAbnrelTrans() { return sgnbAbnrelTrans; }
    public void setSgnbAbnrelTrans(Long v) { this.sgnbAbnrelTrans = v; }
    public Long getRaTaUeIdx0() { return raTaUeIdx0; }
    public void setRaTaUeIdx0(Long v) { this.raTaUeIdx0 = v; }
    public Long getRaTaUeIdx1() { return raTaUeIdx1; }
    public void setRaTaUeIdx1(Long v) { this.raTaUeIdx1 = v; }
    public Long getRaTaUeIdx2() { return raTaUeIdx2; }
    public void setRaTaUeIdx2(Long v) { this.raTaUeIdx2 = v; }
    public Long getRaTaUeIdx3() { return raTaUeIdx3; }
    public void setRaTaUeIdx3(Long v) { this.raTaUeIdx3 = v; }
    public Long getRaTaUeIdx4() { return raTaUeIdx4; }
    public void setRaTaUeIdx4(Long v) { this.raTaUeIdx4 = v; }
    public Long getRaTaUeIdx5() { return raTaUeIdx5; }
    public void setRaTaUeIdx5(Long v) { this.raTaUeIdx5 = v; }
    public Long getRaTaUeIdx6() { return raTaUeIdx6; }
    public void setRaTaUeIdx6(Long v) { this.raTaUeIdx6 = v; }
    public Long getRaTaUeIdx7() { return raTaUeIdx7; }
    public void setRaTaUeIdx7(Long v) { this.raTaUeIdx7 = v; }
    public Long getRaTaUeIdx8() { return raTaUeIdx8; }
    public void setRaTaUeIdx8(Long v) { this.raTaUeIdx8 = v; }
    public Long getRaTaUeIdx9() { return raTaUeIdx9; }
    public void setRaTaUeIdx9(Long v) { this.raTaUeIdx9 = v; }
    public Long getRaTaUeIdx10() { return raTaUeIdx10; }
    public void setRaTaUeIdx10(Long v) { this.raTaUeIdx10 = v; }
    public Long getRaTaUeIdx11() { return raTaUeIdx11; }
    public void setRaTaUeIdx11(Long v) { this.raTaUeIdx11 = v; }
    public Long getRaTaUeIdx12() { return raTaUeIdx12; }
    public void setRaTaUeIdx12(Long v) { this.raTaUeIdx12 = v; }
    public Long getRaTaUeIdx13() { return raTaUeIdx13; }
    public void setRaTaUeIdx13(Long v) { this.raTaUeIdx13 = v; }
    public Long getRaTaUeIdx14() { return raTaUeIdx14; }
    public void setRaTaUeIdx14(Long v) { this.raTaUeIdx14 = v; }
    public Long getRaTaUeIdx15() { return raTaUeIdx15; }
    public void setRaTaUeIdx15(Long v) { this.raTaUeIdx15 = v; }
    public Long getMeasrptRsrpIdx0() { return measrptRsrpIdx0; }
    public void setMeasrptRsrpIdx0(Long v) { this.measrptRsrpIdx0 = v; }
    public Long getMeasrptRsrpIdx1() { return measrptRsrpIdx1; }
    public void setMeasrptRsrpIdx1(Long v) { this.measrptRsrpIdx1 = v; }
    public Long getMeasrptRsrpIdx2() { return measrptRsrpIdx2; }
    public void setMeasrptRsrpIdx2(Long v) { this.measrptRsrpIdx2 = v; }
    public Long getMeasrptRsrpIdx3() { return measrptRsrpIdx3; }
    public void setMeasrptRsrpIdx3(Long v) { this.measrptRsrpIdx3 = v; }
    public Long getMeasrptRsrpIdx4() { return measrptRsrpIdx4; }
    public void setMeasrptRsrpIdx4(Long v) { this.measrptRsrpIdx4 = v; }
    public Long getMeasrptRsrpIdx5() { return measrptRsrpIdx5; }
    public void setMeasrptRsrpIdx5(Long v) { this.measrptRsrpIdx5 = v; }
    public Long getMeasrptRsrpIdx6() { return measrptRsrpIdx6; }
    public void setMeasrptRsrpIdx6(Long v) { this.measrptRsrpIdx6 = v; }
    public Long getMeasrptRsrpIdx7() { return measrptRsrpIdx7; }
    public void setMeasrptRsrpIdx7(Long v) { this.measrptRsrpIdx7 = v; }
    public Long getMeasrptRsrpIdx8() { return measrptRsrpIdx8; }
    public void setMeasrptRsrpIdx8(Long v) { this.measrptRsrpIdx8 = v; }
    public Long getMeasrptRsrpIdx9() { return measrptRsrpIdx9; }
    public void setMeasrptRsrpIdx9(Long v) { this.measrptRsrpIdx9 = v; }
    public Long getUlPuschRsrpIdx0() { return ulPuschRsrpIdx0; }
    public void setUlPuschRsrpIdx0(Long v) { this.ulPuschRsrpIdx0 = v; }
    public Long getUlPuschRsrpIdx1() { return ulPuschRsrpIdx1; }
    public void setUlPuschRsrpIdx1(Long v) { this.ulPuschRsrpIdx1 = v; }
    public Long getUlPuschRsrpIdx2() { return ulPuschRsrpIdx2; }
    public void setUlPuschRsrpIdx2(Long v) { this.ulPuschRsrpIdx2 = v; }
    public Long getUlPuschRsrpIdx3() { return ulPuschRsrpIdx3; }
    public void setUlPuschRsrpIdx3(Long v) { this.ulPuschRsrpIdx3 = v; }
    public Long getUlPuschRsrpIdx4() { return ulPuschRsrpIdx4; }
    public void setUlPuschRsrpIdx4(Long v) { this.ulPuschRsrpIdx4 = v; }
    public Long getUlPuschRsrpIdx5() { return ulPuschRsrpIdx5; }
    public void setUlPuschRsrpIdx5(Long v) { this.ulPuschRsrpIdx5 = v; }
    public Long getUlPuschRsrpIdx6() { return ulPuschRsrpIdx6; }
    public void setUlPuschRsrpIdx6(Long v) { this.ulPuschRsrpIdx6 = v; }
    public Long getUlPuschRsrpIdx7() { return ulPuschRsrpIdx7; }
    public void setUlPuschRsrpIdx7(Long v) { this.ulPuschRsrpIdx7 = v; }
    public Long getUlPuschRsrpIdx8() { return ulPuschRsrpIdx8; }
    public void setUlPuschRsrpIdx8(Long v) { this.ulPuschRsrpIdx8 = v; }
    public Long getUlPuschRsrpIdx9() { return ulPuschRsrpIdx9; }
    public void setUlPuschRsrpIdx9(Long v) { this.ulPuschRsrpIdx9 = v; }
    public Long getUlPuschRsrpIdx10() { return ulPuschRsrpIdx10; }
    public void setUlPuschRsrpIdx10(Long v) { this.ulPuschRsrpIdx10 = v; }
    public Long getUlPuschRsrpIdx11() { return ulPuschRsrpIdx11; }
    public void setUlPuschRsrpIdx11(Long v) { this.ulPuschRsrpIdx11 = v; }
    public Long getMeasrptSinrIdx0() { return measrptSinrIdx0; }
    public void setMeasrptSinrIdx0(Long v) { this.measrptSinrIdx0 = v; }
    public Long getMeasrptSinrIdx1() { return measrptSinrIdx1; }
    public void setMeasrptSinrIdx1(Long v) { this.measrptSinrIdx1 = v; }
    public Long getMeasrptSinrIdx2() { return measrptSinrIdx2; }
    public void setMeasrptSinrIdx2(Long v) { this.measrptSinrIdx2 = v; }
    public Long getMeasrptSinrIdx3() { return measrptSinrIdx3; }
    public void setMeasrptSinrIdx3(Long v) { this.measrptSinrIdx3 = v; }
    public Long getMeasrptSinrIdx4() { return measrptSinrIdx4; }
    public void setMeasrptSinrIdx4(Long v) { this.measrptSinrIdx4 = v; }
    public Long getUlPuschSinrIdx0() { return ulPuschSinrIdx0; }
    public void setUlPuschSinrIdx0(Long v) { this.ulPuschSinrIdx0 = v; }
    public Long getUlPuschSinrIdx1() { return ulPuschSinrIdx1; }
    public void setUlPuschSinrIdx1(Long v) { this.ulPuschSinrIdx1 = v; }
    public Long getUlPuschSinrIdx2() { return ulPuschSinrIdx2; }
    public void setUlPuschSinrIdx2(Long v) { this.ulPuschSinrIdx2 = v; }
    public Long getUlPuschSinrIdx3() { return ulPuschSinrIdx3; }
    public void setUlPuschSinrIdx3(Long v) { this.ulPuschSinrIdx3 = v; }
    public Long getUlPuschSinrIdx4() { return ulPuschSinrIdx4; }
    public void setUlPuschSinrIdx4(Long v) { this.ulPuschSinrIdx4 = v; }
    public Long getUlPuschSinrIdx5() { return ulPuschSinrIdx5; }
    public void setUlPuschSinrIdx5(Long v) { this.ulPuschSinrIdx5 = v; }
    public Long getUlPuschSinrIdx6() { return ulPuschSinrIdx6; }
    public void setUlPuschSinrIdx6(Long v) { this.ulPuschSinrIdx6 = v; }
    public Long getUlPuschSinrIdx7() { return ulPuschSinrIdx7; }
    public void setUlPuschSinrIdx7(Long v) { this.ulPuschSinrIdx7 = v; }
    public Long getSgnbRelTotal() { return sgnbRelTotal; }
    public void setSgnbRelTotal(Long v) { this.sgnbRelTotal = v; }
}
