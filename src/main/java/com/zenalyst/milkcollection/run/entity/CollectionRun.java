package com.zenalyst.milkcollection.run.entity;

import com.zenalyst.milkcollection.chillingplant.entity.ChillingPlant;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.route.entity.RouteVersion;
import com.zenalyst.milkcollection.tanker.entity.Tanker;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One execution of a route: this tanker, this plan, this date, this shift.
 *
 * <p>{@code version} carries JPA optimistic locking. It guards <b>state transitions</b>, where
 * the risk is two dispatchers acting on the same run at once and one silently overwriting the
 * other. Tanker-capacity accumulation is a different problem and uses a pessimistic row lock
 * instead - see {@code MilkCollectionService}.
 */
@Entity
@Table(name = "collection_run")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_number", nullable = false, unique = true, length = 32)
    private String runNumber;

    /** The exact plan being driven. Never a Route: routes change, versions do not. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_version_id", nullable = false)
    private RouteVersion routeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tanker_id", nullable = false)
    private Tanker tanker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chilling_plant_id", nullable = false)
    private ChillingPlant chillingPlant;

    /** Local calendar date of the collection, in the configured application timezone. */
    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Shift shift;

    @Column(name = "planned_start_time", nullable = false)
    private Instant plannedStartTime;

    @Column(name = "actual_start_time")
    private Instant actualStartTime;

    @Column(name = "actual_end_time")
    private Instant actualEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status;

    @Version
    @Column(nullable = false)
    private Long version;
}
