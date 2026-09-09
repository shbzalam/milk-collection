package com.zenalyst.milkcollection.tanker.repository;

import com.zenalyst.milkcollection.tanker.entity.Tanker;
import com.zenalyst.milkcollection.tanker.entity.TankerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TankerRepository extends JpaRepository<Tanker, Long> {

    boolean existsByTankerCode(String tankerCode);

    boolean existsByRegistrationNumber(String registrationNumber);

    /** Planning uses the largest tankers first, so ordering is part of the query. */
    List<Tanker> findByStatusOrderByCapacityLitresDesc(TankerStatus status);

    List<Tanker> findByIdIn(List<Long> ids);
}
