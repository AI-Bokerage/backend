package com.example.authsvc.api.controller;

import com.example.authsvc.application.service.ClientTokenService;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class V1InternalTokenControllerConditionalRegistrationTest {

    private static InternalHmacAuthProperties propertiesWithPaths(String... paths) {
        InternalHmacAuthProperties properties = new InternalHmacAuthProperties();
        properties.setTargetPaths(List.of(paths));
        return properties;
    }

    @Test
    void hmacOff_controllerAbsent() {
        new ApplicationContextRunner()
                .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
                .withBean(InternalHmacAuthProperties.class,
                        () -> propertiesWithPaths("/v1/impersonation-token"))
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(V1InternalTokenController.class));
    }

    @Test
    void hmacOnOnly_controllerPresentBothHandlersBackingBeansAbsent() {
        new ApplicationContextRunner()
                .withBean(InternalHmacAuthProperties.class, InternalHmacAuthProperties::new)
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(V1InternalTokenController.class);
                    assertThat(context).doesNotHaveBean(ImpersonationTokenService.class);
                    assertThat(context).doesNotHaveBean(ClientTokenService.class);
                });
    }

    @Test
    void hmacAndSuperAdminOn_impersonationTargetPathMissing_contextFailsToStart() {
        new ApplicationContextRunner()
                .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
                .withBean(InternalHmacAuthProperties.class, InternalHmacAuthProperties::new) // empty
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void hmacAndClientTokenOn_clientTokenTargetPathMissing_contextFailsToStart() {
        new ApplicationContextRunner()
                .withBean(ClientTokenService.class, () -> mock(ClientTokenService.class))
                .withBean(InternalHmacAuthProperties.class, InternalHmacAuthProperties::new) // empty
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void hmacAndSuperAdminOnOnly_impersonationTargetPathPresentClientTokenAbsent_contextStartsClean() {
        new ApplicationContextRunner()
                .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
                .withBean(InternalHmacAuthProperties.class,
                        () -> propertiesWithPaths("/v1/impersonation-token"))
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(V1InternalTokenController.class));
    }

    @Test
    void hmacAndClientTokenOnOnly_clientTokenTargetPathPresentImpersonationAbsent_contextStartsClean() {
        new ApplicationContextRunner()
                .withBean(ClientTokenService.class, () -> mock(ClientTokenService.class))
                .withBean(InternalHmacAuthProperties.class,
                        () -> propertiesWithPaths("/v1/client-token"))
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(V1InternalTokenController.class));
    }

    @Test
    void hmacAndBothTokenServicesOn_bothTargetPathsPresent_contextStartsClean() {
        new ApplicationContextRunner()
                .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
                .withBean(ClientTokenService.class, () -> mock(ClientTokenService.class))
                .withBean(InternalHmacAuthProperties.class,
                        () -> propertiesWithPaths("/v1/impersonation-token", "/v1/client-token"))
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(V1InternalTokenController.class));
    }
}
