package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.ResolvePriceRequest;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the Pricing Resolver Engine (PPM-09).
 *
 * <p>Exercises: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * Plans and prices are seeded via domain ports in each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Pricing Resolver Engine — REST API (Integration)")
class PricingResolverIntegrationTest extends AbstractContainerIntegrationTest {

    static final String RESOLVE_URL = "/api/v1/ppm/prices/resolve";

    @Autowired MockMvc                mockMvc;
    @Autowired ObjectMapper           objectMapper;
    @Autowired PlanRepositoryPort     planRepository;
    @Autowired PlanPriceRepositoryPort planPriceRepository;
    @Autowired PlanJpaRepository      planJpaRepository;
    @Autowired JdbcTemplate           jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;

    @BeforeEach
    void seedPlan() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-PR-" + planId.toString().substring(0, 6))
            .slug("it-pr-" + planId.toString().substring(0, 6))
            .name("Pricing Resolver Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
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

    private PlanPrice seedPrice(LocalDate effectiveFrom, boolean active,
                                 BillingCycle cycle, BigDecimal amount) {
        Instant now = Instant.now();
        return planPriceRepository.save(PlanPrice.builder()
            .id(UUID.randomUUID())
            .planId(planId)
            .cycle(cycle).currency("INR").region("INDIA")
            .amount(amount).taxInclusive(false)
            .effectiveFrom(effectiveFrom).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    private String body(String region, String currency, String cycle) throws Exception {
        return objectMapper.writeValueAsString(
            new ResolvePriceRequest(planId, region, currency, BillingCycle.fromValue(cycle)));
    }

    // ── Case 1: one valid price ───────────────────────────────────────────────

    @Nested
    @DisplayName("Case 1 — One valid price")
    class OneValidPrice {

        @Test
        @DisplayName("C1 — single active price in the past resolves with correct amount")
        void resolve_singleActivePrice_returnsCorrectAmount() throws Exception {
            seedPrice(LocalDate.now().minusDays(30), true, BillingCycle.MONTHLY, new BigDecimal("999.00"));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("INDIA", "INR", "monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(999.00))
                .andExpect(jsonPath("$.data.cycle").value("monthly"))
                .andExpect(jsonPath("$.data.region").value("INDIA"))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.planId").value(planId.toString()));
        }
    }

    // ── Case 2: multiple effective dates ─────────────────────────────────────

    @Nested
    @DisplayName("Case 2 — Multiple effective dates: latest applicable wins")
    class MultipleEffectiveDates {

        @Test
        @DisplayName("C2 — latest past-effective price is selected over an earlier one")
        void resolve_multiplePastPrices_latestWins() throws Exception {
            seedPrice(LocalDate.now().minusDays(180), true, BillingCycle.MONTHLY, new BigDecimal("799.00"));
            seedPrice(LocalDate.now().minusDays(30),  true, BillingCycle.MONTHLY, new BigDecimal("999.00"));
            seedPrice(LocalDate.now().minusDays(10),  true, BillingCycle.MONTHLY, new BigDecimal("1099.00"));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("INDIA", "INR", "monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(1099.00));
        }
    }

    // ── Case 3: future price ignored ─────────────────────────────────────────

    @Nested
    @DisplayName("Case 3 — Future price ignored")
    class FuturePriceIgnored {

        @Test
        @DisplayName("C3 — future price is ignored; earlier applicable price is returned")
        void resolve_futurePriceIgnored_earlierSelected() throws Exception {
            seedPrice(LocalDate.now().minusDays(30), true, BillingCycle.MONTHLY, new BigDecimal("999.00"));
            seedPrice(LocalDate.now().plusDays(30),  true, BillingCycle.MONTHLY, new BigDecimal("1299.00"));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("INDIA", "INR", "monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(999.00));
        }
    }

    // ── Case 4: inactive price ignored ────────────────────────────────────────

    @Nested
    @DisplayName("Case 4 — Inactive price ignored")
    class InactivePriceIgnored {

        @Test
        @DisplayName("C4 — only inactive price returns 404 PLAN_PRICE_NOT_RESOLVED")
        void resolve_onlyInactivePrice_returns404() throws Exception {
            seedPrice(LocalDate.now().minusDays(10), false, BillingCycle.MONTHLY, new BigDecimal("999.00"));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("INDIA", "INR", "monthly")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Case 5: cycle mismatch → 404 ─────────────────────────────────────────

    @Nested
    @DisplayName("Case 5 — Cycle mismatch")
    class CycleMismatch {

        @Test
        @DisplayName("C5 — requesting ANNUAL when only MONTHLY exists returns 404")
        void resolve_cycleMismatch_returns404() throws Exception {
            seedPrice(LocalDate.now().minusDays(10), true, BillingCycle.MONTHLY, new BigDecimal("999.00"));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("INDIA", "INR", "annual")))
                .andExpect(status().isNotFound());
        }
    }

    // ── Case 6: unknown plan ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Case 6 — Unknown plan")
    class UnknownPlan {

        @Test
        @DisplayName("C6 — unknown planId returns 404 PLAN_NOT_FOUND")
        void resolve_unknownPlan_returns404() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new ResolvePriceRequest(UUID.randomUUID(), "INDIA", "INR", BillingCycle.MONTHLY));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Case 7: unknown region ────────────────────────────────────────────────

    @Nested
    @DisplayName("Case 7 — Unknown region")
    class UnknownRegion {

        @Test
        @DisplayName("C7 — region with no prices returns 404 PLAN_PRICE_NOT_RESOLVED")
        void resolve_unknownRegion_returns404() throws Exception {
            seedPrice(LocalDate.now().minusDays(10), true, BillingCycle.MONTHLY, new BigDecimal("999.00"));

            // Price is for INDIA; request is for US
            String requestBody = objectMapper.writeValueAsString(
                new ResolvePriceRequest(planId, "US", "INR", BillingCycle.MONTHLY));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    // ── Case 8: unknown currency ──────────────────────────────────────────────

    @Nested
    @DisplayName("Case 8 — Unknown currency")
    class UnknownCurrency {

        @Test
        @DisplayName("C8 — currency with no prices returns 404 PLAN_PRICE_NOT_RESOLVED")
        void resolve_unknownCurrency_returns404() throws Exception {
            seedPrice(LocalDate.now().minusDays(10), true, BillingCycle.MONTHLY, new BigDecimal("999.00"));

            // Price is for INR; request is for USD
            String requestBody = objectMapper.writeValueAsString(
                new ResolvePriceRequest(planId, "INDIA", "USD", BillingCycle.MONTHLY));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }
}
