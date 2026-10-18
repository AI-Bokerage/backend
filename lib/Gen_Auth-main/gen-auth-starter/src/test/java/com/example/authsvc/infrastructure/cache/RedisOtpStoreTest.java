package com.example.authsvc.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOtpStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void issue_storesHashWithTtl_returnsGeneratedId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisOtpStore store = new RedisOtpStore(redisTemplate);

        UUID otpId = store.issue("hash123", Duration.ofMinutes(5));

        assertThat(otpId).isNotNull();
        verify(valueOperations).set(eq("otp:" + otpId), eq("hash123"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void verifyAndConsume_matchingHash_returnsTrueAndDeletes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID otpId = UUID.randomUUID();
        when(valueOperations.get("otp:" + otpId)).thenReturn("hash123");

        boolean result = new RedisOtpStore(redisTemplate).verifyAndConsume(otpId, "hash123");

        assertThat(result).isTrue();
        verify(redisTemplate).delete("otp:" + otpId);
    }

    @Test
    void verifyAndConsume_mismatchedHash_returnsFalseButStillDeletes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID otpId = UUID.randomUUID();
        when(valueOperations.get("otp:" + otpId)).thenReturn("hash123");

        boolean result = new RedisOtpStore(redisTemplate).verifyAndConsume(otpId, "wrong-hash");

        assertThat(result).isFalse();
        verify(redisTemplate).delete("otp:" + otpId);
    }

    @Test
    void verifyAndConsume_missingId_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID otpId = UUID.randomUUID();
        when(valueOperations.get("otp:" + otpId)).thenReturn(null);

        boolean result = new RedisOtpStore(redisTemplate).verifyAndConsume(otpId, "any-hash");

        assertThat(result).isFalse();
    }
}
