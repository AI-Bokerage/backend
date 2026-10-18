package com.company.ppmsvc.unit.controller;

import com.company.ppmsvc.api.controller.AddOnController;
import com.company.ppmsvc.api.converter.StringToAddOnTypeConverter;
import com.company.ppmsvc.api.dto.request.CreateAddOnRequest;
import com.company.ppmsvc.api.dto.request.UpdateAddOnRequest;
import com.company.ppmsvc.api.dto.response.AddOnResponse;
import com.company.ppmsvc.api.mapper.AddOnApiMapper;
import com.company.ppmsvc.addon.usecase.AddOnApplicationService;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.config.PpmAuthorizationConfig;
import com.company.ppmsvc.config.SecurityConfig;
import com.company.ppmsvc.addon.model.AddOnType;
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
 * Slice test for {@link AddOnController}.
 *
 * <p>Loads only the web layer (controller + security + exception handler).
 * {@link AddOnApplicationService} and {@link AddOnApiMapper} are mocked — no DB,
 * no Testcontainers, no {@code ppm-core} wiring.
 *
 * <p>{@link StringToAddOnTypeConverter} is imported so the ?type=feature
 * query param binding works correctly in the list endpoint test.
 */
@WebMvcTest(value = AddOnController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, PpmAuthorizationConfig.class, StringToAddOnTypeConverter.class})
@DisplayName("AddOnController")
class AddOnControllerTest {

    static final String BASE      = "/api/v1/ppm/add-ons";
    static final UUID   ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID   ADD_ON_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean AddOnApplicationService                                  addOnService;
    @MockitoBean AddOnApiMapper                                            addOnApiMapper;
    @MockitoBean com.company.ppmsvc.addonprice.usecase.AddOnPriceApplicationService addOnPriceService;
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

    private AddOn stubAddOnDomain(UUID id, String code, AddOnType type, boolean active) {
        Instant now = Instant.now();
        return AddOn.builder()
            .id(id).version(0L).code(code).name("Add-On " + code).description("Desc")
            .type(type).active(active).createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private AddOnResponse toResponse(AddOn a) {
        return new AddOnResponse(a.getId(), a.getCode(), a.getName(), a.getDescription(),
            a.getType(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt());
    }

    // ── POST /api/v1/ppm/add-ons ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /add-ons")
    class CreateAddOn {

        @Test
        @DisplayName("201 — valid request creates add-on")
        void create_valid_returns201() throws Exception {
            CreateAddOnRequest req = new CreateAddOnRequest(
                "EXTRA_USERS_10", "Extra Users 10", "10 extra user seats", AddOnType.QUOTA, null);
            AddOn addOn = stubAddOnDomain(ADD_ON_ID, "EXTRA_USERS_10", AddOnType.QUOTA, true);

            when(addOnService.createAddOn(any(), any(), any(), any(), any(), any())).thenReturn(addOn);
            when(addOnApiMapper.toResponse(addOn)).thenReturn(toResponse(addOn));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("EXTRA_USERS_10"))
                .andExpect(jsonPath("$.data.type").value("quota"));
        }

        @Test
        @DisplayName("422 — missing required code field returns validation error")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"name":"Extra Users","type":"quota","active":true}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing required type field returns validation error")
        void create_missingType_returns422() throws Exception {
            String body = """
                {"code":"CODE","name":"Name","active":true}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict from service")
        void create_duplicateCode_returns409() throws Exception {
            CreateAddOnRequest req = new CreateAddOnRequest(
                "DUPLICATE", "Duplicate", null, AddOnType.FEATURE, null);

            when(addOnService.createAddOn(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.ADD_ON_CODE_ALREADY_EXISTS,
                    "An add-on with code 'DUPLICATE' already exists."));

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PATCH /api/v1/ppm/add-ons/{id} ───────────────────────────────────────

    @Nested
    @DisplayName("PATCH /add-ons/{id}")
    class UpdateAddOn {

        @Test
        @DisplayName("200 — valid patch returns updated add-on")
        void update_valid_returns200() throws Exception {
            UpdateAddOnRequest req = new UpdateAddOnRequest("Updated Name", null, false);
            AddOn addOn = stubAddOnDomain(ADD_ON_ID, "CODE", AddOnType.FEATURE, false);

            when(addOnService.updateAddOn(any(), eq(ADD_ON_ID), any(), any(), any())).thenReturn(addOn);
            when(addOnApiMapper.toResponse(addOn)).thenReturn(toResponse(addOn));

            mockMvc.perform(patch(BASE + "/{id}", ADD_ON_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
        }

        @Test
        @DisplayName("404 — add-on not found returns 404")
        void update_notFound_returns404() throws Exception {
            when(addOnService.updateAddOn(any(), eq(ADD_ON_ID), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.ADD_ON_NOT_FOUND, "Add-on not found: " + ADD_ON_ID));

            mockMvc.perform(patch(BASE + "/{id}", ADD_ON_ID)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new UpdateAddOnRequest(null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/add-ons/{id} ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /add-ons/{id}")
    class GetAddOn {

        @Test
        @DisplayName("200 — found add-on is returned")
        void getById_found_returns200() throws Exception {
            AddOn addOn = stubAddOnDomain(ADD_ON_ID, "EXTRA_STORAGE", AddOnType.QUOTA, true);
            when(addOnService.getAddOn(ADD_ON_ID)).thenReturn(addOn);
            when(addOnApiMapper.toResponse(addOn)).thenReturn(toResponse(addOn));

            mockMvc.perform(get(BASE + "/{id}", ADD_ON_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ADD_ON_ID.toString()))
                .andExpect(jsonPath("$.data.code").value("EXTRA_STORAGE"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_notFound_returns404() throws Exception {
            when(addOnService.getAddOn(ADD_ON_ID))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.ADD_ON_NOT_FOUND, "Add-on not found"));

            mockMvc.perform(get(BASE + "/{id}", ADD_ON_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/ppm/add-ons/code/{code} ──────────────────────────────────

    @Nested
    @DisplayName("GET /add-ons/code/{code}")
    class GetAddOnByCode {

        @Test
        @DisplayName("200 — known code returns add-on")
        void getByCode_known_returns200() throws Exception {
            AddOn addOn = stubAddOnDomain(ADD_ON_ID, "EXTRA_USERS_10", AddOnType.QUOTA, true);
            when(addOnService.getAddOnByCode("EXTRA_USERS_10")).thenReturn(addOn);
            when(addOnApiMapper.toResponse(addOn)).thenReturn(toResponse(addOn));

            mockMvc.perform(get(BASE + "/code/EXTRA_USERS_10")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("EXTRA_USERS_10"));
        }

        @Test
        @DisplayName("404 — unknown code returns 404 from service")
        void getByCode_unknown_returns404() throws Exception {
            when(addOnService.getAddOnByCode(any()))
                .thenThrow(new ResourceNotFoundException(
                    ErrorCode.ADD_ON_NOT_FOUND, "No add-on with code: UNKNOWN"));

            mockMvc.perform(get(BASE + "/code/UNKNOWN")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/v1/ppm/add-ons ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /add-ons")
    class ListAddOns {

        @Test
        @DisplayName("200 — no filter returns all add-ons")
        void list_noFilter_returnsAll() throws Exception {
            List<AddOn> addOns = List.of(
                stubAddOnDomain(UUID.randomUUID(), "EXTRA_USERS", AddOnType.QUOTA, true),
                stubAddOnDomain(UUID.randomUUID(), "PREMIUM_SUPPORT", AddOnType.SERVICE, true));

            when(addOnService.listAddOns(any(), any())).thenReturn(addOns);
            when(addOnApiMapper.toResponseList(addOns))
                .thenReturn(addOns.stream().map(this::toResponse).toList());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?active=true filter is forwarded to service")
        void list_activeFilter_forwardedToService() throws Exception {
            when(addOnService.listAddOns(any(), any())).thenReturn(List.of());
            when(addOnApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(addOnService).listAddOns(eq(true), any());
        }

        @Test
        @DisplayName("200 — ?type=feature filter is bound via StringToAddOnTypeConverter")
        void list_typeFilter_featureIsBoundViaConverter() throws Exception {
            when(addOnService.listAddOns(any(), any())).thenReturn(List.of());
            when(addOnApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "?type=feature")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk());

            verify(addOnService).listAddOns(any(), eq(AddOnType.FEATURE));
        }

        @Test
        @DisplayName("400 — ?type=invalid returns bad request (converter throws IllegalArgumentException)")
        void list_invalidType_returns400() throws Exception {
            mockMvc.perform(get(BASE + "?type=not_a_real_type")
                    .with(authentication(buildAuth())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("200 — empty catalog returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            when(addOnService.listAddOns(any(), any())).thenReturn(List.of());
            when(addOnApiMapper.toResponseList(List.of())).thenReturn(List.of());

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }

        private AddOnResponse toResponse(AddOn a) {
            return AddOnControllerTest.this.toResponse(a);
        }
    }

    // ── DELETE /api/v1/ppm/add-ons/{id} ──────────────────────────────────────

    @Nested
    @DisplayName("DELETE /add-ons/{id}")
    class DeleteAddOn {

        @Test
        @DisplayName("204 — successful soft-delete returns no content")
        void delete_existing_returns204() throws Exception {
            doNothing().when(addOnService).deleteAddOn(any(), eq(ADD_ON_ID));

            mockMvc.perform(delete(BASE + "/{id}", ADD_ON_ID)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            verify(addOnService).deleteAddOn(any(), eq(ADD_ON_ID));
        }

        @Test
        @DisplayName("404 — deleting unknown add-on returns 404")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException(
                ErrorCode.ADD_ON_NOT_FOUND, "Add-on not found: " + ADD_ON_ID))
                .when(addOnService).deleteAddOn(any(), eq(ADD_ON_ID));

            mockMvc.perform(delete(BASE + "/{id}", ADD_ON_ID)
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
