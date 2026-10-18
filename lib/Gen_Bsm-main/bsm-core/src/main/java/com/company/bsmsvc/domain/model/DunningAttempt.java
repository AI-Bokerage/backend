package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DunningAttempt {
    private UUID id;
    private Long version;
    private UUID subscriptionId;
    private UUID tenantId;
    private UUID invoiceId;
    private int attemptNumber;
    private DunningAttemptStatus status;
    private String failureCode;
    private String failureMessage;
    private String externalPaymentId;
    private Instant nextRetryAt;
    private Instant attemptedAt;
    private Instant createdAt;
}
