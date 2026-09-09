package com.zenalyst.milkcollection.route.repository;

import com.zenalyst.milkcollection.route.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {

    boolean existsByRouteCode(String routeCode);
}
