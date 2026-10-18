package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.ProrationMode;
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
public class ProrationPreview {

    private UUID id;
    private UUID subscriptionId;
    private UUID fromPlanVersionId;
    private UUID toPlanVersionId;
    private ProrationMode prorationMode;
    private long currentPlanCreditMinor;
    private long targetPlanChargeMinor;
    private long proratedAmountMinor;
    private String currency;
    private Map<String, Object> breakdown;
    private Instant expiresAt;
    private Instant createdAt;
}
