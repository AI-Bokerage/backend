package com.company.bsmsvc.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Boundary tripwire for bsm-core, installed in Phase 1 ahead of any migrated business logic.
 *
 * <p>bsm-core has stricter forbidden dependencies than ppm-core's equivalent (see Gen_PPM's
 * ArchitectureTest): BSM must additionally exclude Resilience4j and the Stripe/Razorpay payment
 * SDKs, since bsm-svc's host layer talks to payment providers directly and that must never leak
 * into the library. This is a deliberate BSM difference, not a gap to reconcile with ppm-core.
 *
 * <p>bsm-core is empty of production code this phase, so every rule here runs over zero (or
 * near-zero, i.e. just package-info) real classes and passes vacuously — ArchUnit's default
 * behaviour on an empty "that()" match is a pass, not a failure. That's intentional: this test is
 * a tripwire staged before content arrives, not a statement that the boundary has been verified
 * against real code yet.
 */
@DisplayName("bsm-core architecture rules (library/host boundary)")
class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.company.bsmsvc";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
    }

    @Test
    @DisplayName("no class depends on host-framework or payment-provider types")
    void noForbiddenHostDependency() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.web..",
                "org.springframework.http..",
                "jakarta.servlet..",
                "org.springframework.security..",
                "jakarta.persistence..",
                "org.hibernate..",
                "org.springframework.data.jpa..",
                "org.springframework.amqp..",
                "com.rabbitmq..",
                "org.apache.kafka..",
                "io.github.resilience4j..",
                "com.stripe..",
                "com.razorpay..")
            .as("no class in bsm-core should depend on Spring MVC, Spring Security, JPA/Hibernate, "
                + "AMQP/Kafka messaging clients, Resilience4j, or the Stripe/Razorpay SDKs — these "
                + "are host-layer/provider concerns")
            .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("no host-layer package name (api, controller, infrastructure, persistence, adapter, messaging) appears in bsm-core")
    void noHostLayerPackageNames() {
        ArchRule rule = noClasses().should().resideInAnyPackage(
                BASE_PACKAGE + ".api..",
                BASE_PACKAGE + ".controller..",
                BASE_PACKAGE + ".infrastructure..",
                BASE_PACKAGE + ".persistence..",
                BASE_PACKAGE + ".adapter..",
                BASE_PACKAGE + ".messaging..")
            .as("no class should reside in a host-layer-named package inside bsm-core "
                + "(these names are reserved for bsm-svc, the host)")
            .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("domain must not depend on application")
    void domainDoesNotDependOnApplication() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".domain..")
            .should().dependOnClassesThat().resideInAPackage(BASE_PACKAGE + ".application..")
            .as("domain is the innermost layer — application depends on domain, never the reverse")
            .allowEmptyShould(true);
        rule.check(classes);
    }
}
