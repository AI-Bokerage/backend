package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** JPA entity for the {@code ppm_referral_programs} table. All scalar columns — no JSONB. */
@Entity
@Table(name = "ppm_referral_programs")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ReferralProgramEntity extends JpaBaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "referrer_reward_promotion_id", nullable = false)
    private UUID referrerRewardPromotionId;

    @Column(name = "referred_reward_promotion_id", nullable = false)
    private UUID referredRewardPromotionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "max_referrals_per_referrer")
    private Integer maxReferralsPerReferrer;
}
