package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreateAddOnRequest;
import com.company.ppmsvc.api.dto.request.UpdateAddOnRequest;
import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.infrastructure.persistence.repository.AddOnJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
 * Full-stack integration tests for the Add-On Catalog REST API (PPM-11 Phase 3).
 *
 * <p>Extends {@link AbstractContainerIntegrationTest} for a shared Postgres
 * Testcontainers instance.  Exercises the complete stack:
 * HTTP → Security → Controller → Service → JPA → PostgreSQL.
 *
 * <p>Each test class owns its cleanup via {@code @AfterEach}. Tests are
 * ordered such that the create endpoint is exercised first so subsequent
 * tests can extract an ID for further operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Add-On Catalog — REST API (Integration)")
class AddOnCatalogIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/add-ons";

    @Autowired MockMvc          mockMvc;
    @Autowired ObjectMapper     objectMapper;
    @Autowired AddOnJpaRepository addOnJpaRepository;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    @AfterEach
    void cleanUp() {
        addOnJpaRepository.deleteAll();
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

    private String createAddOn(String code, AddOnType type, Boolean active) throws Exception {
        CreateAddOnRequest req = new CreateAddOnRequest(code, "Add-On " + code, "Desc", type, active);
        return mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /add-ons")
    class CreateAddOn {

        @Test
        @DisplayName("201 — creates add-on and returns persisted data with normalised code")
        void create_valid_returns201WithPersistedData() throws Exception {
            CreateAddOnRequest req = new CreateAddOnRequest(
                " extra_users_10 ", "Extra Users 10", "10 extra seats", AddOnType.QUOTA, null);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.code").value("EXTRA_USERS_10"))
                .andExpect(jsonPath("$.data.type").value("quota"))
                .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict")
        void create_duplicateCode_returns409() throws Exception {
            createAddOn("UNIQUE_CODE", AddOnType.FEATURE, true);

            CreateAddOnRequest dup = new CreateAddOnRequest(
                "UNIQUE_CODE", "Duplicate", null, AddOnType.FEATURE, null);
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing code fails validation")
        void create_missingCode_returns422() throws Exception {
            String body = """
                {"name":"Missing Code","type":"quota","active":true}
                """;
            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("422 — missing type fails validation")
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
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /add-ons/{id}")
    class UpdateAddOn {

        @Test
        @DisplayName("200 — updates name and active, type and code are preserved")
        void update_valid_returns200() throws Exception {
            String createResponse = createAddOn("PATCH_CODE", AddOnType.SERVICE, true);
            String addOnId = objectMapper.readTree(createResponse).at("/data/id").asText();

            UpdateAddOnRequest update = new UpdateAddOnRequest("Updated Name", null, false);

            mockMvc.perform(patch(BASE + "/{id}", addOnId)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.code").value("PATCH_CODE"))
                .andExpect(jsonPath("$.data.type").value("service"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void update_unknownId_returns404() throws Exception {
            UpdateAddOnRequest update = new UpdateAddOnRequest("X", null, null);

            mockMvc.perform(patch(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /add-ons/{id}")
    class GetAddOnById {

        @Test
        @DisplayName("200 — returns persisted add-on")
        void getById_existing_returns200() throws Exception {
            String createResponse = createAddOn("GET_BY_ID_CODE", AddOnType.QUOTA, true);
            String addOnId = objectMapper.readTree(createResponse).at("/data/id").asText();

            mockMvc.perform(get(BASE + "/{id}", addOnId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(addOnId))
                .andExpect(jsonPath("$.data.code").value("GET_BY_ID_CODE"));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_unknown_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /code/{code} ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /add-ons/code/{code}")
    class GetAddOnByCode {

        @Test
        @DisplayName("200 — lowercase code path is normalised and returns the add-on")
        void getByCode_lowercaseInput_normalisedToUppercase() throws Exception {
            createAddOn("LOOKUP_CODE", AddOnType.FEATURE, true);

            // Service normalises 'lookup_code' → 'LOOKUP_CODE' before DB lookup
            mockMvc.perform(get(BASE + "/code/lookup_code")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("LOOKUP_CODE"));
        }

        @Test
        @DisplayName("200 — uppercase code path returns add-on directly")
        void getByCode_uppercaseInput_returns200() throws Exception {
            createAddOn("DIRECT_CODE", AddOnType.SERVICE, true);

            mockMvc.perform(get(BASE + "/code/DIRECT_CODE")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("DIRECT_CODE"));
        }

        @Test
        @DisplayName("404 — no add-on with the given code returns 404")
        void getByCode_noMatch_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/code/NONEXISTENT")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET (list) ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /add-ons")
    class ListAddOns {

        @Test
        @DisplayName("200 — returns all created add-ons ordered by code")
        void list_multipleAddOns_returnsAll() throws Exception {
            createAddOn("B_CODE", AddOnType.SERVICE, true);
            createAddOn("A_CODE", AddOnType.QUOTA, false);

            mockMvc.perform(get(BASE)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("A_CODE"))  // ordered by code asc
                .andExpect(jsonPath("$.data[1].code").value("B_CODE"));
        }

        @Test
        @DisplayName("200 — ?active=true filters inactive add-ons")
        void list_activeFilter_returnsOnlyActive() throws Exception {
            createAddOn("ACTIVE_CODE", AddOnType.FEATURE, true);
            createAddOn("INACTIVE_CODE", AddOnType.FEATURE, false);

            mockMvc.perform(get(BASE + "?active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("ACTIVE_CODE"));
        }

        @Test
        @DisplayName("200 — ?type=quota filters to quota add-ons only")
        void list_typeFilter_quotaOnly() throws Exception {
            createAddOn("QUOTA_CODE", AddOnType.QUOTA, true);
            createAddOn("FEATURE_CODE", AddOnType.FEATURE, true);

            mockMvc.perform(get(BASE + "?type=quota")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("quota"));
        }

        @Test
        @DisplayName("200 — ?type=service combined with ?active=true filters correctly")
        void list_combinedFilter_activeService() throws Exception {
            createAddOn("SVC_ACTIVE", AddOnType.SERVICE, true);
            createAddOn("SVC_INACTIVE", AddOnType.SERVICE, false);
            createAddOn("FEAT_ACTIVE", AddOnType.FEATURE, true);

            mockMvc.perform(get(BASE + "?type=service&active=true")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("SVC_ACTIVE"));
        }

        @Test
        @DisplayName("400 — invalid ?type value returns bad request")
        void list_invalidType_returns400() throws Exception {
            mockMvc.perform(get(BASE + "?type=not_a_type")
                    .with(authentication(buildAuth())))
                .andExpect(status().isBadRequest());
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /add-ons/{id}")
    class DeleteAddOn {

        @Test
        @DisplayName("204 — soft-deletes add-on; subsequent GET returns 404")
        void delete_existing_returns204AndHidesAddOn() throws Exception {
            String createResponse = createAddOn("DEL_CODE", AddOnType.QUOTA, true);
            String addOnId = objectMapper.readTree(createResponse).at("/data/id").asText();

            mockMvc.perform(delete(BASE + "/{id}", addOnId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/{id}", addOnId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 — deleting unknown add-on returns 404")
        void delete_unknown_returns404() throws Exception {
            mockMvc.perform(delete(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("J-gate: code can be reused after soft-delete (partial unique index)")
        void delete_andRecreate_sameCodeSucceeds() throws Exception {
            String createResponse = createAddOn("REUSE_CODE", AddOnType.FEATURE, true);
            String addOnId = objectMapper.readTree(createResponse).at("/data/id").asText();

            mockMvc.perform(delete(BASE + "/{id}", addOnId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateAddOnRequest("REUSE_CODE", "Reused", null, AddOnType.FEATURE, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("REUSE_CODE"));
        }
    }
}
