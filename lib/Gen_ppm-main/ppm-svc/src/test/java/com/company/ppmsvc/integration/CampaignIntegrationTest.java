package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreateCampaignRequest;
import com.company.ppmsvc.api.dto.request.UpdateCampaignRequest;
import com.company.ppmsvc.campaign.model.CampaignStatus;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Campaign catalog REST API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Campaign Catalog — REST API (Integration)")
class CampaignIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/campaigns";

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
        jdbcTemplate.execute("UPDATE ppm_promotions SET campaign_id = NULL");
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

    @Nested
    @DisplayName("E2E — full lifecycle")
    class HappyPath {

        @Test
        @DisplayName("POST (DRAFT) -> GET -> PATCH (status=ACTIVE) -> GET -> LIST ?status=active -> DELETE -> 404")
        void fullLifecycle() throws Exception {
            CreateCampaignRequest createReq = new CreateCampaignRequest(
                "Diwali Sale", "Festival campaign", VALID_FROM, VALID_UNTIL, null);

            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Diwali Sale"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();
            String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"));

            mockMvc.perform(patch(BASE + "/" + id)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdateCampaignRequest(null, null, null, null, CampaignStatus.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

            mockMvc.perform(get(BASE + "?status=active").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id));

            mockMvc.perform(delete(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Status round-trip")
    class StatusRoundTrip {

        @Test
        @DisplayName("all four statuses round-trip through PATCH")
        void allStatuses_roundTrip() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateCampaignRequest("Status Test", null, VALID_FROM, VALID_UNTIL, CampaignStatus.DRAFT))))
                .andExpect(status().isCreated())
                .andReturn();
            String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

            for (CampaignStatus s : CampaignStatus.values()) {
                mockMvc.perform(patch(BASE + "/" + id)
                        .with(authentication(buildAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCampaignRequest(null, null, null, null, s))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(s.getValue()));
            }
        }
    }

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("POST with validUntil before validFrom returns 422 VALIDATION_ERROR")
        void create_invalidDateRange_returns422() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateCampaignRequest("Bad", null, VALID_UNTIL, VALID_FROM, null))))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("GET unknown UUID returns 404")
        void get_unknownId_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID()).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }
}
