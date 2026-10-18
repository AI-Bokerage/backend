package com.company.ppmsvc.referral.model;

import com.company.ppmsvc.common.AuditableEntity;
import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A referral code — reusable, belongs to a program, attributed to a single
 * referrer. Many referred customers may use the same code. Immutable
 * {@code code} after creation; one code per (program, referrer) pair.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class ReferralCode extends AuditableEntity {

    private final String code;
    private final UUID referralProgramId;
    private final String referrerCustomerId;
    private final ReferralCodeStatus status;

    @Builder
    private ReferralCode(UUID id, Long version,
                         Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                         String code, UUID referralProgramId, String referrerCustomerId,
                         ReferralCodeStatus status) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version            = version;
        this.code               = code;
        this.referralProgramId  = referralProgramId;
        this.referrerCustomerId = referrerCustomerId;
        this.status             = status;
    }
}
