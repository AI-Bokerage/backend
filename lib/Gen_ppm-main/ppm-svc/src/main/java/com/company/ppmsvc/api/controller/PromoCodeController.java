package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreatePromoCodeRequest;
import com.company.ppmsvc.api.dto.request.UpdatePromoCodeRequest;
import com.company.ppmsvc.api.dto.response.PromoCodeResponse;
import com.company.ppmsvc.api.mapper.PromoCodeApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.promocode.usecase.PromoCodeApplicationService;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocode.model.PromoCode;
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
 * REST API for the PPM Promo Code Catalog (PPM-07).
 *
 * <p>Manages the platform-wide catalog of promo codes. All endpoints require
 * {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 *
 * <p>Base path: {@code /api/v1/ppm/promo-codes}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/promo-codes")
@RequiredArgsConstructor
@Tag(name = "Promo Code Catalog", description = "Promo code catalog — create, update, retrieve, and soft-delete promo codes.")
public class PromoCodeController {

    private final PromoCodeApplicationService promoCodeService;
    private final PromoCodeApiMapper          apiMapper;

    // ── POST /api/v1/ppm/promo-codes ─────────────────────────────────────────

    @Operation(summary = "Create a new promo code",
        description = "Creates a new promo code entry. The code is normalised to uppercase before uniqueness check and persistence.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Promo code created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A promo code with the given code already exists (PROMO_CODE_ALREADY_EXISTS)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PromoCodeResponse>> create(
            @Valid @RequestBody CreatePromoCodeRequest request) {
        log.debug("→ POST /api/v1/ppm/promo-codes");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var promoCode = promoCodeService.createPromoCode(actorId, request.code(), request.type(),
            request.value(), request.validFrom(), request.validUntil(), request.usageCap(),
            request.firstTimeOnly(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Promo code created.", apiMapper.toResponse(promoCode)));
    }

    // ── PATCH /api/v1/ppm/promo-codes/{id} ───────────────────────────────────

    @Operation(summary = "Partially update a promo code",
        description = "Updates one or more mutable fields. Null fields are left unchanged. "
            + "The code field is immutable after creation.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promo code updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promo code not found (PROMO_CODE_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation error (e.g. PERCENTAGE value > 100 or inverted date range)")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> update(
            @Parameter(description = "Promo code UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdatePromoCodeRequest request) {
        log.debug("→ PATCH /api/v1/ppm/promo-codes id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var promoCode = promoCodeService.updatePromoCode(actorId, id, request.type(), request.value(),
            request.validFrom(), request.validUntil(), request.usageCap(), request.firstTimeOnly(),
            request.active());
        return ResponseEntity.ok(
            ApiResponse.ok("Promo code updated.", apiMapper.toResponse(promoCode)));
    }

    // ── GET /api/v1/ppm/promo-codes/{id} ─────────────────────────────────────

    @Operation(summary = "Get a promo code by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promo code found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promo code not found or has been deleted (PROMO_CODE_NOT_FOUND)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> getById(
            @Parameter(description = "Promo code UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/promo-codes id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Promo code retrieved.", apiMapper.toResponse(promoCodeService.getPromoCode(id))));
    }

    // ── GET /api/v1/ppm/promo-codes/code/{code} ───────────────────────────────

    @Operation(summary = "Get a promo code by its code string",
        description = "Looks up a promo code by its normalised code string (e.g. \"SUMMER20\"). "
            + "Returns 404 if no active promo code with that code exists.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promo code found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active promo code with the given code (PROMO_CODE_NOT_FOUND)")
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> getByCode(
            @Parameter(description = "Promo code string (e.g. \"SUMMER20\")", required = true)
            @PathVariable String code) {
        log.debug("→ GET /api/v1/ppm/promo-codes/code code={}", code);
        PromoCode promoCode = promoCodeService.getPromoCodeByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PROMO_CODE_NOT_FOUND, "No active promo code with code: " + code));
        return ResponseEntity.ok(
            ApiResponse.ok("Promo code retrieved.", apiMapper.toResponse(promoCode)));
    }

    // ── GET /api/v1/ppm/promo-codes ───────────────────────────────────────────

    @Operation(summary = "List promo codes",
        description = "Returns all non-deleted promo codes ordered by code ascending. "
            + "Pass ?active=true/false to filter by active flag. "
            + "Pass ?type=percentage or ?type=flat to filter by discount type. "
            + "Both filters may be combined.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promo code list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<PromoCodeResponse>>> list(
            @Parameter(description = "Filter by active flag; omit for all promo codes")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filter by discount type (percentage / flat); omit for all promo codes")
            @RequestParam(required = false) DiscountType type) {
        log.debug("→ GET /api/v1/ppm/promo-codes active={} type={}", active, type);
        return ResponseEntity.ok(
            ApiResponse.ok("Promo codes retrieved.",
                apiMapper.toResponseList(promoCodeService.listPromoCodes(active, type))));
    }

    // ── DELETE /api/v1/ppm/promo-codes/{id} ──────────────────────────────────

    @Operation(summary = "Soft-delete a promo code",
        description = "Marks the promo code as deleted. The record is retained in the database but hidden from all catalog queries.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Promo code deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promo code not found or already deleted (PROMO_CODE_NOT_FOUND)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Promo code UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/promo-codes id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        promoCodeService.deletePromoCode(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
