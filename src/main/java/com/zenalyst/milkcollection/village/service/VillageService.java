package com.zenalyst.milkcollection.village.service;

import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.village.dto.CreateVillageRequest;
import com.zenalyst.milkcollection.village.dto.VillageResponse;
import com.zenalyst.milkcollection.village.entity.Village;
import com.zenalyst.milkcollection.village.repository.VillageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VillageService {

    private final VillageRepository villageRepository;

    @Transactional
    public VillageResponse create(CreateVillageRequest request) {
        if (villageRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_RESOURCE,
                    "Village code " + request.code() + " already exists");
        }
        Village village = villageRepository.save(Village.builder()
                .code(request.code())
                .name(request.name())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .status(request.statusOrDefault())
                .build());
        log.info("Created village {} ({})", village.getCode(), village.getId());
        return VillageResponse.from(village);
    }

    @Transactional(readOnly = true)
    public PageResponse<VillageResponse> list(Pageable pageable) {
        return PageResponse.from(villageRepository.findAll(pageable), VillageResponse::from);
    }

    @Transactional(readOnly = true)
    public VillageResponse get(Long id) {
        return VillageResponse.from(require(id));
    }

    /** Shared entity lookup for services in other packages that need a Village reference. */
    @Transactional(readOnly = true)
    public Village require(Long id) {
        return villageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Village", id));
    }
}
