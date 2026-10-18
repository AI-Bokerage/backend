package com.company.bsmsvc.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionLimitSnapshot {

    private UUID id;
    private UUID subscriptionId;
    private UUID planVersionId;
    private Map<String, Object> limitsSnapshot;
    private Map<String, Object> usageSnapshot;
    private boolean overLimit;
    private Instant createdAt;
}
