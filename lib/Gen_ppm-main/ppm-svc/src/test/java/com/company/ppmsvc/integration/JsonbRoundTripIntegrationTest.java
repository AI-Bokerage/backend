package com.company.ppmsvc.integration;

import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.api.dto.request.CreatePromotionRequest;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.FreeAddOnDiscount;
import com.company.ppmsvc.promotion.model.FreeModuleDiscount;
import com.company.ppmsvc.promotion.model.FreePeriodDiscount;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionStatus;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves all six {@code PromotionAction} concrete types round-trip through
 * {@code action_payload} JSONB correctly — the two-level sealed hierarchy
 * ({@code PriceAction}/{@code EntitlementAction}) introduced in Phase 4
 * doesn't break Jackson polymorphic resolution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("PromotionAction JSONB Round-Trip — REST API (Integration)")
class JsonbRoundTripIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/promotions";

    static final LocalDate VALID_FROM  = LocalDate.now().minusDays(10);
    static final LocalDate VALID_UNTIL = LocalDate.now().plusDays(60);

    @Autowired MockMvc               mockMvc;
    @Autowired ObjectMapper          objectMapper;
    @Autowired ModuleRepositoryPort  moduleRepository;
    @Autowired AddOnRepositoryPort   addOnRepository;
    @Autowired JdbcTemplate          jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    UUID moduleId;
    UUID addOnId;

    @BeforeEach
    void seed() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));

        Instant now = Instant.now();
        moduleId = moduleRepository.save(Module.builder()
            .id(UUID.randomUUID())
            .code(ModuleCode.REPORTING).name("Reporting").active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();

        addOnId = addOnRepository.save(AddOn.builder()
            .id(UUID.randomUUID())
            .code("EXTRA_STORAGE").name("Extra Storage").type(AddOnType.QUOTA).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build()).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_promotions");
        jdbcTemplate.execute("DELETE FROM ppm_modules");
        jdbcTemplate.execute("DELETE FROM ppm_add_ons");
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

    private String createAndFetchActionType(String name, PromotionAction action) throws Exception {
        CreatePromotionRequest req = new CreatePromotionRequest(
            name, null, action, VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

        MvcResult created = mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("id").asText();

        MvcResult fetched = mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(fetched.getResponse().getContentAsString())
            .path("data").path("action").path("type").asText();
    }

    @Test
    @DisplayName("percentage round-trips")
    void percentage_roundTrips() throws Exception {
        String type = createAndFetchActionType("Percentage",
            new PercentageDiscount(new BigDecimal("20"), new BigDecimal("200"), null));
        org.assertj.core.api.Assertions.assertThat(type).isEqualTo("percentage");
    }

    @Test
    @DisplayName("flat round-trips")
    void flat_roundTrips() throws Exception {
        String type = createAndFetchActionType("Flat", new FlatDiscount(new BigDecimal("100")));
        org.assertj.core.api.Assertions.assertThat(type).isEqualTo("flat");
    }

    @Test
    @DisplayName("fixed_price round-trips")
    void fixedPrice_roundTrips() throws Exception {
        String type = createAndFetchActionType("FixedPrice", new FixedPriceDiscount(new BigDecimal("499")));
        org.assertj.core.api.Assertions.assertThat(type).isEqualTo("fixed_price");
    }

    @Test
    @DisplayName("free_period round-trips")
    void freePeriod_roundTrips() throws Exception {
        String type = createAndFetchActionType("FreePeriod", new FreePeriodDiscount(3));
        org.assertj.core.api.Assertions.assertThat(type).isEqualTo("free_period");
    }

    @Test
    @DisplayName("free_module round-trips")
    void freeModule_roundTrips() throws Exception {
        String type = createAndFetchActionType("FreeModule", new FreeModuleDiscount(moduleId, 3));
        org.assertj.core.api.Assertions.assertThat(type).isEqualTo("free_module");
    }

    @Test
    @DisplayName("free_addon round-trips")
    void freeAddOn_roundTrips() throws Exception {
        String type = createAndFetchActionType("FreeAddOn", new FreeAddOnDiscount(addOnId, 3));
        org.assertj.core.api.Assertions.assertThat(type).isEqualTo("free_addon");
    }
}
