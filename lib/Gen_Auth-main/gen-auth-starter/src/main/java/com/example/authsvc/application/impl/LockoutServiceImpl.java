package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.common.exception.AccountLockedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockoutServiceImpl implements LockoutService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final String FAILURES_PREFIX = "auth:lockout:failures:";
    private static final String LOCKED_PREFIX = "auth:lockout:locked:";

    private final StringRedisTemplate redis;

    @Override
    public void checkLockout(String email, String ip) {
        log.debug("lockout.check email={} ip={}", email, ip);
        if (Boolean.TRUE.equals(redis.hasKey(lockedKey(email, ip)))) {
            log.warn("lockout.blocked email={} ip={}", email, ip);
            throw new AccountLockedException();
        }
    }

    @Override
    public void recordFailure(String email, String ip) {
        String failureKey = failuresKey(email, ip);
        Long count = redis.opsForValue().increment(failureKey);
        if (count != null && count == 1L) {
            redis.expire(failureKey, LOCKOUT_WINDOW);
        }
        log.debug("lockout.failure_recorded email={} ip={} count={}", email, ip, count);
        if (count != null && count >= MAX_FAILURES) {
            redis.opsForValue().set(lockedKey(email, ip), "1", LOCKOUT_DURATION);
            log.warn("lockout.triggered email={} ip={} failures={}", email, ip, count);
        }
    }

    @Override
    public void clearFailure(String email, String ip) {
        log.debug("lockout.cleared email={} ip={}", email, ip);
        redis.delete(failuresKey(email, ip));
        redis.delete(lockedKey(email, ip));
    }

    private String failuresKey(String email, String ip) {
        return FAILURES_PREFIX + email.toLowerCase() + ":" + ip;
    }

    private String lockedKey(String email, String ip) {
        return LOCKED_PREFIX + email.toLowerCase() + ":" + ip;
    }
}
