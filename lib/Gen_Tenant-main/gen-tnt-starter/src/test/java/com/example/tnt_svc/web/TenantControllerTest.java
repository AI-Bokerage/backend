// gen-tnt-starter/src/test/java/com/example/tnt_svc/web/TenantControllerTest.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.config.GenTntProperties;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.service.TenantService;
import com.example.tnt_svc.web.security.InternalSecretFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantControllerTest {

    private final TenantService tenantService = mock(TenantService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GenTntProperties properties = new GenTntProperties();
        properties.setInternalSecret("test-secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new TenantController(tenantService))
            .addFilter(new InternalSecretFilter(properties))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createTenantWithoutSecretHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createTenantWithSecretHeaderSucceeds() throws Exception {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).slug("acme").name("Acme").status(TenantStatus.PROVISIONING).build();
        when(tenantService.createTenant(any())).thenReturn(tenant);

        String body = new ObjectMapper().writeValueAsString(new com.example.tnt_svc.web.dto.CreateTenantRequest(
            "Acme", "acme", "us", UUID.randomUUID(), null));

        mockMvc.perform(post("/api/v1/tenants")
                .header("X-Internal-Secret", "test-secret")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isAccepted());
    }

    @Test
    void getTenantWithWrongSecretIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/" + UUID.randomUUID())
                .header("X-Internal-Secret", "wrong-secret"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createTenantReturns409OnDataIntegrityViolation() throws Exception {
        // Concurrent inserts racing on the slug / idempotency-key unique constraint surface
        // as DataIntegrityViolationException — must be mapped to 409, not a raw 500.
        when(tenantService.createTenant(any()))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        String body = new ObjectMapper().writeValueAsString(new com.example.tnt_svc.web.dto.CreateTenantRequest(
            "Acme", "acme", "us", UUID.randomUUID(), null));

        mockMvc.perform(post("/api/v1/tenants")
                .header("X-Internal-Secret", "test-secret")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void getBySlugReturns200WhenTaken() throws Exception {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).slug("acme").name("Acme")
            .status(TenantStatus.ACTIVE).primaryOwnerUserId(UUID.randomUUID()).build();
        when(tenantService.getTenantBySlug("acme")).thenReturn(tenant);

        mockMvc.perform(get("/api/v1/tenants/by-slug/acme").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isOk());
    }

    @Test
    void getBySlugReturns404WhenFree() throws Exception {
        when(tenantService.getTenantBySlug("free-slug")).thenThrow(new TenantNotFoundException("free-slug"));

        mockMvc.perform(get("/api/v1/tenants/by-slug/free-slug").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isNotFound());
    }
}
