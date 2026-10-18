package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.port.TenantScopePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Permissive demo implementation — every caller is trusted, no real auth wiring. */
@Component
public class DemoTenantScopeAdapter implements TenantScopePort {

    @Override
    public void assertTenantAccess(UUID requestedTenantId) {
        // no-op: demo caller is always permitted
    }

    @Override
    public UUID resolveEffectiveTenantId(UUID requestedTenantId) {
        return requestedTenantId;
    }
}
