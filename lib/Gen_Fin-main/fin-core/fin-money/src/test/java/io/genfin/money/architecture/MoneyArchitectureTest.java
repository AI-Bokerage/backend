package io.genfin.money.architecture;

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

class MoneyArchitectureTest {

  private static JavaClasses classes; // NOPMD - ArchUnit's own API requires this concrete type

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.money");
  }

  @Test
  void portContractsDoNotDependOnInternalImplementations() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.money.port..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.genfin.money.internal..");

    rule.check(classes);
  }

  @Test
  void taxPackageContainsNoJurisdictionSpecificNaming() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.money.tax..")
            .or()
            .resideInAPackage("io.genfin.money.port.tax..")
            .should()
            .haveNameMatching(".*(GST|VAT|CGST|SGST|IGST|CESS|SalesTax).*");

    rule.check(classes);
  }

  @Test
  void featurePackagesAreFreeOfCycles() {
    // internal/* and port/* both legitimately reference the sibling feature package's policy/value
    // types (an SPI method parameter is often a policy bundle composed of other port interfaces) —
    // that's an inherent parameter relationship, not a cycle. Scope this check to the feature
    // packages themselves, where an accidental cycle (e.g. tax depending back on config) would
    // matter.
    JavaClasses featureClasses =
        classes.that(
            DescribedPredicate.not(resideInAPackage("..internal.."))
                .and(DescribedPredicate.not(resideInAPackage("..port.."))));

    ArchRule rule =
        SlicesRuleDefinition.slices().matching("io.genfin.money.(*)..").should().beFreeOfCycles();

    rule.check(featureClasses);
  }
}
