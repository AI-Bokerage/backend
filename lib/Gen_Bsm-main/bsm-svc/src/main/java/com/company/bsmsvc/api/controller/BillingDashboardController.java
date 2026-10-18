package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.mapper.BillingSummaryApiMapper;
import com.company.bsmsvc.application.service.BillingDashboardService;
import com.company.bsmsvc.api.dto.response.BillingSummary;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/billing")
@Tag(name = "Billing Dashboard", description = "Aggregate billing metrics for a tenant")
public class BillingDashboardController {

    private final BillingDashboardService billingDashboardService;
    private final BillingSummaryApiMapper mapper;

    public BillingDashboardController(BillingDashboardService billingDashboardService,
                                       BillingSummaryApiMapper mapper) {
        this.billingDashboardService = billingDashboardService;
        this.mapper = mapper;
    }

    @RequirePermission("billing.read")
    @GetMapping("/summary")
    @Operation(summary = "Get billing summary for a tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Summary returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing required parameter",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<BillingSummary>> summary(
        @Parameter(description = "Tenant ID", required = true) @RequestParam UUID tenantId,
        @Parameter(description = "Max number of recent invoices/credits to include (max 100)")
            @RequestParam(defaultValue = "10") int recentLimit
    ) {
        BillingSummary s = mapper.toResponse(billingDashboardService.getSummary(tenantId, Math.min(100, recentLimit)));
        return ResponseEntity.ok(ApiResponse.ok("Billing summary fetched successfully", s));
    }
}
