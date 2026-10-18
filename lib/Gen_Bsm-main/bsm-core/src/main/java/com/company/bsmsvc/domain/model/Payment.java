package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
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
public class Payment {
    private UUID id;
    private UUID tenantId;
    private UUID invoiceId;
    private PaymentProvider paymentProvider;
    private String externalPaymentId;
    private String externalChargeId;
    private PaymentStatus status;
    private long amountMinor;
    private String currency;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public void markSucceeded(String chargeId) {
        this.status = PaymentStatus.SUCCEEDED;
        this.externalChargeId = chargeId;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }
}
