package com.zenalyst.milkcollection.route.optimizer;

import com.zenalyst.milkcollection.route.planning.CollectionPointDemand;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.TankerCapacity;

import java.util.List;

/**
 * Proposes how to cover a set of collection points with a fleet of tankers.
 *
 * <p>The result is a <b>proposal</b>: it is returned to the planner and persists nothing.
 * Turning a proposal into a route version is an explicit, separate decision.
 *
 * <p>A full Vehicle Routing Problem solver is intentionally out of scope for this MVP. This
 * interface is the seam that lets one be introduced later without touching operations code.
 */
public interface RouteOptimizer {

    OptimizedRoute optimize(List<CollectionPointDemand> collectionPoints,
                            List<TankerCapacity> tankers,
                            PlanningConstraints constraints);
}
