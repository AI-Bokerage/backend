package com.company.ppmsvc.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the append-only {@code ppm_promotion_redemptions} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity} for convention consistency, even though redemption
 * rows are never updated or deleted. {@code appliedAction} snapshots the
 * action applied at redemption time as native JSONB — the promotion's action
 * may later change, but the ledger must reflect what actually happened.
 */
@Entity
@Table(name = "ppm_promotion_redemptions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PromotionRedemptionEntity extends JpaBaseEntity {

    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_payload", nullable = false)
    private JsonNode appliedAction;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;
}
