package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PlanEntitlementController;
import com.company.ppmsvc.api.dto.request.AssignEntitlementsRequest;
import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import com.company.ppmsvc.planentitlement.usecase.EntitlementResolver;
import com.company.ppmsvc.planentitlement.usecase.PlanEntitlementApplicationService;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
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
 * Slice test for {@link PlanEntitlementController}.
 *
 * <p>Loads only the web layer. Both {@link PlanEntitlementApplicationService} and
 * {@link EntitlementResolver} are mocked. No DB, no Testcontainers.
 */
@WebMvcTest(value = PlanEntitlementController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PlanEntitlementController")
class PlanEntitlementControllerTest {

    static final String BASE           = "/api/v1/ppm/plans";
    static final UUID   ACTOR_ID       = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   PLAN_ID        = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID   ENTITLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PlanEntitlementApplicationService planEntitlementService;
    @MockitoBean EntitlementResolver               entitlementResolver;
    @MockitoBean RedisRolePermissionResolver        rolePermissionResolver;
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

    private AssignEntitlementsRequest assignRequest(UUID... ids) {
        return new AssignEntitlementsRequest(Set.of(ids));
    }

    private List<ResolvedEntitlementResponse> stubResolved() {
        return List.of(
            new ResolvedEntitlementResponse("max_users", "Max Users", EntitlementType.QUOTA, "25"),
            new ResolvedEntitlementResponse("sso", "SSO", EntitlementType.BOOLEAN, "true"));
    }

    private String planEntitlementsPath() {
        return BASE + "/" + PLAN_ID + "/entitlements";
    }

    // ── POST /{planId}/entitlements ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /{planId}/entitlements")
    class AssignEntitlements {

        @Test
        @DisplayName("200 — valid request assigns entitlements and returns resolved list")
        void assign_valid_returns200() throws Exception {
            AssignEntitlementsRequest req = assignRequest(ENTITLEMENT_ID);
            List<ResolvedEntitlementResponse> response = stubResolved();

            when(planEntitlementService.assignEntitlements(any(), eq(PLAN_ID), any()))
                .thenReturn(response);

            mockMvc.perform(post(planEntitlementsPath())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("422 — empty entitlementIds fails @NotEmpty validation")
        void assign_emptyIds_returns422() throws Exception {
            String body = """
                {"entitlementIds":[]}
                """;

            mockMvc.perform(post(planEntitlementsPath())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void assign_planNotFound_returns404() throws Exception {
            AssignEntitlementsRequest req = assignRequest(ENTITLEMENT_ID);

            when(planEntitlementService.assignEntitlements(any(), eq(PLAN_ID), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID));

            mockMvc.perform(post(planEntitlementsPath())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("409 — already assigned entitlement returns conflict")
        void assign_alreadyAssigned_returns409() throws Exception {
            AssignEntitlementsRequest req = assignRequest(ENTITLEMENT_ID);

            when(planEntitlementService.assignEntitlements(any(), eq(PLAN_ID), any()))
                .thenThrow(new BusinessException(
                    ErrorCode.PLAN_ENTITLEMENT_ALREADY_ASSIGNED,
                    "Entitlement already assigned."));

            mockMvc.perform(post(planEntitlementsPath())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PUT /{planId}/entitlements ────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /{planId}/entitlements")
    class ReplaceEntitlements {

        @Test
        @DisplayName("200 — valid request replaces entitlements and returns new list")
        void replace_valid_returns200() throws Exception {
            AssignEntitlementsRequest req = assignRequest(ENTITLEMENT_ID);
            List<ResolvedEntitlementResponse> response = stubResolved();

            when(planEntitlementService.replaceEntitlements(any(), eq(PLAN_ID), any()))
                .thenReturn(response);

            mockMvc.perform(put(planEntitlementsPath())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("404 — entitlement not found in catalog returns 404 without deleting originals")
        void replace_entitlementNotFound_returns404() throws Exception {
            AssignEntitlementsRequest req = assignRequest(UUID.randomUUID());

            when(planEntitlementService.replaceEntitlements(any(), eq(PLAN_ID), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "Entitlement not found."));

            mockMvc.perform(put(planEntitlementsPath())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /{planId}/entitlements/resolved ───────────────────────────────────

    @Nested
    @DisplayName("GET /{planId}/entitlements/resolved")
    class ResolveEntitlements {

        @Test
        @DisplayName("200 — resolved set is returned from resolver")
        void resolved_found_returns200() throws Exception {
            when(entitlementResolver.resolveEntitlements(PLAN_ID))
                .thenReturn(stubResolved());

            mockMvc.perform(get(BASE + "/" + PLAN_ID + "/entitlements/resolved")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("max_users"))
                .andExpect(jsonPath("$.data[0].value").value("25"));
        }

        @Test
        @DisplayName("200 — plan with no assignments returns empty resolved list")
        void resolved_empty_returnsEmptyList() throws Exception {
            when(entitlementResolver.resolveEntitlements(PLAN_ID)).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/" + PLAN_ID + "/entitlements/resolved")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void resolved_planNotFound_returns404() throws Exception {
            when(entitlementResolver.resolveEntitlements(PLAN_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID));

            mockMvc.perform(get(BASE + "/" + PLAN_ID + "/entitlements/resolved")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /{planId}/entitlements ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{planId}/entitlements")
    class ListPlanEntitlements {

        @Test
        @DisplayName("200 — assignments returned from service")
        void list_found_returns200() throws Exception {
            when(planEntitlementService.getPlanEntitlements(PLAN_ID))
                .thenReturn(stubResolved());

            mockMvc.perform(get(planEntitlementsPath())
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — plan with no assignments returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            when(planEntitlementService.getPlanEntitlements(PLAN_ID)).thenReturn(List.of());

            mockMvc.perform(get(planEntitlementsPath())
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void list_planNotFound_returns404() throws Exception {
            when(planEntitlementService.getPlanEntitlements(PLAN_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID));

            mockMvc.perform(get(planEntitlementsPath())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── DELETE /{planId}/entitlements/{entitlementId} ─────────────────────────

    @Nested
    @DisplayName("DELETE /{planId}/entitlements/{entitlementId}")
    class RemoveEntitlement {

        @Test
        @DisplayName("204 — successful removal returns no content")
        void remove_existing_returns204() throws Exception {
            doNothing().when(planEntitlementService).removeEntitlement(PLAN_ID, ENTITLEMENT_ID);

            mockMvc.perform(delete(BASE + "/" + PLAN_ID + "/entitlements/" + ENTITLEMENT_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(planEntitlementService).removeEntitlement(PLAN_ID, ENTITLEMENT_ID);
        }

        @Test
        @DisplayName("404 — mapping not found returns 404")
        void remove_mappingNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.PLAN_ENTITLEMENT_MAPPING_NOT_FOUND,
                "No entitlement mapping found."))
                .when(planEntitlementService).removeEntitlement(PLAN_ID, ENTITLEMENT_ID);

            mockMvc.perform(delete(BASE + "/" + PLAN_ID + "/entitlements/" + ENTITLEMENT_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void remove_planNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID))
                .when(planEntitlementService).removeEntitlement(PLAN_ID, ENTITLEMENT_ID);

            mockMvc.perform(delete(BASE + "/" + PLAN_ID + "/entitlements/" + ENTITLEMENT_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── Security ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request returns 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(planEntitlementsPath()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — authenticated but missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(planEntitlementsPath())
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
