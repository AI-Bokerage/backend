package com.company.bsmsvc.infrastructure.persistence.entity;

import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "dunning_attempts")
public class DunningAttemptEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Version @Column(name = "version", nullable = false) private Long version;
    @Column(name = "subscription_id", nullable = false) private UUID subscriptionId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50) private DunningAttemptStatus status;

    @Column(name = "failure_code", length = 255) private String failureCode;
    @Column(name = "failure_message", columnDefinition = "TEXT") private String failureMessage;
    @Column(name = "external_payment_id", length = 255) private String externalPaymentId;
    @Column(name = "next_retry_at") private Instant nextRetryAt;
    @Column(name = "attempted_at") private Instant attemptedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
