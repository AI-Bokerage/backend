package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PromoCodeController;
import com.company.ppmsvc.api.dto.request.CreatePromoCodeRequest;
import com.company.ppmsvc.api.dto.request.UpdatePromoCodeRequest;
import com.company.ppmsvc.api.dto.response.PromoCodeResponse;
import com.company.ppmsvc.api.mapper.PromoCodeApiMapper;
import com.company.ppmsvc.promocode.usecase.PromoCodeApplicationService;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocode.model.PromoCode;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link PromoCodeController}.
 *
 * <p>Loads only the web layer (controller + security + exception handler).
 * Both {@link PromoCodeApplicationService} and {@link PromoCodeApiMapper} are
 * mocked — no DB, no Testcontainers.
 */
@WebMvcTest(value = PromoCodeController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PromoCodeController")
class PromoCodeControllerTest {

    static final String BASE       = "/api/v1/ppm/promo-codes";
    static final UUID   ACTOR_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   CODE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PromoCodeApplicationService promoCodeService;
    @MockitoBean PromoCodeApiMapper          apiMapper;
    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;
    @MockitoBean StringRedisTemplate           stringRedisTemplate;


    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private PromoCodeResponse stubResponse(UUID id, String code, DiscountType type) {
        Instant now = Instant.now();
        return new PromoCodeResponse(id, code, type, new BigDecimal("20"),
            VALID_FROM, VALID_UNTIL, null, 0, false, true, now, now);
    }

    // ── POST /api/v1/ppm/promo-codes ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /promo-codes")
    class CreatePromoCode {

        @Test
        @DisplayName("201 — valid request creates promo code")
        void create_valid_returns201() throws Exception {
            CreatePromoCodeRequest req = new CreatePromoCodeRequest(
                "SUMMER20", DiscountType.PERCENTAGE, new BigDecimal("20"),
                VALID_FROM, VALID_UNTIL, null, null, null);
            PromoCode promoCode = Mockito.mock(PromoCode.class);
            PromoCodeResponse response = stubResponse(CODE_ID, "SUMMER20", DiscountType.PERCENTAGE);

            when(promoCodeService.createPromoCode(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(promoCode);
            when(apiMapper.toResponse(promoCode)).thenReturn(response);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SUMMER20"))
                .andExpect(jsonPath("$.data.type").value("percentage"));
        }

        @Test
        @DisplayName("422 — missing code field returns validation error")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"type":"percentage","value":20,"validFrom":"2025-01-01","validUntil":"2025-12-31"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — blank code fails @NotBlank validation")
        void create_blankCode_returns422() throws Exception {
            String body = """
                {"code":"  ","type":"percentage","value":20,"validFrom":"2025-01-01","validUntil":"2025-12-31"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("422 — missing type field returns validation error")
        void create_missingType_returns422() throws Exception {
            String body = """
                {"code":"SUMMER20","value":20,"validFrom":"2025-01-01","validUntil":"2025-12-31"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing validFrom returns validation error")
        void create_missingValidFrom_returns422() throws Exception {
            String body = """
                {"code":"SUMMER20","type":"flat","value":10,"validUntil":"2025-12-31"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — PERCENTAGE value > 100 triggers VALIDATION_ERROR from service")
        void create_percentageOver100_returns422() throws Exception {
            CreatePromoCodeRequest req = new CreatePromoCodeRequest(
                "OVER", DiscountType.PERCENTAGE, new BigDecimal("101"),
                VALID_FROM, VALID_UNTIL, null, null, null);

            when(promoCodeService.createPromoCode(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Percentage discount value must not exceed 100."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict")
        void create_duplicateCode_returns409() throws Exception {
            CreatePromoCodeRequest req = new CreatePromoCodeRequest(
                "SUMMER20", DiscountType.PERCENTAGE, new BigDecimal("20"),
                VALID_FROM, VALID_UNTIL, null, null, null);

            when(promoCodeService.createPromoCode(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PROMO_CODE_ALREADY_EXISTS,
                    "A promo code with code 'SUMMER20' already exists."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PATCH /api/v1/ppm/promo-codes/{id} ───────────────────────────────────

    @Nested
    @DisplayName("PATCH /promo-codes/{id}")
    class UpdatePromoCode {

        @Test
        @DisplayName("200 — valid patch returns updated promo code")
        void update_valid_returns200() throws Exception {
            UpdatePromoCodeRequest req = new UpdatePromoCodeRequest(
                DiscountType.FLAT, new BigDecimal("15"), null, null, null, null, null);
            PromoCode promoCode = Mockito.mock(PromoCode.class);
            PromoCodeResponse response = stubResponse(CODE_ID, "SUMMER20", DiscountType.FLAT);

            when(promoCodeService.updatePromoCode(any(), eq(CODE_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(promoCode);
            when(apiMapper.toResponse(promoCode)).thenReturn(response);

            mockMvc.perform(patch(BASE + "/{id}", CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("flat"));
        }

        @Test
        @DisplayName("200 — empty body {} accepted (PATCH semantics)")
        void update_emptyBody_returns200() throws Exception {
            PromoCode promoCode = Mockito.mock(PromoCode.class);
            PromoCodeResponse response = stubResponse(CODE_ID, "SUMMER20", DiscountType.PERCENTAGE);
            when(promoCodeService.updatePromoCode(any(), eq(CODE_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(promoCode);
            when(apiMapper.toResponse(promoCode)).thenReturn(response);

            mockMvc.perform(patch(BASE + "/{id}", CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 — promo code not found returns 404")
        void update_notFound_returns404() throws Exception {
            when(promoCodeService.updatePromoCode(any(), eq(CODE_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found: " + CODE_ID));

            mockMvc.perform(patch(BASE + "/{id}", CODE_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/promo-codes/{id} ─────────────────────────────────────

    @Nested
    @DisplayName("GET /promo-codes/{id}")
    class GetById {

        @Test
        @DisplayName("200 — found promo code is returned")
        void getById_found_returns200() throws Exception {
            PromoCode promoCode = Mockito.mock(PromoCode.class);
            PromoCodeResponse response = stubResponse(CODE_ID, "SUMMER20", DiscountType.PERCENTAGE);
            when(promoCodeService.getPromoCode(CODE_ID)).thenReturn(promoCode);
            when(apiMapper.toResponse(promoCode)).thenReturn(response);

            mockMvc.perform(get(BASE + "/{id}", CODE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(CODE_ID.toString()))
                .andExpect(jsonPath("$.data.code").value("SUMMER20"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_notFound_returns404() throws Exception {
            when(promoCodeService.getPromoCode(CODE_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found: " + CODE_ID));

            mockMvc.perform(get(BASE + "/{id}", CODE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/promo-codes/code/{code} ──────────────────────────────

    @Nested
    @DisplayName("GET /promo-codes/code/{code}")
    class GetByCode {

        @Test
        @DisplayName("200 — existing code returns promo code response")
        void getByCode_existing_returns200() throws Exception {
            PromoCode promoCode = Mockito.mock(PromoCode.class);
            PromoCodeResponse response = stubResponse(CODE_ID, "SUMMER20", DiscountType.PERCENTAGE);

            when(promoCodeService.getPromoCodeByCode("SUMMER20")).thenReturn(Optional.of(promoCode));
            when(apiMapper.toResponse(promoCode)).thenReturn(response);

            mockMvc.perform(get(BASE + "/code/SUMMER20")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SUMMER20"));
        }

        @Test
        @DisplayName("404 — missing code returns 404")
        void getByCode_missing_returns404() throws Exception {
            when(promoCodeService.getPromoCodeByCode("MISSING")).thenReturn(Optional.empty());

            mockMvc.perform(get(BASE + "/code/MISSING")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/promo-codes ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /promo-codes")
    class ListPromoCodes {

        @Test
        @DisplayName("200 — no filter returns all promo codes")
        void list_noFilter_returnsAll() throws Exception {
            List<PromoCode> promoCodes = List.of(
                Mockito.mock(PromoCode.class),
                Mockito.mock(PromoCode.class));
            List<PromoCodeResponse> items = List.of(
                stubResponse(UUID.randomUUID(), "FLAT10",  DiscountType.FLAT),
                stubResponse(UUID.randomUUID(), "PCT20",   DiscountType.PERCENTAGE));

            when(promoCodeService.listPromoCodes(any(), any())).thenReturn(promoCodes);
            when(apiMapper.toResponseList(promoCodes)).thenReturn(items);

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?active=true filter is forwarded to service as criteria")
        void list_activeFilter_passesCriteriaToService() throws Exception {
            when(promoCodeService.listPromoCodes(any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(promoCodeService).listPromoCodes(eq(true), any());
        }

        @Test
        @DisplayName("200 — ?type=flat filter is forwarded to service as criteria")
        void list_typeFilter_passesCriteriaToService() throws Exception {
            when(promoCodeService.listPromoCodes(any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?type=flat")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(promoCodeService).listPromoCodes(any(), eq(DiscountType.FLAT));
        }

        @Test
        @DisplayName("200 — empty catalog returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            when(promoCodeService.listPromoCodes(any(), any())).thenReturn(List.of());
            when(apiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── DELETE /api/v1/ppm/promo-codes/{id} ──────────────────────────────────

    @Nested
    @DisplayName("DELETE /promo-codes/{id}")
    class DeletePromoCode {

        @Test
        @DisplayName("204 — successful soft-delete returns no content")
        void delete_existing_returns204() throws Exception {
            doNothing().when(promoCodeService).deletePromoCode(any(), eq(CODE_ID));

            mockMvc.perform(delete(BASE + "/{id}", CODE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(promoCodeService).deletePromoCode(any(), eq(CODE_ID));
        }

        @Test
        @DisplayName("404 — deleting unknown promo code returns 404")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found: " + CODE_ID))
                .when(promoCodeService).deletePromoCode(any(), eq(CODE_ID));

            mockMvc.perform(delete(BASE + "/{id}", CODE_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request returns 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE + "/" + CODE_ID))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — authenticated but missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(BASE + "/" + CODE_ID)
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
