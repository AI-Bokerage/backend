package io.genfin.refund.architecture;

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

class RefundArchitectureTest {

  private static JavaClasses classes; // NOPMD - ArchUnit's own API requires this concrete type

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.refund");
  }

  @Test
  void portContractsDoNotDependOnInternalImplementations() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.refund.port..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.genfin.refund.internal..");

    rule.check(classes);
  }

  @Test
  void noProviderSpecificConceptsLeakIntoThePublicModel() {
    ArchRule rule =
        noClasses()
            .should()
            .haveNameMatching(
                ".*(Stripe|Razorpay|PayPal|Adyen|Cashfree|PhonePe|PayU|Http|Webhook|Sdk)$");

    rule.check(classes);
  }

  @Test
  void noCpmsConceptsLeakIntoTheRefundModel() {
    ArchRule rule =
        noClasses().should().haveNameMatching(".*(OrderId|ProjectId|TenantId|SubscriptionId)$");

    rule.check(classes);
  }

  @Test
  void doesNotDependOnTheInvoiceEngine() {
    ArchRule rule =
        noClasses().should().dependOnClassesThat().resideInAPackage("io.genfin.invoice..");

    rule.check(classes);
  }

  @Test
  void doesNotDependOnAnyProviderModule() {
    ArchRule rule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.genfin.stripe..", "io.genfin.razorpay..");

    rule.check(classes);
  }

  @Test
  void featurePackagesAreFreeOfCycles() {
    // internal/port both legitimately reference sibling feature-package policy types used as SPI
    // method parameters — an inherent parameter relationship, not a cycle.
    JavaClasses featureClasses =
        classes.that(
            DescribedPredicate.not(resideInAPackage("..internal.."))
                .and(DescribedPredicate.not(resideInAPackage("..port.."))));

    ArchRule rule =
        SlicesRuleDefinition.slices().matching("io.genfin.refund.(*)..").should().beFreeOfCycles();

    rule.check(featureClasses);
  }
}
