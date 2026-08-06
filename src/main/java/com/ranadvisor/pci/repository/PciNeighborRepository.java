package com.ranadvisor.pci.repository;

import com.ranadvisor.pci.entity.PciNeighbor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PciNeighborRepository extends JpaRepository<PciNeighbor, Long> {

    List<PciNeighbor> findByCellName(String cellName);

    List<PciNeighbor> findByNeighborCellName(String neighborCellName);
}
