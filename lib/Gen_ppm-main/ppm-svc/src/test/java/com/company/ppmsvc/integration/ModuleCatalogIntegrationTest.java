package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreateModuleRequest;
import com.company.ppmsvc.api.dto.request.UpdateModuleRequest;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.infrastructure.persistence.repository.ModuleJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
 * Full-stack integration tests for the Module Catalog REST API.
 *
 * <p>Extends {@link AbstractContainerIntegrationTest} for a shared Postgres
 * Testcontainers instance. Overrides the parent's {@code NONE} web environment
 * with {@code RANDOM_PORT} and enables {@link AutoConfigureMockMvc} so HTTP
 * requests flow through the full Spring Security + controller + service + DB stack.
 *
 * <p>No business logic is duplicated here — these tests verify HTTP status codes,
 * response shapes, and end-to-end data flow across all layers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Module Catalog — REST API (Integration)")
class ModuleCatalogIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/modules";

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ModuleJpaRepository moduleJpaRepository;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    @AfterEach
    void cleanUp() {
        moduleJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            DEV_USER, null, null, null,
            CpmsUserType.SUPER_ADMIN,
            "test-session", "test-jti",
            Instant.now().plusSeconds(900)
        );
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /modules")
    class CreateModule {

        @Test
        @DisplayName("201 — creates module and returns persisted data")
        void create_valid_returns201WithPersistedData() throws Exception {
            CreateModuleRequest req = new CreateModuleRequest(
                ModuleCode.INVOICING, "Invoicing", "Invoice management", true);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.code").value("invoicing"))
                .andExpect(jsonPath("$.data.name").value("Invoicing"))
                .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict")
        void create_duplicateCode_returns409() throws Exception {
            CreateModuleRequest req = new CreateModuleRequest(
                ModuleCode.REPORTING, "Reporting", null, null);

            // First creation must succeed
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

            // Second creation with same code must fail
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing code fails validation")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"name":"Missing Code","active":true}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /modules/{id}")
    class UpdateModule {

        @Test
        @DisplayName("200 — updates module name and returns updated data")
        void update_valid_returns200() throws Exception {
            // Create module first
            CreateModuleRequest create = new CreateModuleRequest(
                ModuleCode.MESSAGING, "Messaging", null, true);
            String createResponse = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

            String moduleId = objectMapper.readTree(createResponse).at("/data/id").asText();

            // Now update
            UpdateModuleRequest update = new UpdateModuleRequest("Messaging v2", null, false);

            mockMvc.perform(patch(BASE + "/{id}", moduleId)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Messaging v2"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.code").value("messaging"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void update_unknownId_returns404() throws Exception {
            UpdateModuleRequest update = new UpdateModuleRequest("X", null, null);

            mockMvc.perform(patch(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /modules/{id}")
    class GetModule {

        @Test
        @DisplayName("200 — returns persisted module")
        void getById_existing_returns200() throws Exception {
            CreateModuleRequest create = new CreateModuleRequest(
                ModuleCode.SSO, "SSO", null, true);
            String createResponse = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
                .andReturn().getResponse().getContentAsString();

            String moduleId = objectMapper.readTree(createResponse).at("/data/id").asText();

            mockMvc.perform(get(BASE + "/{id}", moduleId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(moduleId))
                .andExpect(jsonPath("$.data.code").value("sso"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_unknown_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET (list) ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /modules")
    class ListModules {

        @Test
        @DisplayName("200 — returns all created modules")
        void list_multipleModules_returnsAll() throws Exception {
            mockMvc.perform(post(BASE).with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateModuleRequest(ModuleCode.INVOICING, "Invoicing", null, true))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE).with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateModuleRequest(ModuleCode.REPORTING, "Reporting", null, false))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?active=true filters inactive modules")
        void list_activeFilter_returnsOnlyActive() throws Exception {
            mockMvc.perform(post(BASE).with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateModuleRequest(ModuleCode.API_ACCESS, "API Access", null, true))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE).with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateModuleRequest(ModuleCode.AUDIT_LOG_EXPORT, "Audit Log", null, false))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?active=true").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("api_access"));
        }
    }

    // ── GET /code/{code} ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /modules/code/{code}")
    class GetModuleByCode {

        @Test
        @DisplayName("200 — returns module by wire code")
        void getByCode_existing_returns200() throws Exception {
            CreateModuleRequest create = new CreateModuleRequest(
                ModuleCode.REPORTING, "Reporting", null, true);
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "/code/reporting")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("reporting"))
                .andExpect(jsonPath("$.data.name").value("Reporting"));
        }

        @Test
        @DisplayName("404 — unknown code value returns 404")
        void getByCode_unknownValue_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/code/not_a_real_code")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — valid code but no module exists returns 404")
        void getByCode_validCodeNoModule_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/code/invoicing")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /modules/{id}")
    class DeleteModule {

        @Test
        @DisplayName("204 — soft-deletes module; subsequent GET returns 404")
        void delete_existing_returns204AndHidesModule() throws Exception {
            CreateModuleRequest create = new CreateModuleRequest(
                ModuleCode.TIME_TRACKING, "Time Tracking", null, true);
            String createResponse = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
                .andReturn().getResponse().getContentAsString();

            String moduleId = objectMapper.readTree(createResponse).at("/data/id").asText();

            // Delete
            mockMvc.perform(delete(BASE + "/{id}", moduleId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Must be hidden
            mockMvc.perform(get(BASE + "/{id}", moduleId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 — deleting unknown module returns 404")
        void delete_unknown_returns404() throws Exception {
            mockMvc.perform(delete(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("J-gate: code can be reused after soft-delete")
        void delete_andRecreate_sameCodeSucceeds() throws Exception {
            // Create REPORTING
            CreateModuleRequest create = new CreateModuleRequest(
                ModuleCode.REPORTING, "Reporting", null, true);
            String createResponse = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
                .andReturn().getResponse().getContentAsString();
            String moduleId = objectMapper.readTree(createResponse).at("/data/id").asText();

            // Soft-delete REPORTING
            mockMvc.perform(delete(BASE + "/{id}", moduleId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Recreate REPORTING — partial unique index allows this
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("reporting"));
        }
    }
}
