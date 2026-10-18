package com.example.authsvc.api.dto.response;

import java.util.List;

public record MfaEnrollConfirmResponse(
        List<String> backupCodes
) {}
