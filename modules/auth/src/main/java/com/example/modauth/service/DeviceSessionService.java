package com.example.modauth.service;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.SessionSummaryResponse;

import java.util.List;
import java.util.UUID;

/**
 * "Devices I'm signed in on" (Flow 4.12) — AuthSessionJpaRepository already
 * has everything this needs (findAllByUserIdAndActiveTrue), just no
 * controller ever called it. Revoking one "takes effect immediately."
 */
public interface DeviceSessionService {

    List<SessionSummaryResponse> listMine(AuthenticatedUser caller);

    void revoke(AuthenticatedUser caller, UUID sessionId);
}
