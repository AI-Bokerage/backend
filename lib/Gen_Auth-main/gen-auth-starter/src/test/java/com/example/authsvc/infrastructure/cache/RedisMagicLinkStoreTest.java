package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.MagicLinkEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No live Redis / Testcontainers Redis is wired into this starter's test
 * suite (only a Postgres Testcontainer exists elsewhere); mirrors that by
 * mocking {@link StringRedisTemplate}/{@link ValueOperations} rather than
 * standing up real infrastructure.
 */
@ExtendWith(MockitoExtension.class)
class RedisMagicLinkStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisMagicLinkStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        store = new RedisMagicLinkStore(redisTemplate, objectMapper);
    }

    @Test
    void saveThenFind_roundTripsEntry() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MagicLinkEntry entry = new MagicLinkEntry(
                UUID.randomUUID(), UUID.randomUUID(),
                MagicLinkEntry.PURPOSE_PASSWORD_RESET, Instant.now().plusSeconds(900));
        String tokenHash = "abc123";
        Duration ttl = Duration.ofMinutes(15);

        store.save(tokenHash, entry, ttl);

        verify(valueOperations).set(eq(RedisMagicLinkStore.MAGIC_KEY_PREFIX + tokenHash),
                anyString(), eq(ttl));

        String storedJson = objectMapper.writeValueAsString(entry);
        when(valueOperations.get(RedisMagicLinkStore.MAGIC_KEY_PREFIX + tokenHash)).thenReturn(storedJson);

        Optional<MagicLinkEntry> found = store.find(tokenHash);

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(entry.userId());
        assertThat(found.get().tenantId()).isEqualTo(entry.tenantId());
        assertThat(found.get().purpose()).isEqualTo(entry.purpose());
    }

    @Test
    void find_missingKey_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<MagicLinkEntry> found = store.find("missing-hash");

        assertThat(found).isEmpty();
    }

    @Test
    void delete_removesKey() {
        store.delete("abc123");

        verify(redisTemplate).delete(RedisMagicLinkStore.MAGIC_KEY_PREFIX + "abc123");
    }

    @Test
    void incrementRateCounter_setsTtlOnlyOnFirstCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String counterKey = "127.0.0.1:hash";
        Duration window = Duration.ofHours(1);
        String expectedKey = RedisMagicLinkStore.RATE_LIMIT_PREFIX + counterKey;

        when(valueOperations.increment(expectedKey)).thenReturn(1L);

        long first = store.incrementRateCounter(counterKey, window);

        assertThat(first).isEqualTo(1L);
        verify(redisTemplate).expire(expectedKey, window);
    }

    @Test
    void incrementRateCounter_doesNotResetTtlAfterFirstCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String counterKey = "127.0.0.1:hash";
        Duration window = Duration.ofHours(1);
        String expectedKey = RedisMagicLinkStore.RATE_LIMIT_PREFIX + counterKey;

        when(valueOperations.increment(expectedKey)).thenReturn(2L);

        long second = store.incrementRateCounter(counterKey, window);

        assertThat(second).isEqualTo(2L);
        verify(redisTemplate, never()).expire(anyString(), any());
    }
}
