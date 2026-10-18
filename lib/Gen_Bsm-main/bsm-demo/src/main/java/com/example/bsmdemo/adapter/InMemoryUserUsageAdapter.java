package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.UserUsageCounts;
import com.company.bsmsvc.domain.port.UserUsagePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Deterministic stub counts — no ADM-SVC to call from a demo. */
@Component
public class InMemoryUserUsageAdapter implements UserUsagePort {

    @Override
    public UserUsageCounts getUserUsageCounts(UUID tenantId) {
        return new UserUsageCounts(1, 1);
    }
}
