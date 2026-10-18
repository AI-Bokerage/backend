package com.company.bsmsvc.infrastructure.persistence.specification;

import com.company.bsmsvc.domain.model.LimitSnapshotFilter;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionLimitSnapshotEntity;
import org.springframework.data.jpa.domain.Specification;

public final class SubscriptionLimitSnapshotSpecifications {

    private SubscriptionLimitSnapshotSpecifications() {
    }

    public static Specification<SubscriptionLimitSnapshotEntity> withFilter(LimitSnapshotFilter filter) {
        return Specification.allOf(
            hasSubscriptionId(filter.subscriptionId()),
            hasOverLimit(filter.overLimit())
        );
    }

    private static Specification<SubscriptionLimitSnapshotEntity> hasSubscriptionId(java.util.UUID subscriptionId) {
        return (root, query, criteriaBuilder) ->
            subscriptionId == null ? null : criteriaBuilder.equal(root.get("subscription").get("id"), subscriptionId);
    }

    private static Specification<SubscriptionLimitSnapshotEntity> hasOverLimit(Boolean overLimit) {
        return (root, query, criteriaBuilder) ->
            overLimit == null ? null : criteriaBuilder.equal(root.get("overLimit"), overLimit);
    }
}
