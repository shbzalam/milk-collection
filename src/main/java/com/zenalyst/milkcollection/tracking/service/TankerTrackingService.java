package com.zenalyst.milkcollection.tracking.service;

import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.repository.CollectionRunRepository;
import com.zenalyst.milkcollection.tanker.entity.Tanker;
import com.zenalyst.milkcollection.tanker.service.TankerService;
import com.zenalyst.milkcollection.tracking.dto.TankerLocationRequest;
import com.zenalyst.milkcollection.tracking.dto.TankerLocationResponse;
import com.zenalyst.milkcollection.tracking.entity.TankerLocation;
import com.zenalyst.milkcollection.tracking.repository.TankerLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

/**
 * Position reporting.
 *
 * <p>Positions are append-only, so the history stays intact, and each ping is attached to the
 * run the tanker is currently working - the caller does not have to know the run id. Reads go
 * through a descending composite index, so "latest position" stays a single-row lookup no
 * matter how much history has accumulated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TankerTrackingService {

    private final TankerLocationRepository tankerLocationRepository;
    private final CollectionRunRepository collectionRunRepository;
    private final TankerService tankerService;
    private final Clock clock;

    @Transactional
    public TankerLocationResponse recordLocation(Long tankerId, TankerLocationRequest request) {
        Tanker tanker = tankerService.require(tankerId);
        CollectionRun activeRun = findRunOnTheRoad(tankerId);

        TankerLocation location = tankerLocationRepository.save(TankerLocation.builder()
                .tanker(tanker)
                .collectionRun(activeRun)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .recordedAt(clock.instant())
                .build());

        log.debug("Tanker {} reported position {},{} at {}{}", tanker.getTankerCode(),
                request.latitude(), request.longitude(), location.getRecordedAt(),
                activeRun != null ? " on run " + activeRun.getRunNumber() : " (no active run)");
        return TankerLocationResponse.from(location);
    }

    @Transactional(readOnly = true)
    public TankerLocationResponse latestLocation(Long tankerId) {
        tankerService.require(tankerId);
        return tankerLocationRepository.findFirstByTankerIdOrderByRecordedAtDesc(tankerId)
                .map(TankerLocationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No location has been reported for tanker " + tankerId));
    }

    /**
     * A tanker may only have one run on the road at a time, which the run state machine
     * enforces, so the first match is the only match.
     */
    private CollectionRun findRunOnTheRoad(Long tankerId) {
        List<CollectionRun> onTheRoad = collectionRunRepository.findByTankerIdAndStatusIn(
                tankerId, EnumSet.of(RunStatus.STARTED, RunStatus.IN_PROGRESS));
        return onTheRoad.isEmpty() ? null : onTheRoad.get(0);
    }
}
