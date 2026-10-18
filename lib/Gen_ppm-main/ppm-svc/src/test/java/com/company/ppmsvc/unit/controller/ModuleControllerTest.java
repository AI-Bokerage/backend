package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.ModuleController;
import com.company.ppmsvc.api.dto.request.CreateModuleRequest;
import com.company.ppmsvc.api.dto.request.UpdateModuleRequest;
import com.company.ppmsvc.api.dto.response.ModuleResponse;
import com.company.ppmsvc.api.mapper.ModuleApiMapper;
import com.company.ppmsvc.module.usecase.ModuleApplicationService;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link ModuleController}.
 *
 * <p>Loads only the web layer (controller + security + exception handler).
 * {@link ModuleApplicationService} and {@link ModuleApiMapper} are mocked — no DB,
 * no Testcontainers, no {@code ppm-core} wiring.
 *
 * <p>Spring Boot 4 {@code @WebMvcTest} does NOT load {@code @Configuration} beans,
 * so our {@link SecurityConfig} (which carries {@code @EnableWebSecurity} and
 * wires up {@code HttpSecurity}) must be imported explicitly.
 * {@link OAuth2ResourceServerAutoConfiguration} is excluded to prevent a conflicting
 * auto-configured {@code SecurityFilterChain} that would also require {@code HttpSecurity}.
 */
@WebMvcTest(value = ModuleController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("ModuleController")
class ModuleControllerTest {

    static final String BASE = "/api/v1/ppm/modules";
    static final UUID   ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   MODULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired MockMvc mockMvc;

    // Standalone ObjectMapper — Spring's auto-configuration for ObjectMapper is not loaded
    // in @WebMvcTest when SecurityConfig is imported. A plain instance with JSR310 module
    // covers all serialization needs for request DTOs in these tests.
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean ModuleApplicationService   moduleService;
    @MockitoBean ModuleApiMapper             moduleApiMapper;
    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;
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
            Instant.now().plusSeconds(900)
        );
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }

    private Authentication buildTenantUserAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, UUID.fromString("00000000-0000-0000-0000-000000000099"),
            "test-slug", UUID.fromString("00000000-0000-0000-0000-000000000088"),
            CpmsUserType.TENANT_USER, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private Module stubModuleDomain(UUID id, ModuleCode code, String name, boolean active) {
        Instant now = Instant.now();
        return Module.builder()
            .id(id).version(0L).code(code).name(name).description("Description")
            .active(active).createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private ModuleResponse toResponse(Module m) {
        return new ModuleResponse(m.getId(), m.getCode(), m.getName(),
            m.getDescription(), m.isActive(), m.getCreatedAt(), m.getUpdatedAt());
    }

    // ── POST /api/v1/ppm/modules ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /modules")
    class CreateModule {

        @Test
        @DisplayName("201 — valid request creates module")
        void create_valid_returns201() throws Exception {
            CreateModuleRequest req = new CreateModuleRequest(
                ModuleCode.INVOICING, "Invoicing", "Invoice management", true);
            Module module = stubModuleDomain(MODULE_ID, ModuleCode.INVOICING, "Invoicing", true);

            when(moduleService.createModule(any(), any(), any(), any(), any())).thenReturn(module);
            when(moduleApiMapper.toResponse(module)).thenReturn(toResponse(module));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("invoicing"))
                .andExpect(jsonPath("$.data.name").value("Invoicing"));
        }

        @Test
        @DisplayName("422 — missing required code field returns validation error")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"name":"Invoicing","description":"desc","active":true}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — blank name fails @NotBlank validation")
        void create_blankName_returns422() throws Exception {
            String body = """
                {"code":"invoicing","name":"  ","active":true}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict from service")
        void create_duplicateCode_returns409() throws Exception {
            CreateModuleRequest req = new CreateModuleRequest(
                ModuleCode.INVOICING, "Invoicing", null, null);

            when(moduleService.createModule(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.MODULE_CODE_ALREADY_EXISTS,
                    "A module with code 'invoicing' already exists."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PATCH /api/v1/ppm/modules/{id} ────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /modules/{id}")
    class UpdateModule {

        @Test
        @DisplayName("200 — valid patch returns updated module")
        void update_valid_returns200() throws Exception {
            UpdateModuleRequest req = new UpdateModuleRequest("Invoicing v2", null, null);
            Module module = stubModuleDomain(MODULE_ID, ModuleCode.INVOICING, "Invoicing v2", true);

            when(moduleService.updateModule(any(), eq(MODULE_ID), any(), any(), any())).thenReturn(module);
            when(moduleApiMapper.toResponse(module)).thenReturn(toResponse(module));

            mockMvc.perform(patch(BASE + "/{id}", MODULE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Invoicing v2"));
        }

        @Test
        @DisplayName("404 — module not found returns 404")
        void update_notFound_returns404() throws Exception {
            UpdateModuleRequest req = new UpdateModuleRequest("X", null, null);

            when(moduleService.updateModule(any(), eq(MODULE_ID), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.MODULE_NOT_FOUND, "Module not found: " + MODULE_ID));

            mockMvc.perform(patch(BASE + "/{id}", MODULE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/modules/{id} ──────────────────────────────────────────

    @Nested
    @DisplayName("GET /modules/{id}")
    class GetModule {

        @Test
        @DisplayName("200 — found module is returned")
        void getById_found_returns200() throws Exception {
            Module module = stubModuleDomain(MODULE_ID, ModuleCode.REPORTING, "Reporting", true);
            when(moduleService.getModule(MODULE_ID)).thenReturn(module);
            when(moduleApiMapper.toResponse(module)).thenReturn(toResponse(module));

            mockMvc.perform(get(BASE + "/{id}", MODULE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(MODULE_ID.toString()))
                .andExpect(jsonPath("$.data.code").value("reporting"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_notFound_returns404() throws Exception {
            when(moduleService.getModule(MODULE_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.MODULE_NOT_FOUND, "Module not found"));

            mockMvc.perform(get(BASE + "/{id}", MODULE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/ppm/modules ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /modules")
    class ListModules {

        @Test
        @DisplayName("200 — no filter returns all modules")
        void list_noFilter_returnsAll() throws Exception {
            List<Module> modules = List.of(
                stubModuleDomain(UUID.randomUUID(), ModuleCode.INVOICING, "Invoicing", true),
                stubModuleDomain(UUID.randomUUID(), ModuleCode.REPORTING, "Reporting", false));

            when(moduleService.listModules(any())).thenReturn(modules);
            when(moduleApiMapper.toResponseList(modules))
                .thenReturn(modules.stream().map(this::toResponse).toList());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?active=true filter is forwarded to service")
        void list_activeFilter_passesCriteriaToService() throws Exception {
            when(moduleService.listModules(any())).thenReturn(List.of());
            when(moduleApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(moduleService).listModules(eq(true));
        }

        @Test
        @DisplayName("200 — empty catalog returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            when(moduleService.listModules(any())).thenReturn(List.of());
            when(moduleApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        private ModuleResponse toResponse(Module m) {
            return ModuleControllerTest.this.toResponse(m);
        }
    }

    // ── GET /api/v1/ppm/modules/code/{code} ──────────────────────────────────

    @Nested
    @DisplayName("GET /modules/code/{code}")
    class GetModuleByCode {

        @Test
        @DisplayName("200 — known code returns module")
        void getByCode_known_returns200() throws Exception {
            Module module = stubModuleDomain(MODULE_ID, ModuleCode.REPORTING, "Reporting", true);
            when(moduleService.getModuleByCode(ModuleCode.REPORTING)).thenReturn(module);
            when(moduleApiMapper.toResponse(module)).thenReturn(toResponse(module));

            mockMvc.perform(get(BASE + "/code/reporting")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("reporting"));
        }

        @Test
        @DisplayName("404 — unknown code value returns 404")
        void getByCode_unknown_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/code/not_a_real_code")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — valid code but module not found returns 404")
        void getByCode_validCodeNoModule_returns404() throws Exception {
            when(moduleService.getModuleByCode(ModuleCode.INVOICING))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.MODULE_NOT_FOUND, "No module with code: invoicing"));

            mockMvc.perform(get(BASE + "/code/invoicing")
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

    // ── DELETE /api/v1/ppm/modules/{id} ───────────────────────────────────────

    @Nested
    @DisplayName("DELETE /modules/{id}")
    class DeleteModule {

        @Test
        @DisplayName("204 — successful soft-delete returns no content")
        void delete_existing_returns204() throws Exception {
            doNothing().when(moduleService).deleteModule(any(), eq(MODULE_ID));

            mockMvc.perform(delete(BASE + "/{id}", MODULE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(moduleService).deleteModule(any(), eq(MODULE_ID));
        }

        @Test
        @DisplayName("404 — deleting unknown module returns 404")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.MODULE_NOT_FOUND, "Module not found: " + MODULE_ID))
                .when(moduleService).deleteModule(any(), eq(MODULE_ID));

            mockMvc.perform(delete(BASE + "/{id}", MODULE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }
}
