package com.example.tnt_svc.saga;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningLockServiceTest {

    static GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    static ProvisioningLockService lockService;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        lockService = new ProvisioningLockService(redisTemplate);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Test
    void tryLockSucceedsWhenUnlocked() {
        Optional<String> token = lockService.tryLock(UUID.randomUUID(), Duration.ofMinutes(1));
        assertThat(token).isPresent();
    }

    @Test
    void tryLockFailsWhenAlreadyLocked() {
        UUID tenantId = UUID.randomUUID();
        lockService.tryLock(tenantId, Duration.ofMinutes(1));

        Optional<String> second = lockService.tryLock(tenantId, Duration.ofMinutes(1));

        assertThat(second).isEmpty();
    }

    @Test
    void unlockWithCorrectTokenReleasesLock() {
        UUID tenantId = UUID.randomUUID();
        String token = lockService.tryLock(tenantId, Duration.ofMinutes(1)).orElseThrow();

        lockService.unlock(tenantId, token);

        assertThat(lockService.tryLock(tenantId, Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void unlockWithWrongTokenDoesNotReleaseLock() {
        UUID tenantId = UUID.randomUUID();
        lockService.tryLock(tenantId, Duration.ofMinutes(1));

        lockService.unlock(tenantId, "wrong-token");

        assertThat(lockService.tryLock(tenantId, Duration.ofMinutes(1))).isEmpty();
    }
}
