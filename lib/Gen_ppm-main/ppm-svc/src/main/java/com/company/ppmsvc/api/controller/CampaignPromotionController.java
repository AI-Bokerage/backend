package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.response.PromotionResponse;
import com.company.ppmsvc.api.mapper.PromotionApiMapper;
import com.company.ppmsvc.campaign.usecase.CampaignApplicationService;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of a campaign's promotions — "show me all promotions in
 * Diwali Sale". Promotions are assigned to a campaign via the Promotion CRUD
 * ({@code campaignId} field on create/update), not via this endpoint — there
 * is deliberately no POST/DELETE here.
 *
 * <p>Base path: {@code /api/v1/ppm/campaigns/{campaignId}/promotions}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/campaigns/{campaignId}/promotions")
@RequiredArgsConstructor
@Tag(name = "Campaign Promotions", description = "Lists the promotions belonging to a campaign.")
public class CampaignPromotionController {

    private final CampaignApplicationService campaignService;
    private final PromotionRepositoryPort    promotionRepository;
    private final PromotionApiMapper         apiMapper;

    @Operation(summary = "List promotions belonging to a campaign")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Promotion list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Campaign not found (CAMPAIGN_NOT_FOUND)")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> list(
            @Parameter(description = "Campaign UUID", required = true) @PathVariable UUID campaignId) {
        log.debug("→ GET /api/v1/ppm/campaigns/{}/promotions", campaignId);
        campaignService.getCampaign(campaignId); // 404s if the campaign doesn't exist
        return ResponseEntity.ok(
            ApiResponse.ok("Campaign promotions retrieved.",
                apiMapper.toResponseList(promotionRepository.findByCampaignId(campaignId))));
    }
}
