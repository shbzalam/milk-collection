package com.zenalyst.milkcollection.route.repository;

import com.zenalyst.milkcollection.route.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    /**
     * Collection point is fetch-joined: every caller (responses, schedule projection, ETA)
     * needs its coordinates, and without the join this is a textbook N+1.
     */
    @Query("""
            select s from RouteStop s
              join fetch s.collectionPoint
             where s.routeVersion.id = :routeVersionId
             order by s.sequenceNumber asc
            """)
    List<RouteStop> findByRouteVersionIdWithCollectionPoint(@Param("routeVersionId") Long routeVersionId);

    List<RouteStop> findByRouteVersionIdOrderBySequenceNumberAsc(Long routeVersionId);

    boolean existsByRouteVersionIdAndCollectionPointId(Long routeVersionId, Long collectionPointId);

    boolean existsByRouteVersionIdAndSequenceNumber(Long routeVersionId, int sequenceNumber);

    long countByRouteVersionId(Long routeVersionId);

    @Query("select coalesce(max(s.sequenceNumber), 0) from RouteStop s where s.routeVersion.id = :routeVersionId")
    int highestSequenceNumber(@Param("routeVersionId") Long routeVersionId);
}
