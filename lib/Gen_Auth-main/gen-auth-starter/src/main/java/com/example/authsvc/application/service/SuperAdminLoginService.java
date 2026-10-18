package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;

/**
 * Authentication service for the platform-level super admin account.
 * Credentials are stored in {@code platform_super_admin}, separate from
 * tenant user records in {@code auth_users}. Exposed via
 * {@code POST /api/v1/auth/super-admin/login}.
 */
public interface SuperAdminLoginService {

    LoginResult login(LoginRequest request, String ipAddress, String userAgent);
}
