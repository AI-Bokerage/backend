package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreateCouponRequest;
import com.company.ppmsvc.api.dto.request.UpdateCouponRequest;
import com.company.ppmsvc.api.dto.response.CouponResponse;
import com.company.ppmsvc.api.mapper.CouponApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.usecase.CouponApplicationService;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
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
 * REST API for the PPM Coupon catalog.
 *
 * <p>All endpoints require {@code ppm.access} permission, enforced globally.
 *
 * <p>Base path: {@code /api/v1/ppm/coupons}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupon Catalog", description = "Coupon catalog — create, update, retrieve, and soft-delete coupons.")
public class CouponController {

    private final CouponApplicationService couponService;
    private final CouponApiMapper          apiMapper;

    @Operation(summary = "Create a new coupon",
        description = "The code is normalised to uppercase before uniqueness check and persistence.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Coupon created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referenced promotion does not exist (PROMOTION_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A coupon with the given code already exists (COUPON_CODE_ALREADY_EXISTS)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> create(
            @Valid @RequestBody CreateCouponRequest request) {
        log.debug("→ POST /api/v1/ppm/coupons");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var coupon = couponService.createCoupon(actorId, request.code(), request.promotionId(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Coupon created.", apiMapper.toResponse(coupon)));
    }

    @Operation(summary = "Partially update a coupon",
        description = "Updates one or more mutable fields. Null fields are left unchanged. "
            + "The code field is immutable after creation.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Coupon updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Coupon or referenced promotion not found"),
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> update(
            @Parameter(description = "Coupon UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdateCouponRequest request) {
        log.debug("→ PATCH /api/v1/ppm/coupons id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var coupon = couponService.updateCoupon(actorId, id, request.promotionId(), request.active());
        return ResponseEntity.ok(
            ApiResponse.ok("Coupon updated.", apiMapper.toResponse(coupon)));
    }

    @Operation(summary = "Get a coupon by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Coupon found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Coupon not found or has been deleted (COUPON_NOT_FOUND)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> getById(
            @Parameter(description = "Coupon UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/coupons id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Coupon retrieved.", apiMapper.toResponse(couponService.getCoupon(id))));
    }

    @Operation(summary = "Get a coupon by its code string")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Coupon found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active coupon with the given code (COUPON_NOT_FOUND)")
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<CouponResponse>> getByCode(
            @Parameter(description = "Coupon code string", required = true) @PathVariable String code) {
        log.debug("→ GET /api/v1/ppm/coupons/code code={}", code);
        Coupon coupon = couponService.getCouponByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.COUPON_NOT_FOUND, "No active coupon with code: " + code));
        return ResponseEntity.ok(
            ApiResponse.ok("Coupon retrieved.", apiMapper.toResponse(coupon)));
    }

    @Operation(summary = "List coupons",
        description = "Returns all non-deleted coupons. Pass ?active=true/false to filter.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Coupon list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<CouponResponse>>> list(
            @Parameter(description = "Filter by active flag; omit for all coupons")
            @RequestParam(required = false) Boolean active) {
        log.debug("→ GET /api/v1/ppm/coupons active={}", active);
        return ResponseEntity.ok(
            ApiResponse.ok("Coupons retrieved.",
                apiMapper.toResponseList(couponService.listCoupons(active))));
    }

    @Operation(summary = "Soft-delete a coupon",
        description = "Marks the coupon as deleted. The record is retained in the database but hidden from all catalog queries.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Coupon deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Coupon not found or already deleted (COUPON_NOT_FOUND)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Coupon UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/coupons id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        couponService.deleteCoupon(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
