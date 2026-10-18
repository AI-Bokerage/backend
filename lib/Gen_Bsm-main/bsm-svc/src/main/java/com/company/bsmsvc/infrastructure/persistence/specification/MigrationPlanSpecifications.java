package com.company.bsmsvc.infrastructure.persistence.specification;

import com.company.bsmsvc.domain.model.MigrationPlanFilter;
import com.company.bsmsvc.infrastructure.persistence.entity.MigrationPlanEntity;
import org.springframework.data.jpa.domain.Specification;

public final class MigrationPlanSpecifications {

    private MigrationPlanSpecifications() {
    }

    public static Specification<MigrationPlanEntity> withFilter(MigrationPlanFilter filter) {
        return Specification.allOf(
            hasTenantId(filter.tenantId()),
            hasSubscriptionId(filter.subscriptionId()),
            hasTargetPlanVersionId(filter.targetPlanVersionId()),
            hasStatus(filter),
            createdAtGte(filter),
            createdAtLte(filter)
        );
    }

    private static Specification<MigrationPlanEntity> hasTenantId(java.util.UUID tenantId) {
        return (root, query, criteriaBuilder) ->
            tenantId == null ? null : criteriaBuilder.equal(root.get("tenantId"), tenantId);
    }

    private static Specification<MigrationPlanEntity> hasSubscriptionId(java.util.UUID subscriptionId) {
        return (root, query, criteriaBuilder) ->
            subscriptionId == null ? null : criteriaBuilder.equal(root.get("subscription").get("id"), subscriptionId);
    }

    private static Specification<MigrationPlanEntity> hasTargetPlanVersionId(java.util.UUID targetPlanVersionId) {
        return (root, query, criteriaBuilder) ->
            targetPlanVersionId == null ? null : criteriaBuilder.equal(root.get("targetPlanVersionId"), targetPlanVersionId);
    }

    private static Specification<MigrationPlanEntity> hasStatus(MigrationPlanFilter filter) {
        return (root, query, criteriaBuilder) ->
            filter.status() == null ? null : criteriaBuilder.equal(root.get("status"), filter.status().name());
    }

    private static Specification<MigrationPlanEntity> createdAtGte(MigrationPlanFilter filter) {
        return (root, query, criteriaBuilder) ->
            filter.dateFrom() == null ? null : criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.dateFrom());
    }

    private static Specification<MigrationPlanEntity> createdAtLte(MigrationPlanFilter filter) {
        return (root, query, criteriaBuilder) ->
            filter.dateTo() == null ? null : criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), filter.dateTo());
    }
}
