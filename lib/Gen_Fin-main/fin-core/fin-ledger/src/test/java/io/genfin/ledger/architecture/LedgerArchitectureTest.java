package io.genfin.ledger.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LedgerArchitectureTest {

  private static JavaClasses classes; // NOPMD - ArchUnit's own API requires this concrete type

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.ledger");
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
  void noCpmsConceptsLeakIntoTheLedgerModel() {
    ArchRule rule =
        noClasses().should().haveNameMatching(".*(OrderId|ProjectId|TenantId|SubscriptionId)$");

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
  void portContractsDoNotDependOnInternalImplementations() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.ledger.port..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.genfin.ledger.internal..");

    rule.check(classes);
  }

  @Test
  void noPersistenceOrTransportConcernsLeakIntoTheModule() {
    ArchRule rule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "javax.persistence..",
                "jakarta.persistence..",
                "org.springframework..",
                "com.fasterxml.jackson..",
                "com.google.gson..");

    rule.check(classes);
  }

  @Test
  void featurePackagesAreFreeOfCycles() {
    JavaClasses featureClasses =
        classes.that(
            DescribedPredicate.not(resideInAPackage("..internal.."))
                .and(DescribedPredicate.not(resideInAPackage("..port.."))));

    ArchRule rule =
        SlicesRuleDefinition.slices().matching("io.genfin.ledger.(*)..").should().beFreeOfCycles();

    rule.check(featureClasses);
  }

  @Test
  void moduleInfoNeverExportsAnInternalPackage() throws IOException {
    Path moduleInfo = Path.of("src/main/java/module-info.java");
    List<String> exportLines =
        Files.readAllLines(moduleInfo).stream().filter(line -> line.contains("exports")).toList();

    assertThat(exportLines).isNotEmpty();
    assertThat(exportLines).noneMatch(line -> line.contains(".internal"));
  }

  @Test
  void mainSourceUsesNoInstanceofOrSwitchWhereAnExtensionPointBelongs() throws IOException {
    // Gen-Fin's ledger is configured entirely via Strategy/Policy/Registry/SPI - dispatch on a
    // fact/account/rule type must go through a registered extension, never a bare instanceof or
    // switch. This is a regression guard, not a style nit: main source is already clean of both.
    Path mainSourceRoot = Path.of("src/main/java/io/genfin/ledger");
    try (Stream<Path> files = Files.walk(mainSourceRoot)) {
      List<Path> offenders =
          files
              .filter(path -> path.toString().endsWith(".java"))
              .filter(LedgerArchitectureTest::containsInstanceofOrSwitch)
              .toList();

      assertThat(offenders).isEmpty();
    }
  }

  private static boolean containsInstanceofOrSwitch(Path path) {
    try {
      String source = Files.readString(path);
      return source.contains("instanceof ")
          || source.contains("switch (")
          || source.contains("switch(");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
