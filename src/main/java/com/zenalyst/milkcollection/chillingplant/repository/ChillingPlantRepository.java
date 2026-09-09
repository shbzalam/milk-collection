package com.zenalyst.milkcollection.chillingplant.repository;

import com.zenalyst.milkcollection.chillingplant.entity.ChillingPlant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChillingPlantRepository extends JpaRepository<ChillingPlant, Long> {

    boolean existsByCode(String code);
}
