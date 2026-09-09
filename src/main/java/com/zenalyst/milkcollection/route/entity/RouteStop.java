package com.zenalyst.milkcollection.route.entity;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * One stop on a route version.
 *
 * <p>A stop points at a {@link CollectionPoint} - a place - and never at a farmer. That is what
 * keeps "this collection point serves two farmers" a single physical stop.
 *
 * <p>{@code plannedArrivalTime}/{@code plannedDepartureTime} are wall-clock times of day and
 * are optional: they record the planner's intent (and are filled in by the optimizer's
 * proposal). The concrete timetable a tanker is held to is projected per run onto
 * {@code run_stop}, so the schedule always agrees with the feasibility check that approved it.
 */
@Entity
@Table(name = "route_stop")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_version_id", nullable = false)
    private RouteVersion routeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_point_id", nullable = false)
    private CollectionPoint collectionPoint;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "planned_arrival_time")
    private LocalTime plannedArrivalTime;

    @Column(name = "planned_departure_time")
    private LocalTime plannedDepartureTime;
}
