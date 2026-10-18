package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreateCampaignRequest;
import com.company.ppmsvc.api.dto.request.CreatePromotionRequest;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the "list a campaign's promotions" flow —
 * {@code GET /api/v1/ppm/campaigns/{id}/promotions}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Campaign Promotions — REST API (Integration)")
class CampaignPromotionIntegrationTest extends AbstractContainerIntegrationTest {

    static final String CAMPAIGNS_BASE  = "/api/v1/ppm/campaigns";
    static final String PROMOTIONS_BASE = "/api/v1/ppm/promotions";

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_coupons");
        jdbcTemplate.execute("DELETE FROM ppm_promotions");
        jdbcTemplate.execute("DELETE FROM ppm_campaigns");
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

    private String createCampaign() throws Exception {
        MvcResult created = mockMvc.perform(post(CAMPAIGNS_BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateCampaignRequest("Diwali Sale", null, VALID_FROM, VALID_UNTIL, null))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private void createPromotion(String name, UUID campaignId) throws Exception {
        mockMvc.perform(post(PROMOTIONS_BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePromotionRequest(
                    name, null, new FlatDiscount(new BigDecimal("10")),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, campaignId, null, null))))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("one promotion in campaign — GET returns it")
    void listCampaignPromotions_singlePromotion() throws Exception {
        String campaignId = createCampaign();
        createPromotion("Diwali 10% off", UUID.fromString(campaignId));

        mockMvc.perform(get(CAMPAIGNS_BASE + "/" + campaignId + "/promotions").with(authentication(buildAuth())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].name").value("Diwali 10% off"));
    }

    @Test
    @DisplayName("two promotions in same campaign — GET returns both")
    void listCampaignPromotions_twoPromotions() throws Exception {
        String campaignId = createCampaign();
        createPromotion("Diwali VIP", UUID.fromString(campaignId));
        createPromotion("Diwali New Customer", UUID.fromString(campaignId));

        mockMvc.perform(get(CAMPAIGNS_BASE + "/" + campaignId + "/promotions").with(authentication(buildAuth())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("promotion with null campaignId is NOT in any campaign's list")
    void listCampaignPromotions_excludesUnassignedPromotions() throws Exception {
        String campaignId = createCampaign();
        createPromotion("In Campaign", UUID.fromString(campaignId));
        createPromotion("No Campaign", null);

        mockMvc.perform(get(CAMPAIGNS_BASE + "/" + campaignId + "/promotions").with(authentication(buildAuth())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].name").value("In Campaign"));
    }

    @Test
    @DisplayName("GET for a nonexistent campaign returns 404")
    void listCampaignPromotions_unknownCampaign_returns404() throws Exception {
        mockMvc.perform(get(CAMPAIGNS_BASE + "/" + UUID.randomUUID() + "/promotions").with(authentication(buildAuth())))
            .andExpect(status().isNotFound());
    }
}
