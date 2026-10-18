package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.CreateBillingProfileRequest;
import com.company.bsmsvc.api.dto.request.UpdateCurrencyRequest;
import com.company.bsmsvc.api.dto.request.UpdateProviderRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.dto.response.TenantBillingProfileResponse;
import com.company.bsmsvc.api.mapper.TenantBillingProfileApiMapper;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/billing-profile")
@Tag(name = "Tenant Billing Profile", description = "Manage tenant payment provider configuration")
public class TenantBillingProfileController {

    private final TenantBillingProfileService service;
    private final TenantBillingProfileApiMapper mapper;

    public TenantBillingProfileController(TenantBillingProfileService service,
                                          TenantBillingProfileApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @RequirePermission("billing.write")
    @PostMapping
    @Operation(summary = "Create a billing profile for a tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Profile created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Profile already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<TenantBillingProfileResponse>> createProfile(
        @Valid @RequestBody CreateBillingProfileRequest request
    ) {
        TenantBillingProfile profile = service.createProfile(
            request.tenantId(), request.paymentProvider(), null, request.currency()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Billing profile created successfully", mapper.toResponse(profile)));
    }

    @RequirePermission("billing.read")
    @GetMapping("/{tenantId}")
    @Operation(summary = "Get billing profile for a tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Profile not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<TenantBillingProfileResponse>> getProfile(@PathVariable UUID tenantId) {
        TenantBillingProfile profile = service.getProfile(tenantId);
        return ResponseEntity.ok(ApiResponse.ok("Billing profile fetched successfully", mapper.toResponse(profile)));
    }

    @RequirePermission("billing.admin")
    @PatchMapping("/{tenantId}/currency")
    @Operation(summary = "Update billing currency for a tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Currency updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Profile not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<TenantBillingProfileResponse>> updateCurrency(
        @PathVariable UUID tenantId,
        @Valid @RequestBody UpdateCurrencyRequest request
    ) {
        TenantBillingProfile profile = service.updateCurrency(tenantId, request.currency());
        return ResponseEntity.ok(ApiResponse.ok("Billing currency updated successfully", mapper.toResponse(profile)));
    }

    @RequirePermission("billing.admin")
    @PatchMapping("/{tenantId}/provider")
    @Operation(summary = "Update payment provider for a tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Provider updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Profile not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<TenantBillingProfileResponse>> updateProvider(
        @PathVariable UUID tenantId,
        @Valid @RequestBody UpdateProviderRequest request
    ) {
        TenantBillingProfile profile = service.updateProvider(tenantId, request.paymentProvider());
        return ResponseEntity.ok(ApiResponse.ok("Payment provider updated successfully", mapper.toResponse(profile)));
    }
}
