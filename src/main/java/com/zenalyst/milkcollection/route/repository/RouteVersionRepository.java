package com.zenalyst.milkcollection.route.repository;

import com.zenalyst.milkcollection.route.entity.RouteVersion;
import com.zenalyst.milkcollection.route.entity.RouteVersionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteVersionRepository extends JpaRepository<RouteVersion, Long> {

    @EntityGraph(attributePaths = "route")
    List<RouteVersion> findByRouteIdOrderByVersionNumberDesc(Long routeId);

    @EntityGraph(attributePaths = "route")
    Optional<RouteVersion> findWithRouteById(Long id);

    /** The current plan of record, guaranteed unique by a partial unique index. */
    Optional<RouteVersion> findByRouteIdAndStatus(Long routeId, RouteVersionStatus status);

    @Query("select coalesce(max(v.versionNumber), 0) from RouteVersion v where v.route.id = :routeId")
    int highestVersionNumber(@Param("routeId") Long routeId);
}
