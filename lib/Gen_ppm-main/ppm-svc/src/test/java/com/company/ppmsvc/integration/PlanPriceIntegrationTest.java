package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreatePlanPriceRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanPriceRequest;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Full-stack integration tests for the Pricing Catalog REST API (PPM-05 Phase 3).
 *
 * <p>Exercises the complete stack: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * A plan is seeded in {@code @BeforeEach} via the domain port; price entries are created through
 * the HTTP POST endpoint or the domain port depending on the test scenario.
 *
 * <p>Cleanup: native SQL bypasses {@code @SQLRestriction} so soft-deleted price rows are removed
 * before the plan hard-delete, preventing FK violations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Pricing Catalog — REST API (Integration)")
class PlanPriceIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/prices";

    @Autowired MockMvc                  mockMvc;
    @Autowired ObjectMapper             objectMapper;
    @Autowired PlanRepositoryPort       planRepository;
    @Autowired PlanPriceRepositoryPort  planPriceRepository;
    @Autowired PlanJpaRepository        planJpaRepository;
    @Autowired JdbcTemplate             jdbcTemplate;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;

    @BeforeEach
    void seedPlan() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-PRICE-" + planId.toString().substring(0, 8))
            .slug("IT-PX-" + planId.toString().substring(0, 8))
            .name("Price Integration Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // Native SQL bypasses @SQLRestriction — removes soft-deleted rows before plan delete
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

    private CreatePlanPriceRequest buildCreateRequest(String currency, String region,
                                                       BillingCycle cycle, BigDecimal amount,
                                                       LocalDate effectiveFrom) {
        return new CreatePlanPriceRequest(planId, cycle, currency, region, amount, null, effectiveFrom);
    }

    private PlanPrice seedPrice(String region, String currency, BillingCycle cycle,
                                 BigDecimal amount, boolean active) {
        Instant now = Instant.now();
        return planPriceRepository.save(PlanPrice.builder()
            .id(UUID.randomUUID())
            .planId(planId)
            .region(region).currency(currency)
            .cycle(cycle).amount(amount)
            .taxInclusive(false)
            .effectiveFrom(LocalDate.of(2025, 1, 1))
            .active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    // ── E2E lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E2E — create, update, get, list, delete")
    class HappyPath {

        @Test
        @DisplayName("E1 — full lifecycle: POST → GET → PATCH → GET → DELETE → GET 404")
        void fullLifecycle() throws Exception {
            // 1. Create
            CreatePlanPriceRequest req = buildCreateRequest(
                "INR", "INDIA", BillingCycle.MONTHLY, new BigDecimal("499"), LocalDate.of(2025, 1, 1));

            MvcResult createResult = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.region").value("INDIA"))
                .andExpect(jsonPath("$.data.cycle").value("monthly"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

            String priceId = objectMapper.readTree(
                createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

            // 2. GET by ID
            mockMvc.perform(get(BASE + "/" + priceId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(priceId))
                .andExpect(jsonPath("$.data.amount").value(499));

            // 3. PATCH — update amount
            mockMvc.perform(patch(BASE + "/" + priceId)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdatePlanPriceRequest(new BigDecimal("599"), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(599))
                .andExpect(jsonPath("$.data.currency").value("INR"))  // identity immutable
                .andExpect(jsonPath("$.data.cycle").value("monthly")); // identity immutable

            // 4. GET — reflects updated amount
            mockMvc.perform(get(BASE + "/" + priceId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(599));

            // 5. Soft-delete
            mockMvc.perform(delete(BASE + "/" + priceId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // 6. GET after deletion → 404
            mockMvc.perform(get(BASE + "/" + priceId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── Filter tests ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /prices — filter tests")
    class FilterTests {

        @BeforeEach
        void seedPrices() {
            seedPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), true);
            seedPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("4990"), true);
            seedPrice("US",    "USD", BillingCycle.MONTHLY, new BigDecimal("9.99"), true);
            seedPrice("EU",    "EUR", BillingCycle.ANNUAL,  new BigDecimal("89"),   false);
        }

        @Test
        @DisplayName("F1 — ?planId filter returns only prices for that plan")
        void list_planIdFilter_returnsMatchingPrices() throws Exception {
            mockMvc.perform(get(BASE + "?planId=" + planId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
        }

        @Test
        @DisplayName("F2 — ?region=INDIA returns only INDIA prices")
        void list_regionFilter_returnsMatchingPrices() throws Exception {
            mockMvc.perform(get(BASE + "?region=INDIA")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("F3 — ?currency=USD returns only USD prices")
        void list_currencyFilter_returnsMatchingPrices() throws Exception {
            mockMvc.perform(get(BASE + "?currency=USD")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].currency").value("USD"));
        }

        @Test
        @DisplayName("F4 — ?cycle=monthly returns only MONTHLY prices")
        void list_cycleMonthly_returnsMatchingPrices() throws Exception {
            mockMvc.perform(get(BASE + "?cycle=monthly")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("F5 — ?cycle=annual returns only ANNUAL prices")
        void list_cycleAnnual_returnsMatchingPrices() throws Exception {
            mockMvc.perform(get(BASE + "?cycle=annual")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("F6 — ?active=false returns only inactive prices")
        void list_activeFalse_returnsOnlyInactive() throws Exception {
            mockMvc.perform(get(BASE + "?active=false")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].region").value("EU"));
        }

        @Test
        @DisplayName("F7 — no filter returns all 4 seeded prices")
        void list_noFilter_returnsAll() throws Exception {
            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("B1 — duplicate pricing key returns 409 PLAN_PRICE_ALREADY_EXISTS")
        void create_duplicateKey_returns409() throws Exception {
            // First create succeeds
            CreatePlanPriceRequest req = buildCreateRequest(
                "INR", "INDIA", BillingCycle.MONTHLY, new BigDecimal("499"), LocalDate.of(2025, 1, 1));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

            // Second create with same key must conflict
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B2 — unknown plan ID returns 404 PLAN_NOT_FOUND")
        void create_unknownPlan_returns404() throws Exception {
            CreatePlanPriceRequest req = new CreatePlanPriceRequest(
                UUID.randomUUID(), BillingCycle.MONTHLY, "INR", "INDIA",
                new BigDecimal("499"), null, LocalDate.of(2025, 1, 1));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B3 — amount of zero returns 422 VALIDATION_ERROR")
        void create_zeroAmount_returns422() throws Exception {
            CreatePlanPriceRequest req = new CreatePlanPriceRequest(
                planId, BillingCycle.MONTHLY, "INR", "INDIA",
                BigDecimal.ZERO, null, LocalDate.of(2025, 1, 1));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B4 — missing required field returns 422")
        void create_missingEffectiveFrom_returns422() throws Exception {
            String body = """
                {"planId":"%s","cycle":"monthly","currency":"INR",
                 "region":"INDIA","amount":499}
                """.formatted(planId);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B5 — GET unknown price ID returns 404")
        void get_unknownId_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B6 — PATCH unknown price ID returns 404")
        void update_unknownId_returns404() throws Exception {
            mockMvc.perform(patch(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B7 — DELETE unknown price ID returns 404")
        void delete_unknownId_returns404() throws Exception {
            mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── Normalisation tests ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Currency and region normalisation")
    class Normalisation {

        @Test
        @DisplayName("N1 — lowercase currency stored and returned as uppercase (usd → USD)")
        void create_lowercaseCurrency_storedUppercase() throws Exception {
            CreatePlanPriceRequest req = buildCreateRequest(
                "usd", "US", BillingCycle.MONTHLY, new BigDecimal("9.99"), LocalDate.of(2025, 1, 1));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currency").value("USD"));
        }

        @Test
        @DisplayName("N2 — lowercase region stored and returned as uppercase (india → INDIA)")
        void create_lowercaseRegion_storedUppercase() throws Exception {
            CreatePlanPriceRequest req = buildCreateRequest(
                "INR", "india", BillingCycle.MONTHLY, new BigDecimal("499"), LocalDate.of(2025, 1, 1));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.region").value("INDIA"));
        }

        @Test
        @DisplayName("N3 — future effectiveFrom accepted and persisted correctly")
        void create_futureEffectiveFrom_accepted() throws Exception {
            LocalDate future = LocalDate.now().plusYears(1);
            CreatePlanPriceRequest req = buildCreateRequest(
                "INR", "INDIA", BillingCycle.MONTHLY, new BigDecimal("599"), future);

            MvcResult result = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

            String effectiveFrom = objectMapper.readTree(
                result.getResponse().getContentAsString())
                .path("data").path("effectiveFrom").asText();

            assertThat(effectiveFrom).isEqualTo(future.toString());
        }
    }

    // ── Soft-delete visibility ────────────────────────────────────────────────

    @Nested
    @DisplayName("Soft-delete visibility")
    class SoftDeleteVisibility {

        @Test
        @DisplayName("S1 — soft-deleted price excluded from list but physical row retained")
        void softDelete_excludedFromList_physicalRowRetained() throws Exception {
            // Create a price
            MvcResult createResult = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        buildCreateRequest("INR", "INDIA", BillingCycle.MONTHLY,
                            new BigDecimal("499"), LocalDate.of(2025, 1, 1)))))
                .andExpect(status().isCreated())
                .andReturn();

            String priceId = objectMapper.readTree(
                createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

            // Soft-delete
            mockMvc.perform(delete(BASE + "/" + priceId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Not returned by list
            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

            // Physical row retained with deleted_at set
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_plan_prices WHERE id = ?",
                Integer.class, UUID.fromString(priceId));
            assertThat(count).isEqualTo(1);
        }
    }
}
