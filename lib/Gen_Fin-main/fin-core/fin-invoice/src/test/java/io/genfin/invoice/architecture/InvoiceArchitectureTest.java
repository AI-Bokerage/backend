package io.genfin.invoice.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InvoiceArchitectureTest {

  private static JavaClasses classes; // NOPMD - ArchUnit's own API requires this concrete type

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.invoice");
  }

  @Test
  void portContractsDoNotDependOnInternalImplementations() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.invoice.port..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.genfin.invoice.internal..");

    rule.check(classes);
  }

  @Test
  void noApplicationSpecificConceptsLeakIntoThePublicModel() {
    ArchRule rule =
        noClasses()
            .should()
            .haveNameMatching(
                ".*(Project|Milestone|Tenant|Vendor|PurchaseOrder|CustomerEntity|Subscription|Plan|BillingCycle)$");

    rule.check(classes);
  }

  @Test
  void featurePackagesAreFreeOfCycles() {
    // Same shape as fin-money: internal/port legitimately reference sibling feature-package policy
    // types used as SPI method parameters — an inherent parameter relationship, not a cycle.
    JavaClasses featureClasses =
        classes.that(
            DescribedPredicate.not(resideInAPackage("..internal.."))
                .and(DescribedPredicate.not(resideInAPackage("..port.."))));

    ArchRule rule =
        SlicesRuleDefinition.slices().matching("io.genfin.invoice.(*)..").should().beFreeOfCycles();

    rule.check(featureClasses);
  }
}
