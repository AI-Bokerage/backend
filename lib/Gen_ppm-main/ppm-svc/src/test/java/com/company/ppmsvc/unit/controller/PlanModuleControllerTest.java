package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PlanModuleController;
import com.company.ppmsvc.api.dto.request.AssignModulesRequest;
import com.company.ppmsvc.api.dto.response.PlanModuleResponse;
import com.company.ppmsvc.api.mapper.PlanModuleApiMapper;
import com.company.ppmsvc.planmodule.usecase.PlanModuleApplicationService;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.model.ModuleCode;
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
import static org.mockito.Mockito.never;
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
 * Slice test for {@link PlanModuleController}.
 *
 * <p>Loads only the web layer (controller + security + exception handler).
 * {@link PlanModuleApplicationService} is mocked — no DB, no Testcontainers.
 *
 * <p>Spring Boot 4 {@code @WebMvcTest} requires {@link SecurityConfig} imported
 * explicitly and {@link OAuth2ResourceServerAutoConfiguration} excluded to avoid
 * a conflicting auto-configured security filter chain.
 */
@WebMvcTest(value = PlanModuleController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PlanModuleController")
class PlanModuleControllerTest {

    static final String BASE      = "/api/v1/ppm/plans";
    static final UUID   ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   PLAN_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID   MODULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PlanModuleApplicationService planModuleService;
    @MockitoBean PlanModuleApiMapper           planModuleApiMapper;
    @MockitoBean RedisRolePermissionResolver   rolePermissionResolver;
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

    private PlanModuleResponse stubResponse(UUID moduleId, ModuleCode code) {
        return new PlanModuleResponse(moduleId, code, code.getValue(), true);
    }

    private Module stubModuleDomain(UUID moduleId, ModuleCode code) {
        Instant now = Instant.now();
        return Module.builder()
            .id(moduleId).version(0L).code(code).name(code.getValue())
            .active(true).createdAt(now).updatedAt(now)
            .build();
    }

    // ── POST /{planId}/modules ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{planId}/modules")
    class AssignModules {

        @Test
        @DisplayName("200 — valid assignment returns assigned modules")
        void assign_valid_returns200() throws Exception {
            AssignModulesRequest req = new AssignModulesRequest(Set.of(MODULE_ID));
            Module module = stubModuleDomain(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);

            when(planModuleService.assignModules(any(), eq(PLAN_ID), any())).thenReturn(List.of(module));
            when(planModuleApiMapper.toResponse(module)).thenReturn(stubResponse(MODULE_ID, ModuleCode.LEAD_MANAGEMENT));

            mockMvc.perform(post(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].moduleId").value(MODULE_ID.toString()));
        }

        @Test
        @DisplayName("422 — empty moduleIds returns validation error")
        void assign_emptyModuleIds_returns422() throws Exception {
            String body = """
                {"moduleIds": []}
                """;
            mockMvc.perform(post(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void assign_planNotFound_returns404() throws Exception {
            AssignModulesRequest req = new AssignModulesRequest(Set.of(MODULE_ID));

            when(planModuleService.assignModules(any(), eq(PLAN_ID), any()))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            mockMvc.perform(post(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — module not found returns 404")
        void assign_moduleNotFound_returns404() throws Exception {
            AssignModulesRequest req = new AssignModulesRequest(Set.of(MODULE_ID));

            when(planModuleService.assignModules(any(), eq(PLAN_ID), any()))
                .thenThrow(new ResourceNotFoundException(ErrorCode.MODULE_NOT_FOUND, "Module not found"));

            mockMvc.perform(post(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 — duplicate assignment returns conflict")
        void assign_duplicate_returns409() throws Exception {
            AssignModulesRequest req = new AssignModulesRequest(Set.of(MODULE_ID));

            when(planModuleService.assignModules(any(), eq(PLAN_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.MODULE_ALREADY_ASSIGNED_TO_PLAN,
                    "Module already assigned."));

            mockMvc.perform(post(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /{planId}/modules ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{planId}/modules")
    class ListModules {

        @Test
        @DisplayName("200 — returns all assigned modules")
        void list_twoModules_returns200WithList() throws Exception {
            UUID mod2 = UUID.randomUUID();
            Module m1 = stubModuleDomain(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);
            Module m2 = stubModuleDomain(mod2, ModuleCode.PROJECT_MANAGEMENT);

            when(planModuleService.getModules(PLAN_ID)).thenReturn(List.of(m1, m2));
            when(planModuleApiMapper.toResponse(m1)).thenReturn(stubResponse(MODULE_ID, ModuleCode.LEAD_MANAGEMENT));
            when(planModuleApiMapper.toResponse(m2)).thenReturn(stubResponse(mod2, ModuleCode.PROJECT_MANAGEMENT));

            mockMvc.perform(get(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — empty list when plan has no assignments")
        void list_empty_returns200EmptyList() throws Exception {
            when(planModuleService.getModules(PLAN_ID)).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void list_planNotFound_returns404() throws Exception {
            when(planModuleService.getModules(PLAN_ID))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            mockMvc.perform(get(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── PUT /{planId}/modules ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /{planId}/modules")
    class ReplaceModules {

        @Test
        @DisplayName("200 — valid replace returns new module set")
        void replace_valid_returns200() throws Exception {
            AssignModulesRequest req = new AssignModulesRequest(Set.of(MODULE_ID));
            Module module = stubModuleDomain(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);

            when(planModuleService.replaceModules(any(), eq(PLAN_ID), any())).thenReturn(List.of(module));
            when(planModuleApiMapper.toResponse(module)).thenReturn(stubResponse(MODULE_ID, ModuleCode.LEAD_MANAGEMENT));

            mockMvc.perform(put(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("422 — empty moduleIds returns validation error")
        void replace_emptyModuleIds_returns422() throws Exception {
            String body = """
                {"moduleIds": []}
                """;
            mockMvc.perform(put(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void replace_planNotFound_returns404() throws Exception {
            AssignModulesRequest req = new AssignModulesRequest(Set.of(MODULE_ID));

            when(planModuleService.replaceModules(any(), eq(PLAN_ID), any()))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            mockMvc.perform(put(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /{planId}/modules/{moduleId} ───────────────────────────────────

    @Nested
    @DisplayName("DELETE /{planId}/modules/{moduleId}")
    class RemoveModule {

        @Test
        @DisplayName("204 — successful removal returns no content")
        void remove_existing_returns204() throws Exception {
            doNothing().when(planModuleService).removeModule(PLAN_ID, MODULE_ID);

            mockMvc.perform(delete(BASE + "/{planId}/modules/{moduleId}", PLAN_ID, MODULE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(planModuleService).removeModule(PLAN_ID, MODULE_ID);
        }

        @Test
        @DisplayName("404 — mapping not found returns 404")
        void remove_mappingNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(ErrorCode.PLAN_MODULE_MAPPING_NOT_FOUND,
                "No mapping found."))
                .when(planModuleService).removeModule(PLAN_ID, MODULE_ID);

            mockMvc.perform(delete(BASE + "/{planId}/modules/{moduleId}", PLAN_ID, MODULE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void remove_planNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found."))
                .when(planModuleService).removeModule(PLAN_ID, MODULE_ID);

            mockMvc.perform(delete(BASE + "/{planId}/modules/{moduleId}", PLAN_ID, MODULE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("200 — GET is public, unauthenticated request succeeds")
        void unauthenticated_returns200Public() throws Exception {
            // GET /plans/{id}/modules is on the access filter's public whitelist —
            // REG-SVC's signup pricing page reads it before the visitor has a session.
            when(planModuleService.getModules(PLAN_ID)).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/{planId}/modules", PLAN_ID))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 — GET is public, ppm.read is not required")
        void missingPermission_stillReturns200() throws Exception {
            when(planModuleService.getModules(PLAN_ID)).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/{planId}/modules", PLAN_ID)
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isOk());

            verify(rolePermissionResolver, never()).resolveAll(any());
        }
    }
}
