package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.MfaChallengeEntry;
import com.example.authsvc.domain.port.MfaChallengeStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMfaChallengeStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private MfaChallengeStore store;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new RedisMfaChallengeStore(redisTemplate, mapper);
    }

    @Test
    void issue_storesEntryAndReturnsNonBlankToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UUID userId = UUID.randomUUID();

        String token = store.issue(userId, false, Duration.ofMinutes(5));

        assertThat(token).isNotBlank();
        verify(valueOps).set(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
    }

    @Test
    void find_unknownToken_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);

        Optional<MfaChallengeEntry> result = store.find("unknown-token");

        assertThat(result).isEmpty();
    }

    @Test
    void issueThenFind_roundTrips() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UUID userId = UUID.randomUUID();
        java.util.Map<String, String> fakeRedis = new java.util.HashMap<>();

        org.mockito.Mockito.doAnswer(inv -> {
            fakeRedis.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(valueOps.get(any())).thenAnswer(inv -> fakeRedis.get((String) inv.getArgument(0)));

        String token = store.issue(userId, true, Duration.ofMinutes(5));
        Optional<MfaChallengeEntry> found = store.find(token);

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().enrollmentRequired()).isTrue();
        assertThat(found.get().attempts()).isZero();
    }
}
