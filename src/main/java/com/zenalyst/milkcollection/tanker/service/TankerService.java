package com.zenalyst.milkcollection.tanker.service;

import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.tanker.dto.CreateTankerRequest;
import com.zenalyst.milkcollection.tanker.dto.TankerResponse;
import com.zenalyst.milkcollection.tanker.entity.Tanker;
import com.zenalyst.milkcollection.tanker.repository.TankerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TankerService {

    private final TankerRepository tankerRepository;

    @Transactional
    public TankerResponse create(CreateTankerRequest request) {
        if (tankerRepository.existsByTankerCode(request.tankerCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_RESOURCE,
                    "Tanker code " + request.tankerCode() + " already exists");
        }
        if (tankerRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_RESOURCE,
                    "Tanker registration number " + request.registrationNumber() + " already exists");
        }
        Tanker tanker = tankerRepository.save(Tanker.builder()
                .tankerCode(request.tankerCode())
                .registrationNumber(request.registrationNumber())
                .capacityLitres(request.capacityLitres())
                .status(request.statusOrDefault())
                .build());
        log.info("Created tanker {} with capacity {} L", tanker.getTankerCode(), tanker.getCapacityLitres());
        return TankerResponse.from(tanker);
    }

    @Transactional(readOnly = true)
    public PageResponse<TankerResponse> list(Pageable pageable) {
        return PageResponse.from(tankerRepository.findAll(pageable), TankerResponse::from);
    }

    @Transactional(readOnly = true)
    public TankerResponse get(Long id) {
        return TankerResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Tanker require(Long id) {
        return tankerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Tanker", id));
    }
}
