package com.company.bsmsvc.infrastructure.persistence.specification;

import com.company.bsmsvc.domain.model.ProrationPreviewFilter;
import com.company.bsmsvc.infrastructure.persistence.entity.ProrationPreviewEntity;
import org.springframework.data.jpa.domain.Specification;

public final class ProrationPreviewSpecifications {

    private ProrationPreviewSpecifications() {
    }

    public static Specification<ProrationPreviewEntity> withFilter(ProrationPreviewFilter filter) {
        return Specification.allOf(
            hasSubscriptionId(filter.subscriptionId()),
            createdAtGte(filter),
            createdAtLte(filter)
        );
    }

    private static Specification<ProrationPreviewEntity> hasSubscriptionId(java.util.UUID subscriptionId) {
        return (root, query, criteriaBuilder) ->
            subscriptionId == null ? null : criteriaBuilder.equal(root.get("subscription").get("id"), subscriptionId);
    }

    private static Specification<ProrationPreviewEntity> createdAtGte(ProrationPreviewFilter filter) {
        return (root, query, criteriaBuilder) ->
            filter.dateFrom() == null ? null : criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.dateFrom());
    }

    private static Specification<ProrationPreviewEntity> createdAtLte(ProrationPreviewFilter filter) {
        return (root, query, criteriaBuilder) ->
            filter.dateTo() == null ? null : criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), filter.dateTo());
    }
}
