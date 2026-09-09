package com.zenalyst.milkcollection.run.repository;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional filters for the run listing.
 *
 * <p>Built as a {@link Specification} rather than a JPQL query full of {@code :param is null}
 * checks: an absent filter then contributes no predicate at all, so the generated SQL stays
 * minimal and PostgreSQL is never asked to infer the type of an untyped null parameter.
 */
public final class CollectionRunSpecifications {

    private CollectionRunSpecifications() {
    }

    public static Specification<CollectionRun> filter(LocalDate runDate, Shift shift,
                                                      RunStatus status, Long tankerId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>(4);
            if (runDate != null) {
                predicates.add(criteriaBuilder.equal(root.get("runDate"), runDate));
            }
            if (shift != null) {
                predicates.add(criteriaBuilder.equal(root.get("shift"), shift));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (tankerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("tanker").get("id"), tankerId));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
