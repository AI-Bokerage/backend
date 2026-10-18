package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PlanAddOnController;
import com.company.ppmsvc.api.dto.request.AssignAddOnsRequest;
import com.company.ppmsvc.api.dto.response.PlanAddOnResponse;
import com.company.ppmsvc.api.mapper.PlanAddOnApiMapper;
import com.company.ppmsvc.planaddon.model.PlanAddOn;
import com.company.ppmsvc.planaddon.usecase.PlanAddOnApplicationService;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
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
 * Slice test for {@link PlanAddOnController}.
 *
 * <p>Loads only the web layer (controller + security + exception handler).
 * {@link PlanAddOnApplicationService} is mocked — no DB, no Testcontainers.
 */
@WebMvcTest(value = PlanAddOnController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PlanAddOnController")
class PlanAddOnControllerTest {

    static final String BASE      = "/api/v1/ppm/plans";
    static final UUID   ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   PLAN_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID   ADD_ON_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PlanAddOnApplicationService planAddOnService;
    @MockitoBean PlanAddOnApiMapper           planAddOnApiMapper;
    @MockitoBean RedisRolePermissionResolver  rolePermissionResolver;
    @MockitoBean StringRedisTemplate           stringRedisTemplate;

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

    private PlanAddOnResponse stubPlanAddOn(UUID id, UUID planId, UUID addOnId) {
        return new PlanAddOnResponse(id, planId, addOnId, Instant.now());
    }

    private PlanAddOn stubPlanAddOnDomain(UUID id, UUID planId, UUID addOnId) {
        return PlanAddOn.builder()
            .id(id).version(0L).planId(planId).addOnId(addOnId)
            .createdAt(Instant.now()).createdBy(ACTOR_ID)
            .build();
    }

    // ── POST /{planId}/add-ons ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{planId}/add-ons")
    class AssignAddOns {

        @Test
        @DisplayName("200 — valid assignment succeeds")
        void assign_valid_returns200() throws Exception {
            AssignAddOnsRequest req = new AssignAddOnsRequest(Set.of(ADD_ON_ID));
            doNothing().when(planAddOnService).assignAddOns(any(), eq(PLAN_ID), any());

            mockMvc.perform(post(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

            verify(planAddOnService).assignAddOns(any(), eq(PLAN_ID), any());
        }

        @Test
        @DisplayName("422 — empty addOnIds returns validation error")
        void assign_emptyAddOnIds_returns422() throws Exception {
            String body = """
                {"addOnIds": []}
                """;
            mockMvc.perform(post(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void assign_planNotFound_returns404() throws Exception {
            AssignAddOnsRequest req = new AssignAddOnsRequest(Set.of(ADD_ON_ID));
            doThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"))
                .when(planAddOnService).assignAddOns(any(), eq(PLAN_ID), any());

            mockMvc.perform(post(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — add-on not found returns 404")
        void assign_addOnNotFound_returns404() throws Exception {
            AssignAddOnsRequest req = new AssignAddOnsRequest(Set.of(ADD_ON_ID));
            doThrow(new ResourceNotFoundException(ErrorCode.ADD_ON_NOT_FOUND, "Add-on not found"))
                .when(planAddOnService).assignAddOns(any(), eq(PLAN_ID), any());

            mockMvc.perform(post(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 — duplicate assignment returns conflict")
        void assign_duplicate_returns409() throws Exception {
            AssignAddOnsRequest req = new AssignAddOnsRequest(Set.of(ADD_ON_ID));
            doThrow(new BusinessException(ErrorCode.PLAN_ADD_ON_ALREADY_ASSIGNED,
                "Add-on already assigned."))
                .when(planAddOnService).assignAddOns(any(), eq(PLAN_ID), any());

            mockMvc.perform(post(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /{planId}/add-ons ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{planId}/add-ons")
    class ListAddOns {

        @Test
        @DisplayName("200 — returns all assigned add-ons")
        void list_assigned_returns200WithList() throws Exception {
            List<PlanAddOn> domainList = List.of(
                stubPlanAddOnDomain(UUID.randomUUID(), PLAN_ID, ADD_ON_ID),
                stubPlanAddOnDomain(UUID.randomUUID(), PLAN_ID, UUID.randomUUID()));
            List<PlanAddOnResponse> response = domainList.stream()
                .map(d -> stubPlanAddOn(d.getId(), d.getPlanId(), d.getAddOnId())).toList();

            when(planAddOnService.getPlanAddOns(PLAN_ID)).thenReturn(domainList);
            when(planAddOnApiMapper.toResponseList(domainList)).thenReturn(response);

            mockMvc.perform(get(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — empty list when plan has no assignments")
        void list_empty_returns200EmptyList() throws Exception {
            when(planAddOnService.getPlanAddOns(PLAN_ID)).thenReturn(List.of());
            when(planAddOnApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void list_planNotFound_returns404() throws Exception {
            when(planAddOnService.getPlanAddOns(PLAN_ID))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            mockMvc.perform(get(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── PUT /{planId}/add-ons ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /{planId}/add-ons")
    class ReplaceAddOns {

        @Test
        @DisplayName("200 — valid replace succeeds")
        void replace_valid_returns200() throws Exception {
            AssignAddOnsRequest req = new AssignAddOnsRequest(Set.of(ADD_ON_ID));
            doNothing().when(planAddOnService).replaceAddOns(any(), eq(PLAN_ID), any());

            mockMvc.perform(put(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("422 — empty addOnIds returns validation error")
        void replace_emptyAddOnIds_returns422() throws Exception {
            String body = """
                {"addOnIds": []}
                """;
            mockMvc.perform(put(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void replace_planNotFound_returns404() throws Exception {
            AssignAddOnsRequest req = new AssignAddOnsRequest(Set.of(ADD_ON_ID));
            doThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"))
                .when(planAddOnService).replaceAddOns(any(), eq(PLAN_ID), any());

            mockMvc.perform(put(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /{planId}/add-ons/{addOnId} ────────────────────────────────────

    @Nested
    @DisplayName("DELETE /{planId}/add-ons/{addOnId}")
    class RemoveAddOn {

        @Test
        @DisplayName("204 — successful removal returns no content")
        void remove_existing_returns204() throws Exception {
            doNothing().when(planAddOnService).removeAddOn(PLAN_ID, ADD_ON_ID);

            mockMvc.perform(delete(BASE + "/{planId}/add-ons/{addOnId}", PLAN_ID, ADD_ON_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(planAddOnService).removeAddOn(PLAN_ID, ADD_ON_ID);
        }

        @Test
        @DisplayName("404 — mapping not found returns 404")
        void remove_mappingNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(ErrorCode.PLAN_ADD_ON_MAPPING_NOT_FOUND,
                "No mapping found."))
                .when(planAddOnService).removeAddOn(PLAN_ID, ADD_ON_ID);

            mockMvc.perform(delete(BASE + "/{planId}/add-ons/{addOnId}", PLAN_ID, ADD_ON_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void remove_planNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found."))
                .when(planAddOnService).removeAddOn(PLAN_ID, ADD_ON_ID);

            mockMvc.perform(delete(BASE + "/{planId}/add-ons/{addOnId}", PLAN_ID, ADD_ON_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request returns 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE + "/{planId}/add-ons", PLAN_ID))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — authenticated but missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(BASE + "/{planId}/add-ons", PLAN_ID)
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
