package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreatePromotionRequest;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PromotionSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test proving {@code PromotionSource} round-trips correctly and
 * is persisted as {@code 'normal'} — never {@code NULL} — when unset.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Promotion Source — REST API (Integration)")
class PromotionSourceIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/promotions";

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
    }

    @AfterEach
    void cleanUp() {
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

    @Test
    @DisplayName("create with source=influencer — round-trips as 'influencer'")
    void create_withInfluencerSource_roundTrips() throws Exception {
        CreatePromotionRequest req = new CreatePromotionRequest(
            "Influencer Deal", null, new FlatDiscount(new BigDecimal("10")),
            VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, PromotionSource.INFLUENCER, null, null, null);

        MvcResult created = mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.source").value("influencer"))
            .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("id").asText();

        mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.source").value("influencer"));
    }

    @Test
    @DisplayName("create without source — defaults to 'normal', persisted column is never NULL")
    void create_withoutSource_defaultsToNormalNeverNull() throws Exception {
        CreatePromotionRequest req = new CreatePromotionRequest(
            "Default Source Promo", null, new FlatDiscount(new BigDecimal("10")),
            VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

        MvcResult created = mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.source").value("normal"))
            .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("id").asText();

        mockMvc.perform(get(BASE + "/" + id).with(authentication(buildAuth())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.source").value("normal"));

        String columnValue = jdbcTemplate.queryForObject(
            "SELECT source FROM ppm_promotions WHERE id = ?", String.class, UUID.fromString(id));
        assertThat(columnValue).isEqualTo("normal");
        assertThat(columnValue).isNotNull();
    }
}
