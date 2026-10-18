package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.AssignEntitlementsRequest;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.EntitlementJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanEntitlementJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Full-stack integration tests for Plan ↔ Entitlement assignment REST API (PPM-04 Phase 3).
 *
 * <p>Exercises the complete stack: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * Data is seeded via domain ports. FK-safe cleanup order: plan_entitlements → plans → entitlements.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Plan Entitlement Mapping — REST API (Integration)")
class PlanEntitlementIntegrationTest extends AbstractContainerIntegrationTest {

    static final String PLANS_BASE = "/api/v1/ppm/plans";

    @Autowired MockMvc                        mockMvc;
    @Autowired ObjectMapper                   objectMapper;
    @Autowired PlanRepositoryPort             planRepository;
    @Autowired EntitlementRepositoryPort      entitlementRepository;
    @Autowired PlanEntitlementJpaRepository   planEntitlementJpaRepository;
    @Autowired PlanJpaRepository              planJpaRepository;
    @Autowired EntitlementJpaRepository       entitlementJpaRepository;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;
    UUID entitlementId1;
    UUID entitlementId2;

    @BeforeEach
    void seedData() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId         = UUID.randomUUID();
        entitlementId1 = UUID.randomUUID();
        entitlementId2 = UUID.randomUUID();
        Instant now    = Instant.now();

        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-STARTER-ENT")
            .slug("IT-ENT-" + planId.toString().substring(0, 8))
            .name("IT Starter Entitlement Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        entitlementRepository.save(Entitlement.builder()
            .id(entitlementId1)
            .code("max_seats")
            .name("Max Seats")
            .type(EntitlementType.QUOTA)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());

        entitlementRepository.save(Entitlement.builder()
            .id(entitlementId2)
            .code("sso")
            .name("SSO")
            .type(EntitlementType.BOOLEAN)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // FK-safe deletion order: mappings first, then plans, then entitlements
        planEntitlementJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        entitlementJpaRepository.deleteAll();
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

    private String entitlementsUrl(UUID pid) {
        return PLANS_BASE + "/" + pid + "/entitlements";
    }

    private String resolvedUrl(UUID pid) {
        return PLANS_BASE + "/" + pid + "/entitlements/resolved";
    }

    // ── E2E lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E2E — assign, list, resolve, replace, remove")
    class HappyPath {

        @Test
        @DisplayName("E1 — full lifecycle: assign two → list → resolve → replace with one → remove → empty")
        void fullLifecycle() throws Exception {
            // 1. Assign both entitlements
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1, entitlementId2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // 2. List — both present
            mockMvc.perform(get(entitlementsUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // 3. Resolved — same two entries via resolver (batch IN-query path)
            mockMvc.perform(get(resolvedUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // 4. Replace with only entitlementId1
            mockMvc.perform(put(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

            // 5. List — only entitlementId1
            mockMvc.perform(get(entitlementsUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

            // 6. Remove entitlementId1
            mockMvc.perform(delete(entitlementsUrl(planId) + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // 7. List — empty
            mockMvc.perform(get(entitlementsUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

            // 8. Resolved — empty after all removed
            mockMvc.perform(get(resolvedUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("B1 — assigning an entitlement twice returns 409 PLAN_ENTITLEMENT_ALREADY_ASSIGNED")
        void assign_duplicate_returns409() throws Exception {
            // First assignment
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1)))))
                .andExpect(status().isOk());

            // Duplicate
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B2 — assigning to non-existent plan returns 404 PLAN_NOT_FOUND")
        void assign_unknownPlan_returns404() throws Exception {
            mockMvc.perform(post(entitlementsUrl(UUID.randomUUID()))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1)))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B3 — assigning non-existent entitlement returns 404 ENTITLEMENT_NOT_FOUND")
        void assign_unknownEntitlement_returns404() throws Exception {
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(UUID.randomUUID())))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B4 — removing a mapping that does not exist returns 404 PLAN_ENTITLEMENT_MAPPING_NOT_FOUND")
        void remove_notAssigned_returns404() throws Exception {
            mockMvc.perform(delete(entitlementsUrl(planId) + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B5 — POST with empty entitlementIds returns 422")
        void assign_emptySet_returns422() throws Exception {
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entitlementIds\":[]}"))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("B6 — resolve on non-existent plan returns 404")
        void resolve_unknownPlan_returns404() throws Exception {
            mockMvc.perform(get(resolvedUrl(UUID.randomUUID()))
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── Replace atomicity ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replace atomicity — rollback on invalid entitlement")
    class ReplaceRollback {

        @Test
        @DisplayName("I1 — replace with one invalid entitlement returns 404; original assignments intact")
        void replace_withInvalidEntitlementId_rollbackAndOriginalMappingsIntact() throws Exception {
            // Seed: assign both
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1, entitlementId2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // Attempt replace with one valid + one unknown UUID
            UUID unknownId = UUID.randomUUID();
            mockMvc.perform(put(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1, unknownId)))))
                .andExpect(status().isNotFound());

            // Original 2 assignments must still be intact
            MvcResult listResult = mockMvc.perform(get(entitlementsUrl(planId))
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

            JsonNode data = objectMapper.readTree(
                listResult.getResponse().getContentAsString()).path("data");
            java.util.Set<String> returnedCodes = new java.util.HashSet<>();
            data.forEach(node -> returnedCodes.add(node.path("code").asText()));
            assertThat(returnedCodes).containsExactlyInAnyOrder("max_seats", "sso");
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("F1 — removing a mapping does not delete the underlying entitlement")
        void remove_mapping_doesNotDeleteEntitlement() throws Exception {
            // Assign
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1)))))
                .andExpect(status().isOk());

            // Remove mapping
            mockMvc.perform(delete(entitlementsUrl(planId) + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Entitlement catalog entry still exists
            assertThat(entitlementJpaRepository.findById(entitlementId1)).isPresent();
        }

        @Test
        @DisplayName("F2 — replace atomically removes old and inserts new in single transaction")
        void replace_isAtomic_oldGoneNewPresent() throws Exception {
            // Assign both
            mockMvc.perform(post(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId1, entitlementId2)))))
                .andExpect(status().isOk());

            // Replace with only entitlementId2
            mockMvc.perform(put(entitlementsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignEntitlementsRequest(Set.of(entitlementId2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("sso"));

            // entitlementId1 mapping gone — remove returns 404
            mockMvc.perform(delete(entitlementsUrl(planId) + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());

            // Both entitlement catalog entries still intact
            assertThat(entitlementJpaRepository.findById(entitlementId1)).isPresent();
            assertThat(entitlementJpaRepository.findById(entitlementId2)).isPresent();
        }
    }
}
