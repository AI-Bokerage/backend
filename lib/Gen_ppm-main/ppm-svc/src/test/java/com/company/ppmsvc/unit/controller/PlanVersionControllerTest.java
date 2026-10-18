package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PlanVersionController;
import com.company.ppmsvc.api.dto.request.CreatePlanVersionRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanVersionRequest;
import com.company.ppmsvc.api.dto.response.PlanVersionResponse;
import com.company.ppmsvc.api.mapper.PlanVersionApiMapper;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.model.PlanVersionLimitsResponse;
import com.company.ppmsvc.plan.model.PlanVersionMetaResponse;
import com.company.ppmsvc.plan.usecase.PlanVersionApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * Slice test for {@link PlanVersionController}.
 *
 * <p>Loads only the web layer. {@link PlanVersionApplicationService} and
 * {@link PlanVersionApiMapper} are mocked. No DB, no Testcontainers.
 */
@WebMvcTest(value = PlanVersionController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PlanVersionController")
class PlanVersionControllerTest {

    static final String BASE       = "/api/v1/ppm/versions";
    static final String PLANS_BASE = "/api/v1/ppm/plans";

    static final UUID ACTOR_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static final LocalDate DATE_2026_JAN = LocalDate.of(2026, 1, 1);
    static final LocalDate DATE_2026_DEC = LocalDate.of(2026, 12, 31);

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PlanVersionApplicationService planVersionService;
    @MockitoBean PlanVersionApiMapper          apiMapper;
    @MockitoBean RedisRolePermissionResolver   rolePermissionResolver;
    @MockitoBean StringRedisTemplate           stringRedisTemplate;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, null, null, null,
            CpmsUserType.SUPER_ADMIN,
            "test-session", "test-jti",
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

    private PlanVersion buildVersion(UUID id, Integer versionNo,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {
        Instant now = Instant.now();
        return PlanVersion.builder()
            .id(id).version(0L)
            .planId(PLAN_ID)
            .versionNo(versionNo)
            .effectiveFrom(effectiveFrom)
            .effectiveTo(effectiveTo)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PlanVersionResponse stubResponse(PlanVersion v) {
        return new PlanVersionResponse(v.getId(), v.getPlanId(), v.getVersionNo(),
            v.getEffectiveFrom(), v.getEffectiveTo(), v.isActive(),
            v.getCreatedAt(), v.getUpdatedAt());
    }

    // ── POST /api/v1/ppm/versions ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /versions")
    class CreateVersion {

        @Test
        @DisplayName("201 — valid request creates version")
        void create_valid_returns201() throws Exception {
            CreatePlanVersionRequest req = new CreatePlanVersionRequest(
                PLAN_ID, 1, DATE_2026_JAN, null, null, null, null, null, null, null, null);
            PlanVersion saved = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);

            when(planVersionService.createVersion(eq(ACTOR_ID), eq(PLAN_ID), eq(1), eq(DATE_2026_JAN),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(saved);
            when(apiMapper.toResponse(saved)).thenReturn(stubResponse(saved));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.data.versionNo").value(1));
        }

        @Test
        @DisplayName("422 — missing planId returns validation error")
        void create_missingPlanId_returns422() throws Exception {
            String body = """
                {"versionNo":1,"effectiveFrom":"2026-01-01"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing effectiveFrom returns validation error")
        void create_missingEffectiveFrom_returns422() throws Exception {
            String body = """
                {"planId":"%s","versionNo":1}
                """.formatted(PLAN_ID);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void create_planNotFound_returns404() throws Exception {
            CreatePlanVersionRequest req = new CreatePlanVersionRequest(
                PLAN_ID, 1, DATE_2026_JAN, null, null, null, null, null, null, null, null);

            when(planVersionService.createVersion(any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("409 — duplicate version number returns conflict")
        void create_duplicateVersionNo_returns409() throws Exception {
            CreatePlanVersionRequest req = new CreatePlanVersionRequest(
                PLAN_ID, 1, DATE_2026_JAN, null, null, null, null, null, null, null, null);

            when(planVersionService.createVersion(any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                    ErrorCode.PLAN_VERSION_ALREADY_EXISTS, "Version already exists."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("409 — effective date conflict returns 409")
        void create_dateConflict_returns409() throws Exception {
            CreatePlanVersionRequest req = new CreatePlanVersionRequest(
                PLAN_ID, 2, LocalDate.of(2026, 6, 1), null, null, null, null, null, null, null, null);

            when(planVersionService.createVersion(any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                    ErrorCode.PLAN_VERSION_DATE_CONFLICT,
                    "effectiveFrom falls inside an existing version's range."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PATCH /api/v1/ppm/versions/{id} ──────────────────────────────────────

    @Nested
    @DisplayName("PATCH /versions/{id}")
    class UpdateVersion {

        @Test
        @DisplayName("200 — valid patch returns updated version")
        void update_valid_returns200() throws Exception {
            UpdatePlanVersionRequest req = new UpdatePlanVersionRequest(DATE_2026_DEC, null, null, null, null, null, null, null, null);
            PlanVersion saved = buildVersion(VERSION_ID, 1, DATE_2026_JAN, DATE_2026_DEC);

            when(planVersionService.updateVersion(eq(ACTOR_ID), eq(VERSION_ID), eq(DATE_2026_DEC),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(saved);
            when(apiMapper.toResponse(saved)).thenReturn(stubResponse(saved));

            mockMvc.perform(patch(BASE + "/{id}", VERSION_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.data.effectiveTo").value("2026-12-31"));
        }

        @Test
        @DisplayName("200 — empty body {} accepted (PATCH semantics)")
        void update_emptyBody_returns200() throws Exception {
            PlanVersion saved = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);

            when(planVersionService.updateVersion(any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);
            when(apiMapper.toResponse(saved)).thenReturn(stubResponse(saved));

            mockMvc.perform(patch(BASE + "/{id}", VERSION_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 — unknown version returns 404")
        void update_notFound_returns404() throws Exception {
            when(planVersionService.updateVersion(any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + VERSION_ID));

            mockMvc.perform(patch(BASE + "/{id}", VERSION_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/versions/{id} ────────────────────────────────────────

    @Nested
    @DisplayName("GET /versions/{id}")
    class GetById {

        @Test
        @DisplayName("200 — found version is returned")
        void getById_found_returns200() throws Exception {
            PlanVersion version = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);

            when(planVersionService.getVersion(VERSION_ID)).thenReturn(version);
            when(apiMapper.toResponse(version)).thenReturn(stubResponse(version));

            mockMvc.perform(get(BASE + "/{id}", VERSION_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(VERSION_ID.toString()));
        }

        @Test
        @DisplayName("404 — unknown version ID returns 404")
        void getById_notFound_returns404() throws Exception {
            when(planVersionService.getVersion(VERSION_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + VERSION_ID));

            mockMvc.perform(get(BASE + "/{id}", VERSION_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/plans/{planId}/versions ───────────────────────────────

    @Nested
    @DisplayName("GET /plans/{planId}/versions")
    class ListVersions {

        @Test
        @DisplayName("200 — returns all versions for the plan")
        void list_planId_returns200() throws Exception {
            PlanVersion v2 = buildVersion(UUID.randomUUID(), 2, LocalDate.of(2027, 1, 1), null);
            PlanVersion v1 = buildVersion(UUID.randomUUID(), 1, DATE_2026_JAN, DATE_2026_DEC);
            List<PlanVersion> versions = List.of(v2, v1);

            when(planVersionService.listVersions(PLAN_ID, null)).thenReturn(versions);
            when(apiMapper.toResponseList(versions)).thenReturn(
                versions.stream().map(this::toResp).toList());

            mockMvc.perform(get(PLANS_BASE + "/{planId}/versions", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));

            verify(planVersionService).listVersions(PLAN_ID, null);
        }

        @Test
        @DisplayName("200 — empty version list returns empty array")
        void list_empty_returnsEmptyList() throws Exception {
            when(planVersionService.listVersions(PLAN_ID, null)).thenReturn(List.of());
            when(apiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(PLANS_BASE + "/{planId}/versions", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        private PlanVersionResponse toResp(PlanVersion v) {
            return new PlanVersionResponse(v.getId(), v.getPlanId(), v.getVersionNo(),
                v.getEffectiveFrom(), v.getEffectiveTo(), v.isActive(),
                v.getCreatedAt(), v.getUpdatedAt());
        }
    }

    // ── GET /api/v1/ppm/plans/{planId}/versions/latest ────────────────────────

    @Nested
    @DisplayName("GET /plans/{planId}/versions/latest")
    class GetLatest {

        @Test
        @DisplayName("200 — returns highest versionNo for plan")
        void getLatest_found_returns200() throws Exception {
            PlanVersion version = buildVersion(VERSION_ID, 3, LocalDate.of(2028, 1, 1), null);

            when(planVersionService.getLatestVersion(PLAN_ID)).thenReturn(version);
            when(apiMapper.toResponse(version)).thenReturn(stubResponse(version));

            mockMvc.perform(get(PLANS_BASE + "/{planId}/versions/latest", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionNo").value(3));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void getLatest_planNotFound_returns404() throws Exception {
            when(planVersionService.getLatestVersion(PLAN_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID));

            mockMvc.perform(get(PLANS_BASE + "/{planId}/versions/latest", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — plan exists but no versions returns 404")
        void getLatest_noVersions_returns404() throws Exception {
            when(planVersionService.getLatestVersion(PLAN_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "No versions for plan: " + PLAN_ID));

            mockMvc.perform(get(PLANS_BASE + "/{planId}/versions/latest", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── DELETE /api/v1/ppm/versions/{id} ─────────────────────────────────────

    @Nested
    @DisplayName("DELETE /versions/{id}")
    class DeleteVersion {

        @Test
        @DisplayName("204 — successful soft-delete returns no content")
        void delete_existing_returns204() throws Exception {
            doNothing().when(planVersionService).deleteVersion(ACTOR_ID, VERSION_ID);

            mockMvc.perform(delete(BASE + "/{id}", VERSION_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(planVersionService).deleteVersion(ACTOR_ID, VERSION_ID);
        }

        @Test
        @DisplayName("404 — deleting unknown version returns 404")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + VERSION_ID))
                .when(planVersionService).deleteVersion(ACTOR_ID, VERSION_ID);

            mockMvc.perform(delete(BASE + "/{id}", VERSION_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/plan-versions/{id}/meta ──────────────────────────────

    @Nested
    @DisplayName("GET /plan-versions/{id}/meta (PPM-12A)")
    class GetPlanVersionMeta {

        static final String META_BASE = "/api/v1/ppm/plan-versions";

        @Test
        @DisplayName("200 — returns meta without authentication (public endpoint)")
        void getMeta_noAuth_returns200() throws Exception {
            PlanVersionMetaResponse meta = new PlanVersionMetaResponse(
                VERSION_ID, PLAN_ID, "PRO", 1, true, true, DATE_2026_JAN, null, null);

            when(planVersionService.getPlanVersionMeta(VERSION_ID)).thenReturn(meta);

            mockMvc.perform(get(META_BASE + "/{id}/meta", VERSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionId").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.data.planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.data.planCode").value("PRO"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.versionActive").value(true))
                .andExpect(jsonPath("$.data.planActive").value(true))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.data.effectiveTo").doesNotExist());
        }

        @Test
        @DisplayName("200 — also accessible when authenticated")
        void getMeta_authenticated_returns200() throws Exception {
            PlanVersionMetaResponse meta = new PlanVersionMetaResponse(
                VERSION_ID, PLAN_ID, "BASIC", 2, false, true, DATE_2026_JAN, DATE_2026_DEC, null);

            when(planVersionService.getPlanVersionMeta(VERSION_ID)).thenReturn(meta);

            mockMvc.perform(get(META_BASE + "/{id}/meta", VERSION_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("BASIC"))
                .andExpect(jsonPath("$.data.versionActive").value(false))
                .andExpect(jsonPath("$.data.effectiveTo").value("2026-12-31"));
        }

        @Test
        @DisplayName("404 — unknown version ID returns 404")
        void getMeta_notFound_returns404() throws Exception {
            when(planVersionService.getPlanVersionMeta(VERSION_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + VERSION_ID));

            mockMvc.perform(get(META_BASE + "/{id}/meta", VERSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/plan-versions/{id}/limits ─────────────────────────────

    @Nested
    @DisplayName("GET /plan-versions/{id}/limits (PPM-12B)")
    class GetPlanVersionLimits {

        static final String META_BASE = "/api/v1/ppm/plan-versions";

        @Test
        @DisplayName("200 — returns limits without authentication (public endpoint)")
        void getLimits_noAuth_returns200() throws Exception {
            PlanVersionLimitsResponse limits = new PlanVersionLimitsResponse(
                VERSION_ID, 50, 20, 10, 5_368_709_120L, true, false, false);

            when(planVersionService.getPlanVersionLimits(VERSION_ID)).thenReturn(limits);

            mockMvc.perform(get(META_BASE + "/{id}/limits", VERSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.planVersionId").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.data.maxInternalUsers").value(50))
                .andExpect(jsonPath("$.data.maxClientUsers").value(20))
                .andExpect(jsonPath("$.data.maxActiveProjects").value(10))
                .andExpect(jsonPath("$.data.storageQuotaBytes").value(5368709120L))
                .andExpect(jsonPath("$.data.customDomainEnabled").value(true))
                .andExpect(jsonPath("$.data.ssoEnabled").value(false))
                .andExpect(jsonPath("$.data.prioritySupport").value(false));
        }

        @Test
        @DisplayName("200 — null fields returned for unlimited plan (no auth)")
        void getLimits_unlimitedPlan_nullFields_returns200() throws Exception {
            PlanVersionLimitsResponse limits = new PlanVersionLimitsResponse(
                VERSION_ID, null, null, null, null, null, null, null);

            when(planVersionService.getPlanVersionLimits(VERSION_ID)).thenReturn(limits);

            mockMvc.perform(get(META_BASE + "/{id}/limits", VERSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxInternalUsers").doesNotExist())
                .andExpect(jsonPath("$.data.storageQuotaBytes").doesNotExist());
        }

        @Test
        @DisplayName("404 — unknown version ID returns 404")
        void getLimits_notFound_returns404() throws Exception {
            when(planVersionService.getPlanVersionLimits(VERSION_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + VERSION_ID));

            mockMvc.perform(get(META_BASE + "/{id}/limits", VERSION_ID))
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
            mockMvc.perform(get(BASE + "/" + VERSION_ID))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — authenticated but missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(BASE + "/" + VERSION_ID)
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
