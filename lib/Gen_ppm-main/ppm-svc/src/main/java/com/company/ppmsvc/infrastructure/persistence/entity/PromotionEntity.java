package com.company.ppmsvc.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code ppm_promotions} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}. {@code action} is stored as native JSONB via
 * {@code @JdbcTypeCode(SqlTypes.JSON)}; conversion to/from the sealed
 * {@code PromotionAction} domain type happens in {@link
 * com.company.ppmsvc.infrastructure.persistence.mapper.PromotionPersistenceMapper}.
 * {@code status} is stored as the enum's raw wire value; the mapper converts
 * to/from {@code PromotionStatus}.
 */
@Entity
@Table(name = "ppm_promotions")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PromotionEntity extends JpaBaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_payload", nullable = false)
    private JsonNode action;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_payload", nullable = false)
    private JsonNode conditions;

    @Column(name = "usage_cap_per_user")
    private Integer usageCapPerUser;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    /** Optional M:1 to a campaign. No FK constraint — see V016 migration comment. */
    @Column(name = "campaign_id")
    private UUID campaignId;
}
