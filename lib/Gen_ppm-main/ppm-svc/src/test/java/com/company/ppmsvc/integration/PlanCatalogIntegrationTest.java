package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreatePlanRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanRequest;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

import static org.assertj.core.api.Assertions.assertThat;
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
 * Full-stack integration tests for the Plan Catalog REST API.
 *
 * <p>Extends {@link AbstractContainerIntegrationTest} for the shared Postgres
 * Testcontainers instance. HTTP requests flow through the full Spring Security +
 * controller + service + DB stack with no mocking of business logic.
 *
 * <p>Slugs are system-generated via {@code ppm_plan_slug_seq}.  Tests verify
 * that responses contain a {@code PLN-XXXX} slug and that concurrent plan
 * creation produces unique slugs without collision.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Plan Catalog — REST API (Integration)")
class PlanCatalogIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/plans";

    @Autowired MockMvc          mockMvc;
    @Autowired ObjectMapper     objectMapper;
    @Autowired PlanJpaRepository planJpaRepository;

    @MockitoBean
    RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    @AfterEach
    void cleanUp() {
        planJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Authentication buildAuth() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            DEV_USER, null, null, null,
            CpmsUserType.SUPER_ADMIN,
            "test-session", "test-jti",
            Instant.now().plusSeconds(900)
        );
        return new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }

    /** Creates a plan and returns the persisted ID. */
    private String createPlan(String code, String name, PlanVisibility visibility) throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(code, name, null, null, visibility, null, null, null);
        String body = mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    /** Creates a plan and returns the full response body as a JSON string. */
    private String createPlanGetBody(String code, String name, PlanVisibility visibility) throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(code, name, null, null, visibility, null, null, null);
        return mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /plans")
    class CreatePlan {

        @Test
        @DisplayName("201 — creates plan; response contains system-generated PLN-XXXX slug")
        void create_valid_returns201WithGeneratedSlug() throws Exception {
            CreatePlanRequest req = new CreatePlanRequest(
                "STARTER", "Starter", null, null, PlanVisibility.PUBLIC, 7, true, null);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.code").value("STARTER"))
                .andExpect(jsonPath("$.data.slug").value(org.hamcrest.Matchers.matchesPattern("PLN-\\d{4}")))
                .andExpect(jsonPath("$.data.visibility").value("public"))
                .andExpect(jsonPath("$.data.trialDays").value(7))
                .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("201 — trialDays defaults to 0; active defaults to true when omitted")
        void create_noTrialDays_defaultsToZero() throws Exception {
            CreatePlanRequest req = new CreatePlanRequest(
                "BASIC", "Basic", null, null, PlanVisibility.PRIVATE, null, null, null);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.trialDays").value(0))
                .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("201 — each successive plan gets the next sequential slug")
        void create_threePlans_sequentialSlugs() throws Exception {
            String body1 = createPlanGetBody("P1", "Plan One", PlanVisibility.PUBLIC);
            String body2 = createPlanGetBody("P2", "Plan Two", PlanVisibility.PUBLIC);
            String body3 = createPlanGetBody("P3", "Plan Three", PlanVisibility.PUBLIC);

            String slug1 = objectMapper.readTree(body1).at("/data/slug").asText();
            String slug2 = objectMapper.readTree(body2).at("/data/slug").asText();
            String slug3 = objectMapper.readTree(body3).at("/data/slug").asText();

            // All follow PLN-XXXX pattern
            assertThat(slug1).matches("PLN-\\d{4}");
            assertThat(slug2).matches("PLN-\\d{4}");
            assertThat(slug3).matches("PLN-\\d{4}");

            // Extract numeric parts and verify they are sequential
            int n1 = Integer.parseInt(slug1.substring(4));
            int n2 = Integer.parseInt(slug2.substring(4));
            int n3 = Integer.parseInt(slug3.substring(4));
            assertThat(n2).isEqualTo(n1 + 1);
            assertThat(n3).isEqualTo(n1 + 2);
        }

        @Test
        @DisplayName("409 — duplicate code returns conflict")
        void create_duplicateCode_returns409() throws Exception {
            createPlan("GROWTH", "Growth", PlanVisibility.PUBLIC);

            CreatePlanRequest dup = new CreatePlanRequest(
                "GROWTH", "Growth Dup", null, null, PlanVisibility.PUBLIC, null, null, null);

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("422 — missing name fails validation")
        void create_missingName_returns422() throws Exception {
            String body = """
                {"code":"STARTER","visibility":"public"}
                """;

            mockMvc.perform(post(BASE)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("concurrent creation — all plans receive unique slugs")
        void create_concurrent_uniqueSlugs() throws Exception {
            int threads = 5;
            var executor = Executors.newFixedThreadPool(threads);
            var latch = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                final String code = "CONCURRENT_" + i;
                futures.add(executor.submit(() -> {
                    latch.await();
                    CreatePlanRequest req = new CreatePlanRequest(
                        code, "Concurrent " + code, null, null, PlanVisibility.PUBLIC, null, null, null);
                    String responseBody = mockMvc.perform(post(BASE)
                            .with(authentication(buildAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                    return objectMapper.readTree(responseBody).at("/data/slug").asText();
                }));
            }

            latch.countDown(); // release all threads simultaneously
            executor.shutdown();

            List<String> slugs = new ArrayList<>();
            for (Future<String> f : futures) {
                slugs.add(f.get());
            }

            // All slugs must be distinct PLN-XXXX values
            assertThat(slugs).hasSize(threads);
            assertThat(Set.copyOf(slugs)).hasSize(threads); // no duplicates
            assertThat(slugs).allMatch(s -> s.matches("PLN-\\d{4}"));
        }
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /plans/{id}")
    class UpdatePlan {

        @Test
        @DisplayName("200 — updates mutable fields; code and slug remain unchanged")
        void update_valid_returns200WithImmutableFields() throws Exception {
            String body = createPlanGetBody("PRO", "Pro", PlanVisibility.PUBLIC);
            String id   = objectMapper.readTree(body).at("/data/id").asText();
            String slug = objectMapper.readTree(body).at("/data/slug").asText();

            UpdatePlanRequest req = new UpdatePlanRequest("Pro v2", null, null, PlanVisibility.PRIVATE, null, false, null);

            mockMvc.perform(patch(BASE + "/{id}", id)
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Pro v2"))
                .andExpect(jsonPath("$.data.visibility").value("private"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.code").value("PRO"))
                .andExpect(jsonPath("$.data.slug").value(slug)); // slug unchanged
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void update_unknownId_returns404() throws Exception {
            UpdatePlanRequest req = new UpdatePlanRequest("X", null, null, null, null, null, null);

            mockMvc.perform(patch(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /plans/{id}")
    class GetPlanById {

        @Test
        @DisplayName("200 — returns persisted plan by ID with generated slug")
        void getById_existing_returns200() throws Exception {
            String body = createPlanGetBody("TEAM", "Team", PlanVisibility.PUBLIC);
            String id   = objectMapper.readTree(body).at("/data/id").asText();
            String slug = objectMapper.readTree(body).at("/data/slug").asText();

            mockMvc.perform(get(BASE + "/{id}", id)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.code").value("TEAM"))
                .andExpect(jsonPath("$.data.slug").value(slug));
        }

        @Test
        @DisplayName("404 — unknown ID returns 404")
        void getById_unknown_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }
    }

    // ── GET /slug/{slug} ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /plans/slug/{slug}")
    class GetPlanBySlug {

        @Test
        @DisplayName("200 — returns plan by its system-generated PLN-XXXX slug")
        void getBySlug_existing_returns200() throws Exception {
            String createBody = createPlanGetBody("LEGACY_PLAN", "Legacy", PlanVisibility.LEGACY);
            String slug = objectMapper.readTree(createBody).at("/data/slug").asText();

            mockMvc.perform(get(BASE + "/slug/" + slug)
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value(slug))
                .andExpect(jsonPath("$.data.visibility").value("legacy"));
        }

        @Test
        @DisplayName("404 — unknown slug returns 404")
        void getBySlug_unknown_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/slug/PLN-9999")
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("E3 — slug lookup is case-sensitive: pln-0001 does not match PLN-0001")
        void getBySlug_lowercase_returns404() throws Exception {
            // Slugs are stored as PLN-XXXX (uppercase prefix). PostgreSQL VARCHAR
            // comparison is case-sensitive, so the lowercase variant is a different
            // value and must return 404.
            String createBody = createPlanGetBody("CASE_TEST", "Case Test", PlanVisibility.PUBLIC);
            String slug = objectMapper.readTree(createBody).at("/data/slug").asText();

            // Confirm the generated slug starts with uppercase "PLN-"
            assertThat(slug).startsWith("PLN-");

            // Lowercase variant must return 404
            mockMvc.perform(get(BASE + "/slug/" + slug.toLowerCase())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("F2 — deleted slug never appears in lookup again (sequence never reuses)")
        void delete_slugPermanent_neverReused() throws Exception {
            String createBody = createPlanGetBody("PERM_TEST", "Perm Test", PlanVisibility.PUBLIC);
            String id   = objectMapper.readTree(createBody).at("/data/id").asText();
            String slug = objectMapper.readTree(createBody).at("/data/slug").asText();

            // Delete the plan
            mockMvc.perform(delete(BASE + "/{id}", id)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Old slug is gone
            mockMvc.perform(get(BASE + "/slug/" + slug)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());

            // Create a new plan — gets the NEXT sequence value, not the old one
            String newBody = createPlanGetBody("PERM_TEST_2", "Perm Test 2", PlanVisibility.PUBLIC);
            String newSlug = objectMapper.readTree(newBody).at("/data/slug").asText();

            assertThat(newSlug).isNotEqualTo(slug);           // sequence advanced, old slug not recycled
            assertThat(newSlug).matches("PLN-\\d{4}");        // still a valid generated slug
        }
    }

    // ── GET (list) ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /plans")
    class ListPlans {

        @Test
        @DisplayName("200 — returns all created plans")
        void list_multiplePlans_returnsAll() throws Exception {
            createPlan("PLAN_A", "Plan A", PlanVisibility.PUBLIC);
            createPlan("PLAN_B", "Plan B", PlanVisibility.PRIVATE);

            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 — ?active=false filters active plans")
        void list_activeFilter_returnsOnlyInactive() throws Exception {
            CreatePlanRequest active = new CreatePlanRequest(
                "ACTIVE_PLAN", "Active", null, null, PlanVisibility.PUBLIC, null, true, null);
            CreatePlanRequest inactive = new CreatePlanRequest(
                "INACTIVE_PLAN", "Inactive", null, null, PlanVisibility.PUBLIC, null, false, null);

            mockMvc.perform(post(BASE).with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(active)))
                .andExpect(status().isCreated());
            mockMvc.perform(post(BASE).with(authentication(buildAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(inactive)))
                .andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "?active=false").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("INACTIVE_PLAN"));
        }

        @Test
        @DisplayName("200 — ?visibility=public returns only PUBLIC plans")
        void list_visibilityFilter_returnsOnlyPublic() throws Exception {
            createPlan("PUB", "Public Plan", PlanVisibility.PUBLIC);
            createPlan("PRIV", "Private Plan", PlanVisibility.PRIVATE);

            mockMvc.perform(get(BASE + "?visibility=public").with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].visibility").value("public"));
        }

        @Test
        @DisplayName("200 — ?active=true&visibility=public returns plans matching both criteria")
        void list_combinedFilter_returnsMatchingPlans() throws Exception {
            CreatePlanRequest activePublic = new CreatePlanRequest(
                "ACTIVE_PUB", "Active Public", null, null, PlanVisibility.PUBLIC, null, true, null);
            CreatePlanRequest inactivePublic = new CreatePlanRequest(
                "INACTIVE_PUB", "Inactive Public", null, null, PlanVisibility.PUBLIC, null, false, null);
            CreatePlanRequest activePrivate = new CreatePlanRequest(
                "ACTIVE_PRIV", "Active Private", null, null, PlanVisibility.PRIVATE, null, true, null);

            for (CreatePlanRequest req : List.of(activePublic, inactivePublic, activePrivate)) {
                mockMvc.perform(post(BASE).with(authentication(buildAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
            }

            mockMvc.perform(get(BASE + "?active=true&visibility=public")
                    .with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("ACTIVE_PUB"));
        }

        @Test
        @DisplayName("200 — empty catalog returns empty list")
        void list_empty_returnsEmptyList() throws Exception {
            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /plans/{id}")
    class DeletePlan {

        @Test
        @DisplayName("204 — soft-deletes plan; subsequent GET by ID returns 404")
        void delete_existing_returns204AndHidesPlan() throws Exception {
            String id = createPlan("SUNSET", "Sunset", PlanVisibility.LEGACY);

            mockMvc.perform(delete(BASE + "/{id}", id)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/{id}", id)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("204 — soft-deleted plan is hidden from slug lookup")
        void delete_existing_hiddenFromSlugLookup() throws Exception {
            String createBody = createPlanGetBody("HIDDEN", "Hidden", PlanVisibility.PUBLIC);
            String id   = objectMapper.readTree(createBody).at("/data/id").asText();
            String slug = objectMapper.readTree(createBody).at("/data/slug").asText();

            mockMvc.perform(delete(BASE + "/{id}", id)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/slug/" + slug)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("204 — soft-deleted plan is excluded from list")
        void delete_existing_hiddenFromList() throws Exception {
            createPlan("VISIBLE", "Visible", PlanVisibility.PUBLIC);
            String deletedId = createPlan("DELETED", "Deleted", PlanVisibility.PUBLIC);

            mockMvc.perform(delete(BASE + "/{id}", deletedId)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE).with(authentication(buildAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("VISIBLE"));
        }

        @Test
        @DisplayName("404 — deleting unknown plan returns 404")
        void delete_unknown_returns404() throws Exception {
            mockMvc.perform(delete(BASE + "/{id}", UUID.randomUUID())
                    .with(authentication(buildAuth())))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("J-gate: code can be reused after soft-delete (sequence continues, new slug assigned)")
        void delete_andRecreate_sameCodeSucceeds() throws Exception {
            String createBody = createPlanGetBody("REUSE", "Reuse", PlanVisibility.PUBLIC);
            String id       = objectMapper.readTree(createBody).at("/data/id").asText();
            String oldSlug  = objectMapper.readTree(createBody).at("/data/slug").asText();

            mockMvc.perform(delete(BASE + "/{id}", id)
                    .with(authentication(buildAuth())))
                .andExpect(status().isNoContent());

            // Same code, new plan — partial unique index allows code reuse; sequence produces a new slug
            String recreateBody = createPlanGetBody("REUSE", "Reuse v2", PlanVisibility.PUBLIC);
            String newSlug = objectMapper.readTree(recreateBody).at("/data/slug").asText();

            assertThat(newSlug).matches("PLN-\\d{4}");
            assertThat(newSlug).isNotEqualTo(oldSlug); // sequence never goes backward
        }
    }
}
