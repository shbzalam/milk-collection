package com.zenalyst.milkcollection.chillingplant.service;

import com.zenalyst.milkcollection.chillingplant.dto.ChillingPlantResponse;
import com.zenalyst.milkcollection.chillingplant.dto.CreateChillingPlantRequest;
import com.zenalyst.milkcollection.chillingplant.entity.ChillingPlant;
import com.zenalyst.milkcollection.chillingplant.repository.ChillingPlantRepository;
import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChillingPlantService {

    private final ChillingPlantRepository chillingPlantRepository;

    @Transactional
    public ChillingPlantResponse create(CreateChillingPlantRequest request) {
        if (chillingPlantRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_RESOURCE,
                    "Chilling plant code " + request.code() + " already exists");
        }
        ChillingPlant plant = chillingPlantRepository.save(ChillingPlant.builder()
                .code(request.code())
                .name(request.name())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .status(request.statusOrDefault())
                .build());
        log.info("Created chilling plant {} ({})", plant.getCode(), plant.getId());
        return ChillingPlantResponse.from(plant);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChillingPlantResponse> list(Pageable pageable) {
        return PageResponse.from(chillingPlantRepository.findAll(pageable), ChillingPlantResponse::from);
    }

    @Transactional(readOnly = true)
    public ChillingPlantResponse get(Long id) {
        return ChillingPlantResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public ChillingPlant require(Long id) {
        return chillingPlantRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("ChillingPlant", id));
    }
}
