package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.PlanController;
import com.company.ppmsvc.api.dto.request.CreatePlanRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanRequest;
import com.company.ppmsvc.api.dto.response.PlanResponse;
import com.company.ppmsvc.api.mapper.PlanApiMapper;
import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Instant;
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
 * Slice test for {@link PlanController}.
 *
 * <p>Loads only the web layer (controller + security + exception handler).
 * {@link PlanApplicationService} and {@link PlanApiMapper} are mocked — no DB,
 * no Testcontainers, no framework-agnostic {@code ppm-core} wiring.
 *
 * <p>Spring Boot 4 {@code @WebMvcTest} requires {@link SecurityConfig} imported
 * explicitly and {@link OAuth2ResourceServerAutoConfiguration} excluded to avoid
 * a conflicting auto-configured security filter chain.
 */
@WebMvcTest(value = PlanController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("PlanController")
class PlanControllerTest {

    static final String BASE      = "/api/v1/ppm/plans";
    static final UUID   ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   PLAN_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean PlanApplicationService      planService;
    @MockitoBean PlanApiMapper                planApiMapper;
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
            CpmsUserType.SUPER_ADMIN,
            "test-session", "test-jti",
            Instant.now().plusSeconds(900)
        );
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }

    private Authentication buildTenantUserAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            ACTOR_ID, UUID.fromString("00000000-0000-0000-0000-000000000099"),
            "test-slug", UUID.fromString("00000000-0000-0000-0000-000000000088"),
            CpmsUserType.TENANT_USER, "test-session", "test-jti",
            Instant.now().plusSeconds(900));
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private Plan stubPlanDomain(UUID id, String code, String slug, String name, PlanVisibility visibility) {
        Instant now = Instant.now();
        return Plan.builder()
            .id(id).version(0L).code(code).slug(slug).name(name)
            .visibility(visibility).trialDays(0).active(true)
            .createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PlanResponse toResponse(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getCode(), plan.getSlug(), plan.getName(),
            plan.getTagline(), plan.getDescription(), plan.getVisibility(),
            plan.getTrialDays(), plan.isActive(), plan.getTier(), plan.getCreatedAt(), plan.getUpdatedAt());
    }

    // ── POST /api/v1/ppm/plans ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /plans")
    class CreatePlan {

        @Test
        @DisplayName("201 — valid request creates plan; response contains system-generated slug")
        void create_valid_returns201() throws Exception {
            CreatePlanRequest req = new CreatePlanRequest(
                "STARTER", "Starter", null, null, PlanVisibility.PUBLIC, null, null, null);
            Plan plan = stubPlanDomain(PLAN_ID, "STARTER", "PLN-0001", "Starter", PlanVisibility.PUBLIC);

            when(planService.createPlan(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(plan);
            when(planApiMapper.toResponse(plan)).thenReturn(toResponse(plan));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("STARTER"))
                .andExpect(jsonPath("$.data.slug").value("PLN-0001"))
                .andExpect(jsonPath("$.data.visibility").value("public"));
        }

        @Test
        @DisplayName("422 — missing code returns validation error")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"name":"Starter","visibility":"public"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — blank name fails @NotBlank validation")
        void create_blankName_returns422() throws Exception {
            String body = """
                {"code":"STARTER","name":"  ","visibility":"public"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict")
        void create_duplicateCode_returns409() throws Exception {
            CreatePlanRequest req = new CreatePlanRequest(
                "STARTER", "Starter", null, null, PlanVisibility.PUBLIC, null, null, null);

            when(planService.createPlan(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PLAN_CODE_ALREADY_EXISTS,
                    "A plan with code 'STARTER' already exists."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PATCH /api/v1/ppm/plans/{id} ─────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /plans/{id}")
    class UpdatePlan {

        @Test
        @DisplayName("200 — valid patch returns updated plan")
        void update_valid_returns200() throws Exception {
            UpdatePlanRequest req = new UpdatePlanRequest("Starter v2", null, null, null, null, null, null);
            Plan plan = stubPlanDomain(PLAN_ID, "STARTER", "PLN-0001", "Starter v2", PlanVisibility.PUBLIC);

            when(planService.updatePlan(any(), eq(PLAN_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(plan);
            when(planApiMapper.toResponse(plan)).thenReturn(toResponse(plan));

            mockMvc.perform(patch(BASE + "/{id}", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Starter v2"));
        }

        @Test
        @DisplayName("404 — plan not found returns 404")
        void update_notFound_returns404() throws Exception {
            UpdatePlanRequest req = new UpdatePlanRequest("X", null, null, null, null, null, null);

            when(planService.updatePlan(any(), eq(PLAN_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID));

            mockMvc.perform(patch(BASE + "/{id}", PLAN_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/plans/{id} ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /plans/{id}")
    class GetPlanById {

        @Test
        @DisplayName("200 — found plan is returned with system slug")
        void getById_found_returns200() throws Exception {
            Plan plan = stubPlanDomain(PLAN_ID, "GROWTH", "PLN-0002", "Growth", PlanVisibility.PUBLIC);
            when(planService.getPlan(PLAN_ID)).thenReturn(plan);
            when(planApiMapper.toResponse(plan)).thenReturn(toResponse(plan));

            mockMvc.perform(get(BASE + "/{id}", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.data.slug").value("PLN-0002"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_notFound_returns404() throws Exception {
            when(planService.getPlan(PLAN_ID))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            mockMvc.perform(get(BASE + "/{id}", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/ppm/plans/slug/{slug} ────────────────────────────────────

    @Nested
    @DisplayName("GET /plans/slug/{slug}")
    class GetPlanBySlug {

        @Test
        @DisplayName("200 — known PLN-XXXX slug returns plan")
        void getBySlug_found_returns200() throws Exception {
            Plan plan = stubPlanDomain(PLAN_ID, "STARTER", "PLN-0001", "Starter", PlanVisibility.PUBLIC);
            when(planService.getPlanBySlug("PLN-0001")).thenReturn(plan);
            when(planApiMapper.toResponse(plan)).thenReturn(toResponse(plan));

            mockMvc.perform(get(BASE + "/slug/PLN-0001")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("PLN-0001"));
        }

        @Test
        @DisplayName("404 — unknown slug returns 404")
        void getBySlug_notFound_returns404() throws Exception {
            when(planService.getPlanBySlug("PLN-9999"))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "No plan with slug: PLN-9999"));

            mockMvc.perform(get(BASE + "/slug/PLN-9999")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/plans ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /plans")
    class ListPlans {

        @Test
        @DisplayName("200 — no filter returns all plans")
        void list_noFilter_returnsAll() throws Exception {
            List<Plan> plans = List.of(
                stubPlanDomain(UUID.randomUUID(), "STARTER", "PLN-0001", "Starter", PlanVisibility.PUBLIC),
                stubPlanDomain(UUID.randomUUID(), "GROWTH",  "PLN-0002", "Growth",  PlanVisibility.PRIVATE));

            when(planService.listPlans(any(), any())).thenReturn(plans);
            when(planApiMapper.toResponseList(plans))
                .thenReturn(plans.stream().map(this::toResponse).toList());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?active=true filter is forwarded to service")
        void list_activeFilter_passesCriteriaToService() throws Exception {
            when(planService.listPlans(any(), any())).thenReturn(List.of());
            when(planApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(planService).listPlans(eq(true), any());
        }

        @Test
        @DisplayName("200 — ?visibility=public filter is forwarded to service")
        void list_visibilityFilter_passesCriteriaToService() throws Exception {
            when(planService.listPlans(any(), any())).thenReturn(List.of());
            when(planApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?visibility=public")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(planService).listPlans(any(), eq(PlanVisibility.PUBLIC));
        }

        @Test
        @DisplayName("200 — combined ?active=true&visibility=public filters forwarded to service")
        void list_combinedFilters_passesCriteriaToService() throws Exception {
            when(planService.listPlans(any(), any())).thenReturn(List.of());
            when(planApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?active=true&visibility=public")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(planService).listPlans(eq(true), eq(PlanVisibility.PUBLIC));
        }

        @Test
        @DisplayName("200 — empty catalog returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            when(planService.listPlans(any(), any())).thenReturn(List.of());
            when(planApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        private PlanResponse toResponse(Plan p) {
            return PlanControllerTest.this.toResponse(p);
        }
    }

    // ── Security ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated write request returns 401")
        void unauthenticated_returns401() throws Exception {
            // GET /plans is public — use POST (non-public) to verify auth is required
            mockMvc.perform(post(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — tenant user missing ppm.read on protected write returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            // PATCH /plans/{id} is not public — the access filter rejects it before
            // the request ever reaches the (mocked) service.
            mockMvc.perform(patch(BASE + "/" + PLAN_ID)
                    .with(authentication(buildTenantUserAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    // ── DELETE /api/v1/ppm/plans/{id} ─────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /plans/{id}")
    class DeletePlan {

        @Test
        @DisplayName("204 — successful soft-delete returns no content")
        void delete_existing_returns204() throws Exception {
            doNothing().when(planService).deletePlan(any(), eq(PLAN_ID));

            mockMvc.perform(delete(BASE + "/{id}", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(planService).deletePlan(any(), eq(PLAN_ID));
        }

        @Test
        @DisplayName("404 — deleting unknown plan returns 404")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID))
                .when(planService).deletePlan(any(), eq(PLAN_ID));

            mockMvc.perform(delete(BASE + "/{id}", PLAN_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }
}
