package com.zenalyst.milkcollection.village.repository;

import com.zenalyst.milkcollection.village.entity.Village;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VillageRepository extends JpaRepository<Village, Long> {

    boolean existsByCode(String code);
}
