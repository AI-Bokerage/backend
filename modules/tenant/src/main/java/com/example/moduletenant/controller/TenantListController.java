package com.example.moduletenant.controller;

import com.example.tnt_svc.persistence.TenantRepository;
import com.example.tnt_svc.web.dto.TenantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * gen-tnt-starter's own {@code TenantController} covers create/get-by-id/
 * get-by-slug/suspend/reactivate/cancel/purge but has no list endpoint — the
 * "Brokerages" console screen needs one. Rather than edit the starter, this
 * injects its own {@code TenantRepository} bean directly (registered by
 * {@code GenTntAutoConfiguration}'s {@code @EnableJpaRepositories}) and adds
 * the one missing route, gated by the same {@code InternalSecretFilter}
 * every other {@code /api/v1/**} path already goes through (path-prefix
 * based, not controller-based, so it applies here too with no extra wiring).
 */
@Tag(name = "Tenants", description = "The one gen-tnt-starter endpoint this module adds: list all brokerages")
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantListController {

    private final TenantRepository tenantRepository;

    @Operation(summary = "List brokerages", description = "Paginated; gated by X-Internal-Secret like every other /api/v1/** route here.")
    @GetMapping
    public Page<TenantResponse> list(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(TenantResponse::from);
    }
}
