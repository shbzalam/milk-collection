package com.zenalyst.milkcollection.run.repository;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CollectionRunRepository extends JpaRepository<CollectionRun, Long>,
        JpaSpecificationExecutor<CollectionRun> {

    /** Human-facing run numbers come from a database sequence, so they are unique by construction. */
    @Query(value = "select nextval('run_number_seq')", nativeQuery = true)
    long nextRunNumber();

    @EntityGraph(attributePaths = {"routeVersion", "routeVersion.route", "tanker", "chillingPlant"})
    Optional<CollectionRun> findWithDetailById(Long id);

    /**
     * Locks the run row for the duration of the transaction.
     *
     * <p>This is how tanker capacity stays correct under concurrent collections: two requests
     * against the same run are serialised by the database, so each one sees the other's litres
     * before deciding whether there is room. Application-level synchronisation would not
     * survive a second instance of the service.
     *
     * <p>Deliberately no fetch joins: {@code FOR UPDATE} combined with joins would either lock
     * rows in the joined tables or be rejected outright by PostgreSQL. Only the run row needs
     * locking, and the tanker and plant it references are loaded lazily afterwards - two
     * single-row primary key lookups, and neither row is being modified.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CollectionRun r where r.id = :id")
    Optional<CollectionRun> findByIdForUpdate(@Param("id") Long id);

    /** Slot occupancy check; cancelled runs release their slot. */
    boolean existsByTankerIdAndRunDateAndShiftAndStatusNot(Long tankerId, LocalDate runDate,
                                                           Shift shift, RunStatus excludedStatus);

    List<CollectionRun> findByTankerIdAndStatusIn(Long tankerId, Collection<RunStatus> statuses);

    /**
     * The dispatch listing. Optional filters are supplied as a {@link Specification}, and the
     * entity graph keeps the four embedded references to one query instead of N.
     */
    @Override
    @EntityGraph(attributePaths = {"routeVersion", "routeVersion.route", "tanker", "chillingPlant"})
    Page<CollectionRun> findAll(Specification<CollectionRun> specification, Pageable pageable);

    /**
     * The run a farmer's collection point is served by on a given date.
     *
     * <p>Joins from the run's route version down to the route stops, so a farmer never has to
     * know which route they are on. Ordered by planned start so the earliest relevant run wins
     * when both shifts are still open.
     */
    @Query("""
            select r from CollectionRun r
              join fetch r.routeVersion rv
              join fetch rv.route
              join fetch r.tanker
              join fetch r.chillingPlant
             where r.runDate = :runDate
               and r.status in :statuses
               and exists (select 1 from RunStop s
                            where s.collectionRun = r
                              and s.routeStop.collectionPoint.id = :collectionPointId)
             order by r.plannedStartTime asc
            """)
    List<CollectionRun> findRunsServingCollectionPoint(@Param("collectionPointId") Long collectionPointId,
                                                       @Param("runDate") LocalDate runDate,
                                                       @Param("statuses") Collection<RunStatus> statuses);
}
