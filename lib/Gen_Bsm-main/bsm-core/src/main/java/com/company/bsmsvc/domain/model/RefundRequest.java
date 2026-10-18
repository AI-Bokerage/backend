package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.RefundStatus;
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
public class RefundRequest {
    private UUID id;
    private UUID tenantId;
    private UUID invoiceId;
    private UUID paymentId;
    private long requestedAmountMinor;
    private PaymentProvider provider;
    /** re_xxx (Stripe), rfnd_xxx (Razorpay). Populated after provider call succeeds. */
    private String providerRefundId;
    private RefundStatus status;
    /** Populated after credit note is created in completeLocalWork / recovery. */
    private UUID creditNoteId;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
