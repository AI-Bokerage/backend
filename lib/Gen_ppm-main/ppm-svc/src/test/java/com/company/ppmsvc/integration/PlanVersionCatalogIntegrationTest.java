package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreatePlanVersionRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanVersionRequest;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanVersionJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the Plan Versioning REST API (PPM-06 Phase 3).
 *
 * <p>Exercises the complete stack: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * A plan is seeded in {@code @BeforeEach} via the domain port; versions are created through
 * the HTTP POST endpoint or the domain port depending on the scenario.
 *
 * <p>Cleanup: native SQL bypasses {@code @SQLRestriction} so soft-deleted version rows
 * are removed before the plan hard-delete, preventing FK violations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Plan Versioning Catalog — REST API (Integration)")
class PlanVersionCatalogIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE       = "/api/v1/ppm/versions";
    static final String PLANS_BASE = "/api/v1/ppm/plans";

    @Autowired MockMvc                    mockMvc;
    @Autowired ObjectMapper               objectMapper;
    @Autowired PlanRepositoryPort         planRepository;
    @Autowired PlanVersionRepositoryPort  planVersionRepository;
    @Autowired PlanJpaRepository          planJpaRepository;
    @Autowired PlanVersionJpaRepository   planVersionJpaRepository;
    @Autowired JdbcTemplate               jdbcTemplate;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;

    @BeforeEach
    void seedPlan() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-VER-" + planId.toString().substring(0, 8))
            .slug("IT-VX-" + planId.toString().substring(0, 8))
            .name("Version Integration Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_plan_versions");
        planJpaRepository.deleteAll();
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

    private Authentication buildTenantUserAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            DEV_USER, UUID.fromString("00000000-0000-0000-0000-000000000099"),
            "test-slug", UUID.fromString("00000000-0000-0000-0000-000000000088"),
            CpmsUserType.TENANT_USER, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));
    }

    private CreatePlanVersionRequest createReq(int versionNo, LocalDate effectiveFrom) {
        return new CreatePlanVersionRequest(planId, versionNo, effectiveFrom, null, null, null, null, null, null, null, null);
    }

    private PlanVersion seedVersion(int versionNo, LocalDate effectiveFrom, LocalDate effectiveTo) {
        Instant now = Instant.now();
        return planVersionRepository.save(PlanVersion.builder()
            .id(UUID.randomUUID())
            .planId(planId).versionNo(versionNo)
            .effectiveFrom(effectiveFrom).effectiveTo(effectiveTo)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("id").asText();
    }

    // ── E2E lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E2E — full versioning lifecycle")
    class HappyPath {

        @Test
        @DisplayName("L1 — POST v1 → GET v1 → GET latest=v1 → POST v2 → latest=v2 → v1 auto-closed → LIST → DELETE v2 → GET 404")
        void fullLifecycle() throws Exception {
            LocalDate v1From = LocalDate.of(2026, 1, 1);
            LocalDate v2From = LocalDate.of(2027, 1, 1);

            // 1. Create v1
            MvcResult v1Result = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(1, v1From))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.effectiveTo").doesNotExist())
                .andReturn();
            String v1Id = extractId(v1Result);

            // 2. GET v1 by ID
            mockMvc.perform(get(BASE + "/" + v1Id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1));

            // 3. GET latest → v1
            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions/latest")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1));

            // 4. Create v2 — triggers BR-6 auto-close on v1
            MvcResult v2Result = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(2, v2From))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andReturn();
            String v2Id = extractId(v2Result);

            // 5. GET latest → v2 (highest versionNo)
            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions/latest")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));

            // 6. Verify v1 was auto-closed — effectiveTo = v2From.minusDays(1) = 2026-12-31
            mockMvc.perform(get(BASE + "/" + v1Id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveTo").value("2026-12-31"));

            // 7. LIST for plan — returns v2 first, then v1 (desc versionNo)
            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].versionNo").value(2))
                .andExpect(jsonPath("$.data[1].versionNo").value(1));

            // 8. DELETE v2
            mockMvc.perform(delete(BASE + "/" + v2Id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // 9. GET v2 → 404
            mockMvc.perform(get(BASE + "/" + v2Id).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("L2 — PATCH effectiveTo and active — identity fields unchanged")
        void patchVersion_mutatesOnlyMutableFields() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(1, LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isCreated())
                .andReturn();
            String vId = extractId(created);

            // Patch: set effectiveTo and deactivate
            mockMvc.perform(patch(BASE + "/" + vId)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdatePlanVersionRequest(LocalDate.of(2026, 12, 31), false, null, null, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveTo").value("2026-12-31"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.versionNo").value(1))           // immutable
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-01-01")); // immutable
        }
    }

    // ── BR-6 Auto-Close verification ──────────────────────────────────────────

    @Nested
    @DisplayName("BR-6 — auto-close previous open version")
    class AutoClose {

        @Test
        @DisplayName("AC1 — previous open-ended version gets effectiveTo = newFrom.minusDays(1)")
        void autoClose_previousOpen_closedCorrectly() throws Exception {
            // Seed v1 directly via port (open-ended)
            PlanVersion v1 = seedVersion(1, LocalDate.of(2026, 1, 1), null);

            // Create v2 via HTTP
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(2, LocalDate.of(2027, 1, 1)))))
                .andExpect(status().isCreated());

            // Verify v1 was auto-closed
            mockMvc.perform(get(BASE + "/" + v1.getId()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveTo").value("2026-12-31"));
        }

        @Test
        @DisplayName("AC2 — already-closed previous version is NOT re-closed")
        void autoClose_previousAlreadyClosed_noChange() throws Exception {
            // Seed v1 already closed
            PlanVersion v1 = seedVersion(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            // Create v2 for 2027
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(2, LocalDate.of(2027, 1, 1)))))
                .andExpect(status().isCreated());

            // v1 effectiveTo unchanged
            mockMvc.perform(get(BASE + "/" + v1.getId()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveTo").value("2026-12-31"));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("E1 — duplicate versionNo returns 409")
        void create_duplicateVersionNo_returns409() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(1, LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePlanVersionRequest(planId, 1, LocalDate.of(2027, 1, 1), null, null, null, null, null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("E2 — duplicate effectiveFrom returns 409")
        void create_duplicateEffectiveFrom_returns409() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(1, LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePlanVersionRequest(planId, 2, LocalDate.of(2026, 1, 1), null, null, null, null, null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("E3 — effectiveFrom inside closed version range returns 409 PLAN_VERSION_DATE_CONFLICT")
        void create_dateConflict_returns409() throws Exception {
            // Seed a closed version: 2026-01-01 → 2026-12-31
            seedVersion(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            // Attempt V2 with effectiveFrom inside V1's range
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePlanVersionRequest(planId, 2, LocalDate.of(2026, 6, 1), null, null, null, null, null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("E4 — unknown plan ID returns 404 PLAN_NOT_FOUND")
        void create_unknownPlan_returns404() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePlanVersionRequest(UUID.randomUUID(), 1, LocalDate.of(2026, 1, 1), null, null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("E5 — GET unknown version ID returns 404")
        void get_unknownId_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E6 — GET latest for plan with no versions returns 404")
        void getLatest_noVersions_returns404() throws Exception {
            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions/latest")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E7 — GET latest for unknown plan returns 404")
        void getLatest_unknownPlan_returns404() throws Exception {
            mockMvc.perform(get(PLANS_BASE + "/" + UUID.randomUUID() + "/versions/latest")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E8 — PATCH unknown version ID returns 404")
        void update_unknownId_returns404() throws Exception {
            mockMvc.perform(patch(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E9 — DELETE unknown version ID returns 404")
        void delete_unknownId_returns404() throws Exception {
            mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E10 — missing required field returns 422")
        void create_missingVersionNo_returns422() throws Exception {
            String body = """
                {"planId":"%s","effectiveFrom":"2026-01-01"}
                """.formatted(planId);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Soft-delete visibility ────────────────────────────────────────────────

    @Nested
    @DisplayName("Soft-delete visibility")
    class SoftDeleteVisibility {

        @Test
        @DisplayName("S1 — deleted version excluded from list, physical row retained")
        void softDelete_excludedFromList_physicalRowRetained() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(1, LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isCreated())
                .andReturn();
            String vId = extractId(created);

            // Soft-delete
            mockMvc.perform(delete(BASE + "/" + vId).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Not returned by list
            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

            // Physical row still present with deleted_at set
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_plan_versions WHERE id = ?",
                Integer.class, UUID.fromString(vId));
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("S2 — deleted version not returned by GET /versions/{id}")
        void softDelete_getReturns404() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq(1, LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isCreated())
                .andReturn();
            String vId = extractId(created);

            mockMvc.perform(delete(BASE + "/" + vId).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + vId).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("S3 — delete latest version; GET latest returns next-highest versionNo (not 404)")
        void softDelete_latest_nextVersionBecomeLatest() throws Exception {
            // Seed v1 (closed) and v2 (open) via port so no auto-close side-effects
            seedVersion(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            PlanVersion v2 = seedVersion(2, LocalDate.of(2027, 1, 1), null);

            // Verify latest is v2
            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions/latest")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));

            // Delete v2
            mockMvc.perform(delete(BASE + "/" + v2.getId()).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Latest is now v1
            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions/latest")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1));
        }
    }

    // ── List ordering ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("List ordering")
    class ListOrdering {

        @Test
        @DisplayName("O1 — 5 versions returned in strict descending versionNo order")
        void list_fiveVersions_returnedDescending() throws Exception {
            // Seed 5 versions directly via port (already-closed dates, no auto-close interference)
            seedVersion(1, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31));
            seedVersion(2, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31));
            seedVersion(3, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));
            seedVersion(4, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            seedVersion(5, LocalDate.of(2025, 1, 1), null);

            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].versionNo").value(5))
                .andExpect(jsonPath("$.data[1].versionNo").value(4))
                .andExpect(jsonPath("$.data[2].versionNo").value(3))
                .andExpect(jsonPath("$.data[3].versionNo").value(2))
                .andExpect(jsonPath("$.data[4].versionNo").value(1));
        }

        @Test
        @DisplayName("O2 — soft-deleting v5 leaves v4..v1 in descending order")
        void list_deleteHighest_remainingDescending() throws Exception {
            seedVersion(1, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31));
            seedVersion(2, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31));
            seedVersion(3, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));
            seedVersion(4, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            PlanVersion v5 = seedVersion(5, LocalDate.of(2025, 1, 1), null);

            // Delete v5
            mockMvc.perform(delete(BASE + "/" + v5.getId()).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(PLANS_BASE + "/" + planId + "/versions")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].versionNo").value(4))
                .andExpect(jsonPath("$.data[1].versionNo").value(3))
                .andExpect(jsonPath("$.data[2].versionNo").value(2))
                .andExpect(jsonPath("$.data[3].versionNo").value(1));
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request rejected")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — tenant user missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
