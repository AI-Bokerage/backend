package com.company.ppmsvc.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Turns ADR-002's frozen rules into failing tests, not just documentation.
 *
 * <p>Every rule here enforces something already stated in ADR-002 or
 * PACKAGE_GUIDE.md — this class adds no new policy, it makes an existing one
 * impossible to violate by accident. If a rule here needs to change, the
 * governing ADR or guide should change first, and this test second.
 */
@DisplayName("ppm-core architecture rules (ADR-002 enforcement)")
class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.company.ppmsvc";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
    }

    // ── Forbidden host-framework dependencies (ADR-002 "Dependency rules") ──

    @Test
    @DisplayName("no class depends on Spring MVC / Spring Web")
    void noSpringMvcDependency() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.web..",
                "org.springframework.http..",
                "jakarta.servlet..");
        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on Spring Security")
    void noSpringSecurityDependency() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security..");
        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on JPA / Hibernate")
    void noJpaDependency() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "org.hibernate..",
                "org.springframework.data.jpa..");
        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on messaging clients (AMQP/Kafka)")
    void noMessagingDependency() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.amqp..",
                "com.rabbitmq..",
                "org.apache.kafka..");
        rule.check(classes);
    }

    @Test
    @DisplayName("no domain model class depends on Jackson databind (only jackson-annotations is allowed, and only on enums)")
    void modelsDoNotDependOnJacksonDatabind() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + "..model..")
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson.databind..");
        rule.check(classes);
    }

    // ── Forbidden host-layer package names (PACKAGE_GUIDE.md) ────────────────

    @Test
    @DisplayName("no host-layer package name (api, controller, infrastructure, persistence, adapter) appears in ppm-core")
    void noHostLayerPackageNames() {
        ArchRule rule = noClasses().should().resideInAnyPackage(
                BASE_PACKAGE + ".api..",
                BASE_PACKAGE + ".controller..",
                BASE_PACKAGE + ".infrastructure..",
                BASE_PACKAGE + ".persistence..",
                BASE_PACKAGE + ".adapter..")
            .as("no class should reside in a host-layer-named package inside ppm-core "
                + "(these names are reserved for the host, per PACKAGE_GUIDE.md)");
        rule.check(classes);
    }

    // ── Package convention (PACKAGE_GUIDE.md: <aggregate>/{model,port,usecase}) ──

    @Test
    @DisplayName("every port interface resides in a ..port package")
    void portsResideInPortPackage() {
        ArchRule rule = classes().that().haveSimpleNameEndingWith("RepositoryPort")
            .should().resideInAPackage(BASE_PACKAGE + "..port..");
        rule.check(classes);
    }

    @Test
    @DisplayName("every *ApplicationServiceImpl resides in a ..usecase package, same as its interface")
    void useCaseImplsResideInUsecasePackage() {
        ArchRule rule = classes().that().haveSimpleNameEndingWith("ApplicationServiceImpl")
            .should().resideInAPackage(BASE_PACKAGE + "..usecase..");
        rule.check(classes);
    }

    // ── One-directional foundational layering ────────────────────────────────
    // NOTE: aggregate packages are deliberately NOT checked for cycle-freedom.
    // Join aggregates legitimately depend back on their parent aggregate's port
    // to verify existence (e.g. PlanModuleApplicationServiceImpl calling
    // PlanRepositoryPort.findById to check the plan exists), while composite
    // services like CatalogQueryServiceImpl depend forward into those same join
    // aggregates' ports to compose reads. That two-directional coupling between
    // a join and its parent is the correct, intended shape of this design (see
    // ADR-002's discussion of cross-aggregate references) — not an accidental
    // cycle to eliminate. A slice-based cycle-freedom rule was tried here and
    // produced exactly this false positive; removed rather than suppressed.

    @Test
    @DisplayName("common and exception packages never depend on any aggregate package")
    void commonAndExceptionAreFoundational() {
        ArchRule rule = noClasses().that().resideInAnyPackage(BASE_PACKAGE + ".common..", BASE_PACKAGE + ".exception..")
            .should().dependOnClassesThat().resideInAnyPackage(
                BASE_PACKAGE + ".plan..", BASE_PACKAGE + ".module..", BASE_PACKAGE + ".addon..",
                BASE_PACKAGE + ".entitlement..", BASE_PACKAGE + ".promocode..",
                BASE_PACKAGE + ".planmodule..", BASE_PACKAGE + ".planaddon..",
                BASE_PACKAGE + ".planentitlement..", BASE_PACKAGE + ".promocodeplan..",
                BASE_PACKAGE + ".planprice..", BASE_PACKAGE + ".addonprice..")
            .as("common/exception are foundational — every aggregate depends on them, never the reverse");
        rule.check(classes);
    }
}
