package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreateCampaignRequest;
import com.company.ppmsvc.api.dto.request.UpdateCampaignRequest;
import com.company.ppmsvc.api.dto.response.CampaignResponse;
import com.company.ppmsvc.api.mapper.CampaignApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.campaign.model.CampaignStatus;
import com.company.ppmsvc.campaign.usecase.CampaignApplicationService;
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
 * REST API for the PPM Campaign catalog.
 *
 * <p>All endpoints require {@code ppm.access} permission, enforced globally.
 *
 * <p>Base path: {@code /api/v1/ppm/campaigns}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign Catalog", description = "Campaign catalog — create, update, retrieve, and soft-delete campaigns.")
public class CampaignController {

    private final CampaignApplicationService campaignService;
    private final CampaignApiMapper          apiMapper;

    @Operation(summary = "Create a new campaign")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Campaign created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignResponse>> create(
            @Valid @RequestBody CreateCampaignRequest request) {
        log.debug("→ POST /api/v1/ppm/campaigns");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var campaign = campaignService.createCampaign(actorId, request.name(), request.description(),
            request.validFrom(), request.validUntil(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Campaign created.", apiMapper.toResponse(campaign)));
    }

    @Operation(summary = "Partially update a campaign",
        description = "Updates one or more mutable fields. Null fields are left unchanged.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Campaign updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Campaign not found (CAMPAIGN_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation error")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> update(
            @Parameter(description = "Campaign UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdateCampaignRequest request) {
        log.debug("→ PATCH /api/v1/ppm/campaigns id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var campaign = campaignService.updateCampaign(actorId, id, request.name(), request.description(),
            request.validFrom(), request.validUntil(), request.status());
        return ResponseEntity.ok(ApiResponse.ok("Campaign updated.", apiMapper.toResponse(campaign)));
    }

    @Operation(summary = "Get a campaign by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Campaign found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Campaign not found or has been deleted (CAMPAIGN_NOT_FOUND)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> getById(
            @Parameter(description = "Campaign UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/campaigns id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Campaign retrieved.", apiMapper.toResponse(campaignService.getCampaign(id))));
    }

    @Operation(summary = "List campaigns",
        description = "Returns all non-deleted campaigns. Pass ?status=draft/active/completed/archived to filter.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Campaign list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<CampaignResponse>>> list(
            @Parameter(description = "Filter by status; omit for all campaigns")
            @RequestParam(required = false) CampaignStatus status) {
        log.debug("→ GET /api/v1/ppm/campaigns status={}", status);
        return ResponseEntity.ok(
            ApiResponse.ok("Campaigns retrieved.", apiMapper.toResponseList(campaignService.listCampaigns(status))));
    }

    @Operation(summary = "Soft-delete a campaign",
        description = "Marks the campaign as deleted. Does not cascade to promotions — they keep a dangling campaignId and continue to work.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Campaign deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Campaign not found or already deleted (CAMPAIGN_NOT_FOUND)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Campaign UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/campaigns id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        campaignService.deleteCampaign(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
