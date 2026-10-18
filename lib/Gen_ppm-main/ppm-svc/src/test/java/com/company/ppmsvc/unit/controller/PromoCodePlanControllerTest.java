package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PromoCodePlanController;
import com.company.ppmsvc.api.dto.request.AssignPromoPlansRequest;
import com.company.ppmsvc.api.dto.response.PromoCodePlanResponse;
import com.company.ppmsvc.api.mapper.PromoCodePlanApiMapper;
import com.company.ppmsvc.promocodeplan.usecase.PromoCodePlanApplicationService;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Instant;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link PromoCodePlanController}.
 *
 * <p>Loads only the web layer. Both {@link PromoCodePlanApplicationService} and
 * {@link PromoCodePlanApiMapper} are mocked — no DB, no Testcontainers.
 */
@WebMvcTest(value = PromoCodePlanController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PromoCodePlanController")
class PromoCodePlanControllerTest {

    static final String BASE           = "/api/v1/ppm/promo-codes";
    static final UUID   ACTOR_ID       = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   PROMO_CODE_ID  = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID   PLAN_ID_1      = UUID.fromString("00000000-0000-0000-0000-000000000020");
    static final UUID   PLAN_ID_2      = UUID.fromString("00000000-0000-0000-0000-000000000030");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PromoCodePlanApplicationService promoCodePlanService;
    @MockitoBean PromoCodePlanApiMapper          apiMapper;
    @MockitoBean RedisRolePermissionResolver      rolePermissionResolver;
    @MockitoBean StringRedisTemplate           stringRedisTemplate;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, null, null, null,
            CpmsUserType.SUPER_ADMIN, "test-session", "test-jti",
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

    private PromoCodePlanResponse stubMappingResponse(UUID planId) {
        return new PromoCodePlanResponse(UUID.randomUUID(), PROMO_CODE_ID, planId, Instant.now());
    }

    private PromoCodePlan stubDomainMapping(UUID planId) {
        return PromoCodePlan.builder()
            .id(UUID.randomUUID())
            .promoCodeId(PROMO_CODE_ID)
            .planId(planId)
            .createdAt(Instant.now())
            .createdBy(ACTOR_ID)
            .build();
    }

    // ── POST /{promoCodeId}/plans ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{promoCodeId}/plans")
    class AssignPlans {

        @Test
        @DisplayName("200 — valid assignment returns list with assigned mapping")
        void assign_valid_returns200() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1));
            PromoCodePlan domain = stubDomainMapping(PLAN_ID_1);
            PromoCodePlanResponse response = stubMappingResponse(PLAN_ID_1);

            when(promoCodePlanService.assignPlans(any(), eq(PROMO_CODE_ID), any()))
                .thenReturn(List.of(domain));
            when(apiMapper.toResponseList(any())).thenReturn(List.of(response));

            mockMvc.perform(post(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].planId").value(PLAN_ID_1.toString()));
        }

        @Test
        @DisplayName("200 — assigning multiple plans returns all new mappings")
        void assign_multiplePlans_returnsAll() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1, PLAN_ID_2));

            when(promoCodePlanService.assignPlans(any(), eq(PROMO_CODE_ID), any()))
                .thenReturn(List.of(stubDomainMapping(PLAN_ID_1), stubDomainMapping(PLAN_ID_2)));
            when(apiMapper.toResponseList(any()))
                .thenReturn(List.of(stubMappingResponse(PLAN_ID_1), stubMappingResponse(PLAN_ID_2)));

            mockMvc.perform(post(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("422 — empty planIds returns validation error")
        void assign_emptyPlanIds_returns422() throws Exception {
            String body = """
                {"planIds": []}
                """;

            mockMvc.perform(post(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — promo code not found returns 404")
        void assign_promoCodeNotFound_returns404() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1));

            when(promoCodePlanService.assignPlans(any(), eq(PROMO_CODE_ID), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found."));

            mockMvc.perform(post(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void assign_planNotFound_returns404() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1));

            when(promoCodePlanService.assignPlans(any(), eq(PROMO_CODE_ID), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found."));

            mockMvc.perform(post(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 — duplicate plan assignment returns conflict")
        void assign_duplicate_returns409() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1));

            when(promoCodePlanService.assignPlans(any(), eq(PROMO_CODE_ID), any()))
                .thenThrow(new BusinessException(
                    ErrorCode.PROMO_CODE_PLAN_ALREADY_ASSIGNED, "Already assigned."));

            mockMvc.perform(post(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PUT /{promoCodeId}/plans ──────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /{promoCodeId}/plans")
    class ReplacePlans {

        @Test
        @DisplayName("200 — valid replace returns replacement mapping list")
        void replace_valid_returns200() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1));
            PromoCodePlanResponse response = stubMappingResponse(PLAN_ID_1);

            when(promoCodePlanService.replacePlans(any(), eq(PROMO_CODE_ID), any()))
                .thenReturn(List.of(stubDomainMapping(PLAN_ID_1)));
            when(apiMapper.toResponseList(any())).thenReturn(List.of(response));

            mockMvc.perform(put(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("422 — empty planIds returns validation error")
        void replace_emptyPlanIds_returns422() throws Exception {
            String body = """
                {"planIds": []}
                """;

            mockMvc.perform(put(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("404 — promo code not found aborts replace")
        void replace_promoCodeNotFound_returns404() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1));

            when(promoCodePlanService.replacePlans(any(), eq(PROMO_CODE_ID), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found."));

            mockMvc.perform(put(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — invalid plan in replace request aborts with 404")
        void replace_planNotFound_returns404() throws Exception {
            AssignPromoPlansRequest req = new AssignPromoPlansRequest(Set.of(PLAN_ID_1));

            when(promoCodePlanService.replacePlans(any(), eq(PROMO_CODE_ID), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found."));

            mockMvc.perform(put(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /{promoCodeId}/plans ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{promoCodeId}/plans")
    class ListRestrictedPlans {

        @Test
        @DisplayName("200 — returns all plan restrictions for the promo code")
        void list_twoMappings_returns200WithList() throws Exception {
            PromoCodePlanResponse r1 = stubMappingResponse(PLAN_ID_1);
            PromoCodePlanResponse r2 = stubMappingResponse(PLAN_ID_2);

            when(promoCodePlanService.getRestrictedPlans(PROMO_CODE_ID))
                .thenReturn(List.of(stubDomainMapping(PLAN_ID_1), stubDomainMapping(PLAN_ID_2)));
            when(apiMapper.toResponseList(any())).thenReturn(List.of(r1, r2));

            mockMvc.perform(get(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — empty restriction list means unrestricted promo code")
        void list_empty_returns200EmptyList() throws Exception {
            when(promoCodePlanService.getRestrictedPlans(PROMO_CODE_ID)).thenReturn(List.of());
            when(apiMapper.toResponseList(any())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── DELETE /{promoCodeId}/plans/{planId} ──────────────────────────────────

    @Nested
    @DisplayName("DELETE /{promoCodeId}/plans/{planId}")
    class RemovePlan {

        @Test
        @DisplayName("204 — successful removal returns no content")
        void remove_existing_returns204() throws Exception {
            doNothing().when(promoCodePlanService).removePlan(PROMO_CODE_ID, PLAN_ID_1);

            mockMvc.perform(delete(BASE + "/{promoCodeId}/plans/{planId}", PROMO_CODE_ID, PLAN_ID_1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(promoCodePlanService).removePlan(PROMO_CODE_ID, PLAN_ID_1);
        }

        @Test
        @DisplayName("404 — mapping not found returns 404")
        void remove_mappingNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.PROMO_CODE_PLAN_MAPPING_NOT_FOUND, "Mapping not found."))
                .when(promoCodePlanService).removePlan(PROMO_CODE_ID, PLAN_ID_1);

            mockMvc.perform(delete(BASE + "/{promoCodeId}/plans/{planId}", PROMO_CODE_ID, PLAN_ID_1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request returns 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — authenticated but missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(BASE + "/{promoCodeId}/plans", PROMO_CODE_ID)
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
