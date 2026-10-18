package com.company.ppmsvc.integration;

import com.company.ppmsvc.api.dto.request.CreatePromotionRequest;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.promotion.model.FreeAddOnDiscount;
import com.company.ppmsvc.promotion.model.FreeModuleDiscount;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves cross-aggregate target verification for entitlement actions:
 * {@code FreeModuleDiscount}/{@code FreeAddOnDiscount} referencing a
 * non-existent module/add-on are rejected at promotion creation with 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Entitlement Target Validation — REST API (Integration)")
class EntitlementValidationIntegrationTest extends AbstractContainerIntegrationTest {

    static final String BASE = "/api/v1/ppm/promotions";

    static final LocalDate VALID_FROM  = LocalDate.now().minusDays(10);
    static final LocalDate VALID_UNTIL = LocalDate.now().plusDays(60);

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean RedisRolePermissionResolver rolePermissionResolver;

    @BeforeEach
    void stubPermissions() {
        when(rolePermissionResolver.resolveAll(any())).thenReturn(Set.of("ppm.read"));
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
    @DisplayName("free_module referencing non-existent module — 404 MODULE_NOT_FOUND")
    void create_freeModule_unknownModule_returns404() throws Exception {
        CreatePromotionRequest req = new CreatePromotionRequest(
            "Bad Free Module", null, new FreeModuleDiscount(UUID.randomUUID(), 3),
            VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

        mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("free_addon referencing non-existent add-on — 404 ADD_ON_NOT_FOUND")
    void create_freeAddOn_unknownAddOn_returns404() throws Exception {
        CreatePromotionRequest req = new CreatePromotionRequest(
            "Bad Free AddOn", null, new FreeAddOnDiscount(UUID.randomUUID(), 3),
            VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

        mockMvc.perform(post(BASE)
                .with(authentication(buildAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }
}
