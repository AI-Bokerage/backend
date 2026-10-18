package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreateReferralProgramRequest;
import com.company.ppmsvc.api.dto.request.UpdateReferralProgramRequest;
import com.company.ppmsvc.api.dto.response.ReferralProgramResponse;
import com.company.ppmsvc.api.mapper.ReferralProgramApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.referral.usecase.ReferralProgramApplicationService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the Referral Program catalog.
 *
 * <p>Base path: {@code /api/v1/ppm/referral-programs}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/referral-programs")
@RequiredArgsConstructor
@Tag(name = "Referral Programs", description = "Referral program catalog — create, update, retrieve, and soft-delete referral programs.")
public class ReferralProgramController {

    private final ReferralProgramApplicationService programService;
    private final ReferralProgramApiMapper           apiMapper;

    @Operation(summary = "Create a new referral program")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Referral program created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "A reward promotion does not exist (PROMOTION_NOT_FOUND)")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ReferralProgramResponse>> create(
            @Valid @RequestBody CreateReferralProgramRequest request) {
        log.debug("→ POST /api/v1/ppm/referral-programs");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var program = programService.createProgram(actorId, request.name(), request.description(),
            request.referrerRewardPromotionId(), request.referredRewardPromotionId(), request.status(),
            request.maxReferralsPerReferrer());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Referral program created.", apiMapper.toResponse(program)));
    }

    @Operation(summary = "Partially update a referral program")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Referral program updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referral program not found (REFERRAL_PROGRAM_NOT_FOUND)")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ReferralProgramResponse>> update(
            @Parameter(description = "Referral program UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdateReferralProgramRequest request) {
        log.debug("→ PATCH /api/v1/ppm/referral-programs id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var program = programService.updateProgram(actorId, id, request.name(), request.description(),
            request.referrerRewardPromotionId(), request.referredRewardPromotionId(), request.status(),
            request.maxReferralsPerReferrer());
        return ResponseEntity.ok(ApiResponse.ok("Referral program updated.", apiMapper.toResponse(program)));
    }

    @Operation(summary = "Get a referral program by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Referral program found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referral program not found (REFERRAL_PROGRAM_NOT_FOUND)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReferralProgramResponse>> getById(
            @Parameter(description = "Referral program UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/referral-programs id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Referral program retrieved.", apiMapper.toResponse(programService.getProgram(id))));
    }

    @Operation(summary = "List referral programs")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Referral program list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReferralProgramResponse>>> list() {
        log.debug("→ GET /api/v1/ppm/referral-programs");
        return ResponseEntity.ok(
            ApiResponse.ok("Referral programs retrieved.", apiMapper.toResponseList(programService.listPrograms())));
    }

    @Operation(summary = "Soft-delete a referral program")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Referral program deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referral program not found or already deleted (REFERRAL_PROGRAM_NOT_FOUND)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Referral program UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/referral-programs id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        programService.deleteProgram(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
