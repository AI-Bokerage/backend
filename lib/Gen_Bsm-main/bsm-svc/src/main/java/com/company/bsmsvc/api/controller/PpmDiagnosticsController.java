package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.dto.response.PpmDiagnosticsResponse;
import com.company.bsmsvc.application.service.PpmSnapshotIntegrityService;
import com.company.bsmsvc.domain.model.PpmSnapshotDiagnostics;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/bsm/subscriptions")
@RequiredArgsConstructor
@Tag(name = "PPM Diagnostics", description = "Operational diagnostics for PPM snapshot integrity")
public class PpmDiagnosticsController {

    private final PpmSnapshotIntegrityService integrityService;

    @RequirePermission("billing.read")
    @GetMapping("/{id}/ppm-diagnostics")
    @Operation(
        summary = "PPM snapshot diagnostics",
        description = "Returns the integrity status of the subscription's PPM snapshot. "
            + "HEALTHY and NOT_PPM_BACKED are normal states. All other statuses warrant investigation.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
        description = "Diagnostics retrieved successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
        description = "Subscription not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ApiResponse<PpmDiagnosticsResponse>> getDiagnostics(
        @PathVariable UUID id
    ) {
        log.debug("→ GET /api/v1/bsm/subscriptions/{}/ppm-diagnostics", id);
        PpmSnapshotDiagnostics diagnostics = integrityService.diagnose(id);
        PpmDiagnosticsResponse response = PpmDiagnosticsResponse.from(diagnostics);
        return ResponseEntity.ok(ApiResponse.ok("PPM diagnostics retrieved successfully", response));
    }
}
