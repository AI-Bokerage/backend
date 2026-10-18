package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CustomerContextRequest;
import com.company.ppmsvc.api.dto.request.ReferralQuoteRequest;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.promotion.model.EligibilityCondition;
import com.company.ppmsvc.promotion.model.EligibilityType;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Referral Pricing Engine —
 * {@code POST /api/v1/ppm/referrals/quote}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Referral Quote — REST API (Integration)")
class ReferralQuoteIntegrationTest extends AbstractContainerIntegrationTest {

    static final String QUOTE_URL = "/api/v1/ppm/referrals/quote";

    @Autowired MockMvc                    mockMvc;
    @Autowired ObjectMapper               objectMapper;
    @Autowired PlanRepositoryPort         planRepository;
    @Autowired PlanPriceRepositoryPort    planPriceRepository;
    @Autowired PromotionRepositoryPort    promotionRepository;
    @Autowired ReferralProgramRepositoryPort programRepository;
    @Autowired ReferralCodeRepositoryPort    codeRepository;
    @Autowired PlanJpaRepository          planJpaRepository;
    @Autowired JdbcTemplate               jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID planId;

    @BeforeEach
    void seed() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("IT-REFQ-" + planId.toString().substring(0, 6))
            .slug("it-refq-" + planId.toString().substring(0, 6))
            .name("Referral Quote Test Plan")
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
    }

    @AfterEach
    void cleanUp() {
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

    private String seedReferralCode(ReferralCodeStatus status) {
        Instant now = Instant.now();
        UUID rewardPromotionId = promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("Referred customer 10% off")
            .action(new PercentageDiscount(new BigDecimal("10"), null, null))
            .validFrom(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().plusDays(60))
            .status(PromotionStatus.ACTIVE)
            .conditions(List.of(new EligibilityCondition(EligibilityType.NEW_CUSTOMER)))
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        UUID referrerRewardPromotionId = promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("Referrer reward")
            .action(new PercentageDiscount(new BigDecimal("10"), null, null))
            .validFrom(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().plusDays(60))
            .status(PromotionStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        UUID programId = programRepository.save(ReferralProgram.builder()
            .id(UUID.randomUUID())
            .name("Refer a friend")
            .referrerRewardPromotionId(referrerRewardPromotionId)
            .referredRewardPromotionId(rewardPromotionId)
            .status(ReferralProgramStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        return codeRepository.save(ReferralCode.builder()
            .id(UUID.randomUUID())
            .code("REFER-NAMAN").referralProgramId(programId).referrerCustomerId("referrer-1")
            .status(status)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getCode();
    }

    private String body(String referralCode, boolean isNewCustomer) throws Exception {
        return objectMapper.writeValueAsString(new ReferralQuoteRequest(
            planId, "india", "inr", BillingCycle.MONTHLY, referralCode,
            new CustomerContextRequest("cust-1", isNewCustomer)));
    }

    @Test
    @DisplayName("new-customer context — 200 valid with discount")
    void quote_newCustomer_returnsValid() throws Exception {
        String code = seedReferralCode(ReferralCodeStatus.ACTIVE);

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("valid"))
            .andExpect(jsonPath("$.data.discountAmount").value(100.0))
            .andExpect(jsonPath("$.data.conditionsSkipped").value(false));
    }

    @Test
    @DisplayName("existing-customer context — 200 eligibility_violation")
    void quote_existingCustomer_returnsEligibilityViolation() throws Exception {
        String code = seedReferralCode(ReferralCodeStatus.ACTIVE);

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("eligibility_violation"));
    }

    @Test
    @DisplayName("unknown referral code — 200 referral_code_not_found")
    void quote_unknownCode_returnsCodeNotFound() throws Exception {
        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NOEXIST", true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("referral_code_not_found"));
    }

    @Test
    @DisplayName("inactive referral code — 200 referral_code_inactive")
    void quote_inactiveCode_returnsCodeInactive() throws Exception {
        String code = seedReferralCode(ReferralCodeStatus.INACTIVE);

        mockMvc.perform(post(QUOTE_URL)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reason").value("referral_code_inactive"));
    }
}
