package com.example.modauth.service;

import com.example.modauth.dto.ModLoginRequest;

public interface ModAuthLoginService {

    ModAuthLoginResult login(ModLoginRequest request, String ipAddress, String userAgent);
}
