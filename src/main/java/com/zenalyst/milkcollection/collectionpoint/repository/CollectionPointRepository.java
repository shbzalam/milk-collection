package com.zenalyst.milkcollection.collectionpoint.repository;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollectionPointRepository extends JpaRepository<CollectionPoint, Long> {

    boolean existsByCode(String code);

    /**
     * Village is fetched eagerly here (and only here) because the response embeds it.
     * Without this, listing N collection points would issue N extra selects.
     */
    @EntityGraph(attributePaths = "village")
    Optional<CollectionPoint> findWithVillageById(Long id);

    @Query("select cp from CollectionPoint cp join fetch cp.village")
    Page<CollectionPoint> findAllWithVillage(Pageable pageable);

    @Query("select cp from CollectionPoint cp join fetch cp.village v where v.id = :villageId")
    Page<CollectionPoint> findByVillageIdWithVillage(@Param("villageId") Long villageId, Pageable pageable);

    @EntityGraph(attributePaths = "village")
    List<CollectionPoint> findByIdIn(List<Long> ids);
}
