package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreateAddOnRequest;
import com.company.ppmsvc.api.dto.request.UpdateAddOnRequest;
import com.company.ppmsvc.api.dto.response.AddOnPriceResponse;
import com.company.ppmsvc.api.dto.response.AddOnResponse;
import com.company.ppmsvc.api.mapper.AddOnApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.addon.usecase.AddOnApplicationService;
import com.company.ppmsvc.addonprice.model.AddOnPrice;
import com.company.ppmsvc.addonprice.usecase.AddOnPriceApplicationService;
import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.common.BillingCycle;
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
 * REST API for the PPM Add-On Catalog (PPM-11).
 *
 * <p>Manages the platform-wide catalog of purchasable add-on items
 * (e.g. Extra Users, Extra Storage, Premium Support).  Add-ons are
 * NOT tenant-owned and do NOT use RLS.
 *
 * <p>All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 *
 * <p>Base path: {@code /api/v1/ppm/add-ons}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/add-ons")
@RequiredArgsConstructor
@Tag(name = "Add-On Catalog", description = "Platform-wide purchasable add-on catalog — create, update, retrieve, and soft-delete add-ons.")
public class AddOnController {

    private final AddOnApplicationService addOnService;
    private final AddOnPriceApplicationService addOnPriceService;
    private final AddOnApiMapper addOnApiMapper;

    // ── POST /api/v1/ppm/add-ons ─────────────────────────────────────────────

    @Operation(summary = "Create a new add-on",
        description = "Creates a new purchasable add-on. The code must be unique across all active "
            + "(non-deleted) add-ons. The code is stripped and uppercased before uniqueness check.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Add-on created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "An add-on with the given code already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AddOnResponse>> create(
            @Valid @RequestBody CreateAddOnRequest request) {
        log.debug("→ POST /api/v1/ppm/add-ons");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var addOn = addOnService.createAddOn(actorId, request.code(), request.name(),
            request.description(), request.type(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Add-on created.", addOnApiMapper.toResponse(addOn)));
    }

    // ── PATCH /api/v1/ppm/add-ons/{id} ───────────────────────────────────────

    @Operation(summary = "Partially update an add-on",
        description = "Updates one or more fields of an existing add-on. Null fields are left unchanged. "
            + "The code and type are immutable after creation.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Add-on updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Add-on not found or has been deleted")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AddOnResponse>> update(
            @Parameter(description = "Add-on UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdateAddOnRequest request) {
        log.debug("→ PATCH /api/v1/ppm/add-ons id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var addOn = addOnService.updateAddOn(actorId, id, request.name(), request.description(), request.active());
        return ResponseEntity.ok(
            ApiResponse.ok("Add-on updated.", addOnApiMapper.toResponse(addOn)));
    }

    // ── GET /api/v1/ppm/add-ons/{id} ─────────────────────────────────────────

    @Operation(summary = "Get an add-on by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Add-on found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Add-on not found or has been deleted")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddOnResponse>> getById(
            @Parameter(description = "Add-on UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/add-ons id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Add-on retrieved.", addOnApiMapper.toResponse(addOnService.getAddOn(id))));
    }

    // ── GET /api/v1/ppm/add-ons/code/{code} ──────────────────────────────────

    @Operation(summary = "Get an add-on by its code",
        description = "Looks up an add-on by its stable machine-readable code (e.g. \"EXTRA_USERS_10\"). "
            + "Codes are immutable and unique within the active catalog.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Add-on found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No add-on with the given code")
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<AddOnResponse>> getByCode(
            @Parameter(description = "Add-on machine-readable code (e.g. \"EXTRA_USERS_10\")", required = true)
            @PathVariable String code) {
        log.debug("→ GET /api/v1/ppm/add-ons/code code={}", code);
        return ResponseEntity.ok(
            ApiResponse.ok("Add-on retrieved.", addOnApiMapper.toResponse(addOnService.getAddOnByCode(code))));
    }

    // ── GET /api/v1/ppm/add-ons ──────────────────────────────────────────────

    @Operation(summary = "List add-ons",
        description = "Returns all non-deleted add-ons ordered by code ascending. "
            + "Pass ?active=true/false to filter by active status, "
            + "and/or ?type=feature|quota|service to filter by type.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Add-on list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid type parameter value")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddOnResponse>>> list(
            @Parameter(description = "Filter by active flag; omit for all add-ons")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filter by add-on type: feature, quota, or service; omit for all types")
            @RequestParam(required = false) AddOnType type) {
        log.debug("→ GET /api/v1/ppm/add-ons active={} type={}", active, type);
        return ResponseEntity.ok(
            ApiResponse.ok("Add-ons retrieved.", addOnApiMapper.toResponseList(addOnService.listAddOns(active, type))));
    }

    // ── GET /api/v1/ppm/add-ons/{addOnId}/prices/active ──────────────────────

    @Operation(summary = "Resolve active add-on price",
        description = "Returns the most recently effective active price for the given add-on, "
            + "region, currency, and billing cycle. Used by BSM C4 to lock the price "
            + "at add-on purchase time. This endpoint is public — no Authorization header required.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Active price resolved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active price for the given combination")
    })
    @GetMapping("/{addOnId}/prices/active")
    public ResponseEntity<ApiResponse<AddOnPriceResponse>> resolveActivePrice(
            @Parameter(description = "Add-on UUID", required = true) @PathVariable UUID addOnId,
            @Parameter(description = "Region (e.g. INDIA)", required = true) @RequestParam String region,
            @Parameter(description = "ISO 4217 currency code (e.g. INR)", required = true) @RequestParam String currency,
            @Parameter(description = "Billing cycle: MONTHLY or YEARLY", required = true) @RequestParam BillingCycle cycle) {
        log.debug("→ GET /api/v1/ppm/add-ons/{}/prices/active region={} currency={} cycle={}",
            addOnId, region, currency, cycle);
        AddOnPrice price = addOnPriceService.resolveActivePrice(addOnId, region, currency, cycle);
        return ResponseEntity.ok(
            ApiResponse.ok("Active add-on price resolved.", toAddOnPriceResponse(price)));
    }

    // ── DELETE /api/v1/ppm/add-ons/{id} ──────────────────────────────────────

    @Operation(summary = "Soft-delete an add-on",
        description = "Marks the add-on as deleted. The record is retained in the database "
            + "but hidden from all catalog queries. The code becomes available for reuse.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Add-on deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Add-on not found or already deleted")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Add-on UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/add-ons id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        addOnService.deleteAddOn(actorId, id);
        return ResponseEntity.noContent().build();
    }

    private AddOnPriceResponse toAddOnPriceResponse(AddOnPrice price) {
        return new AddOnPriceResponse(
            price.getId(), price.getAddOnId(), price.getCycle().name(), price.getCurrency(),
            price.getRegion(), price.getAmount(), price.isTaxInclusive(), price.getEffectiveFrom());
    }
}
