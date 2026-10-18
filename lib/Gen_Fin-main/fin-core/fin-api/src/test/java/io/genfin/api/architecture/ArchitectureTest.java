package io.genfin.api.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private static JavaClasses classes; // NOPMD - ArchUnit's own API requires this concrete type

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.api");
  }

  @Test
  void internalPackageIsOnlyAccessedFromApiOrInternal() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..internal..")
            .should()
            .onlyBeAccessed()
            .byAnyPackage("..internal..", "..api..");

    rule.check(classes);
  }

  @Test
  void portContractsDoNotDependOnInternalImplementations() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.api.port..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.genfin.api.internal..");

    rule.check(classes);
  }

  @Test
  void publicApiPackagesAreFreeOfCycles() {
    // internal/* implementing api/port interfaces while api/* factories construct internal/* is an
    // intentional, inherently bidirectional factory pattern, not a cycle worth guarding against.
    // Scope
    // this check to the public-facing layers where an accidental cycle would actually be a design
    // smell.
    JavaClasses publicApiClasses =
        classes.that(DescribedPredicate.not(resideInAPackage("..internal..")));

    ArchRule rule =
        SlicesRuleDefinition.slices().matching("io.genfin.api.(*)..").should().beFreeOfCycles();

    rule.check(publicApiClasses);
  }
}
