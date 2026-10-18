package io.genfin.tax.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Mirrors {@code io.genfin.money.architecture.MoneyArchitectureTest}. Unlike {@code fin-money}
 * (jurisdiction-agnostic by design), this module IS the India-specific implementation — CGST/SGST/
 * IGST naming is expected throughout, not banned.
 */
class TaxArchitectureTest {

  private static final String FACTORY_CLASS_PATTERN =
      "io\\.genfin\\.tax\\.port\\.(TaxDecisionResolvers|TaxRateProviders|TaxEngines)(\\$.*)?";

  private static JavaClasses classes; // NOPMD - ArchUnit's own API requires this concrete type

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.tax");
  }

  @Test
  void portContractsDoNotDependOnInternalImplementations() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.genfin.tax.port..")
            .and(
                DescribedPredicate.describe(
                    "not a standard-factory class",
                    cls -> !cls.getName().matches(FACTORY_CLASS_PATTERN)))
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.genfin.tax.internal..");

    rule.check(classes);
  }

  @Test
  void moduleInfoDoesNotExportInternalPackages() throws IOException {
    Path moduleInfo = Path.of("src/main/java/module-info.java");
    List<String> lines = Files.readAllLines(moduleInfo);
    assertThat(lines).noneMatch(line -> line.contains("exports") && line.contains(".internal"));
  }

  @Test
  void featurePackagesAreFreeOfCycles() {
    // TaxDecisionResolvers/TaxRateProviders/TaxEngines (port) legitimately construct the internal
    // default implementations they hand back — the same factory-into-internal edge every sibling
    // module's ArchitectureTest carves out (see fin-document's DocumentArchitectureTest).
    ArchRule rule =
        SlicesRuleDefinition.slices()
            .matching("io.genfin.tax.(*)..")
            .should()
            .beFreeOfCycles()
            .ignoreDependency(
                DescribedPredicate.describe(
                    "factory classes in port", cls -> cls.getName().matches(FACTORY_CLASS_PATTERN)),
                DescribedPredicate.describe(
                    "internal classes", cls -> cls.getPackageName().contains(".internal")));
    rule.check(classes);
  }
}
