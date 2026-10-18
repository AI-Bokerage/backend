package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.SupportTicketService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportTicketControllerTest {

    private static final String MANAGE_PERM = "adm:tickets:manage";

    private SupportTicketService supportTicketService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        supportTicketService = mock(SupportTicketService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SupportTicketController(supportTicketService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private SupportTicketEntity ticket(SupportTicketStatus status) {
        return SupportTicketEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId())
                .requestedByUserId(principal.userId())
                .type(SupportTicketType.TECHNICAL)
                .subject("s").description("d")
                .priority(SupportTicketPriority.HIGH)
                .status(status)
                .build();
    }

    @Test
    void createRequiresOnlyAnAuthenticatedPrincipalNoPermissionCheck() throws Exception {
        SupportTicketEntity created = ticket(SupportTicketStatus.OPEN);
        when(supportTicketService.create(eq(principal.tenantId()), eq(principal.userId()),
                eq(SupportTicketType.TECHNICAL), eq("Can't log in"), eq("Getting a 500 error"),
                eq(SupportTicketPriority.URGENT))).thenReturn(created);

        mockMvc.perform(post("/api/v1/support-tickets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("type", "TECHNICAL");
                            put("subject", "Can't log in");
                            put("description", "Getting a 500 error");
                            put("priority", "URGENT");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void listMineRequiresOnlyAnAuthenticatedPrincipalNoPermissionCheck() throws Exception {
        when(supportTicketService.listMine(principal.tenantId(), principal.userId()))
                .thenReturn(List.of(ticket(SupportTicketStatus.OPEN)));

        mockMvc.perform(get("/api/v1/support-tickets/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void listAllRequiresManagePermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(get("/api/v1/support-tickets"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void startReturns404ForACrossTenantTicket() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(supportTicketService.start(principal.tenantId(), ticketId))
                .thenThrow(new GenAdmNotFoundException("Support ticket not found: " + ticketId));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/start"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void resolveRequiresManagePermissionAndPassesResolvedBy() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(supportTicketService.resolve(principal.tenantId(), ticketId, principal.userId()))
                .thenReturn(ticket(SupportTicketStatus.RESOLVED));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        verify(supportTicketService).resolve(principal.tenantId(), ticketId, principal.userId());
    }

    @Test
    void closeRequiresManagePermissionAndPassesClosedBy() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(supportTicketService.close(principal.tenantId(), ticketId, principal.userId()))
                .thenReturn(ticket(SupportTicketStatus.CLOSED));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        verify(supportTicketService).close(principal.tenantId(), ticketId, principal.userId());
    }
}
