package com.company.ppmsvc.integration;

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
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
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
 * Full-stack integration test for {@code FixedPriceDiscount} via {@code
 * POST /api/v1/ppm/quotes}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Fixed-Price Discount — REST API (Integration)")
class FixedPriceIntegrationTest extends AbstractContainerIntegrationTest {

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

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
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

    private UUID seedPlanWithPrice(BigDecimal amount) {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-FIXP-" + planId.toString().substring(0, 6))
            .slug("it-fixp-" + planId.toString().substring(0, 6))
            .name("Fixed Price Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        planPriceRepository.save(PlanPrice.builder()
            .id(UUID.randomUUID())
            .planId(planId).cycle(BillingCycle.MONTHLY)
            .currency("INR").region("INDIA")
            .amount(amount)
            .taxInclusive(false)
            .effectiveFrom(LocalDate.of(2025, 1, 1))
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
        return planId;
    }

    private String seedFixedPriceCoupon(String code, BigDecimal fixedPrice) {
        Instant now = Instant.now();
        UUID promotionId = promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("Fixed price offer")
            .action(new FixedPriceDiscount(fixedPrice))
            .validFrom(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().plusDays(60))
            .status(PromotionStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        return couponRepository.save(Coupon.builder()
            .id(UUID.randomUUID())
            .code(code).promotionId(promotionId).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getCode();
    }

    @Test
    @DisplayName("₹2000 plan, fixed price ₹499 — finalAmount 499, discountAmount 1501")
    void quote_baseAboveFixedPrice_returnsCorrectDiscount() throws Exception {
        UUID planId = seedPlanWithPrice(new BigDecimal("2000.0000"));
        String code = seedFixedPriceCoupon("FIXED499", new BigDecimal("499"));

        String body = objectMapper.writeValueAsString(
            new PriceQuoteRequest(planId, "india", "inr", BillingCycle.MONTHLY, code, null));

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("valid"))
            .andExpect(jsonPath("$.data.finalAmount").value(499.0))
            .andExpect(jsonPath("$.data.discountAmount").value(1501.0))
            .andExpect(jsonPath("$.data.grantedEntitlement").doesNotExist());
    }

    @Test
    @DisplayName("₹300 plan, fixed price ₹499 — no discount, finalAmount stays 300")
    void quote_baseBelowFixedPrice_returnsNoDiscount() throws Exception {
        UUID planId = seedPlanWithPrice(new BigDecimal("300.0000"));
        String code = seedFixedPriceCoupon("FIXED499B", new BigDecimal("499"));

        String body = objectMapper.writeValueAsString(
            new PriceQuoteRequest(planId, "india", "inr", BillingCycle.MONTHLY, code, null));

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("valid"))
            .andExpect(jsonPath("$.data.finalAmount").value(300.0))
            .andExpect(jsonPath("$.data.discountAmount").value(0.0));
    }
}
