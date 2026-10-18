package com.company.bsmsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Snapshot of subscription limits and current usage at a point in time")
public record SubscriptionLimitSnapshotResponse(

    @Schema(description = "Snapshot ID")
    UUID id,

    @Schema(description = "Subscription this snapshot belongs to")
    UUID subscriptionId,

    @Schema(description = "Plan version limits captured in this snapshot")
    UUID planVersionId,

    @Schema(description = "Plan limit values at the time of snapshot (key → limit value)")
    Map<String, Object> limitsSnapshot,

    @Schema(description = "Actual usage values at the time of snapshot (key → usage value)")
    Map<String, Object> usageSnapshot,

    @Schema(description = "Whether any resource was over its limit at the time of snapshot")
    boolean overLimit,

    @Schema(description = "When the snapshot was taken")
    Instant createdAt
) {
}
