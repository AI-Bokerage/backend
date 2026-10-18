package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** JPA entity for the {@code ppm_referral_events} table. */
@Entity
@Table(name = "ppm_referral_events")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ReferralEventEntity extends JpaBaseEntity {

    @Column(name = "referral_code_id", nullable = false)
    private UUID referralCodeId;

    @Column(name = "referred_customer_id", nullable = false, length = 128)
    private String referredCustomerId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "reward_granted_at")
    private Instant rewardGrantedAt;
}
