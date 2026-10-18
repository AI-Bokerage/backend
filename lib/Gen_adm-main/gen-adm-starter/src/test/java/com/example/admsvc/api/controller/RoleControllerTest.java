package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.RoleService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoleControllerTest {

    private RoleService roleService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        roleService = mock(RoleService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RoleController(roleService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                // ponytail: standaloneSetup doesn't run Spring Security's WebMvcConfigurer,
                // so @AuthenticationPrincipal needs its resolver registered by hand here.
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    @Test
    void createsARoleWhenPrincipalHasPermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:roles:manage"));
        RoleEntity created = RoleEntity.builder().id(UUID.randomUUID())
                .tenantId(principal.tenantId()).name("owner").build();
        when(roleService.createRole(eq(principal.tenantId()), eq("owner"), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/roles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                            put("name", "owner");
                            put("description", "Tenant owner");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("owner"));
    }

    @Test
    void returns403WhenPrincipalLacksPermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: adm:roles:manage"))
                .when(permissionChecker).require(eq(principal), eq("adm:roles:manage"));

        mockMvc.perform(post("/api/v1/roles")
                        .contentType("application/json")
                        .content("{\"name\":\"owner\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void returns404ForACrossTenantLookup() throws Exception {
        UUID roleId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:roles:manage"));
        when(roleService.getRole(eq(principal.tenantId()), eq(roleId)))
                .thenThrow(new GenAdmNotFoundException("Role not found: " + roleId));

        mockMvc.perform(get("/api/v1/roles/" + roleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
