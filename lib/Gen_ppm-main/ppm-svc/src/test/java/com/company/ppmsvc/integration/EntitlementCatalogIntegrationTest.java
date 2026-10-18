package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreateEntitlementRequest;
import com.company.ppmsvc.api.dto.request.UpdateEntitlementRequest;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.EntitlementJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the Entitlement Catalog REST API (PPM-04 Phase 3).
 *
 * <p>Exercises the complete stack: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * Entitlements are seeded directly via the domain port. All HTTP assertions verify the
 * complete round-trip including soft-delete visibility, code uniqueness, and type filtering.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Entitlement Catalog — REST API (Integration)")
class EntitlementCatalogIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/entitlements";

    @Autowired MockMvc                   mockMvc;
    @Autowired ObjectMapper              objectMapper;
    @Autowired EntitlementRepositoryPort entitlementRepository;
    @Autowired EntitlementJpaRepository  entitlementJpaRepository;
    @Autowired JdbcTemplate              jdbcTemplate;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID entitlementId1;
    UUID entitlementId2;

    @BeforeEach
    void seedData() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        entitlementId1 = UUID.randomUUID();
        entitlementId2 = UUID.randomUUID();
        Instant now = Instant.now();

        entitlementRepository.save(Entitlement.builder()
            .id(entitlementId1)
            .code("max_users")
            .name("Max Users")
            .type(EntitlementType.QUOTA)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());

        entitlementRepository.save(Entitlement.builder()
            .id(entitlementId2)
            .code("sso_enabled")
            .name("SSO Enabled")
            .type(EntitlementType.BOOLEAN)
            .active(false)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    @AfterEach
    void cleanUp() {
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

    // ── E2E lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E2E — create, update, get, list, delete")
    class HappyPath {

        @Test
        @DisplayName("E1 — full lifecycle: create → update → get by code → list → soft-delete → verify gone")
        void fullLifecycle() throws Exception {
            // 1. Create
            CreateEntitlementRequest req = new CreateEntitlementRequest(
                "api_rate_limit", "API Rate Limit", null, EntitlementType.RATE_LIMIT, true);

            MvcResult createResult = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("api_rate_limit"))
                .andExpect(jsonPath("$.data.type").value("rate_limit"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

            String newId = objectMapper.readTree(
                createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

            // 2. Update name
            mockMvc.perform(patch(BASE + "/" + newId)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdateEntitlementRequest("API Rate Limit v2", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("API Rate Limit v2"))
                .andExpect(jsonPath("$.data.code").value("api_rate_limit")); // code immutable

            // 3. Get by code
            mockMvc.perform(get(BASE + "/code/api_rate_limit")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(newId));

            // 4. List — 3 entitlements (2 seeded + 1 created)
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

            // 5. Soft-delete
            mockMvc.perform(delete(BASE + "/" + newId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // 6. Get by ID after deletion → 404
            mockMvc.perform(get(BASE + "/" + newId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());

            // 7. List → back to 2
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    // ── Filter tests ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /entitlements — filter tests")
    class FilterTests {

        @Test
        @DisplayName("F1 — ?active=true returns only active entitlements")
        void list_activeTrue_returnsOnlyActive() throws Exception {
            mockMvc.perform(get(BASE + "?active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("max_users"));
        }

        @Test
        @DisplayName("F2 — ?active=false returns only inactive entitlements")
        void list_activeFalse_returnsOnlyInactive() throws Exception {
            mockMvc.perform(get(BASE + "?active=false")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("sso_enabled"));
        }

        @Test
        @DisplayName("F3 — ?type=quota returns only QUOTA entitlements")
        void list_typeQuota_returnsOnlyQuota() throws Exception {
            mockMvc.perform(get(BASE + "?type=quota")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("max_users"));
        }

        @Test
        @DisplayName("F4 — ?type=boolean returns only BOOLEAN entitlements")
        void list_typeBoolean_returnsOnlyBoolean() throws Exception {
            mockMvc.perform(get(BASE + "?type=boolean")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("sso_enabled"));
        }

        @Test
        @DisplayName("F5 — no filter returns all 2 seeded entitlements")
        void list_noFilter_returnsAll() throws Exception {
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("B1 — duplicate code on create returns 409 ENTITLEMENT_CODE_ALREADY_EXISTS")
        void create_duplicateCode_returns409() throws Exception {
            CreateEntitlementRequest req = new CreateEntitlementRequest(
                "max_users", "Duplicate", null, EntitlementType.QUOTA, null);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B2 — get by unknown ID returns 404 ENTITLEMENT_NOT_FOUND")
        void getById_unknown_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B3 — get by unknown code returns 404 ENTITLEMENT_NOT_FOUND")
        void getByCode_unknown_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/code/does_not_exist")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B4 — update unknown entitlement returns 404")
        void update_unknown_returns404() throws Exception {
            mockMvc.perform(patch(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdateEntitlementRequest("New Name", null, null))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B5 — create with missing required fields returns 422")
        void create_missingFields_returns422() throws Exception {
            String body = """
                {"name":"Incomplete","type":"quota"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("B6 — delete already-deleted entitlement returns 404")
        void delete_alreadyDeleted_returns404() throws Exception {
            // First delete
            mockMvc.perform(delete(BASE + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Second delete — should fail
            mockMvc.perform(delete(BASE + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B7 — code can be reused after soft-delete (existsByCode respects @SQLRestriction)")
        void create_sameCodeAfterSoftDelete_succeeds() throws Exception {
            // Soft-delete the existing max_users entitlement
            mockMvc.perform(delete(BASE + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Creating with the same code should now succeed — deleted_at IS NULL excludes the old row
            CreateEntitlementRequest req = new CreateEntitlementRequest(
                "max_users", "Max Users Reborn", null, EntitlementType.QUOTA, true);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("max_users"))
                .andExpect(jsonPath("$.data.name").value("Max Users Reborn"));
        }
    }

    // ── Soft-delete visibility ────────────────────────────────────────────────

    @Nested
    @DisplayName("Soft-delete visibility")
    class SoftDeleteVisibility {

        @Test
        @DisplayName("S1 — soft-deleted entitlement is hidden from list but record is retained in DB")
        void softDelete_hiddenFromListButRetainedInDb() throws Exception {
            // Soft-delete max_users
            mockMvc.perform(delete(BASE + "/" + entitlementId1)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Not returned by the list endpoint
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("sso_enabled"));

            // Row physically still exists in DB (deleted_at is set, but the record is not hard-deleted).
            // findById uses @SQLRestriction so we use a native count to bypass the filter.
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_entitlements WHERE id = ?",
                Integer.class, entitlementId1);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("S2 — type serialises as wire value (e.g. 'quota'), not enum name")
        void list_typeSerializesAsWireValue() throws Exception {
            MvcResult result = mockMvc.perform(get(BASE + "?type=quota")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode data = objectMapper.readTree(
                result.getResponse().getContentAsString()).path("data");
            String typeValue = data.get(0).path("type").asText();
            assertThat(typeValue).isEqualTo("quota");
        }
    }
}
