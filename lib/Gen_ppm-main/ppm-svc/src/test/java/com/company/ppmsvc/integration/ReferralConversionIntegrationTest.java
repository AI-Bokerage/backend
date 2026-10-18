package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.PriceQuoteRequest;
import com.company.ppmsvc.api.dto.request.ReferralConversionRequest;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.port.ReferralCodeRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for referral conversion —
 * {@code POST /api/v1/ppm/referrals/convert}. Also proves end-to-end reward
 * delivery: the issued coupon works via {@code POST /api/v1/ppm/quotes}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Referral Conversion — REST API (Integration)")
class ReferralConversionIntegrationTest extends AbstractContainerIntegrationTest {

    static final String CONVERT_URL = "/api/v1/ppm/referrals/convert";
    static final String QUOTE_URL   = "/api/v1/ppm/quotes";

    @Autowired MockMvc                       mockMvc;
    @Autowired ObjectMapper                  objectMapper;
    @Autowired PlanRepositoryPort            planRepository;
    @Autowired PlanPriceRepositoryPort       planPriceRepository;
    @Autowired PromotionRepositoryPort       promotionRepository;
    @Autowired ReferralProgramRepositoryPort programRepository;
    @Autowired ReferralCodeRepositoryPort    codeRepository;
    @Autowired PlanJpaRepository             planJpaRepository;
    @Autowired JdbcTemplate                  jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;
    String referralCode;

    @BeforeEach
    void seed() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-REFC-" + planId.toString().substring(0, 6))
            .slug("it-refc-" + planId.toString().substring(0, 6))
            .name("Referral Conversion Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

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

        UUID referrerRewardId = promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("Referrer flat 50 off")
            .action(new FlatDiscount(new BigDecimal("50")))
            .validFrom(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().plusDays(60))
            .status(PromotionStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        UUID referredRewardId = promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("Referred flat 30 off")
            .action(new FlatDiscount(new BigDecimal("30")))
            .validFrom(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().plusDays(60))
            .status(PromotionStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        UUID programId = programRepository.save(ReferralProgram.builder()
            .id(UUID.randomUUID())
            .name("Refer a friend")
            .referrerRewardPromotionId(referrerRewardId)
            .referredRewardPromotionId(referredRewardId)
            .status(ReferralProgramStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        referralCode = codeRepository.save(ReferralCode.builder()
            .id(UUID.randomUUID())
            .code("REFER-CONV").referralProgramId(programId).referrerCustomerId("referrer-1")
            .status(ReferralCodeStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getCode();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_referral_events");
        jdbcTemplate.execute("DELETE FROM ppm_referral_codes");
        jdbcTemplate.execute("DELETE FROM ppm_referral_programs");
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

    private String convertBody(String customerId) throws Exception {
        return objectMapper.writeValueAsString(new ReferralConversionRequest(referralCode, customerId));
    }

    @Test
    @DisplayName("first conversion succeeds; second same-customer conversion 409; different customer succeeds; " +
        "issued coupon works end-to-end via /quotes")
    void conversion_idempotencyAndEndToEndCouponDelivery() throws Exception {
        MvcResult converted = mockMvc.perform(post(CONVERT_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody("referred-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.couponCode").exists())
            .andReturn();

        String couponCode = objectMapper.readTree(converted.getResponse().getContentAsString())
            .path("data").path("couponCode").asText();

        // Same (code, customer) again -> 409
        mockMvc.perform(post(CONVERT_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody("referred-1")))
            .andExpect(status().isConflict());

        // Different referred customer -> 200
        mockMvc.perform(post(CONVERT_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody("referred-2")))
            .andExpect(status().isOk());

        // The issued coupon works end-to-end through the ordinary coupon quote path
        String quoteBody = objectMapper.writeValueAsString(new PriceQuoteRequest(
            planId, "india", "inr", BillingCycle.MONTHLY, couponCode, null));

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("valid"))
            .andExpect(jsonPath("$.data.discountAmount").value(50.0));
    }

    @Test
    @DisplayName("unknown referral code — 404")
    void conversion_unknownCode_returns404() throws Exception {
        mockMvc.perform(post(CONVERT_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReferralConversionRequest("NOEXIST", "referred-1"))))
            .andExpect(status().isNotFound());
    }
}
