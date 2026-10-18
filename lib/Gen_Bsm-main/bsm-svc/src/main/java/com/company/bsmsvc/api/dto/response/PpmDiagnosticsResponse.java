package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.PpmSnapshotStatus;
import java.util.UUID;

/**
 * API representation of a subscription's PPM snapshot integrity diagnostics.
 *
 * <p>Nullable fields ({@code grandfathered}, {@code catalogVersionNo},
 * {@code lockedVersionId}, {@code latestVersionId}) are omitted from the JSON
 * response when absent, courtesy of {@code @JsonInclude(NON_NULL)} on {@link ApiResponse}.
 */
public record PpmDiagnosticsResponse(

    UUID subscriptionId,

    boolean ppmBacked,

    boolean snapshotValid,

    PpmSnapshotStatus status,

    /** Null when subscription is not PPM-backed or PPM was unreachable. */
    Boolean grandfathered,

    /** Latest catalog version number from PPM-SVC. Null when PPM was unreachable. */
    Integer catalogVersionNo,

    /** Plan-version UUID locked at checkout time. Null when not PPM-backed. */
    UUID lockedVersionId,

    /** Latest plan-version UUID from PPM-SVC. Null when PPM was unreachable. */
    UUID latestVersionId
) {

    /** {@code snapshotValid} is true for HEALTHY and NOT_PPM_BACKED outcomes. */
    public static PpmDiagnosticsResponse from(
        com.company.bsmsvc.domain.model.PpmSnapshotDiagnostics d
    ) {
        boolean snapshotValid = d.status() == PpmSnapshotStatus.HEALTHY
            || d.status() == PpmSnapshotStatus.NOT_PPM_BACKED;

        return new PpmDiagnosticsResponse(
            d.subscriptionId(),
            d.ppmBacked(),
            snapshotValid,
            d.status(),
            d.grandfathered(),
            d.catalogVersionNo(),
            d.lockedVersionId(),
            d.latestVersionId()
        );
    }
}
