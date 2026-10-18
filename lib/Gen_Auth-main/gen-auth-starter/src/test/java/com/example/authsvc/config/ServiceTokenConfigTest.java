package com.example.authsvc.config;

import com.example.authsvc.api.controller.ServiceTokenController;
import com.example.authsvc.application.impl.ServiceTokenServiceImpl;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ServiceTokenConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JwtUtils.class, () -> mock(JwtUtils.class))
            .withUserConfiguration(ServiceTokenServiceImpl.class, ServiceTokenController.class);

    @Test
    void enabled_beansPresent() {
        contextRunner
                .withPropertyValues("app.super-admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ServiceTokenServiceImpl.class);
                    assertThat(context).hasSingleBean(ServiceTokenController.class);
                });
    }

    @Test
    void disabled_beansAbsent() {
        contextRunner
                .withPropertyValues("app.super-admin.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ServiceTokenServiceImpl.class);
                    assertThat(context).doesNotHaveBean(ServiceTokenController.class);
                });
    }
}
