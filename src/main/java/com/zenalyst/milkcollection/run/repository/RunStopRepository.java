package com.zenalyst.milkcollection.run.repository;

import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RunStopRepository extends JpaRepository<RunStop, Long> {

    /**
     * Every caller needs the stop's collection point (name, coordinates), so route stop and
     * collection point are fetch-joined in one query rather than lazily per stop.
     */
    @Query("""
            select s from RunStop s
              join fetch s.routeStop rs
              join fetch rs.collectionPoint
             where s.collectionRun.id = :runId
             order by s.sequenceNumber asc
            """)
    List<RunStop> findByRunIdWithCollectionPoint(@Param("runId") Long runId);

    @Query("""
            select s from RunStop s
              join fetch s.collectionRun
              join fetch s.routeStop rs
              join fetch rs.collectionPoint
             where s.id = :stopId
            """)
    Optional<RunStop> findWithDetailById(@Param("stopId") Long stopId);

    long countByCollectionRunIdAndStatusIn(Long runId, Collection<RunStopStatus> statuses);

    @Query("""
            select s from RunStop s
              join fetch s.routeStop rs
              join fetch rs.collectionPoint
             where s.collectionRun.id = :runId
               and rs.collectionPoint.id = :collectionPointId
            """)
    Optional<RunStop> findByRunIdAndCollectionPointId(@Param("runId") Long runId,
                                                      @Param("collectionPointId") Long collectionPointId);
}
