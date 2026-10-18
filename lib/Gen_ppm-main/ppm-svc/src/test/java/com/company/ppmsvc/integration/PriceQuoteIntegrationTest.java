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
import com.company.ppmsvc.promotion.model.PercentageDiscount;
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
 * Full-stack integration tests for the Promotion Pricing Engine — seeds a
 * plan, price, promotion, and coupon via domain ports, then drives the
 * pipeline entirely through {@code POST /api/v1/ppm/quotes}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Price Quotes — REST API (Integration)")
class PriceQuoteIntegrationTest extends AbstractContainerIntegrationTest {

    static final String QUOTE_URL = "/api/v1/ppm/quotes";

    @Autowired MockMvc                   mockMvc;
    @Autowired ObjectMapper              objectMapper;
    @Autowired PlanRepositoryPort        planRepository;
    @Autowired PlanPriceRepositoryPort   planPriceRepository;
    @Autowired PromotionRepositoryPort   promotionRepository;
    @Autowired CouponRepositoryPort      couponRepository;
    @Autowired PlanJpaRepository         planJpaRepository;
    @Autowired JdbcTemplate              jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;

    @BeforeEach
    void seedPlanAndPrice() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-PQ-" + planId.toString().substring(0, 6))
            .slug("it-pq-" + planId.toString().substring(0, 6))
            .name("Price Quote Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        planPriceRepository.save(PlanPrice.builder()
            .id(UUID.randomUUID())
            .planId(planId).cycle(BillingCycle.MONTHLY)
            .currency("INR").region("INDIA")
            .amount(new BigDecimal("2000.0000"))
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

    private UUID seedPromotion(PromotionStatus status, LocalDate from, LocalDate until) {
        Instant now = Instant.now();
        return promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("20% capped at 200")
            .action(new PercentageDiscount(new BigDecimal("20"), new BigDecimal("200"), null))
            .validFrom(from).validUntil(until)
            .status(status)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();
    }

    private Coupon seedCoupon(String code, UUID promotionId, boolean active) {
        Instant now = Instant.now();
        return couponRepository.save(Coupon.builder()
            .id(UUID.randomUUID())
            .code(code).promotionId(promotionId).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    private String body(UUID plan, String couponCode) throws Exception {
        return objectMapper.writeValueAsString(
            new PriceQuoteRequest(plan, "india", "inr", BillingCycle.MONTHLY, couponCode, null));
    }

    @Nested
    @DisplayName("Valid flow")
    class ValidFlow {

        @Test
        @DisplayName("20% capped at ₹200 promotion — raw would be 400, capped discount is 200")
        void quote_cappedPercentage_returnsCappedDiscount() throws Exception {
            UUID promotionId = seedPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(60));
            seedCoupon("DIWALI20", promotionId, true);

            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId, "DIWALI20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("valid"))
                .andExpect(jsonPath("$.data.baseAmount").value(2000.0))
                .andExpect(jsonPath("$.data.discountAmount").value(200.0))
                .andExpect(jsonPath("$.data.finalAmount").value(1800.0))
                .andExpect(jsonPath("$.data.promotionId").value(promotionId.toString()));
        }

        @Test
        @DisplayName("no couponCode — returns no_coupon with finalAmount == baseAmount")
        void quote_noCoupon_returnsNoCoupon() throws Exception {
            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("no_coupon"))
                .andExpect(jsonPath("$.data.finalAmount").value(2000.0))
                .andExpect(jsonPath("$.data.discountAmount").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Invalid coupon/promotion reasons")
    class InvalidFlow {

        @Test
        @DisplayName("unknown coupon code — returns 200 coupon_not_found")
        void quote_unknownCoupon_returnsCouponNotFound() throws Exception {
            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId, "NOEXIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("coupon_not_found"));
        }

        @Test
        @DisplayName("inactive coupon — returns 200 coupon_inactive")
        void quote_inactiveCoupon_returnsCouponInactive() throws Exception {
            UUID promotionId = seedPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(60));
            seedCoupon("OFFCODE", promotionId, false);

            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId, "OFFCODE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("coupon_inactive"));
        }

        @Test
        @DisplayName("promotion in DRAFT status — returns 200 promotion_inactive")
        void quote_draftPromotion_returnsPromotionInactive() throws Exception {
            UUID promotionId = seedPromotion(PromotionStatus.DRAFT,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(60));
            seedCoupon("STAGED20", promotionId, true);

            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId, "STAGED20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("promotion_inactive"));
        }

        @Test
        @DisplayName("promotion not yet started — returns 200 promotion_not_started")
        void quote_futurePromotion_returnsNotStarted() throws Exception {
            UUID promotionId = seedPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(90));
            seedCoupon("FUTURE20", promotionId, true);

            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId, "FUTURE20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("promotion_not_started"));
        }

        @Test
        @DisplayName("expired promotion — returns 200 promotion_expired")
        void quote_expiredPromotion_returnsExpired() throws Exception {
            UUID promotionId = seedPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1));
            seedCoupon("EXPIRED20", promotionId, true);

            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(planId, "EXPIRED20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("promotion_expired"));
        }
    }

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("unknown planId returns 404 PLAN_NOT_FOUND")
        void quote_unknownPlan_returns404() throws Exception {
            mockMvc.perform(post(QUOTE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(UUID.randomUUID(), null)))
                .andExpect(status().isNotFound());
        }
    }
}
