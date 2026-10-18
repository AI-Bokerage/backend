package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreateReferralCodeRequest;
import com.company.ppmsvc.api.dto.request.UpdateReferralCodeRequest;
import com.company.ppmsvc.api.dto.response.ReferralCodeResponse;
import com.company.ppmsvc.api.mapper.ReferralCodeApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.usecase.ReferralCodeApplicationService;
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
 * REST API for the Referral Code catalog.
 *
 * <p>Base path: {@code /api/v1/ppm/referral-codes}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/referral-codes")
@RequiredArgsConstructor
@Tag(name = "Referral Codes", description = "Referral code catalog — create, update, retrieve, and soft-delete referral codes.")
public class ReferralCodeController {

    private final ReferralCodeApplicationService codeService;
    private final ReferralCodeApiMapper          apiMapper;

    @Operation(summary = "Create a new referral code",
        description = "The code is normalised to uppercase before uniqueness check and persistence.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Referral code created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referenced program does not exist (REFERRAL_PROGRAM_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate code (REFERRAL_CODE_ALREADY_EXISTS)")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> create(
            @Valid @RequestBody CreateReferralCodeRequest request) {
        log.debug("→ POST /api/v1/ppm/referral-codes");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var code = codeService.createCode(actorId, request.code(), request.referralProgramId(),
            request.referrerCustomerId(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Referral code created.", apiMapper.toResponse(code)));
    }

    @Operation(summary = "Partially update a referral code",
        description = "Only the active/inactive status may change; the code is immutable after creation.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Referral code updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referral code not found (REFERRAL_CODE_NOT_FOUND)")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> update(
            @Parameter(description = "Referral code UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdateReferralCodeRequest request) {
        log.debug("→ PATCH /api/v1/ppm/referral-codes id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var code = codeService.updateCode(actorId, id, request.status());
        return ResponseEntity.ok(ApiResponse.ok("Referral code updated.", apiMapper.toResponse(code)));
    }

    @Operation(summary = "Get a referral code by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Referral code found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referral code not found (REFERRAL_CODE_NOT_FOUND)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> getById(
            @Parameter(description = "Referral code UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/referral-codes id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Referral code retrieved.", apiMapper.toResponse(codeService.getCode(id))));
    }

    @Operation(summary = "Get a referral code by its code string")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Referral code found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active referral code with the given code (REFERRAL_CODE_NOT_FOUND)")
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> getByCode(
            @Parameter(description = "Referral code string", required = true) @PathVariable String code) {
        log.debug("→ GET /api/v1/ppm/referral-codes/code code={}", code);
        ReferralCode referralCode = codeService.getCodeByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_CODE_NOT_FOUND, "No active referral code with code: " + code));
        return ResponseEntity.ok(ApiResponse.ok("Referral code retrieved.", apiMapper.toResponse(referralCode)));
    }

    @Operation(summary = "List referral codes")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Referral code list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReferralCodeResponse>>> list() {
        log.debug("→ GET /api/v1/ppm/referral-codes");
        return ResponseEntity.ok(
            ApiResponse.ok("Referral codes retrieved.", apiMapper.toResponseList(codeService.listCodes())));
    }

    @Operation(summary = "Soft-delete a referral code")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Referral code deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referral code not found or already deleted (REFERRAL_CODE_NOT_FOUND)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Referral code UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/referral-codes id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        codeService.deleteCode(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
