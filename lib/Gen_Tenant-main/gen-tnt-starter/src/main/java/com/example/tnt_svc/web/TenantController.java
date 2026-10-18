// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/TenantController.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.service.TenantService;
import com.example.tnt_svc.web.dto.CreateTenantRequest;
import com.example.tnt_svc.web.dto.ErrorResponse;
import com.example.tnt_svc.web.dto.TenantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Tenants", description = "Tenant lifecycle — create kicks off the provisioning saga; " +
        "suspend/reactivate/cancel/purge are direct state transitions.")
@SecurityRequirement(name = "internalSecret")
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Operation(summary = "Create a tenant and start provisioning",
            description = "Persists the tenant in PROVISIONING status and kicks off the configured step " +
                    "pipeline synchronously; the response may already reflect a completed or failed first " +
                    "sync step. Pass idempotencyKey to make retried creates safe.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Tenant created, provisioning started",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "409", description = "Slug already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(TenantResponse.from(tenant));
    }

    @Operation(summary = "Get a tenant by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant found",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "404", description = "No tenant with this id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.getTenant(id));
    }

    @Operation(summary = "Check whether a slug is taken", description = "404 means free, 200 means taken — used for real-time signup-form availability checks and as the authoritative pre-create check.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slug is taken",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "404", description = "Slug is free",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/by-slug/{slug}")
    public TenantResponse getBySlug(@PathVariable String slug) {
        return TenantResponse.from(tenantService.getTenantBySlug(slug));
    }

    @Operation(summary = "Suspend a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant suspended",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not a legal transition from the current status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/suspend")
    public TenantResponse suspend(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.suspend(id));
    }

    @Operation(summary = "Reactivate a suspended tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant reactivated",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not a legal transition from the current status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/reactivate")
    public TenantResponse reactivate(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.reactivate(id));
    }

    @Operation(summary = "Cancel a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant cancelled",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not a legal transition from the current status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/cancel")
    public TenantResponse cancel(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.cancel(id));
    }

    @Operation(summary = "Purge a cancelled tenant", description = "Terminal state — no transitions out of PURGED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant purged",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not a legal transition from the current status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/purge")
    public TenantResponse purge(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.purge(id));
    }
}
