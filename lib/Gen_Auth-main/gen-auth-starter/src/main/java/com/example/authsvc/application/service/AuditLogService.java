package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.AuditLogRequest;

public interface AuditLogService {

    void log(AuditLogRequest request);
}
