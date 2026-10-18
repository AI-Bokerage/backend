package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.EntitlementController;
import com.company.ppmsvc.api.dto.request.CreateEntitlementRequest;
import com.company.ppmsvc.api.dto.request.UpdateEntitlementRequest;
import com.company.ppmsvc.api.dto.response.EntitlementResponse;
import com.company.ppmsvc.api.mapper.EntitlementApiMapper;
import com.company.ppmsvc.entitlement.usecase.EntitlementApplicationService;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
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
 * Slice test for {@link EntitlementController}.
 *
 * <p>Loads only the web layer. {@link EntitlementApplicationService} and
 * {@link EntitlementApiMapper} are mocked. No DB, no Testcontainers.
 */
@WebMvcTest(value = EntitlementController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class})
@DisplayName("EntitlementController")
class EntitlementControllerTest {

    static final String BASE           = "/api/v1/ppm/entitlements";
    static final UUID   ACTOR_ID       = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   ENTITLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean EntitlementApplicationService entitlementService;
    @MockitoBean EntitlementApiMapper           entitlementApiMapper;
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

    private Entitlement stubEntitlementDomain(UUID id, String code, EntitlementType type, boolean active) {
        Instant now = Instant.now();
        return Entitlement.builder()
            .id(id).version(0L).code(code).name(code + "-name").description(null)
            .type(type).active(active).createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private EntitlementResponse toResponse(Entitlement e) {
        return new EntitlementResponse(e.getId(), e.getCode(), e.getName(), e.getDescription(),
            e.getType(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
    }

    // ── POST /api/v1/ppm/entitlements ────────────────────────────────────────

    @Nested
    @DisplayName("POST /entitlements")
    class CreateEntitlement {

        @Test
        @DisplayName("201 — valid request creates entitlement")
        void create_valid_returns201() throws Exception {
            CreateEntitlementRequest req =
                new CreateEntitlementRequest("max_users", "Max Users", null, EntitlementType.QUOTA, null);
            Entitlement entitlement = stubEntitlementDomain(ENTITLEMENT_ID, "max_users", EntitlementType.QUOTA, true);

            when(entitlementService.createEntitlement(any(), any(), any(), any(), any(), any()))
                .thenReturn(entitlement);
            when(entitlementApiMapper.toResponse(entitlement)).thenReturn(toResponse(entitlement));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("max_users"))
                .andExpect(jsonPath("$.data.type").value("quota"));
        }

        @Test
        @DisplayName("422 — missing code returns validation error")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"name":"Max Users","type":"quota"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing type returns validation error")
        void create_missingType_returns422() throws Exception {
            String body = """
                {"code":"max_users","name":"Max Users"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict")
        void create_duplicateCode_returns409() throws Exception {
            CreateEntitlementRequest req =
                new CreateEntitlementRequest("max_users", "Max Users", null, EntitlementType.QUOTA, null);

            when(entitlementService.createEntitlement(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                    ErrorCode.ENTITLEMENT_CODE_ALREADY_EXISTS,
                    "An entitlement with code 'max_users' already exists."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PATCH /api/v1/ppm/entitlements/{id} ──────────────────────────────────

    @Nested
    @DisplayName("PATCH /entitlements/{id}")
    class UpdateEntitlement {

        @Test
        @DisplayName("200 — valid patch returns updated entitlement")
        void update_valid_returns200() throws Exception {
            UpdateEntitlementRequest req = new UpdateEntitlementRequest("Max Users v2", null, null);
            Entitlement entitlement = stubEntitlementDomain(ENTITLEMENT_ID, "max_users", EntitlementType.QUOTA, true);

            when(entitlementService.updateEntitlement(any(), eq(ENTITLEMENT_ID), any(), any(), any()))
                .thenReturn(entitlement);
            when(entitlementApiMapper.toResponse(entitlement)).thenReturn(toResponse(entitlement));

            mockMvc.perform(patch(BASE + "/{id}", ENTITLEMENT_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ENTITLEMENT_ID.toString()));
        }

        @Test
        @DisplayName("404 — unknown entitlement returns 404")
        void update_notFound_returns404() throws Exception {
            UpdateEntitlementRequest req = new UpdateEntitlementRequest("X", null, null);

            when(entitlementService.updateEntitlement(any(), eq(ENTITLEMENT_ID), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "Entitlement not found: " + ENTITLEMENT_ID));

            mockMvc.perform(patch(BASE + "/{id}", ENTITLEMENT_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/entitlements/code/{code} ─────────────────────────────

    @Nested
    @DisplayName("GET /entitlements/code/{code}")
    class GetByCode {

        @Test
        @DisplayName("200 — known code returns entitlement")
        void getByCode_found_returns200() throws Exception {
            Entitlement entitlement = stubEntitlementDomain(ENTITLEMENT_ID, "max_users", EntitlementType.QUOTA, true);

            when(entitlementService.getEntitlementByCode("max_users")).thenReturn(entitlement);
            when(entitlementApiMapper.toResponse(entitlement)).thenReturn(toResponse(entitlement));

            mockMvc.perform(get(BASE + "/code/max_users")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("max_users"));
        }

        @Test
        @DisplayName("404 — unknown code returns 404")
        void getByCode_notFound_returns404() throws Exception {
            when(entitlementService.getEntitlementByCode("unknown"))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "No entitlement with code: unknown"));

            mockMvc.perform(get(BASE + "/code/unknown")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/entitlements/{id} ────────────────────────────────────

    @Nested
    @DisplayName("GET /entitlements/{id}")
    class GetById {

        @Test
        @DisplayName("200 — found entitlement is returned")
        void getById_found_returns200() throws Exception {
            Entitlement entitlement = stubEntitlementDomain(ENTITLEMENT_ID, "max_users", EntitlementType.QUOTA, true);

            when(entitlementService.getEntitlement(ENTITLEMENT_ID)).thenReturn(entitlement);
            when(entitlementApiMapper.toResponse(entitlement)).thenReturn(toResponse(entitlement));

            mockMvc.perform(get(BASE + "/{id}", ENTITLEMENT_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ENTITLEMENT_ID.toString()));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_notFound_returns404() throws Exception {
            when(entitlementService.getEntitlement(ENTITLEMENT_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "Entitlement not found: " + ENTITLEMENT_ID));

            mockMvc.perform(get(BASE + "/{id}", ENTITLEMENT_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/ppm/entitlements ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /entitlements")
    class ListEntitlements {

        @Test
        @DisplayName("200 — no filter returns all entitlements")
        void list_noFilter_returnsAll() throws Exception {
            List<Entitlement> items = List.of(
                stubEntitlementDomain(UUID.randomUUID(), "max_users", EntitlementType.QUOTA,   true),
                stubEntitlementDomain(UUID.randomUUID(), "sso",       EntitlementType.BOOLEAN, true));

            when(entitlementService.listEntitlements(any(), any())).thenReturn(items);
            when(entitlementApiMapper.toResponseList(items))
                .thenReturn(items.stream().map(this::toResponse).toList());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?active=true filter is forwarded to service")
        void list_activeFilter_passesCriteriaToService() throws Exception {
            when(entitlementService.listEntitlements(any(), any())).thenReturn(List.of());
            when(entitlementApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(entitlementService).listEntitlements(eq(true), any());
        }

        @Test
        @DisplayName("200 — ?type=quota filter is converted by StringToEntitlementTypeConverter")
        void list_typeFilter_convertedAndForwarded() throws Exception {
            when(entitlementService.listEntitlements(any(), any())).thenReturn(List.of());
            when(entitlementApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?type=quota")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(entitlementService).listEntitlements(any(), eq(EntitlementType.QUOTA));
        }

        @Test
        @DisplayName("200 — combined ?active=false&type=boolean filters forwarded")
        void list_combinedFilters_passesCriteriaToService() throws Exception {
            when(entitlementService.listEntitlements(any(), any())).thenReturn(List.of());
            when(entitlementApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?active=false&type=boolean")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(entitlementService).listEntitlements(eq(false), eq(EntitlementType.BOOLEAN));
        }

        @Test
        @DisplayName("200 — empty catalog returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            when(entitlementService.listEntitlements(any(), any())).thenReturn(List.of());
            when(entitlementApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        private EntitlementResponse toResponse(Entitlement e) {
            return EntitlementControllerTest.this.toResponse(e);
        }
    }

    // ── DELETE /api/v1/ppm/entitlements/{id} ─────────────────────────────────

    @Nested
    @DisplayName("DELETE /entitlements/{id}")
    class DeleteEntitlement {

        @Test
        @DisplayName("204 — successful soft-delete returns no content")
        void delete_existing_returns204() throws Exception {
            doNothing().when(entitlementService).deleteEntitlement(any(), eq(ENTITLEMENT_ID));

            mockMvc.perform(delete(BASE + "/{id}", ENTITLEMENT_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(entitlementService).deleteEntitlement(any(), eq(ENTITLEMENT_ID));
        }

        @Test
        @DisplayName("404 — deleting unknown entitlement returns 404")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.ENTITLEMENT_NOT_FOUND, "Entitlement not found: " + ENTITLEMENT_ID))
                .when(entitlementService).deleteEntitlement(any(), eq(ENTITLEMENT_ID));

            mockMvc.perform(delete(BASE + "/{id}", ENTITLEMENT_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── Security ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("401 — unauthenticated request returns 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — authenticated but missing ppm.read permission returns 403")
        void missingPermission_returns403() throws Exception {
            when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildTenantUserAuth())))
                .andExpect(status().isForbidden());
        }
    }
}
