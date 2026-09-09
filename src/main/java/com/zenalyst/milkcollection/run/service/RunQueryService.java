package com.zenalyst.milkcollection.run.service;

import com.zenalyst.milkcollection.collection.dto.MilkCollectionSummary;
import com.zenalyst.milkcollection.collection.dto.RunLoadSummary;
import com.zenalyst.milkcollection.collection.repository.MilkCollectionRepository;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.run.dto.CollectionRunResponse;
import com.zenalyst.milkcollection.run.dto.RunStopResponse;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.repository.CollectionRunRepository;
import com.zenalyst.milkcollection.run.repository.CollectionRunSpecifications;
import com.zenalyst.milkcollection.run.repository.RunStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read side of runs. Kept separate from run creation and execution so that the write services
 * stay small and only carry the dependencies they actually need.
 *
 * <p>A run detail is three queries regardless of size: the run with its references, its stops
 * with their collection points, and all of its collections with their farmers.
 */
@Service
@RequiredArgsConstructor
public class RunQueryService {

    private final CollectionRunRepository collectionRunRepository;
    private final RunStopRepository runStopRepository;
    private final MilkCollectionRepository milkCollectionRepository;

    @Transactional(readOnly = true)
    public CollectionRunResponse getDetail(Long runId) {
        CollectionRun run = require(runId);

        Map<Long, List<MilkCollectionSummary>> collectionsByStop =
                milkCollectionRepository.findByRunIdWithFarmer(runId).stream()
                        .collect(Collectors.groupingBy(
                                collection -> collection.getRunStop().getId(),
                                Collectors.mapping(MilkCollectionSummary::from, Collectors.toList())));

        List<RunStopResponse> stops = runStopRepository.findByRunIdWithCollectionPoint(runId).stream()
                .map(stop -> RunStopResponse.of(stop,
                        collectionsByStop.getOrDefault(stop.getId(), List.of())))
                .toList();

        return CollectionRunResponse.withStops(run,
                RunLoadSummary.of(milkCollectionRepository.totalCollectedLitres(runId),
                        run.getTanker().getCapacityLitres()),
                stops);
    }

    @Transactional(readOnly = true)
    public PageResponse<CollectionRunResponse> list(LocalDate runDate, Shift shift, RunStatus status,
                                                    Long tankerId, Pageable pageable) {
        return PageResponse.from(
                collectionRunRepository.findAll(
                        CollectionRunSpecifications.filter(runDate, shift, status, tankerId), pageable),
                CollectionRunResponse::withoutStops);
    }

    @Transactional(readOnly = true)
    public List<MilkCollectionSummary> listCollections(Long runId) {
        require(runId);
        return milkCollectionRepository.findByRunIdWithFarmer(runId).stream()
                .map(MilkCollectionSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionRun require(Long runId) {
        return collectionRunRepository.findWithDetailById(runId)
                .orElseThrow(() -> ResourceNotFoundException.of("CollectionRun", runId));
    }
}
