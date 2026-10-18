package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;

public interface LoginService {

    LoginResult login(LoginRequest request, String ipAddress, String userAgent);
}
