package io.genfin.document.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

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

class DocumentArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.document");
  }

  /** The standard-factory classes (see class javadocs) that legitimately wire up internal impls. */
  private static final String FACTORY_CLASS_PATTERN =
      "io\\.genfin\\.document\\.port\\.(DocumentRenderers|TemplateEngines|PlaceholderResolvers|BrandResolvers|LetterheadResolvers|QrCodeResolvers|DocumentEventPublishers|PreviewRenderers|DocumentValidators)(\\$.*)?";

  @Test
  void portClassesDoNotDependOnInternalClasses() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..port..")
            .and(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "not a standard-factory class (or its nested classes)",
                    cls -> !cls.getName().matches(FACTORY_CLASS_PATTERN)))
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..internal..");
    rule.check(classes);
  }

  @Test
  void apiAndPortClassesDoNotDependOnPdfBox() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage("..api..", "..port..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.apache.pdfbox..");
    rule.check(classes);
  }

  @Test
  void moduleDoesNotDependOnForbiddenFrameworks() {
    ArchRule rule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "javax.persistence..",
                "jakarta.persistence..",
                "com.fasterxml.jackson..",
                "com.google.gson..");
    rule.check(classes);
  }

  @Test
  void moduleDoesNotDependOnSiblingDomainModules() {
    ArchRule rule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.genfin.invoice..",
                "io.genfin.payment..",
                "io.genfin.refund..",
                "io.genfin.ledger..",
                "io.genfin.pricing..",
                "io.genfin.reconciliation..",
                "io.genfin.dunning..");
    rule.check(classes);
  }

  @Test
  void packagesAreFreeOfCycles() {
    ArchRule rule =
        SlicesRuleDefinition.slices()
            .matching("io.genfin.document.(*)..") //
            .should()
            .beFreeOfCycles()
            // DocumentComposers.noOp(), DocumentRenderers.standard(), TemplateEngines.standard()
            // and PlaceholderResolvers.standard() legitimately create internal impls
            .ignoreDependency(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "factory methods in api/port",
                    cls ->
                        cls.getName()
                                .matches(
                                    "io\\.genfin\\.document\\.api\\.compose\\.DocumentComposers(\\$.*)?")
                            || cls.getName().matches(FACTORY_CLASS_PATTERN)),
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "internal classes", cls -> cls.getPackageName().contains(".internal")))
            // PlaceholderContext in api.placeholder needs port interfaces for resolution and
            // formatting
            .ignoreDependency(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "PlaceholderContext",
                    cls ->
                        "io.genfin.document.api.placeholder.PlaceholderContext"
                            .equals(cls.getName())),
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "port package", cls -> "io.genfin.document.port".equals(cls.getPackageName())))
            // TemplateContext in api.template needs port.PlaceholderResolver for delegation
            .ignoreDependency(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "TemplateContext",
                    cls -> "io.genfin.document.api.template.TemplateContext".equals(cls.getName())),
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "port package", cls -> "io.genfin.document.port".equals(cls.getPackageName())))
            // DocumentComposers.templated() legitimately creates internal impls that depend on port
            .ignoreDependency(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "DocumentComposers factory methods",
                    cls ->
                        "io.genfin.document.api.compose.DocumentComposers".equals(cls.getName())),
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "port package", cls -> "io.genfin.document.port".equals(cls.getPackageName())));
    rule.check(classes);
  }

  @Test
  void moduleInfoDoesNotExportInternalPackages() throws IOException {
    Path moduleInfo = Path.of("src/main/java/module-info.java");
    List<String> lines = Files.readAllLines(moduleInfo);
    assertThat(lines).noneMatch(line -> line.contains("exports") && line.contains(".internal"));
  }

  @Test
  void sourceFilesContainNoInstanceofOrSwitch() throws IOException {
    Path sourceRoot = Path.of("src/main/java/io/genfin/document");
    try (var paths = Files.walk(sourceRoot)) {
      List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).toList();
      for (Path file : javaFiles) {
        String content = Files.readString(file);
        assertThat(content).as(file.toString()).doesNotContain("instanceof ");
        assertThat(content).as(file.toString()).doesNotContain("switch (");
        assertThat(content).as(file.toString()).doesNotContain("switch(");
      }
    }
  }
}
