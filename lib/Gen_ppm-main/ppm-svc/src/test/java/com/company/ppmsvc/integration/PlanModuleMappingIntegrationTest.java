package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.AssignModulesRequest;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.ModuleJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanModuleJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the Plan ↔ Module Mapping REST API (PPM-03).
 *
 * <p>Exercises the complete stack: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * Plans and modules are seeded directly via JPA for speed; all HTTP assertions verify
 * the complete round-trip including FK integrity, constraint enforcement, and module
 * persistence after mapping deletion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Plan Module Mapping — REST API (Integration)")
class PlanModuleMappingIntegrationTest extends AbstractContainerIntegrationTest {

    static final String PLANS_BASE = "/api/v1/ppm/plans";

    @Autowired MockMvc              mockMvc;
    @Autowired ObjectMapper         objectMapper;
    @Autowired PlanRepositoryPort   planRepository;
    @Autowired ModuleRepositoryPort moduleRepository;
    @Autowired PlanModuleJpaRepository planModuleJpaRepository;
    @Autowired PlanJpaRepository    planJpaRepository;
    @Autowired ModuleJpaRepository  moduleJpaRepository;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;
    UUID moduleId1;
    UUID moduleId2;

    @BeforeEach
    void seedData() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId    = UUID.randomUUID();
        moduleId1 = UUID.randomUUID();
        moduleId2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Use domain ports so version = null → Spring Data calls persist() not merge()
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-STARTER")
            .slug("IT-" + planId.toString().substring(0, 8))
            .name("IT Starter Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        moduleRepository.save(Module.builder()
            .id(moduleId1)
            .code(ModuleCode.LEAD_MANAGEMENT).name("Lead Management")
            .active(true).createdAt(now).updatedAt(now)
            .build());

        moduleRepository.save(Module.builder()
            .id(moduleId2)
            .code(ModuleCode.PROJECT_MANAGEMENT).name("Project Management")
            .active(true).createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // FK-safe deletion order: mappings first
        planModuleJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        moduleJpaRepository.deleteAll();
    }

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            DEV_USER, null, null, null,
            CpmsUserType.SUPER_ADMIN, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private String modulesUrl(UUID pid) {
        return PLANS_BASE + "/" + pid + "/modules";
    }

    private String assign(UUID pid, UUID... mids) throws Exception {
        AssignModulesRequest req = new AssignModulesRequest(Set.of(mids));
        return mockMvc.perform(post(modulesUrl(pid))
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andReturn().getResponse().getContentAsString();
    }

    // ── E2E happy path ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E2E — assign, list, replace, remove")
    class HappyPath {

        @Test
        @DisplayName("E1 — assign two modules → list → replace with one → remove → verify")
        void fullLifecycle() throws Exception {
            // 1. Assign both modules
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1, moduleId2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // 2. List — both present
            mockMvc.perform(get(modulesUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // 3. Replace with only moduleId1
            mockMvc.perform(put(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].moduleId").value(moduleId1.toString()));

            // 4. List — only moduleId1
            mockMvc.perform(get(modulesUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

            // 5. Remove moduleId1
            mockMvc.perform(delete(modulesUrl(planId) + "/" + moduleId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // 6. List — empty
            mockMvc.perform(get(modulesUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("E2 — assigning a module twice returns 409 MODULE_ALREADY_ASSIGNED_TO_PLAN")
        void assign_duplicate_returns409() throws Exception {
            // First assignment
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1)))))
                .andExpect(status().isOk());

            // Duplicate
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("E3 — assigning to a non-existent plan returns 404 PLAN_NOT_FOUND")
        void assign_unknownPlan_returns404() throws Exception {
            mockMvc.perform(post(modulesUrl(UUID.randomUUID()))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1)))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E4 — assigning a non-existent module returns 404 MODULE_NOT_FOUND")
        void assign_unknownModule_returns404() throws Exception {
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(UUID.randomUUID())))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E5 — removing a mapping that does not exist returns 404 PLAN_MODULE_MAPPING_NOT_FOUND")
        void remove_notAssigned_returns404() throws Exception {
            mockMvc.perform(delete(modulesUrl(planId) + "/" + moduleId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E6 — POST with empty moduleIds returns 422")
        void assign_emptySet_returns422() throws Exception {
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"moduleIds\":[]}"))
                .andExpect(status().isUnprocessableEntity());
        }
    }

    // ── Replace atomicity (rollback) ─────────────────────────────────────────

    @Nested
    @DisplayName("Replace atomicity — rollback on invalid module")
    class ReplaceRollback {

        @Test
        @DisplayName("I1 — replace with one invalid module ID returns 404 and original mappings are intact")
        void replace_withInvalidModuleId_rollbackAndOriginalMappingsIntact() throws Exception {
            // Seed: assign moduleId1 and moduleId2
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1, moduleId2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // Attempt replace with one valid module and one random unknown UUID
            UUID unknownModuleId = UUID.randomUUID();
            mockMvc.perform(put(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1, unknownModuleId)))))
                .andExpect(status().isNotFound());

            // Original mappings must still be intact — transaction rolled back
            MvcResult listResult = mockMvc.perform(get(modulesUrl(planId))
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

            JsonNode data = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data");
            java.util.Set<String> returnedIds = new java.util.HashSet<>();
            data.forEach(node -> returnedIds.add(node.path("moduleId").asText()));
            assertThat(returnedIds).containsExactlyInAnyOrder(
                moduleId1.toString(), moduleId2.toString());
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("F1 — removing a mapping does not delete the underlying module")
        void remove_mapping_doesNotDeleteModule() throws Exception {
            // Assign
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1)))))
                .andExpect(status().isOk());

            // Remove mapping
            mockMvc.perform(delete(modulesUrl(planId) + "/" + moduleId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Module still exists in the catalog
            assertThat(moduleJpaRepository.findById(moduleId1)).isPresent();
        }

        @Test
        @DisplayName("F2 — replace atomically removes old and inserts new in single transaction")
        void replace_isAtomic_oldGoneNewPresent() throws Exception {
            // Assign moduleId1 and moduleId2
            mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1, moduleId2)))))
                .andExpect(status().isOk());

            // Replace with only moduleId2
            mockMvc.perform(put(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].moduleId").value(moduleId2.toString()));

            // moduleId1 no longer assigned
            mockMvc.perform(delete(modulesUrl(planId) + "/" + moduleId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound()); // mapping gone

            // Both modules still intact
            assertThat(moduleJpaRepository.findById(moduleId1)).isPresent();
            assertThat(moduleJpaRepository.findById(moduleId2)).isPresent();
        }

        @Test
        @DisplayName("F3 — response moduleCode serialises as wire value (e.g. 'lead_management')")
        void assign_response_moduleCodeIsWireValue() throws Exception {
            MvcResult result = mockMvc.perform(post(modulesUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignModulesRequest(Set.of(moduleId1)))))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");
            String moduleCode = data.get(0).path("moduleCode").asText();
            assertThat(moduleCode).isEqualTo(ModuleCode.LEAD_MANAGEMENT.getValue());
        }
    }
}
