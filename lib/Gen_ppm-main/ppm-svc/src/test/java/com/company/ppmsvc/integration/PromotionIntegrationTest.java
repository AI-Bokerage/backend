package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreatePromotionRequest;
import com.company.ppmsvc.api.dto.request.UpdatePromotionRequest;
import com.company.ppmsvc.infrastructure.persistence.repository.PromotionJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
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
 * Full-stack integration tests for the Promotion catalog REST API — exercises
 * HTTP -> Security -> Controller -> Service -> JPA -> PostgreSQL, including
 * the JSONB round-trip of both {@code PromotionAction} implementations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Promotion Catalog — REST API (Integration)")
class PromotionIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/promotions";

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Autowired MockMvc                    mockMvc;
    @Autowired ObjectMapper               objectMapper;
    @Autowired PromotionJpaRepository     promotionJpaRepository;
    @Autowired JdbcTemplate               jdbcTemplate;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_coupons");
        jdbcTemplate.execute("DELETE FROM ppm_promotions");
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

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("id").asText();
    }

    @Nested
    @DisplayName("E2E — full lifecycle")
    class HappyPath {

        @Test
        @DisplayName("POST(percentage) -> GET -> LIST -> PATCH(flat) -> DELETE -> GET(404)")
        void fullLifecycle() throws Exception {
            CreatePromotionRequest createReq = new CreatePromotionRequest(
                "Diwali Sale", "20% off, capped", new PercentageDiscount(new BigDecimal("20"), new BigDecimal("200"), null),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Diwali Sale"))
                .andExpect(jsonPath("$.data.action.type").value("percentage"))
                .andExpect(jsonPath("$.data.action.percentage").value(20))
                .andReturn();
            String id = extractId(created);

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action.maxDiscountValue").value(200));

            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

            mockMvc.perform(patch(BASE + "/" + id)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdatePromotionRequest(null, null, new FlatDiscount(new BigDecimal("50")), null, null, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action.type").value("flat"))
                .andExpect(jsonPath("$.data.action.amount").value(50));

            mockMvc.perform(delete(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Status filter")
    class StatusFilter {

        @Test
        @DisplayName("?status=draft returns only draft promotions")
        void list_statusFilter_returnsOnlyMatching() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreatePromotionRequest(
                        "Active One", null, new FlatDiscount(new BigDecimal("10")),
                        VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreatePromotionRequest(
                        "Staged One", null, new FlatDiscount(new BigDecimal("10")),
                        VALID_FROM, VALID_UNTIL, PromotionStatus.DRAFT, null, null, null, null))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?status=draft").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Staged One"));
        }
    }

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("GET unknown UUID returns 404")
        void get_unknownId_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST with percentage > 100 returns 422 VALIDATION_ERROR")
        void create_percentageOver100_returns422() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreatePromotionRequest(
                        "Bad", null, new PercentageDiscount(new BigDecimal("101"), null, null),
                        VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("DELETE unknown UUID returns 404")
        void delete_unknownId_returns404() throws Exception {
            mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request rejected")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        }
    }
}
