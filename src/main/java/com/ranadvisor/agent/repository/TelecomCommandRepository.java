package com.ranadvisor.agent.repository;

import com.ranadvisor.agent.entity.TelecomCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TelecomCommandRepository extends JpaRepository<TelecomCommand, Long> {

    @Query("""
        SELECT c FROM TelecomCommand c
        WHERE LOWER(c.commandCode)  LIKE %:keyword%
           OR LOWER(c.category)     LIKE %:keyword%
           OR LOWER(c.description)  LIKE %:keyword%
           OR LOWER(c.vendor)       LIKE %:keyword%
    """)
    List<TelecomCommand> findByKeyword(@Param("keyword") String keyword);
}
