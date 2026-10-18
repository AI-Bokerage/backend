package com.company.bsmsvc.infrastructure.persistence.specification;

import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.infrastructure.persistence.entity.CreditNoteEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class CreditNoteSpecifications {

    private CreditNoteSpecifications() {}

    public static Specification<CreditNoteEntity> fromFilter(CreditNoteFilter filter) {
        return (root, query, cb) -> {
            if (filter == null) return cb.conjunction();
            var predicates = new ArrayList<Predicate>();
            if (filter.tenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), filter.tenantId()));
            }
            if (filter.invoiceId() != null) {
                predicates.add(cb.equal(root.get("invoiceId"), filter.invoiceId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (StringUtils.hasText(filter.creditNumber())) {
                predicates.add(cb.equal(root.get("creditNumber"), filter.creditNumber()));
            }
            if (filter.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.createdTo()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
