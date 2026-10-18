package com.example.authsvc.application.service;

import com.example.authsvc.domain.model.MfaEnrollment;

import java.util.List;
import java.util.UUID;

public interface MfaService {

    MfaEnrollment enroll(UUID userId, String email);

    List<String> confirmEnrollment(UUID userId, String code);

    void disable(UUID userId);

    boolean verifyCode(UUID userId, String code);

    boolean isEnrolled(UUID userId);
}
