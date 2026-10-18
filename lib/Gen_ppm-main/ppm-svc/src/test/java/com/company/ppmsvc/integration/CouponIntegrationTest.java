package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreateCouponRequest;
import com.company.ppmsvc.api.dto.request.UpdateCouponRequest;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.promotion.model.FlatDiscount;
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
import org.springframework.test.web.servlet.MvcResult;

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
 * Full-stack integration tests for the Coupon catalog REST API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Coupon Catalog — REST API (Integration)")
class CouponIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/coupons";

    @Autowired MockMvc                 mockMvc;
    @Autowired ObjectMapper            objectMapper;
    @Autowired PromotionRepositoryPort promotionRepository;
    @Autowired CouponRepositoryPort    couponRepository;
    @Autowired JdbcTemplate            jdbcTemplate;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    UUID promotionId;

    @BeforeEach
    void seedPromotion() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        Instant now = Instant.now();
        Promotion promotion = promotionRepository.save(Promotion.builder()
            .id(UUID.randomUUID())
            .name("Coupon Test Promotion")
            .action(new FlatDiscount(new BigDecimal("50")))
            .validFrom(LocalDate.of(2025, 1, 1)).validUntil(LocalDate.of(2025, 12, 31))
            .status(PromotionStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
        promotionId = promotion.getId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_coupons");
        jdbcTemplate.execute("DELETE FROM ppm_promotions");
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

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("id").asText();
    }

    @Nested
    @DisplayName("E2E — full lifecycle")
    class HappyPath {

        @Test
        @DisplayName("POST -> GET(id) -> GET(code) -> LIST -> PATCH -> DELETE -> GET(404)")
        void fullLifecycle() throws Exception {
            MvcResult created = mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreateCouponRequest("diwali20", promotionId, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("DIWALI20"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
            String id = extractId(created);

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("DIWALI20"));

            mockMvc.perform(get(BASE + "/code/DIWALI20").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

            mockMvc.perform(patch(BASE + "/" + id)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new UpdateCouponRequest(null, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.code").value("DIWALI20"));

            mockMvc.perform(delete(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Uniqueness (BR)")
    class Uniqueness {

        @Test
        @DisplayName("second POST with the same code (any case) returns 409 COUPON_CODE_ALREADY_EXISTS")
        void create_duplicateCode_returns409() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreateCouponRequest("DUP20", promotionId, null))))
                .andExpect(status().isCreated());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreateCouponRequest("dup20", promotionId, null))))
                .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("POST with unknown promotionId returns 404 PROMOTION_NOT_FOUND")
        void create_unknownPromotion_returns404() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateCouponRequest("NOPROMO", UUID.randomUUID(), null))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /code/{code} for unknown code returns 404")
        void getByCode_unknownCode_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/code/NOSUCHCODE")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }
}
