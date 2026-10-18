package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PricingResolverController;
import com.company.ppmsvc.api.dto.request.ResolvePriceRequest;
import com.company.ppmsvc.api.dto.response.ResolvedPriceResponse;
import com.company.ppmsvc.api.mapper.PlanPriceApiMapper;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.usecase.PricingResolver;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * Slice test for {@link PricingResolverController}.
 *
 * <p>Loads only the web layer. {@link PricingResolver} and {@link PlanPriceApiMapper}
 * are mocked — no DB.
 */
@WebMvcTest(value = PricingResolverController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PricingResolverController")
class PricingResolverControllerTest {

    static final String    RESOLVE_URL = "/api/v1/ppm/prices/resolve";
    static final UUID      ACTOR_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID      PLAN_ID     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID      PRICE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final LocalDate EFF_FROM    = LocalDate.of(2025, 1, 1);

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PricingResolver              pricingResolver;
    @MockitoBean PlanPriceApiMapper           apiMapper;
    @MockitoBean RedisRolePermissionResolver  rolePermissionResolver;
    @MockitoBean StringRedisTemplate          stringRedisTemplate;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, null, null, null,
            CpmsUserType.SUPER_ADMIN, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private String body(UUID planId, String region, String currency, String cycle) throws Exception {
        return objectMapper.writeValueAsString(
            new ResolvePriceRequest(planId, region, currency, BillingCycle.fromValue(cycle)));
    }

    private PlanPrice buildPrice() {
        Instant now = Instant.now();
        return PlanPrice.builder()
            .id(PRICE_ID).version(0L)
            .planId(PLAN_ID).cycle(BillingCycle.MONTHLY).currency("INR").region("INDIA")
            .amount(new BigDecimal("999.00")).taxInclusive(false)
            .effectiveFrom(EFF_FROM).active(true)
            .createdAt(now).updatedAt(now)
            .build();
    }

    private ResolvedPriceResponse toResponse(PlanPrice p) {
        return new ResolvedPriceResponse(
            p.getPlanId(), p.getId(), p.getCycle(), p.getCurrency(), p.getRegion(),
            p.getAmount(), p.isTaxInclusive(), p.getEffectiveFrom(), p.isActive(), p.getVersion());
    }

    // ── 200 Success ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Success")
    class Success {

        @Test
        @DisplayName("200 — resolved price returns full metadata")
        void resolve_success_returns200WithPriceMetadata() throws Exception {
            PlanPrice resolved = buildPrice();

            when(pricingResolver.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY))
                .thenReturn(resolved);
            when(apiMapper.toResolvedPriceResponse(resolved)).thenReturn(toResponse(resolved));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(PLAN_ID, "INDIA", "INR", "monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.priceId").value(PRICE_ID.toString()))
                .andExpect(jsonPath("$.data.planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.data.cycle").value("monthly"))
                .andExpect(jsonPath("$.data.amount").value(999.00))
                .andExpect(jsonPath("$.data.region").value("INDIA"))
                .andExpect(jsonPath("$.data.currency").value("INR"));
        }
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Not found")
    class NotFound {

        @Test
        @DisplayName("404 — plan not found propagates as PLAN_NOT_FOUND")
        void resolve_planNotFound_returns404() throws Exception {
            when(pricingResolver.resolvePrice(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(PLAN_ID, "INDIA", "INR", "monthly")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — no applicable price propagates as PLAN_PRICE_NOT_RESOLVED")
        void resolve_priceNotResolved_returns404() throws Exception {
            when(pricingResolver.resolvePrice(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_PRICE_NOT_RESOLVED, "No active price found"));

            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(PLAN_ID, "INDIA", "INR", "monthly")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── 422 Validation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("422 — missing planId returns validation error")
        void resolve_missingPlanId_returns422() throws Exception {
            String badBody = """
                {"region":"INDIA","currency":"INR","cycle":"monthly"}
                """;
            mockMvc.perform(post(RESOLVE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(badBody))
                .andExpect(status().isUnprocessableEntity());
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("200 — public endpoint accessible without authentication")
        void publicEndpoint_noAuthRequired_returns200() throws Exception {
            PlanPrice resolved = buildPrice();
            when(pricingResolver.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY))
                .thenReturn(resolved);
            when(apiMapper.toResolvedPriceResponse(resolved)).thenReturn(toResponse(resolved));

            mockMvc.perform(post(RESOLVE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(PLAN_ID, "INDIA", "INR", "monthly")))
                .andExpect(status().isOk());
        }
    }
}
