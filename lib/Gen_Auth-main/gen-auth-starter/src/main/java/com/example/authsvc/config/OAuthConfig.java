package com.example.authsvc.config;

import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.config.properties.CookieProperties;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.cache.RedisOAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginFailureHandler;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginSuccessHandler;
import com.example.authsvc.infrastructure.security.oauth.RedisOAuth2AuthorizationRequestRepository;
import com.example.authsvc.infrastructure.security.oauth.TenantAwareOAuth2AuthorizationRequestResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Registers the OAuth login collaborators that have no {@code @Component} of
 * their own. Only active when {@code app.oauth.enabled=true}. Note: enabling
 * this without also configuring at least one
 * {@code spring.security.oauth2.client.registration.<id>.*} entry means
 * {@link ClientRegistrationRepository} is never registered by Spring Boot,
 * so {@link #oAuth2AuthorizationRequestResolver} fails to construct and the
 * application fails fast at startup — this is intentional, not a bug.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.oauth", name = "enabled", havingValue = "true")
public class OAuthConfig {

    @Bean
    public OAuthSignupChallengeStore oAuthSignupChallengeStore(StringRedisTemplate redisTemplate) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new RedisOAuthSignupChallengeStore(redisTemplate, mapper);
    }

    // SecurityJackson2Modules is deprecated for removal in favour of the
    // Jackson 3 org.springframework.security.jackson.SecurityJacksonModules;
    // this whole codebase is still on Jackson 2's ObjectMapper, so the
    // jackson2 variant is the matching API until that migration happens.
    @SuppressWarnings("removal")
    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository(
            StringRedisTemplate redisTemplate, CookieProperties cookieProperties) {
        // SecurityJackson2Modules auto-discovers OAuth2ClientJackson2Module from
        // the classpath and turns on the default typing those mixins need —
        // behind an allow-list validator scoped to Spring Security's own known
        // types, rather than the laissez-faire validator a bare ObjectMapper
        // would supply. This mapper reads JSON out of Redis, so the narrower
        // validator matters.
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        return new RedisOAuth2AuthorizationRequestRepository(redisTemplate, mapper, cookieProperties);
    }

    @Bean
    public OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new TenantAwareOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    // Concrete return types, not Spring Security's generic
    // AuthenticationSuccessHandler/AuthenticationFailureHandler: this is a
    // starter library, and a host app declaring its own bean of either generic
    // interface would make SecurityConfig's constructor autowiring ambiguous.
    @Bean
    public OAuthLoginSuccessHandler oAuthLoginSuccessHandler(
            AuthUserJpaRepository userRepo,
            AuthUserOAuthIdentityJpaRepository identityRepo,
            OAuthSignupChallengeStore signupChallengeStore,
            LoginExecutionService loginExecutor,
            AuthCookieFactory cookieFactory,
            AuthBehaviorProperties behaviorProps,
            PlatformTransactionManager txManager) {
        return new OAuthLoginSuccessHandler(
                userRepo, identityRepo, signupChallengeStore, loginExecutor, cookieFactory, behaviorProps, txManager);
    }

    @Bean
    public OAuthLoginFailureHandler oAuthLoginFailureHandler() {
        return new OAuthLoginFailureHandler();
    }
}
