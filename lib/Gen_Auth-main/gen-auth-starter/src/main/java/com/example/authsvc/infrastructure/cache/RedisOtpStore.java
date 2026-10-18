package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.port.OtpStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis-backed {@link OtpStore}. Key schema: {@code otp:<otpId>} → the SHA-256
 * hex hash of the code (not JSON — nothing else to store), TTL from {@link #issue}.
 *
 * <p>{@code verifyAndConsume} does a plain GET + compare + DELETE (accepted
 * non-atomicity vs. a Lua script — a double-fire race only wastes one extra
 * failed attempt, not a security hole, since the value is deleted either way
 * after the first read completes).
 */
@RequiredArgsConstructor
public class RedisOtpStore implements OtpStore {

    static final String KEY_PREFIX = "otp:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public UUID issue(String codeHash, Duration ttl) {
        UUID otpId = UUID.randomUUID();
        redisTemplate.opsForValue().set(KEY_PREFIX + otpId, codeHash, ttl);
        return otpId;
    }

    @Override
    public boolean verifyAndConsume(UUID otpId, String codeHash) {
        String key = KEY_PREFIX + otpId;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            return false;
        }
        redisTemplate.delete(key);
        return Objects.equals(stored, codeHash);
    }
}
