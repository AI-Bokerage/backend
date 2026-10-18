package com.example.authsvc.config;

import com.example.authsvc.domain.port.OtpStore;
import com.example.authsvc.infrastructure.cache.RedisOtpStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")
public class OtpConfig {

    @Bean
    public OtpStore otpStore(StringRedisTemplate redisTemplate) {
        return new RedisOtpStore(redisTemplate);
    }
}
