package com.zenalyst.milkcollection.run.entity;

import com.zenalyst.milkcollection.route.entity.RouteStop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;

/**
 * One stop of a run: the planned stop plus what actually happened at it.
 *
 * <p>Times here are {@link Instant}s, unlike the {@code LocalTime} on {@link RouteStop}: a route
 * stop is a template ("around 05:40"), a run stop is a real event on a real date.
 */
@Entity
@Table(name = "run_stop")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RunStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_run_id", nullable = false)
    private CollectionRun collectionRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_stop_id", nullable = false)
    private RouteStop routeStop;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "planned_arrival_time", nullable = false)
    private Instant plannedArrivalTime;

    @Column(name = "actual_arrival_time")
    private Instant actualArrivalTime;

    @Column(name = "actual_departure_time")
    private Instant actualDepartureTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStopStatus status;
}
