package com.zenalyst.milkcollection.farmer.repository;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.farmer.entity.Farmer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmerRepository extends JpaRepository<Farmer, Long>,
        JpaSpecificationExecutor<Farmer> {

    boolean existsByFarmerCode(String farmerCode);

    long countByCollectionPointId(Long collectionPointId);

    long countByCollectionPointIdAndStatus(Long collectionPointId, EntityStatus status);

    /**
     * Village and collection point are eagerly loaded because the response embeds both;
     * without the entity graph, listing N farmers would issue 2N extra selects.
     */
    @Override
    @EntityGraph(attributePaths = {"village", "collectionPoint"})
    Page<Farmer> findAll(Specification<Farmer> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"village", "collectionPoint"})
    Optional<Farmer> findWithAssociationsById(Long id);

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
