package com.example.authsvc.config;

import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.config.properties.CookieProperties;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginFailureHandler;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuthConfigTest {

    private static ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email")
                .build();
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withBean(AuthUserJpaRepository.class, () -> mock(AuthUserJpaRepository.class))
            .withBean(AuthUserOAuthIdentityJpaRepository.class, () -> mock(AuthUserOAuthIdentityJpaRepository.class))
            .withBean(LoginExecutionService.class, () -> mock(LoginExecutionService.class))
            .withBean(AuthCookieFactory.class, () -> mock(AuthCookieFactory.class))
            .withBean(AuthBehaviorProperties.class, AuthBehaviorProperties::new)
            .withBean(CookieProperties.class, CookieProperties::new)
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withUserConfiguration(OAuthConfig.class);

    @Test
    void oauthDisabled_noOAuthBeansRegistered() {
        contextRunner
                .withPropertyValues("app.oauth.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OAuthSignupChallengeStore.class);
                    assertThat(context).doesNotHaveBean(AuthorizationRequestRepository.class);
                    assertThat(context).doesNotHaveBean(OAuthLoginSuccessHandler.class);
                });
    }

    @Test
    void oauthEnabled_allBeansPresent() {
        contextRunner
                .withBean(ClientRegistrationRepository.class,
                        () -> new InMemoryClientRegistrationRepository(googleRegistration()))
                .withPropertyValues("app.oauth.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OAuthSignupChallengeStore.class);
                    assertThat(context).hasSingleBean(AuthorizationRequestRepository.class);
                    assertThat(context).hasSingleBean(OAuth2AuthorizationRequestResolver.class);
                    assertThat(context).hasSingleBean(OAuthLoginSuccessHandler.class);
                    assertThat(context).hasSingleBean(OAuthLoginFailureHandler.class);
                });
    }

    @Test
    void oauthEnabled_noProviderConfigured_failsFast() {
        contextRunner
                .withPropertyValues("app.oauth.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
