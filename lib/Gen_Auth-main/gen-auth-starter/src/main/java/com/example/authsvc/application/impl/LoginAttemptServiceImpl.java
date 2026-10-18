package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.LoginAttemptRequest;
import com.example.authsvc.api.mapper.AuthLoginAttemptMapper;
import com.example.authsvc.application.service.LoginAttemptService;
import com.example.authsvc.infrastructure.persistence.repository.AuthLoginAttemptJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final AuthLoginAttemptJpaRepository loginAttemptRepo;

    @Override
    @Async("authAsync")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LoginAttemptRequest request) {
        log.debug("attempt.record email={} success={}", request.email(), request.success());
        try {
            loginAttemptRepo.save(AuthLoginAttemptMapper.toEntity(request));
            log.debug("attempt.recorded email={}", request.email());
        } catch (Exception e) {
            log.error("attempt.record_failed email={}", request.email(), e);
        }
    }
}
