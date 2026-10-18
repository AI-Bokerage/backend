package com.example.authsvc.config;

import com.example.authsvc.domain.port.MfaChallengeStore;
import com.example.authsvc.infrastructure.cache.RedisMfaChallengeStore;
import com.example.authsvc.infrastructure.security.mfa.MfaSecretEncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Registers the MFA collaborators that have no {@code @Component} of their
 * own — {@link MfaSecretEncryptionUtil} (needs constructor-injected config)
 * and {@link MfaChallengeStore} (needs an MFA-specific {@code ObjectMapper}).
 * Only active when {@code app.mfa.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mfa", name = "enabled", havingValue = "true")
public class MfaConfig {

    @Bean
    public MfaSecretEncryptionUtil mfaSecretEncryptionUtil(
            @Value("${mfa.secret-encryption-key}") String base64Secret) {
        return new MfaSecretEncryptionUtil(base64Secret);
    }

    @Bean
    public MfaChallengeStore mfaChallengeStore(StringRedisTemplate redisTemplate) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new RedisMfaChallengeStore(redisTemplate, mapper);
    }
}
