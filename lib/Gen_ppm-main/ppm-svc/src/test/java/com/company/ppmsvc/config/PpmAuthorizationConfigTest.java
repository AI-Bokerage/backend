package com.company.ppmsvc.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.ppmsvc.infrastructure.security.PermitAllPpmAuthorizationService;
import com.company.ppmsvc.infrastructure.security.PlatformPpmAuthorizationService;
import com.company.ppmsvc.security.PpmAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class PpmAuthorizationConfigTest {

    @Test
    void defaultBeanMethod_isPermitAll() {
        assertThat(new PpmAuthorizationConfig().defaultPpmAuthorizationService())
            .isInstanceOf(PermitAllPpmAuthorizationService.class);
    }

    @Test
    void thisPlatform_wiresPlatformImplementation_whenNoConsumerBeanDefined() {
        new ApplicationContextRunner().withUserConfiguration(PpmAuthorizationConfig.class).run(context ->
            assertThat(context.getBean(PpmAuthorizationService.class))
                .isInstanceOf(PlatformPpmAuthorizationService.class));
    }

    /**
     * A future consumer that only pulls in the library's reusable default
     * ({@code @ConditionalOnMissingBean}) — not this platform's own explicit
     * {@link PlatformPpmAuthorizationService} bean, which is this deployment's
     * choice and not part of the reusable default mechanism.
     */
    @Test
    void libraryDefault_appliesOnlyWhenConsumerDefinesNoBean() {
        new ApplicationContextRunner().withUserConfiguration(LibraryDefaultConfig.class).run(context ->
            assertThat(context.getBean(PpmAuthorizationService.class))
                .isInstanceOf(PermitAllPpmAuthorizationService.class));
    }

    @Test
    void consumerOverride_winsOverLibraryDefault() {
        new ApplicationContextRunner()
            .withUserConfiguration(CustomAuthorizationConfig.class, LibraryDefaultConfig.class)
            .run(context ->
                assertThat(context.getBean(PpmAuthorizationService.class))
                    .isInstanceOf(CustomAuthorizationConfig.MyAuthorizationService.class));
    }

    @Configuration
    static class LibraryDefaultConfig {

        @Bean
        @ConditionalOnMissingBean(PpmAuthorizationService.class)
        PpmAuthorizationService defaultPpmAuthorizationService() {
            return new PermitAllPpmAuthorizationService();
        }
    }

    @Configuration
    static class CustomAuthorizationConfig {

        @Bean
        PpmAuthorizationService customPpmAuthorizationService() {
            return new MyAuthorizationService();
        }

        static class MyAuthorizationService extends PermitAllPpmAuthorizationService {
        }
    }
}
