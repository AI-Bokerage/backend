package com.example.modauth.service;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.application.impl.AuditLogServiceImpl;
import com.example.authsvc.application.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * gen-auth-starter's own audit trail, plus the sign-in and security events
 * forwarded to the platform activity log the Super Admin reads (Flow 1).
 */
@Primary
@Service
@RequiredArgsConstructor
public class ForwardingAuditLogService implements AuditLogService {

    /** Starter action → platform activity-log action. Everything else stays local. */
    private static final Map<String, String> FORWARDED = Map.of(
            "LOGIN_SUCCESS", "auth.login.success",
            "LOGIN_FAILED", "auth.login.failed",
            "LOGOUT", "auth.logout",
            "REFRESH_TOKEN_REPLAY_DETECTED", "security.refresh_token_replay",
            "password.reset.success", "auth.password.reset");

    private final AuditLogServiceImpl delegate;
    private final PlatformEventClient platformEvents;

    @Override
    public void log(AuditLogRequest request) {
        delegate.log(request);
        String action = FORWARDED.get(request.action());
        if (action != null) {
            platformEvents.send(action, request.tenantId(), request.userId(), request.ip(), request.details());
        }
    }
}
