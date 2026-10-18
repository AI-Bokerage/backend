package com.example.authsvc.application.service;

import java.util.UUID;

public interface TenantSettingsService {

    boolean isMfaRequired(UUID tenantId);

    void setMfaRequired(UUID tenantId, boolean required);
}
