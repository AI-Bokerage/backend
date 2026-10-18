package io.genfin.providerapi.architecture;

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

class ProviderApiArchitectureTest {

  private static JavaClasses classes; // NOPMD - ArchUnit's own API requires this concrete type

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.providerapi");
  }

  @Test
  void portContractsDoNotDependOnInternalImplementations() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.providerapi.port..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.genfin.providerapi.internal..");

    rule.check(classes);
  }

  @Test
  void noProviderSpecificConceptsLeakIntoThePublicModel() {
    ArchRule rule =
        noClasses()
            .should()
            .haveNameMatching(".*(Stripe|Razorpay|PayPal|Adyen|Cashfree|PhonePe|PayU|Http|Sdk)$");

    rule.check(classes);
  }

  @Test
  void featurePackagesAreFreeOfCycles() {
    JavaClasses featureClasses =
        classes.that(
            DescribedPredicate.not(resideInAPackage("..internal.."))
                .and(DescribedPredicate.not(resideInAPackage("..port.."))));

    ArchRule rule =
        SlicesRuleDefinition.slices()
            .matching("io.genfin.providerapi.(*)..")
            .should()
            .beFreeOfCycles();

    rule.check(featureClasses);
  }
}
