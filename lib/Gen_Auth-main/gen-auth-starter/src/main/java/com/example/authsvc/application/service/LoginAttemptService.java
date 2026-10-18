package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.LoginAttemptRequest;

public interface LoginAttemptService {

    void record(LoginAttemptRequest request);
}
