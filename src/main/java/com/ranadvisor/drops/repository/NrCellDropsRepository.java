package com.ranadvisor.drops.repository;

import com.ranadvisor.drops.entity.NrCellDrops;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NrCellDropsRepository extends JpaRepository<NrCellDrops, Long> {

    List<NrCellDrops> findByCellNameOrderBySampleTimeAsc(String cellName);

    /**
     * Same as above, restricted to a window. Derived query, so it renders as JPQL
     * BETWEEN and stays portable across PostgreSQL and Oracle.
     */
    List<NrCellDrops> findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(
            String cellName, LocalDateTime from, LocalDateTime to);

    @Query("SELECT DISTINCT d.cellName FROM NrCellDrops d ORDER BY d.cellName")
    List<String> findDistinctCellNames();

    /** Dataset lower bound — anchors relative periods to the data, not to the system clock. */
    @Query("SELECT MIN(d.sampleTime) FROM NrCellDrops d")
    LocalDateTime findEarliestSampleTime();

    /** Dataset upper bound. */
    @Query("SELECT MAX(d.sampleTime) FROM NrCellDrops d")
    LocalDateTime findLatestSampleTime();
}
