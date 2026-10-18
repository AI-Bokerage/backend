package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** JPA entity for the {@code ppm_referral_codes} table. */
@Entity
@Table(name = "ppm_referral_codes")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ReferralCodeEntity extends JpaBaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "referral_program_id", nullable = false)
    private UUID referralProgramId;

    @Column(name = "referrer_customer_id", nullable = false, length = 128)
    private String referrerCustomerId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
