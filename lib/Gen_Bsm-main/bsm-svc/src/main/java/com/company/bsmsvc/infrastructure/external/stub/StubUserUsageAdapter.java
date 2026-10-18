package com.company.bsmsvc.infrastructure.external.stub;

import com.company.bsmsvc.domain.model.UserUsageCounts;
import com.company.bsmsvc.domain.port.UserUsagePort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub user usage adapter — active only when {@code stub} Spring profile is set.
 * Activate with {@code SPRING_PROFILES_ACTIVE=stub} for local development without ADM-SVC.
 */
@Slf4j
@Component
@Profile("stub")
public class StubUserUsageAdapter implements UserUsagePort {

    private static final int STUB_INTERNAL_USERS = 30;
    private static final int STUB_CLIENT_USERS   = 15;

    @Override
    public UserUsageCounts getUserUsageCounts(UUID tenantId) {
        log.debug("[StubUserUsageAdapter] tenantId={} returning stub internal={} client={}",
            tenantId, STUB_INTERNAL_USERS, STUB_CLIENT_USERS);
        return new UserUsageCounts(STUB_INTERNAL_USERS, STUB_CLIENT_USERS);
    }
}
