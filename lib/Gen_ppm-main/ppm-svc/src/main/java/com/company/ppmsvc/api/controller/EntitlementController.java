package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreateEntitlementRequest;
import com.company.ppmsvc.api.dto.request.UpdateEntitlementRequest;
import com.company.ppmsvc.api.dto.response.EntitlementResponse;
import com.company.ppmsvc.api.mapper.EntitlementApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.entitlement.usecase.EntitlementApplicationService;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the PPM Entitlement Catalog.
 *
 * <p>Manages the platform-wide catalog of entitlement definitions — the feature
 * limits and capability flags that can be assigned to subscription plans.
 * All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 *
 * <p>Base path: {@code /api/v1/ppm/entitlements}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/entitlements")
@RequiredArgsConstructor
@Tag(name = "Entitlement Catalog",
    description = "Entitlement catalog — create, update, retrieve, and soft-delete entitlement definitions.")
public class EntitlementController {

    private final EntitlementApplicationService entitlementService;
    private final EntitlementApiMapper          entitlementApiMapper;

    // ── POST /api/v1/ppm/entitlements ─────────────────────────────────────────

    @Operation(summary = "Create a new entitlement",
        description = "Creates a new entitlement catalog entry. "
            + "The code must be unique across all active (non-deleted) entitlements. "
            + "Active defaults to true when omitted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Entitlement created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request contained invalid data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ENTITLEMENT_CODE_ALREADY_EXISTS — an entitlement with this code already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation (missing code, name, or type)")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<EntitlementResponse>> create(
            @Valid @RequestBody CreateEntitlementRequest request) {
        log.debug("→ POST /api/v1/ppm/entitlements");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var entitlement = entitlementService.createEntitlement(actorId, request.code(), request.name(),
            request.description(), request.type(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Entitlement created.",
                entitlementApiMapper.toResponse(entitlement)));
    }

    // ── PATCH /api/v1/ppm/entitlements/{id} ──────────────────────────────────

    @Operation(summary = "Partially update an entitlement",
        description = "Updates one or more mutable fields of an existing entitlement. "
            + "Null fields are left unchanged (PATCH semantics). "
            + "The entitlement code is immutable and cannot be changed via this endpoint.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entitlement updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ENTITLEMENT_NOT_FOUND — entitlement not found or has been deleted")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<EntitlementResponse>> update(
            @Parameter(description = "Entitlement UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdateEntitlementRequest request) {
        log.debug("→ PATCH /api/v1/ppm/entitlements id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var entitlement = entitlementService.updateEntitlement(actorId, id, request.name(),
            request.description(), request.active());
        return ResponseEntity.ok(
            ApiResponse.ok("Entitlement updated.",
                entitlementApiMapper.toResponse(entitlement)));
    }

    // ── GET /api/v1/ppm/entitlements/code/{code} ─────────────────────────────
    // Note: this mapping must appear before /{id} so Spring resolves the literal
    // segment "code" as more specific than the UUID template variable.

    @Operation(summary = "Get an entitlement by its stable code",
        description = "Looks up a non-deleted entitlement by its immutable machine-readable code.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entitlement found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ENTITLEMENT_NOT_FOUND — no active entitlement with the given code")
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<EntitlementResponse>> getByCode(
            @Parameter(description = "Entitlement code (e.g. \"max_users\")", required = true)
            @PathVariable String code) {
        log.debug("→ GET /api/v1/ppm/entitlements/code code={}", code);
        return ResponseEntity.ok(
            ApiResponse.ok("Entitlement retrieved.",
                entitlementApiMapper.toResponse(entitlementService.getEntitlementByCode(code))));
    }

    // ── GET /api/v1/ppm/entitlements/{id} ────────────────────────────────────

    @Operation(summary = "Get an entitlement by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entitlement found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ENTITLEMENT_NOT_FOUND — entitlement not found or has been deleted")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EntitlementResponse>> getById(
            @Parameter(description = "Entitlement UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/entitlements id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Entitlement retrieved.",
                entitlementApiMapper.toResponse(entitlementService.getEntitlement(id))));
    }

    // ── GET /api/v1/ppm/entitlements ─────────────────────────────────────────

    @Operation(summary = "List entitlements",
        description = "Returns all non-deleted entitlements ordered by code ascending. "
            + "Pass ?active=true/false to filter by active flag. "
            + "Pass ?type=boolean/quota/rate_limit to filter by entitlement type. "
            + "Both filters may be combined.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entitlement list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid query parameter value"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<EntitlementResponse>>> list(
            @Parameter(description = "Filter by active flag; omit for all entitlements")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filter by type (boolean / quota / rate_limit); omit for all types")
            @RequestParam(required = false) EntitlementType type) {
        log.debug("→ GET /api/v1/ppm/entitlements active={} type={}", active, type);
        return ResponseEntity.ok(
            ApiResponse.ok("Entitlements retrieved.",
                entitlementApiMapper.toResponseList(entitlementService.listEntitlements(active, type))));
    }

    // ── DELETE /api/v1/ppm/entitlements/{id} ─────────────────────────────────

    @Operation(summary = "Soft-delete an entitlement",
        description = "Marks the entitlement as deleted. "
            + "The record is retained in the database but hidden from all catalog queries. "
            + "The code becomes available for reuse once the entitlement is deleted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Entitlement deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ENTITLEMENT_NOT_FOUND — entitlement not found or already deleted")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Entitlement UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/entitlements id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        entitlementService.deleteEntitlement(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
