package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.WebhookEventStatus;
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
public class WebhookEvent {
    private UUID id;
    private PaymentProvider provider;
    private String externalEventId;
    private String eventType;
    private String payload;
    private WebhookEventStatus status;
    private String failureReason;
    private Instant receivedAt;
    private Instant processedAt;
}
