package com.example.modauth.controller;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.SessionSummaryResponse;
import com.example.modauth.service.DeviceSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** "Devices I'm signed in on" — every role has this, not just one. */
@Tag(name = "Sessions", description = "List and revoke the caller's own active sessions/devices")
@RestController
@RequestMapping("/api/v1/modauth/sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final DeviceSessionService sessionService;

    @Operation(summary = "List my active sessions/devices")
    @GetMapping
    public List<SessionSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
        return sessionService.listMine(caller);
    }

    @Operation(summary = "Revoke a session", description = "Takes effect immediately. Can be the caller's own current session.")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "403", description = "Not your session")
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID sessionId) {
        sessionService.revoke(caller, sessionId);
    }
}
