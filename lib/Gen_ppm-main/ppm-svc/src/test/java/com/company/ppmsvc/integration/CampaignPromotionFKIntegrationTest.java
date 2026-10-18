package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreateCampaignRequest;
import com.company.ppmsvc.api.dto.request.CreatePromotionRequest;
import com.company.ppmsvc.api.dto.request.PriceQuoteRequest;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code campaign_id} has no hard FK constraint: creating a promotion
 * with an unknown campaignId is rejected at the application layer (404, not a
 * DB constraint violation), and soft-deleting a campaign leaves its
 * promotions' {@code campaign_id} dangling but fully functional — a quote
 * still resolves correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Campaign-Promotion FK Behaviour (Integration)")
class CampaignPromotionFKIntegrationTest extends AbstractContainerIntegrationTest {

    static final String CAMPAIGNS_BASE  = "/api/v1/ppm/campaigns";
    static final String PROMOTIONS_BASE = "/api/v1/ppm/promotions";
    static final String QUOTE_URL       = "/api/v1/ppm/quotes";

    static final LocalDate VALID_FROM  = LocalDate.now().minusDays(10);
    static final LocalDate VALID_UNTIL = LocalDate.now().plusDays(60);

    @Autowired MockMvc                 mockMvc;
    @Autowired ObjectMapper            objectMapper;
    @Autowired PlanRepositoryPort      planRepository;
    @Autowired PlanPriceRepositoryPort planPriceRepository;
    @Autowired PlanJpaRepository       planJpaRepository;
    @Autowired JdbcTemplate            jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;

    @BeforeEach
    void seed() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-CAMPFK-" + planId.toString().substring(0, 6))
            .slug("it-campfk-" + planId.toString().substring(0, 6))
            .name("Campaign FK Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        planPriceRepository.save(PlanPrice.builder()
            .id(UUID.randomUUID())
            .planId(planId).cycle(BillingCycle.MONTHLY)
            .currency("INR").region("INDIA")
            .amount(new BigDecimal("1000.0000"))
            .taxInclusive(false)
            .effectiveFrom(LocalDate.of(2025, 1, 1))
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_coupons");
        jdbcTemplate.execute("DELETE FROM ppm_promotions");
        jdbcTemplate.execute("DELETE FROM ppm_campaigns");
        jdbcTemplate.execute("DELETE FROM ppm_plan_prices");
        planJpaRepository.deleteAll();
    }

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            DEV_USER, null, null, null,
            CpmsUserType.SUPER_ADMIN, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    @Test
    @DisplayName("create promotion with non-existent campaignId — 404 CAMPAIGN_NOT_FOUND")
    void create_unknownCampaignId_returns404() throws Exception {
        mockMvc.perform(post(PROMOTIONS_BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePromotionRequest(
                    "Bad", null, new FlatDiscount(new BigDecimal("10")),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, UUID.randomUUID(), null, null))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("no hard FK: soft-deleting a campaign leaves campaign_id dangling; quote still works")
    void softDeleteCampaign_promotionStillWorksViaQuotes() throws Exception {
        MvcResult campaignCreated = mockMvc.perform(post(CAMPAIGNS_BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateCampaignRequest("Doomed Campaign", null, VALID_FROM, VALID_UNTIL, null))))
            .andExpect(status().isCreated())
            .andReturn();
        String campaignId = objectMapper.readTree(campaignCreated.getResponse().getContentAsString())
            .path("data").path("id").asText();

        MvcResult promoCreated = mockMvc.perform(post(PROMOTIONS_BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePromotionRequest(
                    "Campaign-linked promo", null, new FlatDiscount(new BigDecimal("40")),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, UUID.fromString(campaignId), null, null))))
            .andExpect(status().isCreated())
            .andReturn();
        String promotionId = objectMapper.readTree(promoCreated.getResponse().getContentAsString())
            .path("data").path("id").asText();

        // Issue a coupon pointing at the promotion directly (no coupon controller needed for the assertion —
        // reuse the existing coupon creation endpoint)
        MvcResult couponCreated = mockMvc.perform(post("/api/v1/ppm/coupons")
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"CAMPFK10\",\"promotionId\":\"" + promotionId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String couponCode = objectMapper.readTree(couponCreated.getResponse().getContentAsString())
            .path("data").path("code").asText();

        // Delete the campaign
        mockMvc.perform(delete(CAMPAIGNS_BASE + "/" + campaignId).with(authentication(buildAuth())))
            .andExpect(status().isNoContent());

        // Confirm no FK constraint fired and the column is genuinely dangling
        String danglingCampaignId = jdbcTemplate.queryForObject(
            "SELECT campaign_id FROM ppm_promotions WHERE id = ?", String.class, UUID.fromString(promotionId));
        assertThat(danglingCampaignId).isEqualTo(campaignId);
        Integer deletedCampaignCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ppm_campaigns WHERE id = ? AND deleted_at IS NOT NULL",
            Integer.class, UUID.fromString(campaignId));
        assertThat(deletedCampaignCount).isEqualTo(1);

        // The promotion still works end-to-end via /quotes despite the dangling reference
        String quoteBody = objectMapper.writeValueAsString(new PriceQuoteRequest(
            planId, "india", "inr", BillingCycle.MONTHLY, couponCode, null));

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("valid"))
            .andExpect(jsonPath("$.data.discountAmount").value(40.0));
    }
}
