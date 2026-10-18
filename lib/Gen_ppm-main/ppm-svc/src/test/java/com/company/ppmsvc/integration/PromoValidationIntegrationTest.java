package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.ValidatePromoCodeRequest;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the Promo Validation Engine (PPM-08).
 *
 * <p>Exercises: HTTP → Security → Controller → Service → JPA → PostgreSQL.
 * Plans and promo codes are seeded via domain ports in {@code @BeforeEach}.
 *
 * <p>Cleanup: native SQL clears mappings first, then promos, then plans to
 * respect FK constraints. {@code @SQLRestriction} bypass is not needed here
 * since all test promos are created fresh per test class (none are soft-deleted).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Promo Validation Engine — REST API (Integration)")
class PromoValidationIntegrationTest extends AbstractContainerIntegrationTest {

    static final String VALIDATE_URL = "/api/v1/ppm/promo-codes/validate";

    @Autowired MockMvc                   mockMvc;
    @Autowired ObjectMapper              objectMapper;
    @Autowired PlanRepositoryPort        planRepository;
    @Autowired PromoCodeRepositoryPort   promoCodeRepository;
    @Autowired PromoCodePlanRepositoryPort promoCodePlanRepository;
    @Autowired PlanJpaRepository         planJpaRepository;
    @Autowired JdbcTemplate              jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;

    @BeforeEach
    void seedPlan() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-PV-" + planId.toString().substring(0, 6))
            .slug("it-pv-" + planId.toString().substring(0, 6))
            .name("Promo Validation Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
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

    private PromoCode seedPromoCode(String code, LocalDate from, LocalDate until,
                                    boolean active, Integer usageCap, int usageCount) {
        Instant now = Instant.now();
        return promoCodeRepository.save(PromoCode.builder()
            .id(UUID.randomUUID())
            .code(code)
            .discountType(DiscountType.PERCENTAGE).value(new BigDecimal("20.00"))
            .validFrom(from).validUntil(until)
            .usageCap(usageCap).usageCount(usageCount)
            .firstTimeOnly(false).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    private String body(String code, UUID plan) throws Exception {
        return objectMapper.writeValueAsString(new ValidatePromoCodeRequest(code, plan));
    }

    // ── Valid flow ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid flow")
    class ValidFlow {

        @Test
        @DisplayName("V1 — unrestricted promo valid today returns valid=true with discount metadata")
        void validate_unrestrictedPromo_returnsValid() throws Exception {
            seedPromoCode("SAVE20", LocalDate.now().minusDays(30),
                LocalDate.now().plusDays(30), true, null, 0);

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SAVE20", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.reason").value("valid"))
                .andExpect(jsonPath("$.data.code").value("SAVE20"))
                .andExpect(jsonPath("$.data.discountType").value("percentage"))
                .andExpect(jsonPath("$.data.discountValue").value(20.0));
        }

        @Test
        @DisplayName("V2 — restricted promo with this plan eligible returns valid=true")
        void validate_restrictedPromo_thisPlanEligible_returnsValid() throws Exception {
            PromoCode promo = seedPromoCode("RESTRICT20", LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(60), true, null, 0);

            promoCodePlanRepository.save(PromoCodePlan.builder()
                .id(UUID.randomUUID()).promoCodeId(promo.getId()).planId(planId)
                .createdAt(Instant.now()).createdBy(DEV_USER)
                .build());

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("RESTRICT20", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.reason").value("valid"));
        }
    }

    // ── Invalid flow ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid flow")
    class InvalidFlow {

        @Test
        @DisplayName("I1 — expired promo returns valid=false reason=promo_expired")
        void validate_expiredPromo_returnsExpired() throws Exception {
            seedPromoCode("EXPIRED20", LocalDate.now().minusDays(60),
                LocalDate.now().minusDays(1), true, null, 0);

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("EXPIRED20", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("promo_expired"));
        }

        @Test
        @DisplayName("I2 — future promo returns valid=false reason=promo_not_started")
        void validate_futurePromo_returnsNotStarted() throws Exception {
            seedPromoCode("FUTURE20", LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(90), true, null, 0);

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("FUTURE20", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("promo_not_started"));
        }

        @Test
        @DisplayName("I3 — usage cap reached returns valid=false reason=usage_cap_reached")
        void validate_usageCapReached_returnsCapReached() throws Exception {
            seedPromoCode("CAPPED20", LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(60), true, 50, 50);

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("CAPPED20", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("usage_cap_reached"));
        }

        @Test
        @DisplayName("I4 — restricted promo with ineligible plan returns valid=false reason=plan_not_eligible")
        void validate_restrictedPromo_ineligiblePlan_returnsNotEligible() throws Exception {
            UUID otherPlanId = UUID.randomUUID();
            Instant now = Instant.now();
            planRepository.save(Plan.builder()
                .id(otherPlanId)
                .code("IT-PV2-" + otherPlanId.toString().substring(0, 6))
                .slug("it-pv2-" + otherPlanId.toString().substring(0, 6))
                .name("Other Plan")
                .visibility(PlanVisibility.PUBLIC)
                .trialDays(0).active(true)
                .createdAt(now).updatedAt(now)
                .build());

            PromoCode promo = seedPromoCode("EXCL20", LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(60), true, null, 0);

            // promo restricted to otherPlanId only
            promoCodePlanRepository.save(PromoCodePlan.builder()
                .id(UUID.randomUUID()).promoCodeId(promo.getId()).planId(otherPlanId)
                .createdAt(now).createdBy(DEV_USER)
                .build());

            // request planId is the main seeded plan — not in restriction list
            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("EXCL20", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("plan_not_eligible"));
        }

        @Test
        @DisplayName("I5 — inactive promo returns valid=false reason=promo_inactive")
        void validate_inactivePromo_returnsInactive() throws Exception {
            seedPromoCode("INACTIVE20", LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(60), false, null, 0);

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("INACTIVE20", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("promo_inactive"));
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("EP1 — unknown planId returns 404 PLAN_NOT_FOUND")
        void validate_unknownPlan_returns404() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SAVE20", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("EP2 — unknown promo code returns 200 valid=false PROMO_NOT_FOUND")
        void validate_unknownCode_returns200NotFound() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("NOEXIST", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("promo_not_found"))
                .andExpect(jsonPath("$.data.code").value("NOEXIST"));
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("200 — public endpoint accessible without authentication")
        void publicEndpoint_noAuthRequired_returns200() throws Exception {
            // SAVE20 doesn't exist in DB → service returns valid=false (PROMO_NOT_FOUND)
            // No auth required because /promo-codes/validate is a public catalog endpoint
            mockMvc.perform(post(VALIDATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SAVE20", planId)))
                .andExpect(status().isOk());
        }
    }
}
