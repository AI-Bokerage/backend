package com.company.bsmsvc.infrastructure.persistence.entity;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.RefundStatus;
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
@Entity @Table(name = "refund_requests")
public class RefundRequestEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "requested_amount_minor", nullable = false) private long requestedAmountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 50) private PaymentProvider provider;

    @Column(name = "provider_refund_id", length = 255) private String providerRefundId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50) private RefundStatus status;

    @Column(name = "credit_note_id") private UUID creditNoteId;
    @Column(name = "failure_reason", columnDefinition = "TEXT") private String failureReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Version @Column(name = "version", nullable = false) private Long version;
}
