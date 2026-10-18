package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CustomerContextRequest;
import com.company.ppmsvc.api.dto.request.PriceQuoteRequest;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PlanRestrictionCondition;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
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
 * Full-stack integration test for Phase 1's plan-restriction condition,
 * driven entirely through {@code POST /api/v1/ppm/quotes} with a {@code
 * customerContext} supplied.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Promotion Conditions — Plan Restriction (Integration)")
class ConditionIntegrationTest extends AbstractContainerIntegrationTest {

    static final String QUOTE_URL = "/api/v1/ppm/quotes";

    @Autowired MockMvc                 mockMvc;
    @Autowired ObjectMapper            objectMapper;
    @Autowired PlanRepositoryPort      planRepository;
    @Autowired PlanPriceRepositoryPort planPriceRepository;
    @Autowired PromotionRepositoryPort promotionRepository;
    @Autowired CouponRepositoryPort    couponRepository;
    @Autowired PlanJpaRepository       planJpaRepository;
    @Autowired JdbcTemplate            jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID allowedPlanId;
    UUID otherPlanId;

    @BeforeEach
    void seed() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        allowedPlanId = seedPlan("ALLOWED");
        otherPlanId   = seedPlan("OTHER");
        seedPrice(allowedPlanId);
        seedPrice(otherPlanId);
    }

    private UUID seedPlan(String suffix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(id)
            .code("IT-COND-" + suffix + "-" + id.toString().substring(0, 6))
            .slug("it-cond-" + suffix.toLowerCase() + "-" + id.toString().substring(0, 6))
            .name("Condition Test Plan " + suffix)
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());
        return id;
    }

    private void seedPrice(UUID planId) {
        Instant now = Instant.now();
        planPriceRepository.save(PlanPrice.builder()
            .id(UUID.randomUUID())
            .planId(planId).cycle(BillingCycle.MONTHLY)
            .currency("INR").region("INDIA")
            .amount(new BigDecimal("1000.0000"))
            .taxInclusive(false)
            .effectiveFrom(LocalDate.of(2025, 1, 1))
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_coupons");
        jdbcTemplate.execute("DELETE FROM ppm_promotions");
        jdbcTemplate.execute("DELETE FROM ppm_plan_prices");
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

    private UUID seedRestrictedPromotion() {
        Instant now = Instant.now();
        UUID promotionId = promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("Restricted to ALLOWED plan")
            .action(new FlatDiscount(new BigDecimal("100")))
            .validFrom(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().plusDays(60))
            .status(PromotionStatus.ACTIVE)
            .conditions(List.of(new PlanRestrictionCondition(Set.of(allowedPlanId))))
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        couponRepository.save(Coupon.builder()
            .id(UUID.randomUUID())
            .code("RESTRICT10").promotionId(promotionId).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
        return promotionId;
    }

    private String body(UUID planId) throws Exception {
        return objectMapper.writeValueAsString(new PriceQuoteRequest(
            planId, "india", "inr", BillingCycle.MONTHLY, "RESTRICT10",
            new CustomerContextRequest("cust-1", false)));
    }

    @Test
    @DisplayName("wrong plan — 200 plan_not_eligible")
    void quote_wrongPlan_returnsPlanNotEligible() throws Exception {
        seedRestrictedPromotion();

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(otherPlanId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("plan_not_eligible"))
            .andExpect(jsonPath("$.data.conditionsSkipped").value(false));
    }

    @Test
    @DisplayName("allowed plan — 200 valid with discount applied")
    void quote_allowedPlan_returnsValid() throws Exception {
        seedRestrictedPromotion();

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(allowedPlanId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("valid"))
            .andExpect(jsonPath("$.data.discountAmount").value(100.0));
    }
}
