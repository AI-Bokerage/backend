package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.InvitationService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvitationControllerTest {

    private static final String MANAGE_PERM = "adm:invitations:manage";

    private InvitationService invitationService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        invitationService = mock(InvitationService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InvitationController(invitationService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private InvitationEntity invitation(InvitationStatus status, String token) {
        InvitationEntity entity = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId()).email("a@example.com")
                .status(status).invitedByUserId(principal.userId())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .plaintextToken(token)
                .build();
        entity.setRoleIds(List.of(UUID.randomUUID()));
        return entity;
    }

    @Test
    void createsAnInvitationWhenPrincipalHasPermissionAndReturnsTheToken() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        InvitationEntity created = invitation(InvitationStatus.PENDING, "plaintext-token-123");
        UUID roleId = created.getRoleIds().get(0);
        when(invitationService.create(eq(principal.tenantId()), eq(principal.userId()), eq("a@example.com"), eq(List.of(roleId))))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/invitations")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("email", "a@example.com");
                            put("roleIds", List.of(roleId.toString()));
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.token").value("plaintext-token-123"));
    }

    @Test
    void returns403WhenPrincipalLacksPermissionToCreate() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(post("/api/v1/invitations")
                        .contentType("application/json")
                        .content("{\"email\":\"a@example.com\",\"roleIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listNeverExposesTheToken() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(invitationService.listActionable(principal.tenantId()))
                .thenReturn(List.of(invitation(InvitationStatus.PENDING, "should-not-appear")));

        mockMvc.perform(get("/api/v1/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void cancelReturns404ForACrossTenantInvitation() throws Exception {
        UUID invitationId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(invitationService.cancel(eq(principal.tenantId()), eq(invitationId), eq(principal.userId())))
                .thenThrow(new GenAdmNotFoundException("Invitation not found: " + invitationId));

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void acceptRequiresNoPermissionCheckAndNeverExposesTheToken() throws Exception {
        InvitationEntity accepted = invitation(InvitationStatus.ACCEPTED, "should-not-appear");
        UUID newUserId = UUID.randomUUID();
        when(invitationService.accept(eq("some-token"), eq(newUserId))).thenReturn(accepted);

        mockMvc.perform(post("/api/v1/invitations/some-token/accept")
                        .contentType("application/json")
                        .content("{\"userId\":\"" + newUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.token").doesNotExist());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void acceptReturns409ForAnAlreadyUsedToken() throws Exception {
        when(invitationService.accept(eq("used-token"), any()))
                .thenThrow(new GenAdmConflictException("This invitation is no longer pending."));

        mockMvc.perform(post("/api/v1/invitations/used-token/accept")
                        .contentType("application/json")
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
