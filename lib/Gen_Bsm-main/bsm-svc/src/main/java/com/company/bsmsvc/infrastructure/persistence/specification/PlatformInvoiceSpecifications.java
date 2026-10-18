package com.company.bsmsvc.infrastructure.persistence.specification;

import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.model.InvoiceFilter;
import com.company.bsmsvc.infrastructure.persistence.entity.PlatformInvoiceEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class PlatformInvoiceSpecifications {

    private PlatformInvoiceSpecifications() {
    }

    public static Specification<PlatformInvoiceEntity> fromFilter(InvoiceFilter filter) {
        return (root, query, criteriaBuilder) -> {
            if (filter == null) {
                return criteriaBuilder.conjunction();
            }
            var predicates = new ArrayList<Predicate>();
            if (filter.tenantId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), filter.tenantId()));
            }
            if (filter.subscriptionId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("subscription").get("id"), filter.subscriptionId()));
            }
            if (StringUtils.hasText(filter.invoiceNumber())) {
                predicates.add(criteriaBuilder.equal(root.get("invoiceNumber"), filter.invoiceNumber()));
            }
            if (filter.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
            }
            return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
