package com.example.authsvc.config.redis;

import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.cache.RedisMagicLinkStore;
import com.example.authsvc.infrastructure.cache.RedisRefreshTokenStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate stores and retrieves all values as plain UTF-8 strings.
     * Refresh-token metadata is serialised to/from JSON explicitly in
     * {@link RedisRefreshTokenStore} using a configured ObjectMapper, which
     * guarantees concrete-type round-trips without relying on deprecated
     * GenericJackson2JsonRedisSerializer or class-metadata embedding.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        long start = System.currentTimeMillis();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        log.info("redis.initialized connectionFactory={}", connectionFactory.getClass().getSimpleName());
        log.info("perf.startup.redis.ms={}", System.currentTimeMillis() - start);
        return template;
    }

    /**
     * Registered here (not as @Component on the class) so that the
     * @ConditionalOnBean check is evaluated after Spring Boot autoconfiguration
     * has registered RedisConnectionFactory — avoiding a component-scan
     * ordering false-negative.
     */
    @Bean
    public RefreshTokenStore refreshTokenStore(StringRedisTemplate stringRedisTemplate) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new RedisRefreshTokenStore(stringRedisTemplate, mapper);
    }

    /**
     * Only registered when {@code app.magic-link.enabled=true}. Reuses the
     * same JSON ObjectMapper configuration as {@link #refreshTokenStore}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
    public MagicLinkStore magicLinkStore(StringRedisTemplate stringRedisTemplate) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new RedisMagicLinkStore(stringRedisTemplate, mapper);
    }
}

