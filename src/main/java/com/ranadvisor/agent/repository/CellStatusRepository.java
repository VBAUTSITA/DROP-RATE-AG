package com.ranadvisor.agent.repository;

import com.ranadvisor.agent.entity.CellStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CellStatusRepository extends JpaRepository<CellStatus, Long> {

    Optional<CellStatus> findByCellId(String cellId);

}
