package com.ranadvisor.pci.repository;

import com.ranadvisor.pci.entity.PciCell;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PciCellRepository extends JpaRepository<PciCell, Long> {

    PciCell findByCellName(String cellName);

    List<PciCell> findByGnodebName(String gnodebName);

    List<PciCell> findByPci(Integer pci);
}
