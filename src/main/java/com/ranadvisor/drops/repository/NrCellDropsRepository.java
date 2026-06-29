package com.ranadvisor.drops.repository;

import com.ranadvisor.drops.entity.NrCellDrops;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NrCellDropsRepository extends JpaRepository<NrCellDrops, Long> {

    List<NrCellDrops> findByCellNameOrderBySampleTimeAsc(String cellName);

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT d.cellName FROM NrCellDrops d ORDER BY d.cellName")
    List<String> findDistinctCellNames();
}
