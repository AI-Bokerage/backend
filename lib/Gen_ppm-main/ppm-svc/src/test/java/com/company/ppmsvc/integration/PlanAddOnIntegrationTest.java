package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.AssignAddOnsRequest;
import com.company.ppmsvc.api.dto.request.CreateAddOnRequest;
import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.AddOnJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanAddOnJpaRepository;
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
 * Full-stack integration tests for the Plan ↔ Add-On Assignment REST API (PPM-11 Phase 3).
 *
 * <p>Plans and add-ons are seeded directly via domain ports for speed.
 * All HTTP assertions verify the complete round-trip including FK integrity,
 * constraint enforcement, and rollback behaviour on invalid input.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Plan Add-On Assignment — REST API (Integration)")
class PlanAddOnIntegrationTest extends AbstractContainerIntegrationTest {

    static final String PLANS_BASE = "/api/v1/ppm/plans";

    @Autowired MockMvc             mockMvc;
    @Autowired ObjectMapper        objectMapper;
    @Autowired PlanRepositoryPort  planRepository;
    @Autowired AddOnRepositoryPort addOnRepository;
    @Autowired PlanAddOnJpaRepository planAddOnJpaRepository;
    @Autowired PlanJpaRepository      planJpaRepository;
    @Autowired AddOnJpaRepository     addOnJpaRepository;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;
    UUID addOnId1;
    UUID addOnId2;

    @BeforeEach
    void seedData() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId   = UUID.randomUUID();
        addOnId1 = UUID.randomUUID();
        addOnId2 = UUID.randomUUID();
        Instant now = Instant.now();

        planRepository.save(Plan.builder()
            .id(planId)
            .code("PAO-" + planId.toString().substring(0, 8))
            .slug("pao-" + planId.toString().substring(0, 8))
            .name("PAO Integration Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        addOnRepository.save(AddOn.builder()
            .id(addOnId1)
            .code("EXTRA_USERS_10")
            .name("Extra Users 10").description("10 extra seats")
            .type(AddOnType.QUOTA).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());

        addOnRepository.save(AddOn.builder()
            .id(addOnId2)
            .code("PREMIUM_SUPPORT")
            .name("Premium Support").description("24/7 support")
            .type(AddOnType.SERVICE).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    @AfterEach
    void cleanUp() {
        planAddOnJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        addOnJpaRepository.deleteAll();
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

    private String addOnsUrl(UUID pid) {
        return PLANS_BASE + "/" + pid + "/add-ons";
    }

    private void assign(UUID pid, UUID... aids) throws Exception {
        AssignAddOnsRequest req = new AssignAddOnsRequest(Set.of(aids));
        mockMvc.perform(post(addOnsUrl(pid))
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    // ── E2E happy path ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E2E — assign, list, replace, remove")
    class HappyPath {

        @Test
        @DisplayName("E1 — assign two add-ons → list → replace with one → remove → verify empty")
        void fullLifecycle() throws Exception {
            // 1. Assign both add-ons
            mockMvc.perform(post(addOnsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignAddOnsRequest(Set.of(addOnId1, addOnId2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

            // 2. List — both present
            mockMvc.perform(get(addOnsUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

            // 3. Replace with only addOnId1
            mockMvc.perform(put(addOnsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignAddOnsRequest(Set.of(addOnId1)))))
                .andExpect(status().isOk());

            // 4. List — only addOnId1
            mockMvc.perform(get(addOnsUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].addOnId").value(addOnId1.toString()));

            // 5. Remove addOnId1
            mockMvc.perform(delete(addOnsUrl(planId) + "/" + addOnId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // 6. List — empty
            mockMvc.perform(get(addOnsUrl(planId)).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("E2 — assigning an add-on twice returns 409 PLAN_ADD_ON_ALREADY_ASSIGNED")
        void assign_duplicate_returns409() throws Exception {
            assign(planId, addOnId1);

            mockMvc.perform(post(addOnsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignAddOnsRequest(Set.of(addOnId1)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("E3 — assigning to a non-existent plan returns 404 PLAN_NOT_FOUND")
        void assign_unknownPlan_returns404() throws Exception {
            mockMvc.perform(post(addOnsUrl(UUID.randomUUID()))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignAddOnsRequest(Set.of(addOnId1)))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E4 — assigning a non-existent add-on returns 404 ADD_ON_NOT_FOUND")
        void assign_unknownAddOn_returns404() throws Exception {
            mockMvc.perform(post(addOnsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignAddOnsRequest(Set.of(UUID.randomUUID())))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E5 — removing a mapping that does not exist returns 404")
        void remove_notAssigned_returns404() throws Exception {
            mockMvc.perform(delete(addOnsUrl(planId) + "/" + addOnId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E6 — POST with empty addOnIds returns 422")
        void assign_emptySet_returns422() throws Exception {
            mockMvc.perform(post(addOnsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"addOnIds\":[]}"))
                .andExpect(status().isUnprocessableEntity());
        }
    }

    // ── Replace atomicity (rollback) ─────────────────────────────────────────

    @Nested
    @DisplayName("Replace atomicity — rollback on invalid add-on")
    class ReplaceRollback {

        @Test
        @DisplayName("I1 — replace with invalid add-on ID returns 404 and original mappings intact")
        void replace_withInvalidAddOnId_rollbackAndOriginalMappingsIntact() throws Exception {
            // Seed: assign both add-ons
            assign(planId, addOnId1, addOnId2);

            mockMvc.perform(get(addOnsUrl(planId)).with(authentication(buildAuth())))
                .andExpect(jsonPath("$.data.length()").value(2));

            // Attempt replace: one valid, one unknown
            UUID unknownAddOnId = UUID.randomUUID();
            mockMvc.perform(put(addOnsUrl(planId))
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new AssignAddOnsRequest(Set.of(addOnId1, unknownAddOnId)))))
                .andExpect(status().isNotFound());

            // Original mappings still intact — transaction rolled back
            MvcResult listResult = mockMvc.perform(get(addOnsUrl(planId))
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

            JsonNode data = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data");
            Set<String> returnedAddOnIds = new java.util.HashSet<>();
            data.forEach(node -> returnedAddOnIds.add(node.path("addOnId").asText()));
            assertThat(returnedAddOnIds).containsExactlyInAnyOrder(
                addOnId1.toString(), addOnId2.toString());
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("F1 — removing a mapping does not delete the underlying add-on")
        void remove_mapping_doesNotDeleteAddOn() throws Exception {
            assign(planId, addOnId1);

            mockMvc.perform(delete(addOnsUrl(planId) + "/" + addOnId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Add-on still exists in the catalog
            assertThat(addOnJpaRepository.findById(addOnId1)).isPresent();
        }

        @Test
        @DisplayName("F2 — response fields serialise correctly (planId, addOnId present)")
        void assign_response_containsPlanIdAndAddOnId() throws Exception {
            assign(planId, addOnId1);

            MvcResult result = mockMvc.perform(get(addOnsUrl(planId))
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");
            assertThat(data.get(0).path("planId").asText()).isEqualTo(planId.toString());
            assertThat(data.get(0).path("addOnId").asText()).isEqualTo(addOnId1.toString());
        }
    }
}
