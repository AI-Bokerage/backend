package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.DowngradePreflightRequest;
import com.company.bsmsvc.api.dto.request.ProrationPreviewRequest;
import com.company.bsmsvc.api.dto.request.UpgradeSubscriptionRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.DowngradePreflightResponse;
import com.company.bsmsvc.api.dto.response.ProrationPreviewResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionResponse;
import com.company.bsmsvc.api.mapper.CommercialEngineApiMapper;
import com.company.bsmsvc.api.mapper.SubscriptionApiMapper;
import com.company.bsmsvc.application.service.CommercialEngineService;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm")
@RequiredArgsConstructor
@Tag(name = "Commercial Engine", description = "Subscription upgrade, proration, downgrade, and plan comparison endpoints")
public class CommercialEngineController {

    private final CommercialEngineService commercialEngineService;
    private final SubscriptionApiMapper subscriptionApiMapper;
    private final CommercialEngineApiMapper commercialEngineApiMapper;

    @RequirePermission("billing.write")
    @PostMapping("/subscriptions/{id}/upgrade")
    @Operation(summary = "Upgrade a subscription to a higher-tier plan version")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(
            schema = @Schema(implementation = UpgradeSubscriptionRequest.class),
            examples = @ExampleObject(
                name = "Upgrade Request",
                value = """
                    {
                      "tenantId": "11111111-1111-1111-1111-111111111111",
                      "targetPlanVersionId": "22222222-2222-2222-2222-222222222222",
                      "reason": "Need SSO and higher project limits",
                      "performedBy": "billing-admin@tenant.example"
                    }
                    """
            )
        )
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription upgraded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class),
            examples = @ExampleObject(value = """
                {
                  "success": false,
                  "message": "Validation failed",
                  "data": {
                    "code": "VALIDATION_ERROR",
                    "message": "targetPlanVersionId: must not be null",
                    "path": "/api/v1/bsm/subscriptions/123/upgrade"
                  }
                }
                """)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or plan not found", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class),
            examples = @ExampleObject(value = """
                {
                  "success": false,
                  "message": "Target plan tier must be higher than current tier",
                  "data": {
                    "code": "BUSINESS_RULE_VIOLATION",
                    "message": "Target plan tier must be higher than current tier. current=BASIC, target=FREE",
                    "path": "/api/v1/bsm/subscriptions/123/upgrade"
                  }
                }
                """)
        ))
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> upgradeSubscription(
        @PathVariable UUID id,
        @Valid @RequestBody UpgradeSubscriptionRequest request
    ) {
        SubscriptionResponse response = subscriptionApiMapper.toResponse(
            commercialEngineService.upgradeSubscription(
                id,
                request.tenantId(),
                request.targetPlanVersionId(),
                request.reason(),
                request.performedBy()
            )
        );
        return ResponseEntity.ok(ApiResponse.ok("Subscription upgraded successfully", response));
    }

    @RequirePermission("billing.read")
    @PostMapping("/subscriptions/{id}/proration-preview")
    @Operation(summary = "Generate and persist a proration preview for a plan change")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(
            schema = @Schema(implementation = ProrationPreviewRequest.class),
            examples = @ExampleObject(
                name = "Proration Preview Request",
                value = """
                    {
                      "tenantId": "11111111-1111-1111-1111-111111111111",
                      "targetPlanVersionId": "22222222-2222-2222-2222-222222222222",
                      "prorationMode": "FLAT"
                    }
                    """
            )
        )
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Proration preview generated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or plan not found", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        ))
    })
    public ResponseEntity<ApiResponse<ProrationPreviewResponse>> generateProrationPreview(
        @PathVariable UUID id,
        @Valid @RequestBody ProrationPreviewRequest request
    ) {
        ProrationPreviewResponse response = commercialEngineApiMapper.toResponse(
            commercialEngineService.generateProrationPreview(
                id,
                request.tenantId(),
                request.targetPlanVersionId(),
                request.prorationMode()
            )
        );
        return ResponseEntity.ok(ApiResponse.ok("Proration preview generated successfully", response));
    }

    @RequirePermission("billing.read")
    @PostMapping("/subscriptions/{id}/downgrade/preflight")
    @Operation(summary = "Execute downgrade preflight analysis for a lower-tier plan")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(
            schema = @Schema(implementation = DowngradePreflightRequest.class),
            examples = @ExampleObject(
                name = "Downgrade Preflight Request",
                value = """
                    {
                      "tenantId": "11111111-1111-1111-1111-111111111111",
                      "targetPlanVersionId": "33333333-3333-3333-3333-333333333333"
                    }
                    """
            )
        )
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Downgrade preflight executed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or plan not found", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        ))
    })
    public ResponseEntity<ApiResponse<DowngradePreflightResponse>> executeDowngradePreflight(
        @PathVariable UUID id,
        @Valid @RequestBody DowngradePreflightRequest request
    ) {
        DowngradePreflightResponse response = commercialEngineApiMapper.toResponse(
            commercialEngineService.executeDowngradePreflight(id, request.tenantId(), request.targetPlanVersionId())
        );
        return ResponseEntity.ok(ApiResponse.ok("Downgrade preflight executed successfully", response));
    }

}
