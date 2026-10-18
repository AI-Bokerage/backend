package com.example.authsvc.config;

import com.example.authsvc.api.controller.OtpController;
import com.example.authsvc.application.impl.OtpServiceImpl;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.domain.port.OtpStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OtpConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withBean(EmailService.class, () -> mock(EmailService.class))
            .withUserConfiguration(OtpConfig.class, OtpServiceImpl.class, OtpController.class);

    @Test
    void enabled_beansPresent() {
        contextRunner
                .withPropertyValues("app.otp.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OtpStore.class);
                    assertThat(context).hasSingleBean(OtpServiceImpl.class);
                    assertThat(context).hasSingleBean(OtpController.class);
                });
    }

    @Test
    void disabled_beansAbsent() {
        contextRunner
                .withPropertyValues("app.otp.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OtpStore.class);
                    assertThat(context).doesNotHaveBean(OtpServiceImpl.class);
                    assertThat(context).doesNotHaveBean(OtpController.class);
                });
    }
}
