package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PromoValidationController;
import com.company.ppmsvc.api.dto.request.ValidatePromoCodeRequest;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.promocode.model.PromoValidationReason;
import com.company.ppmsvc.promocode.model.PromoValidationResult;
import com.company.ppmsvc.promocode.usecase.PromoValidationService;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * Slice test for {@link PromoValidationController}.
 *
 * <p>Loads only the web layer. {@link PromoValidationService} is mocked — no DB.
 */
@WebMvcTest(value = PromoValidationController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PromoValidationController")
class PromoValidationControllerTest {

    static final String   VALIDATE_URL = "/api/v1/ppm/promo-codes/validate";
    static final UUID     ACTOR_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID     PLAN_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID     CODE_ID      = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PromoValidationService     promoValidationService;
    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;
    @MockitoBean StringRedisTemplate           stringRedisTemplate;


    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, null, null, null,
            CpmsUserType.SUPER_ADMIN, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private Authentication buildTenantUserAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, UUID.fromString("00000000-0000-0000-0000-000000000099"),
            "test-slug", UUID.fromString("00000000-0000-0000-0000-000000000088"),
            CpmsUserType.TENANT_USER, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));
    }

    private String body(String code, UUID planId) throws Exception {
        return objectMapper.writeValueAsString(new ValidatePromoCodeRequest(code, planId));
    }

    // ── Valid result ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid code")
    class ValidCode {

        @Test
        @DisplayName("200 — valid promo returns success payload with discount metadata")
        void validate_validCode_returns200WithDiscountMetadata() throws Exception {
            PromoValidationResult result = new PromoValidationResult(
                true, "SUMMER20", CODE_ID,
                DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                PromoValidationReason.VALID, VALID_UNTIL, false);

            when(promoValidationService.validate("SUMMER20", PLAN_ID)).thenReturn(result);

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SUMMER20", PLAN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.reason").value("valid"))
                .andExpect(jsonPath("$.data.code").value("SUMMER20"))
                .andExpect(jsonPath("$.data.discountType").value("percentage"))
                .andExpect(jsonPath("$.data.discountValue").value(20.00));
        }
    }

    // ── Invalid result ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid code")
    class InvalidCode {

        @Test
        @DisplayName("200 — invalid promo returns valid=false with reason")
        void validate_invalidCode_returns200WithValidFalse() throws Exception {
            PromoValidationResult result = new PromoValidationResult(
                false, "OLDCODE", null, null, null,
                PromoValidationReason.PROMO_EXPIRED, null, false);

            when(promoValidationService.validate("OLDCODE", PLAN_ID)).thenReturn(result);

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OLDCODE", PLAN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("promo_expired"))
                .andExpect(jsonPath("$.data.discountType").doesNotExist())
                .andExpect(jsonPath("$.data.discountValue").doesNotExist());
        }
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("404 — unknown plan propagates as PLAN_NOT_FOUND")
        void validate_unknownPlan_returns404() throws Exception {
            when(promoValidationService.validate(any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            mockMvc.perform(post(VALIDATE_URL)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SUMMER20", PLAN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("200 — public endpoint accessible without authentication")
        void publicEndpoint_noAuthRequired_returns200() throws Exception {
            PromoValidationResult result = new PromoValidationResult(
                true, "SUMMER20", CODE_ID, DiscountType.PERCENTAGE,
                new BigDecimal("20.00"), PromoValidationReason.VALID, VALID_UNTIL, false);
            when(promoValidationService.validate("SUMMER20", PLAN_ID)).thenReturn(result);

            mockMvc.perform(post(VALIDATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SUMMER20", PLAN_ID)))
                .andExpect(status().isOk());
        }
    }
}
