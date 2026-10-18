package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.api.mapper.AuthAuditLogMapper;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.infrastructure.persistence.repository.AuthAuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuthAuditLogJpaRepository auditLogRepo;

    @Override
    @Async("authAsync")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditLogRequest request) {
        log.debug("audit.write action={} userId={}", request.action(), request.userId());
        try {
            auditLogRepo.save(AuthAuditLogMapper.toEntity(request));
            log.debug("audit.written action={} userId={}", request.action(), request.userId());
        } catch (Exception e) {
            log.error("audit.write_failed action={} userId={}", request.action(), request.userId(), e);
        }
    }
}
