package com.example.modauth.service;

import com.example.authsvc.api.dto.response.LoginResult;
import com.example.modauth.domain.Role;

/**
 * Either the "please accept the updated terms first" gate (no tokens issued
 * yet) or a real login result carrying gen-auth-starter's own
 * {@link LoginResult} plus the role/dashboard hint this module adds.
 */
public record ModAuthLoginResult(
        boolean requiresTermsAcceptance,
        Integer termsVersion,
        Role role,
        String dashboard,
        LoginResult starterResult
) {
    public static ModAuthLoginResult termsGate(int termsVersion) {
        return new ModAuthLoginResult(true, termsVersion, null, null, null);
    }

    public static ModAuthLoginResult success(Role role, String dashboard, LoginResult starterResult) {
        return new ModAuthLoginResult(false, null, role, dashboard, starterResult);
    }
}
