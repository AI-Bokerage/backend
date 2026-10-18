package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PlanPriceController;
import com.company.ppmsvc.api.dto.request.CreatePlanPriceRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanPriceRequest;
import com.company.ppmsvc.api.dto.response.PlanPriceResponse;
import com.company.ppmsvc.api.mapper.PlanPriceApiMapper;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.usecase.PlanPriceApplicationService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link PlanPriceController}.
 *
 * <p>Loads only the web layer. {@link PlanPriceApplicationService} and
 * {@link PlanPriceApiMapper} are mocked. No DB, no Testcontainers.
 */
@WebMvcTest(value = PlanPriceController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PlanPriceController")
class PlanPriceControllerTest {

    static final String BASE     = "/api/v1/ppm/prices";
    static final UUID   ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID   PRICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PlanPriceApplicationService planPriceService;
    @MockitoBean PlanPriceApiMapper           apiMapper;
    @MockitoBean RedisRolePermissionResolver  rolePermissionResolver;
    @MockitoBean StringRedisTemplate           stringRedisTemplate;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, null, null, null,
            CpmsUserType.SUPER_ADMIN,
            "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private Authentication buildTenantUserAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, UUID.fromString("00000000-0000-0000-0000-000000000099"),
            "test-slug", UUID.fromString("00000000-0000-0000-0000-000000000088"),
            CpmsUserType.TENANT_USER, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));
    }

    private PlanPrice buildPrice(UUID id, BillingCycle cycle, String currency, String region) {
        Instant now = Instant.now();
        return PlanPrice.builder()
            .id(id).version(0L)
            .planId(PLAN_ID).cycle(cycle).currency(currency).region(region)
            .amount(new BigDecimal("499.0000")).taxInclusive(false)
            .effectiveFrom(LocalDate.of(2025, 1, 1)).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PlanPriceResponse toResponse(PlanPrice p) {
        return new PlanPriceResponse(
            p.getId(), p.getPlanId(), p.getCycle(), p.getCurrency(), p.getRegion(),
            p.getAmount(), p.isTaxInclusive(), p.getEffectiveFrom(), p.isActive(),
            p.getCreatedAt(), p.getUpdatedAt());
    }

    // ── POST /api/v1/ppm/prices ───────────────────────────────────────────────

    @Nested
    @DisplayName("POST /prices")
    class CreatePrice {

        @Test
        @DisplayName("201 — valid request creates price")
        void create_valid_returns201() throws Exception {
            CreatePlanPriceRequest req = new CreatePlanPriceRequest(
                PLAN_ID, BillingCycle.MONTHLY, "INR", "INDIA",
                new BigDecimal("499"), null, LocalDate.of(2025, 1, 1));
            PlanPrice saved = buildPrice(PRICE_ID, BillingCycle.MONTHLY, "INR", "INDIA");

            when(planPriceService.createPrice(eq(ACTOR_ID), eq(PLAN_ID), eq(BillingCycle.MONTHLY),
                eq("INR"), eq("INDIA"), eq(new BigDecimal("499")), isNull(), eq(LocalDate.of(2025, 1, 1))))
                .thenReturn(saved);
            when(apiMapper.toResponse(saved)).thenReturn(toResponse(saved));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PRICE_ID.toString()))
                .andExpect(jsonPath("$.data.cycle").value("monthly"))
                .andExpect(jsonPath("$.data.currency").value("INR"));
        }

        @Test
        @DisplayName("422 — missing planId returns validation error")
        void create_missingPlanId_returns422() throws Exception {
            String body = """
                {"cycle":"monthly","currency":"INR","region":"INDIA",
                 "amount":499,"effectiveFrom":"2025-01-01"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing amount returns validation error")
        void create_missingAmount_returns422() throws Exception {
            String body = """
                {"planId":"%s","cycle":"monthly","currency":"INR",
                 "region":"INDIA","effectiveFrom":"2025-01-01"}
                """.formatted(PLAN_ID);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — zero amount rejected by service returns 422")
        void create_zeroAmount_returns422() throws Exception {
            CreatePlanPriceRequest req = new CreatePlanPriceRequest(
                PLAN_ID, BillingCycle.MONTHLY, "INR", "INDIA",
                BigDecimal.ZERO, null, LocalDate.of(2025, 1, 1));

            when(planPriceService.createPrice(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Price amount must be greater than zero."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void create_planNotFound_returns404() throws Exception {
            CreatePlanPriceRequest req = new CreatePlanPriceRequest(
                PLAN_ID, BillingCycle.MONTHLY, "INR", "INDIA",
                new BigDecimal("499"), null, LocalDate.of(2025, 1, 1));

            when(planPriceService.createPrice(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("409 — duplicate pricing key returns conflict")
        void create_duplicate_returns409() throws Exception {
            CreatePlanPriceRequest req = new CreatePlanPriceRequest(
                PLAN_ID, BillingCycle.MONTHLY, "INR", "INDIA",
                new BigDecimal("499"), null, LocalDate.of(2025, 1, 1));

            when(planPriceService.createPrice(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                    ErrorCode.PLAN_PRICE_ALREADY_EXISTS, "Price already exists."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PATCH /api/v1/ppm/prices/{id} ────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /prices/{id}")
    class UpdatePrice {

        @Test
        @DisplayName("200 — valid patch returns updated price")
        void update_valid_returns200() throws Exception {
            UpdatePlanPriceRequest req = new UpdatePlanPriceRequest(new BigDecimal("599"), null, null);
            PlanPrice saved = buildPrice(PRICE_ID, BillingCycle.MONTHLY, "INR", "INDIA");

            when(planPriceService.updatePrice(eq(ACTOR_ID), eq(PRICE_ID), eq(new BigDecimal("599")), isNull(), isNull()))
                .thenReturn(saved);
            when(apiMapper.toResponse(saved)).thenReturn(toResponse(saved));

            mockMvc.perform(patch(BASE + "/{id}", PRICE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PRICE_ID.toString()));
        }

        @Test
        @DisplayName("200 — empty body {} is accepted (PATCH semantics)")
        void update_emptyBody_returns200() throws Exception {
            PlanPrice saved = buildPrice(PRICE_ID, BillingCycle.MONTHLY, "INR", "INDIA");

            when(planPriceService.updatePrice(any(), any(), any(), any(), any())).thenReturn(saved);
            when(apiMapper.toResponse(saved)).thenReturn(toResponse(saved));

            mockMvc.perform(patch(BASE + "/{id}", PRICE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 — unknown price returns 404")
        void update_notFound_returns404() throws Exception {
            when(planPriceService.updatePrice(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_PRICE_NOT_FOUND, "Plan price not found: " + PRICE_ID));

            mockMvc.perform(patch(BASE + "/{id}", PRICE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/prices/{id} ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /prices/{id}")
    class GetById {

        @Test
        @DisplayName("200 — found price is returned")
        void getById_found_returns200() throws Exception {
            PlanPrice price = buildPrice(PRICE_ID, BillingCycle.MONTHLY, "INR", "INDIA");

            when(planPriceService.getPrice(PRICE_ID)).thenReturn(price);
            when(apiMapper.toResponse(price)).thenReturn(toResponse(price));

            mockMvc.perform(get(BASE + "/{id}", PRICE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PRICE_ID.toString()));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_notFound_returns404() throws Exception {
            when(planPriceService.getPrice(PRICE_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_PRICE_NOT_FOUND, "Plan price not found: " + PRICE_ID));

            mockMvc.perform(get(BASE + "/{id}", PRICE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/prices ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /prices")
    class ListPrices {

        @Test
        @DisplayName("200 — no filter returns all prices")
        void list_noFilter_returnsAll() throws Exception {
            List<PlanPrice> items = List.of(
                buildPrice(UUID.randomUUID(), BillingCycle.MONTHLY, "INR",  "INDIA"),
                buildPrice(UUID.randomUUID(), BillingCycle.ANNUAL,  "USD",  "US"));

            when(planPriceService.listPrices(any(), any(), any(), any(), any())).thenReturn(items);
            when(apiMapper.toResponseList(items)).thenReturn(items.stream().map(this::toResp).toList());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?cycle=monthly converted by StringToBillingCycleConverter and forwarded")
        void list_cycleFilter_convertedAndForwarded() throws Exception {
            when(planPriceService.listPrices(any(), any(), any(), any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(any())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?cycle=monthly")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(planPriceService).listPrices(null, null, null, BillingCycle.MONTHLY, null);
        }

        @Test
        @DisplayName("200 — ?cycle=annual converted by StringToBillingCycleConverter and forwarded")
        void list_cycleAnnual_convertedAndForwarded() throws Exception {
            when(planPriceService.listPrices(any(), any(), any(), any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(any())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?cycle=annual")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(planPriceService).listPrices(null, null, null, BillingCycle.ANNUAL, null);
        }

        @Test
        @DisplayName("200 — ?planId filter forwarded to service")
        void list_planIdFilter_forwardedToService() throws Exception {
            when(planPriceService.listPrices(any(), any(), any(), any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(any())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?planId=" + PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(planPriceService).listPrices(PLAN_ID, null, null, null, null);
        }

        @Test
        @DisplayName("200 — combined filters forwarded to service")
        void list_combinedFilters_forwardedToService() throws Exception {
            when(planPriceService.listPrices(any(), any(), any(), any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(any())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?region=INDIA&currency=INR&cycle=monthly&active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(planPriceService).listPrices(null, "INDIA", "INR", BillingCycle.MONTHLY, true);
        }

        @Test
        @DisplayName("200 — empty catalog returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            when(planPriceService.listPrices(any(), any(), any(), any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(any())).thenReturn(List.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        private PlanPriceResponse toResp(PlanPrice p) {
            return new PlanPriceResponse(
                p.getId(), p.getPlanId(), p.getCycle(), p.getCurrency(), p.getRegion(),
                p.getAmount(), p.isTaxInclusive(), p.getEffectiveFrom(), p.isActive(),
                p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    // ── DELETE /api/v1/ppm/prices/{id} ───────────────────────────────────────

    @Nested
    @DisplayName("DELETE /prices/{id}")
    class DeletePrice {

        @Test
        @DisplayName("204 — successful soft-delete returns no content")
        void delete_existing_returns204() throws Exception {
            doNothing().when(planPriceService).deletePrice(ACTOR_ID, PRICE_ID);

            mockMvc.perform(delete(BASE + "/{id}", PRICE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(planPriceService).deletePrice(ACTOR_ID, PRICE_ID);
        }

        @Test
        @DisplayName("404 — deleting unknown price returns 404")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.PLAN_PRICE_NOT_FOUND, "Plan price not found: " + PRICE_ID))
                .when(planPriceService).deletePrice(ACTOR_ID, PRICE_ID);

            mockMvc.perform(delete(BASE + "/{id}", PRICE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Security ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request returns 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — authenticated but missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
