package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;
import com.example.authsvc.application.service.ServiceTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for issuing stateless service-account JWTs. Only registered
 * when {@code app.super-admin.enabled=true}.
 *
 * <p>Protected by {@code X-Internal-Secret} via
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * through the {@code /internal/**} path prefix — no bespoke auth here.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ServiceTokenController {

    private final ServiceTokenService serviceTokenService;

    @PostMapping("/service-token")
    public ResponseEntity<ServiceTokenResponse> issueServiceToken(
            @RequestHeader(value = "X-CPMS-Service", defaultValue = "unknown") String callerService) {
        return ResponseEntity.ok(serviceTokenService.issue(callerService));
    }
}
