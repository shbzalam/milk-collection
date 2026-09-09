package com.zenalyst.milkcollection.route.optimizer;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.common.travel.TravelTimeProvider;
import com.zenalyst.milkcollection.route.planning.CollectionPointDemand;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.TankerCapacity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The optimizer is a pure function of its inputs, so these are plain unit tests with a
 * one-minute-per-kilometre travel model that makes every expected time easy to derive by hand.
 *
 * <p>Geometry used throughout: the plant sits at 18.50N and three collection points lie due
 * north of it, 0.05 degrees apart - roughly 5.6, 11.1 and 16.7 km out, so 5, 11 and 16 minutes
 * from the plant respectively.
 */
class GreedyRouteOptimizerTest {

    private static final Coordinates PLANT = new Coordinates(18.50, 73.80);
    private static final Coordinates NEAR = new Coordinates(18.55, 73.80);
    private static final Coordinates MIDDLE = new Coordinates(18.60, 73.80);
    private static final Coordinates FAR = new Coordinates(18.65, 73.80);

    /** One minute per kilometre of straight-line distance: 60 km/h, no winding factor. */
    private final TravelTimeProvider travelTime = (from, to) ->
            Duration.ofMinutes((long) from.haversineDistanceKm(to));

    private final GreedyRouteOptimizer optimizer = new GreedyRouteOptimizer(travelTime);

    @Test
    @DisplayName("seeds the route at the point farthest from the plant so it finishes next to it")
    void seedsWithTheFarthestPoint() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-NEAR", NEAR, 1, "50"),
                        point(2, "CP-MIDDLE", MIDDLE, 1, "50"),
                        point(3, "CP-FAR", FAR, 1, "50")),
                List.of(tanker(1, "TNK-01", "5000")),
                constraints(Duration.ofHours(4)));

        assertThat(plan.routes()).hasSize(1);
        assertThat(plan.routes().get(0).stops())
                .extracting(OptimizedRoute.ProposedStop::collectionPointCode)
                .containsExactly("CP-FAR", "CP-MIDDLE", "CP-NEAR");
        assertThat(plan.unassigned()).isEmpty();
    }

    @Test
    @DisplayName("planned times and holding duration follow the travel and service model")
    void computesScheduleFromTravelAndServiceTime() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-NEAR", NEAR, 1, "50"),
                        point(2, "CP-FAR", FAR, 1, "50")),
                List.of(tanker(1, "TNK-01", "5000")),
                constraints(Duration.ofHours(4)));

        OptimizedRoute.ProposedRoute route = plan.routes().get(0);
        // Depart 05:00, 16 min to CP-FAR, 5 min service (3 base + 1 farmer x 2),
        // 11 min to CP-NEAR, 5 min service, 5 min back to the plant.
        assertThat(route.departureFromPlant()).isEqualTo(LocalTime.of(5, 0));
        assertThat(route.stops().get(0).plannedArrivalTime()).isEqualTo(LocalTime.of(5, 16));
        assertThat(route.stops().get(0).plannedDepartureTime()).isEqualTo(LocalTime.of(5, 21));
        assertThat(route.stops().get(1).plannedArrivalTime()).isEqualTo(LocalTime.of(5, 32));
        assertThat(route.stops().get(1).plannedDepartureTime()).isEqualTo(LocalTime.of(5, 37));
        assertThat(route.plantArrivalTime()).isEqualTo(LocalTime.of(5, 42));
        // Holding time is measured from the first collection, not from departure.
        assertThat(route.milkHoldingDuration()).isEqualTo(Duration.ofMinutes(26));
        assertThat(route.totalRouteDuration()).isEqualTo(Duration.ofMinutes(42));
    }

    @Test
    @DisplayName("service time grows with the number of farmers at a stop")
    void serviceTimeAccountsForEveryFarmerAtAStop() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-TWO-FARMERS", FAR, 2, "120")),
                List.of(tanker(1, "TNK-01", "5000")),
                constraints(Duration.ofHours(4)));

        OptimizedRoute.ProposedStop stop = plan.routes().get(0).stops().get(0);
        // 3 min base + 2 farmers x 2 min = 7 min standing time.
        assertThat(Duration.between(stop.plannedArrivalTime(), stop.plannedDepartureTime()))
                .isEqualTo(Duration.ofMinutes(7));
        assertThat(stop.farmerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("splits collection points across tankers when capacity runs out")
    void splitsAcrossTankersOnCapacity() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-NEAR", NEAR, 1, "60"),
                        point(2, "CP-MIDDLE", MIDDLE, 1, "60"),
                        point(3, "CP-FAR", FAR, 1, "60")),
                List.of(tanker(1, "TNK-01", "100"), tanker(2, "TNK-02", "100")),
                constraints(Duration.ofHours(4)));

        assertThat(plan.routes()).hasSize(2);
        assertThat(plan.routes()).allSatisfy(route ->
                assertThat(route.totalExpectedLitres()).isEqualByComparingTo("60"));
        assertThat(plan.unassigned()).hasSize(1);
        assertThat(plan.unassigned().get(0).reason())
                .isEqualTo("No tanker capacity remained after the other collection points were assigned");
        assertThat(plan.summary().collectionPointsAssigned()).isEqualTo(2);
        assertThat(plan.summary().tankersUsed()).isEqualTo(2);
    }

    @Test
    @DisplayName("a tight holding limit forces the remote point onto its own tanker")
    void respectsHoldingTimeWhenExtendingARoute() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-NEAR", NEAR, 1, "50"),
                        point(2, "CP-MIDDLE", MIDDLE, 1, "50"),
                        point(3, "CP-FAR", FAR, 1, "50")),
                List.of(tanker(1, "TNK-01", "5000"), tanker(2, "TNK-02", "5000")),
                constraints(Duration.ofMinutes(25)));

        assertThat(plan.routes()).hasSize(2);
        assertThat(plan.routes().get(0).stops())
                .extracting(OptimizedRoute.ProposedStop::collectionPointCode)
                .containsExactly("CP-FAR");
        assertThat(plan.routes().get(1).stops())
                .extracting(OptimizedRoute.ProposedStop::collectionPointCode)
                .containsExactly("CP-MIDDLE", "CP-NEAR");
        assertThat(plan.routes()).allSatisfy(route ->
                assertThat(route.milkHoldingDuration()).isLessThanOrEqualTo(Duration.ofMinutes(25)));
    }

    @Test
    @DisplayName("a point that cannot make the plant in time is reported, not silently dropped")
    void reportsPointsThatCannotMeetHoldingTimeAtAll() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-NEAR", NEAR, 1, "50"),
                        point(2, "CP-FAR", FAR, 1, "50")),
                List.of(tanker(1, "TNK-01", "5000")),
                constraints(Duration.ofMinutes(10)));

        assertThat(plan.routes()).hasSize(1);
        assertThat(plan.routes().get(0).stops())
                .extracting(OptimizedRoute.ProposedStop::collectionPointCode)
                .containsExactly("CP-NEAR");
        assertThat(plan.unassigned()).hasSize(1);
        assertThat(plan.unassigned().get(0).collectionPointCode()).isEqualTo("CP-FAR");
        assertThat(plan.unassigned().get(0).reason())
                .startsWith("Even as the only stop, milk would be");
    }

    @Test
    @DisplayName("a point bigger than the biggest tanker is reported as oversized")
    void reportsOversizedCollectionPoints() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-HUGE", NEAR, 40, "6000")),
                List.of(tanker(1, "TNK-01", "5000")),
                constraints(Duration.ofHours(4)));

        assertThat(plan.routes()).isEmpty();
        assertThat(plan.unassigned()).hasSize(1);
        assertThat(plan.unassigned().get(0).reason())
                .isEqualTo("Expected 6000 L exceeds the largest tanker capacity of 5000 L");
    }

    @Test
    @DisplayName("uses the largest tanker first and reports capacity utilisation")
    void prefersLargerTankersAndReportsUtilisation() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-NEAR", NEAR, 1, "400"),
                        point(2, "CP-FAR", FAR, 1, "400")),
                List.of(tanker(1, "TNK-SMALL", "500"), tanker(2, "TNK-BIG", "1000")),
                constraints(Duration.ofHours(4)));

        assertThat(plan.routes()).hasSize(1);
        assertThat(plan.routes().get(0).tankerCode()).isEqualTo("TNK-BIG");
        assertThat(plan.routes().get(0).capacityUtilisationPercent()).isEqualByComparingTo("80.0");
        assertThat(plan.summary().tankersAvailable()).isEqualTo(2);
        assertThat(plan.summary().tankersUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same input always produces the same plan")
    void isDeterministic() {
        List<CollectionPointDemand> points = List.of(
                point(1, "CP-A", NEAR, 1, "50"),
                point(2, "CP-B", MIDDLE, 1, "50"),
                point(3, "CP-C", FAR, 1, "50"));
        List<TankerCapacity> fleet = List.of(tanker(1, "TNK-01", "5000"));

        OptimizedRoute first = optimizer.optimize(points, fleet, constraints(Duration.ofHours(4)));
        OptimizedRoute second = optimizer.optimize(points, fleet, constraints(Duration.ofHours(4)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void handlesNothingToPlan() {
        OptimizedRoute plan = optimizer.optimize(List.of(), List.of(tanker(1, "TNK-01", "5000")),
                constraints(Duration.ofHours(4)));

        assertThat(plan.routes()).isEmpty();
        assertThat(plan.unassigned()).isEmpty();
        assertThat(plan.summary().collectionPointsConsidered()).isZero();
        assertThat(plan.summary().totalExpectedLitres()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("summary totals add up across routes")
    void summarisesTheWholePlan() {
        OptimizedRoute plan = optimizer.optimize(
                List.of(point(1, "CP-NEAR", NEAR, 2, "50"),
                        point(2, "CP-FAR", FAR, 3, "70")),
                List.of(tanker(1, "TNK-01", "5000")),
                constraints(Duration.ofHours(4)));

        assertThat(plan.summary().collectionPointsConsidered()).isEqualTo(2);
        assertThat(plan.summary().collectionPointsAssigned()).isEqualTo(2);
        assertThat(plan.summary().collectionPointsUnassigned()).isZero();
        assertThat(plan.summary().farmersCovered()).isEqualTo(5);
        assertThat(plan.summary().totalExpectedLitres()).isEqualByComparingTo("120");
    }

    // --- fixtures ----------------------------------------------------------------

    private static CollectionPointDemand point(long id, String code, Coordinates location,
                                               long farmers, String litres) {
        return new CollectionPointDemand(id, code, location, farmers, new BigDecimal(litres));
    }

    private static TankerCapacity tanker(long id, String code, String capacity) {
        return new TankerCapacity(id, code, new BigDecimal(capacity));
    }

    private static PlanningConstraints constraints(Duration maxHolding) {
        return new PlanningConstraints(PLANT, Shift.MORNING, LocalTime.of(5, 0), maxHolding,
                Duration.ofMinutes(3), Duration.ofMinutes(2));
    }
}
