package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.AssignPromoPlansRequest;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
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
 * Full-stack integration tests for Promo Code ↔ Plan Restrictions REST API (PPM-07 Phase 3).
 *
 * <p>Exercises: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * A plan and a promo code are seeded via domain ports in {@code @BeforeEach}.
 *
 * <p>Cleanup: native SQL bypasses FK and {@code @SQLRestriction}. Ordering:
 * mappings first, then promo codes, then plans.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Promo Code Plan Restrictions — REST API (Integration)")
class PromoCodePlanIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/promo-codes";

    @Autowired MockMvc               mockMvc;
    @Autowired ObjectMapper          objectMapper;
    @Autowired PlanRepositoryPort    planRepository;
    @Autowired PromoCodeRepositoryPort promoCodeRepository;
    @Autowired PlanJpaRepository     planJpaRepository;
    @Autowired JdbcTemplate          jdbcTemplate;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID planId1;
    UUID planId2;
    UUID promoCodeId;

    @BeforeEach
    void seedData() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId1     = UUID.randomUUID();
        planId2     = UUID.randomUUID();
        promoCodeId = UUID.randomUUID();
        Instant now = Instant.now();

        planRepository.save(Plan.builder()
            .id(planId1)
            .code("IT-PCPLAN-1-" + planId1.toString().substring(0, 6))
            .slug("it-pcpl1-" + planId1.toString().substring(0, 6))
            .name("Promo Plan Integration 1")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        planRepository.save(Plan.builder()
            .id(planId2)
            .code("IT-PCPLAN-2-" + planId2.toString().substring(0, 6))
            .slug("it-pcpl2-" + planId2.toString().substring(0, 6))
            .name("Promo Plan Integration 2")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        promoCodeRepository.save(PromoCode.builder()
            .id(promoCodeId)
            .code("ITCODE-" + promoCodeId.toString().substring(0, 6).toUpperCase())
            .discountType(DiscountType.FLAT)
            .value(new BigDecimal("10.00"))
            .validFrom(LocalDate.of(2025, 1, 1))
            .validUntil(LocalDate.of(2025, 12, 31))
            .usageCap(null).usageCount(0)
            .firstTimeOnly(false).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_promo_code_plans");
        jdbcTemplate.execute("DELETE FROM ppm_promo_codes");
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

    private String assignUrl()   { return BASE + "/" + promoCodeId + "/plans"; }
    private String removeUrl(UUID planId) { return BASE + "/" + promoCodeId + "/plans/" + planId; }

    private String body(UUID... planIds) throws Exception {
        return objectMapper.writeValueAsString(new AssignPromoPlansRequest(Set.of(planIds)));
    }

    // ── Assignment lifecycle ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Assignment lifecycle")
    class AssignmentLifecycle {

        @Test
        @DisplayName("A1 — POST assign one plan returns mapping with correct planId")
        void assign_onePlan_returnsMappingWithPlanId() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].planId").value(planId1.toString()))
                .andExpect(jsonPath("$.data[0].promoCodeId").value(promoCodeId.toString()));
        }

        @Test
        @DisplayName("A2 — GET restrictions returns the assigned plan")
        void list_afterAssign_returnsOnePlan() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            mockMvc.perform(get(assignUrl()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].planId").value(planId1.toString()));
        }

        @Test
        @DisplayName("A3 — POST assign a second plan appends to restrictions")
        void assign_secondPlan_appendsToList() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId2)))
                .andExpect(status().isOk());

            mockMvc.perform(get(assignUrl()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("A4 — GET before any assignment returns empty list")
        void list_noAssignments_returnsEmptyList() throws Exception {
            mockMvc.perform(get(assignUrl()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── Replace lifecycle ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replace lifecycle")
    class ReplaceLifecycle {

        @Test
        @DisplayName("R1 — PUT replaces existing restriction set with new one")
        void replace_swapsOldForNew() throws Exception {
            // Seed plan1 restriction
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            // Replace with plan2
            mockMvc.perform(put(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].planId").value(planId2.toString()));
        }

        @Test
        @DisplayName("R2 — GET after replace returns only the new plan")
        void list_afterReplace_containsOnlyNewPlan() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            mockMvc.perform(put(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId2)))
                .andExpect(status().isOk());

            mockMvc.perform(get(assignUrl()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].planId").value(planId2.toString()));
        }
    }

    // ── Mapping deletion ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mapping deletion")
    class MappingDeletion {

        @Test
        @DisplayName("MD1 — DELETE specific plan restriction returns 204")
        void remove_existingMapping_returns204() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            mockMvc.perform(delete(removeUrl(planId1)).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("MD2 — GET after DELETE returns empty list; promo code and plan still exist")
        void list_afterDelete_isEmpty() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            mockMvc.perform(delete(removeUrl(planId1)).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(assignUrl()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

            // Promo code and plan still exist (no cascade)
            Integer promoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_promo_codes WHERE id = ?",
                Integer.class, promoCodeId);
            Integer planCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_plans WHERE id = ?",
                Integer.class, planId1);
            org.assertj.core.api.Assertions.assertThat(promoCount).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(planCount).isEqualTo(1);
        }

        @Test
        @DisplayName("MD3 — DELETE non-existent mapping returns 404 PROMO_CODE_PLAN_MAPPING_NOT_FOUND")
        void remove_nonExistentMapping_returns404() throws Exception {
            mockMvc.perform(delete(removeUrl(planId1)).with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("EP1 — POST assign to unknown promoCodeId returns 404")
        void assign_unknownPromoCode_returns404() throws Exception {
            String unknownUrl = BASE + "/" + UUID.randomUUID() + "/plans";

            mockMvc.perform(post(unknownUrl)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("EP2 — POST assign unknown planId returns 404")
        void assign_unknownPlan_returns404() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("EP3 — POST duplicate assignment returns 409 PROMO_CODE_PLAN_ALREADY_ASSIGNED")
        void assign_duplicate_returns409() throws Exception {
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("EP4 — PUT replace with invalid plan aborts before deleting existing restrictions (abort-before-delete)")
        void replace_invalidPlan_abortsWithoutDeletingExisting() throws Exception {
            // Assign plan1 first
            mockMvc.perform(post(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId1)))
                .andExpect(status().isOk());

            // Attempt replace with an unknown plan — must abort before delete
            mockMvc.perform(put(assignUrl())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(UUID.randomUUID())))
                .andExpect(status().isNotFound());

            // plan1 restriction must still exist
            mockMvc.perform(get(assignUrl()).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].planId").value(planId1.toString()));
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request rejected")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(assignUrl()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — tenant user missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(assignUrl())
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
