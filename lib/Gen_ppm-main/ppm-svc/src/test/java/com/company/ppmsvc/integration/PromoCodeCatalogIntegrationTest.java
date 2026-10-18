package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreatePromoCodeRequest;
import com.company.ppmsvc.api.dto.request.UpdatePromoCodeRequest;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.infrastructure.persistence.repository.PromoCodeJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.math.BigDecimal;
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
 * Full-stack integration tests for the Promo Code Catalog REST API (PPM-07 Phase 3).
 *
 * <p>Exercises the complete stack: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 *
 * <p>Cleanup: native SQL bypasses {@code @SQLRestriction} so soft-deleted rows are removed
 * correctly. Promo code plans are deleted before promo codes to respect FK constraints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Promo Code Catalog — REST API (Integration)")
class PromoCodeCatalogIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/promo-codes";

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Autowired MockMvc             mockMvc;
    @Autowired ObjectMapper        objectMapper;
    @Autowired PromoCodeJpaRepository promoCodeJpaRepository;
    @Autowired JdbcTemplate        jdbcTemplate;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_promo_code_plans");
        jdbcTemplate.execute("DELETE FROM ppm_promo_codes");
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

    private CreatePromoCodeRequest createReq(String code, DiscountType type, BigDecimal value) {
        return new CreatePromoCodeRequest(code, type, value, VALID_FROM, VALID_UNTIL, null, null, null);
    }

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("id").asText();
    }

    // ── E2E lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E2E — full lifecycle")
    class HappyPath {

        @Test
        @DisplayName("L1 — POST → GET(id) → GET(code) → LIST → PATCH → DELETE → GET(404)")
        void fullLifecycle() throws Exception {
            // 1. Create
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("SUMMER20", DiscountType.PERCENTAGE, new BigDecimal("20")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SUMMER20"))
                .andExpect(jsonPath("$.data.type").value("percentage"))
                .andReturn();
            String id = extractId(created);

            // 2. GET by ID
            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.code").value("SUMMER20"));

            // 3. GET by code
            mockMvc.perform(get(BASE + "/code/SUMMER20").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("SUMMER20"));

            // 4. LIST — contains our promo code
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("SUMMER20"));

            // 5. PATCH — change to FLAT discount
            mockMvc.perform(patch(BASE + "/" + id)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdatePromoCodeRequest(DiscountType.FLAT, new BigDecimal("50"), null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("flat"))
                .andExpect(jsonPath("$.data.value").value(50))
                .andExpect(jsonPath("$.data.code").value("SUMMER20")); // code is immutable

            // 6. DELETE
            mockMvc.perform(delete(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // 7. GET after delete → 404
            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── Code normalisation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Code normalization (BR-2)")
    class CodeNormalization {

        @Test
        @DisplayName("N1 — lowercase input is stored and returned as uppercase")
        void create_lowercaseInput_storedUppercase() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("  summer20  ", DiscountType.FLAT, new BigDecimal("10")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SUMMER20"));
        }

        @Test
        @DisplayName("N2 — second POST with different casing of same code is rejected as duplicate")
        void create_differentCaseDuplicate_returns409() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("WINTER10", DiscountType.FLAT, new BigDecimal("10")))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("winter10", DiscountType.FLAT, new BigDecimal("5")))))
                .andExpect(status().isConflict());
        }
    }

    // ── Future validity window ────────────────────────────────────────────────

    @Nested
    @DisplayName("Future validity window (C3)")
    class FutureValidity {

        @Test
        @DisplayName("F1 — POST with future validity window is accepted")
        void create_futureWindow_accepted() throws Exception {
            CreatePromoCodeRequest req = new CreatePromoCodeRequest(
                "FUTURE30", DiscountType.FLAT, new BigDecimal("5"),
                LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), null, null, null);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.validFrom").value("2030-01-01"))
                .andExpect(jsonPath("$.data.validUntil").value("2030-12-31"));
        }

        @Test
        @DisplayName("F2 — Future-dated promo code can be retrieved, listed, and updated without restriction")
        void future_createGetListUpdate() throws Exception {
            LocalDate futureFrom  = LocalDate.of(2030, 6, 1);
            LocalDate futureUntil = LocalDate.of(2030, 6, 30);

            // Create
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("FUT2030", DiscountType.PERCENTAGE, new BigDecimal("10"),
                            futureFrom, futureUntil, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
            String id = extractId(created);

            // GET by id
            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validFrom").value("2030-06-01"))
                .andExpect(jsonPath("$.data.validUntil").value("2030-06-30"));

            // LIST — future-dated entry visible
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("FUT2030"));

            // UPDATE — change validUntil to further future
            mockMvc.perform(patch(BASE + "/" + id)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdatePromoCodeRequest(null, null, null, LocalDate.of(2030, 12, 31), null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validUntil").value("2030-12-31"))
                .andExpect(jsonPath("$.data.validFrom").value("2030-06-01")); // immutable in this update
        }
    }

    // ── Duplicate code ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Duplicate code (BR-1)")
    class DuplicateCode {

        @Test
        @DisplayName("D1 — second POST with the same code returns 409 PROMO_CODE_ALREADY_EXISTS")
        void create_duplicateCode_returns409() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("DUP20", DiscountType.PERCENTAGE, new BigDecimal("20")))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("DUP20", DiscountType.FLAT, new BigDecimal("10")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── List filters ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("List filters")
    class ListFilters {

        @Test
        @DisplayName("LF1 — ?active=true returns only active promo codes")
        void list_activeFilter_returnsOnlyActive() throws Exception {
            // Create one active (default) and one explicitly inactive
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("ACTIVE1", DiscountType.FLAT, new BigDecimal("10"),
                            VALID_FROM, VALID_UNTIL, null, null, true))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("INACTIVE1", DiscountType.FLAT, new BigDecimal("10"),
                            VALID_FROM, VALID_UNTIL, null, null, false))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?active=true").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("ACTIVE1"));
        }

        @Test
        @DisplayName("LF2 — ?type=percentage returns only PERCENTAGE promo codes")
        void list_typeFilter_returnsOnlyMatchingType() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("PCT10", DiscountType.PERCENTAGE, new BigDecimal("10")))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("FLAT5", DiscountType.FLAT, new BigDecimal("5")))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?type=percentage").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("percentage"));
        }

        @Test
        @DisplayName("LF2b — ?active=false returns only inactive promo codes")
        void list_activeFalseFilter_returnsOnlyInactive() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("ACTIVE2", DiscountType.FLAT, new BigDecimal("10"),
                            VALID_FROM, VALID_UNTIL, null, null, true))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("INACTIVE2", DiscountType.FLAT, new BigDecimal("10"),
                            VALID_FROM, VALID_UNTIL, null, null, false))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?active=false").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("INACTIVE2"));
        }

        @Test
        @DisplayName("LF4 — ?type=flat returns only FLAT promo codes")
        void list_typeFlatFilter_returnsOnlyFlat() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("PCT11", DiscountType.PERCENTAGE, new BigDecimal("10")))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("FLAT6", DiscountType.FLAT, new BigDecimal("5")))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?type=flat").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("flat"));
        }

        @Test
        @DisplayName("LF3 — ?active=true&type=percentage combined filter narrows to matching promo codes")
        void list_combinedFilter_narrowsCorrectly() throws Exception {
            // active + percentage
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("APCT", DiscountType.PERCENTAGE, new BigDecimal("15"),
                            VALID_FROM, VALID_UNTIL, null, null, true))))
                .andExpect(status().isCreated());

            // active + flat
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("AFLT", DiscountType.FLAT, new BigDecimal("10"),
                            VALID_FROM, VALID_UNTIL, null, null, true))))
                .andExpect(status().isCreated());

            // inactive + percentage
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreatePromoCodeRequest("IPCT", DiscountType.PERCENTAGE, new BigDecimal("5"),
                            VALID_FROM, VALID_UNTIL, null, null, false))))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?active=true&type=percentage")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("APCT"));
        }
    }

    // ── Soft-delete visibility ────────────────────────────────────────────────

    @Nested
    @DisplayName("Soft-delete visibility")
    class SoftDeleteVisibility {

        @Test
        @DisplayName("S1 — deleted promo code excluded from list; physical row retained in DB")
        void softDelete_excludedFromList_physicalRowRetained() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("TODEL", DiscountType.FLAT, new BigDecimal("5")))))
                .andExpect(status().isCreated())
                .andReturn();
            String id = extractId(created);

            mockMvc.perform(delete(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Not in list
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

            // Physical row present with deleted_at set
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_promo_codes WHERE id = ?",
                Integer.class, UUID.fromString(id));
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("S2 — GET /{id} after soft-delete returns 404")
        void softDelete_getByIdReturns404() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("TODEL2", DiscountType.FLAT, new BigDecimal("5")))))
                .andExpect(status().isCreated())
                .andReturn();
            String id = extractId(created);

            mockMvc.perform(delete(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("S3 — GET /code/{code} after soft-delete returns 404")
        void softDelete_getByCodeReturns404() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("TODEL3", DiscountType.FLAT, new BigDecimal("5")))))
                .andExpect(status().isCreated());

            // Fetch the ID
            String id = objectMapper.readTree(
                mockMvc.perform(get(BASE + "/code/TODEL3").with(authentication(buildAuth())))
                    .andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString())
                .path("data").path("id").asText();

            mockMvc.perform(delete(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/code/TODEL3").with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("S4 — same code can be recreated after soft-delete (partial unique index allows reuse)")
        void softDelete_sameCodeCanBeRecreated() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("REUSE", DiscountType.FLAT, new BigDecimal("5")))))
                .andExpect(status().isCreated())
                .andReturn();
            String firstId = extractId(created);

            mockMvc.perform(delete(BASE + "/" + firstId).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Recreate same code — must succeed
            MvcResult recreated = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq("REUSE", DiscountType.PERCENTAGE, new BigDecimal("10")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("REUSE"))
                .andExpect(jsonPath("$.data.type").value("percentage"))
                .andReturn();

            String secondId = extractId(recreated);
            assertThat(secondId).isNotEqualTo(firstId);
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("E1 — GET unknown UUID returns 404")
        void get_unknownId_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E2 — GET /code/{code} for unknown code returns 404")
        void getByCode_unknownCode_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/code/NOSUCHCODE")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E3 — PATCH unknown UUID returns 404")
        void patch_unknownId_returns404() throws Exception {
            mockMvc.perform(patch(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E4 — DELETE unknown UUID returns 404")
        void delete_unknownId_returns404() throws Exception {
            mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E5 — POST missing code returns 422")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"type":"flat","value":10,"validFrom":"2025-01-01","validUntil":"2025-12-31"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("E6 — POST missing validFrom returns 422")
        void create_missingValidFrom_returns422() throws Exception {
            String body = """
                {"code":"X","type":"flat","value":10,"validUntil":"2025-12-31"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("E7 — POST PERCENTAGE value > 100 returns 422 VALIDATION_ERROR")
        void create_percentageOver100_returns422() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        createReq("OVER100", DiscountType.PERCENTAGE, new BigDecimal("101")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
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
