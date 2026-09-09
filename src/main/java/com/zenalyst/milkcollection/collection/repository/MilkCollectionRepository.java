package com.zenalyst.milkcollection.collection.repository;

import com.zenalyst.milkcollection.collection.entity.MilkCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MilkCollectionRepository extends JpaRepository<MilkCollection, Long> {

    boolean existsByRunStopIdAndFarmerId(Long runStopId, Long farmerId);

    /**
     * Litres already on board for a run. Always read inside the transaction that holds the
     * pessimistic lock on the run, so two concurrent collections cannot both see the old total.
     */
    @Query("""
            select coalesce(sum(c.quantityLitres), 0)
              from MilkCollection c
             where c.runStop.collectionRun.id = :runId
            """)
    BigDecimal totalCollectedLitres(@Param("runId") Long runId);

    /**
     * When the oldest milk currently in the tanker was loaded. That is the milk the holding
     * limit actually applies to, so it decides whether more may be taken on.
     */
    @Query("""
            select min(c.collectedAt)
              from MilkCollection c
             where c.runStop.collectionRun.id = :runId
            """)
    Instant earliestCollectedAt(@Param("runId") Long runId);

    long countByRunStopId(Long runStopId);

    /** At most one row by construction - the unique constraint on (run_stop_id, farmer_id). */
    @Query("""
            select c from MilkCollection c
              join fetch c.farmer
             where c.runStop.id = :runStopId and c.farmer.id = :farmerId
            """)
    Optional<MilkCollection> findByRunStopIdAndFarmerId(@Param("runStopId") Long runStopId,
                                                        @Param("farmerId") Long farmerId);

    /**
     * Every collection of a run in one query, with the farmer, so a run detail response can
     * group them per stop without an N+1.
     */
    @Query("""
            select c from MilkCollection c
              join fetch c.farmer
             where c.runStop.collectionRun.id = :runId
             order by c.collectedAt asc
            """)
    List<MilkCollection> findByRunIdWithFarmer(@Param("runId") Long runId);

    @Query("""
            select c from MilkCollection c
              join fetch c.farmer
             where c.runStop.id = :runStopId
             order by c.collectedAt asc
            """)
    List<MilkCollection> findByRunStopIdWithFarmer(@Param("runStopId") Long runStopId);
}
