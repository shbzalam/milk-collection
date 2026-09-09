package com.zenalyst.milkcollection.farmer.repository;

import com.zenalyst.milkcollection.farmer.entity.Farmer;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Optional filters for the farmer listing. See {@code CollectionRunSpecifications} for why. */
public final class FarmerSpecifications {

    private FarmerSpecifications() {
    }

    public static Specification<Farmer> filter(Long villageId, Long collectionPointId, String phone) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>(3);
            if (villageId != null) {
                predicates.add(criteriaBuilder.equal(root.get("village").get("id"), villageId));
            }
            if (collectionPointId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("collectionPoint").get("id"), collectionPointId));
            }
            if (phone != null && !phone.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("phone"), phone));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
