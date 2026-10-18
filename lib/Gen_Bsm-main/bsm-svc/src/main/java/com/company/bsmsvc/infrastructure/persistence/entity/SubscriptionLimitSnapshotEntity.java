package com.company.bsmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscription_limit_snapshots")
public class SubscriptionLimitSnapshotEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionEntity subscription;

    @Column(name = "plan_version_id", nullable = false)
    private UUID planVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limits_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> limitsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> usageSnapshot;

    @Column(name = "over_limit", nullable = false)
    private boolean overLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
