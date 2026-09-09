package com.zenalyst.milkcollection.farmer.repository;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.farmer.entity.Farmer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmerRepository extends JpaRepository<Farmer, Long> {

    boolean existsByFarmerCode(String farmerCode);

    long countByCollectionPointId(Long collectionPointId);

    /**
     * Village and collection point are fetch-joined because the response embeds both;
     * they are to-one associations, so pagination is still applied in SQL.
     */
    @Query(value = """
            select f from Farmer f
              join fetch f.village
              join fetch f.collectionPoint
             where (:villageId is null or f.village.id = :villageId)
               and (:collectionPointId is null or f.collectionPoint.id = :collectionPointId)
               and (:phone is null or f.phone = :phone)
            """,
            countQuery = """
                    select count(f) from Farmer f
                     where (:villageId is null or f.village.id = :villageId)
                       and (:collectionPointId is null or f.collectionPoint.id = :collectionPointId)
                       and (:phone is null or f.phone = :phone)
                    """)
    Page<Farmer> search(@Param("villageId") Long villageId,
                        @Param("collectionPointId") Long collectionPointId,
                        @Param("phone") String phone,
                        Pageable pageable);

    @EntityGraph(attributePaths = {"village", "collectionPoint"})
    Optional<Farmer> findWithAssociationsById(Long id);

    List<Farmer> findByCollectionPointIdAndStatus(Long collectionPointId, EntityStatus status);

    /**
     * Per-collection-point planning aggregate: how many farmers deliver there and how much
     * milk to expect. Computed in one query so the optimizer never loops over farmers.
     */
    @Query("""
            select f.collectionPoint.id            as collectionPointId,
                   count(f)                        as farmerCount,
                   coalesce(sum(f.expectedMorningQuantityLitres), 0) as expectedMorningLitres,
                   coalesce(sum(f.expectedEveningQuantityLitres), 0) as expectedEveningLitres
              from Farmer f
             where f.status = :status
               and f.collectionPoint.id in :collectionPointIds
             group by f.collectionPoint.id
            """)
    List<CollectionPointDemandRow> aggregateDemandByCollectionPoint(
            @Param("collectionPointIds") List<Long> collectionPointIds,
            @Param("status") EntityStatus status);
}
