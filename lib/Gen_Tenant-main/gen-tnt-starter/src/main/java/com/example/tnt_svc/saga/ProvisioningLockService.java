package com.example.tnt_svc.saga;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SET NX PX for acquire, a compare-and-delete Lua script for release — standard Redis lock pattern. */
@Component
public class ProvisioningLockService {

    private static final String UNLOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public ProvisioningLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    }

    private String lockKey(UUID tenantId) {
        return "gentnt:provisioning-lock:" + tenantId;
    }

    public Optional<String> tryLock(UUID tenantId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(tenantId), token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public void unlock(UUID tenantId, String token) {
        redisTemplate.execute(unlockScript, List.of(lockKey(tenantId)), token);
    }
}
