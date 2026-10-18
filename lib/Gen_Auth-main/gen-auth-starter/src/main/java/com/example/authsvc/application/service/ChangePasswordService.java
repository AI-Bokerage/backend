package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.ChangePasswordRequest;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;

public interface ChangePasswordService {

    void changePassword(AuthenticatedUser principal, ChangePasswordRequest request);
}
