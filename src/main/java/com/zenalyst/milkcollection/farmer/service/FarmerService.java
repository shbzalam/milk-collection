package com.zenalyst.milkcollection.farmer.service;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import com.zenalyst.milkcollection.collectionpoint.service.CollectionPointService;
import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.farmer.dto.CreateFarmerRequest;
import com.zenalyst.milkcollection.farmer.dto.FarmerResponse;
import com.zenalyst.milkcollection.farmer.entity.Farmer;
import com.zenalyst.milkcollection.farmer.repository.FarmerRepository;
import com.zenalyst.milkcollection.farmer.repository.FarmerSpecifications;
import com.zenalyst.milkcollection.village.entity.Village;
import com.zenalyst.milkcollection.village.service.VillageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FarmerService {

    private final FarmerRepository farmerRepository;
    private final VillageService villageService;
    private final CollectionPointService collectionPointService;

    /**
     * A farmer must be attached to an existing, active collection point. Several farmers may
     * share the same collection point - that is the normal case, not an error.
     */
    @Transactional
    public FarmerResponse create(CreateFarmerRequest request) {
        if (farmerRepository.existsByFarmerCode(request.farmerCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_RESOURCE,
                    "Farmer code " + request.farmerCode() + " already exists");
        }
        Village village = villageService.require(request.villageId());
        CollectionPoint collectionPoint = collectionPointService.require(request.collectionPointId());
        if (!collectionPoint.isActive()) {
            throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                    "Collection point " + collectionPoint.getCode() + " is not active");
        }

        Farmer farmer = farmerRepository.save(Farmer.builder()
                .farmerCode(request.farmerCode())
                .name(request.name())
                .phone(request.phone())
                .village(village)
                .collectionPoint(collectionPoint)
                .expectedMorningQuantityLitres(request.expectedMorningQuantityLitres())
                .expectedEveningQuantityLitres(request.expectedEveningQuantityLitres())
                .status(request.statusOrDefault())
                .build());
        log.info("Created farmer {} at collection point {}",
                farmer.getFarmerCode(), collectionPoint.getCode());
        return FarmerResponse.from(farmer);
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmerResponse> list(Long villageId, Long collectionPointId, String phone,
                                             Pageable pageable) {
        return PageResponse.from(
                farmerRepository.findAll(
                        FarmerSpecifications.filter(villageId, collectionPointId, phone), pageable),
                FarmerResponse::from);
    }

    @Transactional(readOnly = true)
    public FarmerResponse get(Long id) {
        return FarmerResponse.from(farmerRepository.findWithAssociationsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Farmer", id)));
    }

    @Transactional(readOnly = true)
    public Farmer require(Long id) {
        return farmerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Farmer", id));
    }
}
