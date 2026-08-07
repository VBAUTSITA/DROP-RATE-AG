package com.ranadvisor.pci.repository;

import com.ranadvisor.pci.entity.PciChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PciChangeRepository extends JpaRepository<PciChange, Long> {

    List<PciChange> findByCellNameOrderByChangedAtAsc(String cellName);
}
