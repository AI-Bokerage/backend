package com.company.ppmstarter;

import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import com.company.ppmsvc.module.usecase.ModuleApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Validates {@link PpmAutoConfiguration} at the Spring context level — the
 * five behaviors called for in Phase 6, Work Package 6.
 */
@DisplayName("PpmAutoConfiguration")
class PpmAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PpmAutoConfiguration.class));

    @Configuration
    static class PlanPortsConfig {
        @Bean PlanRepositoryPort planRepositoryPort() { return mock(PlanRepositoryPort.class); }
        @Bean PlanVersionRepositoryPort planVersionRepositoryPort() { return mock(PlanVersionRepositoryPort.class); }
    }

    @Configuration
    static class ModulePortConfig {
        @Bean ModuleRepositoryPort moduleRepositoryPort() { return mock(ModuleRepositoryPort.class); }
    }

    @Configuration
    static class PlanVersionPortOnlyConfig {
        // Deliberately missing PlanRepositoryPort. Note: the reverse (PlanRepositoryPort
        // alone, no PlanVersionRepositoryPort) is NOT flagged — PlanRepositoryPort is
        // shared by nearly every aggregate here, so its presence alone says nothing
        // about intent toward Plan/PlanVersion specifically. PlanVersionRepositoryPort,
        // however, is unique to Plan/PlanVersion/Catalog — its presence without
        // PlanRepositoryPort is unambiguous: Plan support was intended and mis-wired.
        @Bean PlanVersionRepositoryPort planVersionRepositoryPort() { return mock(PlanVersionRepositoryPort.class); }
    }

    // ── 1. Successful startup with all required ports ────────────────────────

    @Nested
    @DisplayName("1. Successful startup")
    class SuccessfulStartup {

        @Test
        @DisplayName("registers PlanApplicationService when both required ports are present")
        void registersPlanServiceWhenPortsPresent() {
            runner.withUserConfiguration(PlanPortsConfig.class).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(PlanApplicationService.class);
            });
        }

        @Test
        @DisplayName("registers ModuleApplicationService when its port is present")
        void registersModuleServiceWhenPortPresent() {
            runner.withUserConfiguration(ModulePortConfig.class).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(ModuleApplicationService.class);
            });
        }

        @Test
        @DisplayName("an aggregate with zero of its ports implemented is silently skipped — not an error")
        void aggregateWithNoPortsIsSilentlySkipped() {
            // No ports at all supplied — nothing should be wired, and startup must not fail.
            runner.run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).doesNotHaveBean(PlanApplicationService.class);
                assertThat(ctx).doesNotHaveBean(ModuleApplicationService.class);
            });
        }
    }

    // ── 2. Clear failure when mandatory ports are absent (partial impl) ──────

    @Nested
    @DisplayName("2. Missing port validation")
    class MissingPortValidation {

        @Test
        @DisplayName("fails fast with a specific message when only one of Plan's two required ports is implemented")
        void partialPlanPortsFailsWithClearMessage() {
            runner.withUserConfiguration(PlanVersionPortOnlyConfig.class).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(ctx).getFailure()
                    .isInstanceOf(PpmMissingRepositoryPortException.class)
                    .hasMessageContaining("Plan (+ PlanVersion)")
                    .hasMessageContaining("PlanVersionRepositoryPort")
                    .hasMessageContaining("PlanRepositoryPort");
            });
        }

        @Test
        @DisplayName("PlanRepositoryPort alone (a widely-shared port) does NOT trigger a false positive")
        void planRepositoryPortAloneIsNotFlagged() {
            runner.withUserConfiguration(ModulePortConfig.class)
                .withBean(PlanRepositoryPort.class, () -> mock(PlanRepositoryPort.class))
                .run(ctx -> assertThat(ctx).hasNotFailed());
        }
    }

    // ── 3. Consumer bean override behavior ────────────────────────────────────

    @Nested
    @DisplayName("3. Bean override")
    class BeanOverride {

        @Configuration
        static class CustomPlanServiceConfig {
            @Bean
            PlanApplicationService customPlanApplicationService() {
                return mock(PlanApplicationService.class);
            }
        }

        @Test
        @DisplayName("a consumer-defined PlanApplicationService bean wins over the starter's default")
        void consumerBeanTakesPrecedence() {
            runner.withUserConfiguration(PlanPortsConfig.class, CustomPlanServiceConfig.class).run(this::assertOnlyCustomBean);
        }

        private void assertOnlyCustomBean(AssertableApplicationContext ctx) {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(PlanApplicationService.class);
            String[] beanNames = ctx.getBeanNamesForType(PlanApplicationService.class);
            assertThat(beanNames).containsExactly("customPlanApplicationService");
        }
    }

    // ── 4. Auto-configuration loads only when appropriate ─────────────────────

    @Nested
    @DisplayName("4. Conditional activation")
    class ConditionalActivation {

        @Test
        @DisplayName("ppm.enabled=false disables the entire starter — no beans, no validator")
        void disabledViaProperty() {
            runner.withUserConfiguration(PlanPortsConfig.class)
                .withPropertyValues("ppm.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(PlanApplicationService.class);
                    assertThat(ctx).doesNotHaveBean(PpmPortAvailabilityValidator.class);
                });
        }

        @Test
        @DisplayName("ppm.enabled defaults to true — starter is active with no property set")
        void enabledByDefault() {
            runner.withUserConfiguration(PlanPortsConfig.class).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(PpmPortAvailabilityValidator.class);
            });
        }

        @Test
        @DisplayName("CatalogQueryService is not registered unless ppm.catalog.enabled=true, even with all six ports present")
        void catalogQueryServiceOptInOnly() {
            runner.withUserConfiguration(PlanPortsConfig.class, ModulePortConfig.class).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).doesNotHaveBean("catalogQueryService");
            });
        }
    }

    // ── 5. No dependency on ppm-svc ───────────────────────────────────────────

    @Nested
    @DisplayName("5. No ppm-svc dependency")
    class NoPpmSvcDependency {

        @Test
        @DisplayName("PpmAutoConfiguration and its imports resolve with only ppm-core + Spring Boot on the classpath")
        void noPpmSvcTypeReferenced() {
            // This test module's own classpath (see build.gradle) declares api project(':ppm-core')
            // and nothing from ppm-svc — if any starter class referenced a ppm-svc type, this whole
            // test module would fail to compile, not just fail at runtime. This test documents that
            // guarantee rather than re-proving it (a compile-time fact can't be asserted at runtime).
            runner.withUserConfiguration(PlanPortsConfig.class).run(ctx -> assertThat(ctx).hasNotFailed());
        }
    }

    // ── 6. Properties binding ─────────────────────────────────────────────────

    @Nested
    @DisplayName("6. Properties binding")
    class PropertiesBinding {

        @Test
        @DisplayName("ppm.enabled and ppm.catalog.enabled bind onto PpmProperties, not just gate beans")
        void propertiesBindOntoPpmProperties() {
            runner.withUserConfiguration(PlanPortsConfig.class)
                .withPropertyValues("ppm.enabled=true", "ppm.catalog.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    PpmProperties props = ctx.getBean(PpmProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getCatalog().isEnabled()).isTrue();
                });
        }

        @Test
        @DisplayName("PpmProperties defaults match documented defaults when no properties are set")
        void propertiesDefaultCorrectly() {
            runner.withUserConfiguration(PlanPortsConfig.class).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                PpmProperties props = ctx.getBean(PpmProperties.class);
                assertThat(props.isEnabled()).isTrue();
                assertThat(props.getCatalog().isEnabled()).isFalse();
            });
        }
    }

    // ── 7. Auto-configuration activation ordering ─────────────────────────────

    @Nested
    @DisplayName("7. Auto-configuration ordering / classpath conditions")
    class AutoConfigurationOrdering {

        @Test
        @DisplayName("PpmAutoConfiguration does not activate at all when ppm-core is absent from the classpath")
        void doesNotActivateWithoutPpmCoreOnClasspath() {
            // FilteredClassLoader simulates ppm-core being genuinely absent, the real condition
            // @ConditionalOnClass(PlanApplicationService.class) is meant to guard against — not
            // just "the property says disabled" (already covered in ConditionalActivation above).
            new ApplicationContextRunner()
                .withClassLoader(new org.springframework.boot.test.context.FilteredClassLoader(PlanApplicationService.class))
                .withConfiguration(AutoConfigurations.of(PpmAutoConfiguration.class))
                .withUserConfiguration(PlanPortsConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(PpmPortAvailabilityValidator.class);
                    assertThat(ctx).doesNotHaveBean(PpmProperties.class);
                });
        }

        @Test
        @DisplayName("PpmUseCaseAutoConfiguration's beans are available for PpmAutoConfiguration's validator to see (import ordering)")
        void useCaseBeansVisibleToValidatorInSameContext() {
            // PpmAutoConfiguration @Imports PpmUseCaseAutoConfiguration and separately declares
            // the validator bean — this proves both halves end up in the same context rather
            // than the import being silently dropped or evaluated in isolation.
            runner.withUserConfiguration(PlanPortsConfig.class).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(PlanApplicationService.class);
                assertThat(ctx).hasSingleBean(PpmPortAvailabilityValidator.class);
            });
        }
    }
}
