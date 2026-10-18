package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;

public interface ClientTokenService {
    ClientTokenResponse issue(ClientTokenRequest request);
}
