package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreatePromotionRequest;
import com.company.ppmsvc.api.dto.request.UpdatePromotionRequest;
import com.company.ppmsvc.api.dto.response.PromotionResponse;
import com.company.ppmsvc.api.mapper.PromotionApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.usecase.PromotionApplicationService;
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
 * REST API for the PPM Promotion catalog.
 *
 * <p>All endpoints require {@code ppm.access} permission, enforced globally.
 *
 * <p>Base path: {@code /api/v1/ppm/promotions}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotion Catalog", description = "Promotion catalog — create, update, retrieve, and soft-delete promotions.")
public class PromotionController {

    private final PromotionApplicationService promotionService;
    private final PromotionApiMapper          apiMapper;

    @Operation(summary = "Create a new promotion")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Promotion created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PromotionResponse>> create(
            @Valid @RequestBody CreatePromotionRequest request) {
        log.debug("→ POST /api/v1/ppm/promotions");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var promotion = promotionService.createPromotion(actorId, request.name(), request.description(),
            request.action(), request.validFrom(), request.validUntil(), request.status(), request.source(),
            request.campaignId(), request.conditions(), request.usageCapPerUser());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Promotion created.", apiMapper.toResponse(promotion)));
    }

    @Operation(summary = "Partially update a promotion",
        description = "Updates one or more mutable fields. Null fields are left unchanged.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promotion updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promotion not found (PROMOTION_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation error")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> update(
            @Parameter(description = "Promotion UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdatePromotionRequest request) {
        log.debug("→ PATCH /api/v1/ppm/promotions id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var promotion = promotionService.updatePromotion(actorId, id, request.name(), request.description(),
            request.action(), request.validFrom(), request.validUntil(), request.status(), request.source(),
            request.campaignId(), request.conditions(), request.usageCapPerUser());
        return ResponseEntity.ok(
            ApiResponse.ok("Promotion updated.", apiMapper.toResponse(promotion)));
    }

    @Operation(summary = "Get a promotion by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promotion found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promotion not found or has been deleted (PROMOTION_NOT_FOUND)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> getById(
            @Parameter(description = "Promotion UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/promotions id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Promotion retrieved.", apiMapper.toResponse(promotionService.getPromotion(id))));
    }

    @Operation(summary = "List promotions",
        description = "Returns all non-deleted promotions. Pass ?status=active/inactive/draft to filter.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promotion list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> list(
            @Parameter(description = "Filter by status; omit for all promotions")
            @RequestParam(required = false) PromotionStatus status) {
        log.debug("→ GET /api/v1/ppm/promotions status={}", status);
        return ResponseEntity.ok(
            ApiResponse.ok("Promotions retrieved.",
                apiMapper.toResponseList(promotionService.listPromotions(status))));
    }

    @Operation(summary = "Soft-delete a promotion",
        description = "Marks the promotion as deleted. The record is retained in the database but hidden from all catalog queries.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Promotion deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promotion not found or already deleted (PROMOTION_NOT_FOUND)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Promotion UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/promotions id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        promotionService.deletePromotion(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
