package com.zenalyst.milkcollection.tracking.repository;

import com.zenalyst.milkcollection.tracking.entity.TankerLocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TankerLocationRepository extends JpaRepository<TankerLocation, Long> {

    /**
     * Latest reported position of a tanker. Backed by the descending composite index
     * {@code (tanker_id, recorded_at desc)}, so this reads one index entry regardless of how
     * long the history has grown.
     */
    @EntityGraph(attributePaths = {"tanker", "collectionRun"})
    Optional<TankerLocation> findFirstByTankerIdOrderByRecordedAtDesc(Long tankerId);

    /** Latest position reported while working a given run - the origin for an ETA. */
    @EntityGraph(attributePaths = {"tanker", "collectionRun"})
    Optional<TankerLocation> findFirstByCollectionRunIdOrderByRecordedAtDesc(Long collectionRunId);
}
