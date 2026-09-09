package com.zenalyst.milkcollection.collectionpoint.service;

import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointResponse;
import com.zenalyst.milkcollection.collectionpoint.dto.CreateCollectionPointRequest;
import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import com.zenalyst.milkcollection.collectionpoint.repository.CollectionPointRepository;
import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.farmer.repository.FarmerRepository;
import com.zenalyst.milkcollection.village.entity.Village;
import com.zenalyst.milkcollection.village.service.VillageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionPointService {

    private final CollectionPointRepository collectionPointRepository;
    private final FarmerRepository farmerRepository;
    private final VillageService villageService;

    @Transactional
    public CollectionPointResponse create(CreateCollectionPointRequest request) {
        if (collectionPointRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_RESOURCE,
                    "Collection point code " + request.code() + " already exists");
        }
        Village village = villageService.require(request.villageId());
        if (!village.isActive()) {
            throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                    "Village " + village.getCode() + " is not active");
        }
        CollectionPoint point = collectionPointRepository.save(CollectionPoint.builder()
                .code(request.code())
                .name(request.name())
                .village(village)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .status(request.statusOrDefault())
                .build());
        log.info("Created collection point {} in village {}", point.getCode(), village.getCode());
        return CollectionPointResponse.from(point);
    }

    @Transactional(readOnly = true)
    public PageResponse<CollectionPointResponse> list(Long villageId, Pageable pageable) {
        Page<CollectionPoint> page = villageId == null
                ? collectionPointRepository.findAllWithVillage(pageable)
                : collectionPointRepository.findByVillageIdWithVillage(villageId, pageable);
        return PageResponse.from(page, CollectionPointResponse::from);
    }

    @Transactional(readOnly = true)
    public CollectionPointResponse get(Long id) {
        CollectionPoint point = collectionPointRepository.findWithVillageById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("CollectionPoint", id));
        return CollectionPointResponse.from(point, farmerRepository.countByCollectionPointId(id));
    }

    @Transactional(readOnly = true)
    public CollectionPoint require(Long id) {
        return collectionPointRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("CollectionPoint", id));
    }
}
