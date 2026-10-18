package com.example.authsvc.config;

import com.example.authsvc.domain.port.MfaChallengeStore;
import com.example.authsvc.infrastructure.security.mfa.MfaSecretEncryptionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MfaConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withUserConfiguration(MfaConfig.class);

    @Test
    void mfaDisabled_noSecretUtilOrChallengeStoreBean() {
        contextRunner
                .withPropertyValues("app.mfa.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MfaSecretEncryptionUtil.class);
                    assertThat(context).doesNotHaveBean(MfaChallengeStore.class);
                });
    }

    @Test
    void mfaEnabled_secretUtilAndChallengeStoreBeanPresent() {
        contextRunner
                .withPropertyValues(
                        "app.mfa.enabled=true",
                        "mfa.secret-encryption-key=" + java.util.Base64.getEncoder().encodeToString(new byte[32]))
                .run(context -> {
                    assertThat(context).hasSingleBean(MfaSecretEncryptionUtil.class);
                    assertThat(context).hasSingleBean(MfaChallengeStore.class);
                });
    }
}
