package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;

public interface ImpersonationTokenService {

    ImpersonationTokenResponse issue(ImpersonationTokenRequest request);
}
