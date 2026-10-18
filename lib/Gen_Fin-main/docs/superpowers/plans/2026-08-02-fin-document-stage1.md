# fin-document Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the fin-document module's Stage 1 spine — identity types, the domain-agnostic `DocumentModel`, a no-op `DocumentComposer`, the `DocumentRenderer`/`RendererResolver` SPI, and five built-in data renderers (JSON/Text/Markdown/CSV/XML) — so `DocumentModel → DocumentComposer → ComposedDocument → RendererResolver → DocumentRenderer → RenderResult` works end-to-end and is fully tested.

**Architecture:** Standard Gen-Fin `api`/`port`/`internal` split inside `io.genfin.document`. `api` holds domain-agnostic value types (identity, model, compose, result). `port` holds SPI contracts apps implement or call (`DocumentRenderer`, `DocumentMapper<T>`, `RendererResolver`) plus the `DocumentRenderers` factory. `internal` holds the registry/resolver implementation and the five built-in renderers — never exported from `module-info.java`. Element rendering uses a generic visitor (`DocumentElement.accept(DocumentElementVisitor<R>)`) instead of `instanceof`/`switch`, per this repo's architecture-test convention (see Task 15).

**Tech Stack:** Java (JPMS modules), Gradle, JUnit 5, AssertJ, ArchUnit. Only dependency: `fin-api` (already wired). No PDF/HTML/Spring/DB/HTTP libraries in this stage.

## Global Constraints

- Module `fin-document` (`fin-core/fin-document`) depends on **`fin-api` only** for Stage 1. No `fin-invoice`/`fin-payment`/`fin-refund`/`fin-ledger`/`fin-pricing`/`fin-reconciliation`/`fin-dunning` imports anywhere in this stage.
- Forbidden imports anywhere in the module: `org.springframework..`, `javax.persistence..`, `jakarta.persistence..`, `com.fasterxml.jackson..`, `com.google.gson..`, any PDF/HTML rendering library, any HTTP/cloud-SDK package.
- **No `instanceof` and no `switch`/`switch(` anywhere in `fin-document` source** — this repo's `DunningArchitectureTest`-style check bans both keywords outright. Use polymorphism/visitor dispatch instead (see `DocumentElementVisitor` in Task 4).
- Package layout is exactly `io.genfin.document.api.*`, `io.genfin.document.port`, `io.genfin.document.internal.*`. Nothing under `internal` is ever listed in `module-info.java`'s `exports`.
- Typed IDs follow the existing `Identifier` base-class pattern (`fin-core/fin-api/src/main/java/io/genfin/api/id/Identifier.java`): `final class X extends Identifier`, private constructor, `static X of(String value)`, `static X generate()`, `static X generate(IdentifierGenerator generator)`.
- SPI interfaces apps implement extend `io.genfin.api.port.spi.Extension` (see `WebhookVerifier` in `fin-provider-api`) and live under `port`.
- Test stack: JUnit 5 (`org.junit.jupiter.api.Test`) + AssertJ (`org.assertj.core.api.Assertions.assertThat`). ArchUnit for the architecture test.
- Build/verify command for every task: `./gradlew :fin-document:check` (compiles, runs Spotless, Checkstyle, PMD, Error Prone, JUnit, JaCoCo — the same gate `./gradlew check` runs repo-wide, scoped to this module). Run `./gradlew :fin-document:spotlessApply` first if Spotless fails on formatting alone.

---

### Task 1: Wire test infrastructure + first identity type (`DocumentId`)

**Files:**
- Modify: `fin-core/fin-document/build.gradle.kts`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/DocumentId.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/identity/DocumentIdTest.java`

**Interfaces:**
- Produces: `DocumentId.of(String value): DocumentId`, `DocumentId.generate(): DocumentId`, `DocumentId.generate(IdentifierGenerator): DocumentId`.

- [ ] **Step 1: Add the testing-conventions plugin to `fin-document`'s build file**

`fin-core/fin-document/build.gradle.kts` currently has no test dependencies wired. Update it to:

```kotlin
plugins {
    id("genfin.java-library-conventions")
    id("genfin.testing-conventions")
    id("genfin.publishing-conventions")
}

dependencies {
    api(project(":fin-api"))
}
```

- [ ] **Step 2: Write the failing test**

```java
package io.genfin.document.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentIdTest {

  @Test
  void ofReturnsIdWithGivenValue() {
    DocumentId id = DocumentId.of("doc-123");
    assertThat(id.value()).isEqualTo("doc-123");
  }

  @Test
  void generateReturnsIdWithNonBlankValue() {
    DocumentId id = DocumentId.generate();
    assertThat(id.value()).isNotBlank();
  }

  @Test
  void ofRejectsBlankValue() {
    assertThatThrownBy(() -> DocumentId.of(" "))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }

  @Test
  void equalsIsBasedOnTypeAndValue() {
    assertThat(DocumentId.of("doc-1")).isEqualTo(DocumentId.of("doc-1"));
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.identity.DocumentIdTest"`
Expected: FAIL — `DocumentId` does not exist.

- [ ] **Step 4: Write minimal implementation**

```java
package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class DocumentId extends Identifier {

  private DocumentId(String value) {
    super(value);
  }

  public static DocumentId of(String value) {
    return new DocumentId(value);
  }

  public static DocumentId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static DocumentId generate(IdentifierGenerator generator) {
    return new DocumentId(generator.generate());
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.identity.DocumentIdTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add fin-core/fin-document/build.gradle.kts \
  fin-core/fin-document/src/main/java/io/genfin/document/api/identity/DocumentId.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/identity/DocumentIdTest.java
git commit -m "feat(fin-document): wire test infra and add DocumentId identity type"
```

---

### Task 2: Remaining eight identity types

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/TemplateId.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/RendererId.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/BrandId.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/ThemeId.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/LayoutId.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/LetterheadId.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/AttachmentId.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/identity/WatermarkId.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/identity/IdentityTypesTest.java`

**Interfaces:**
- Consumes: same `Identifier` base class as Task 1.
- Produces: `TemplateId.of/generate`, `RendererId.of/generate`, `BrandId.of/generate`, `ThemeId.of/generate`, `LayoutId.of/generate`, `LetterheadId.of/generate`, `AttachmentId.of/generate`, `WatermarkId.of/generate` — Task 6+ renderer code needs `RendererId`, Task 5 composer needs none of these yet, later stages need the rest.

- [ ] **Step 1: Write the failing test (parameterized across all 8 types)**

```java
package io.genfin.document.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class IdentityTypesTest {

  @Test
  void allIdentityTypesSupportOfAndGenerate() {
    List<Function<String, ? extends Object>> ofFactories =
        List.of(
            TemplateId::of,
            RendererId::of,
            BrandId::of,
            ThemeId::of,
            LayoutId::of,
            LetterheadId::of,
            AttachmentId::of,
            WatermarkId::of);

    for (Function<String, ? extends Object> factory : ofFactories) {
      assertThat(factory.apply("x-1").toString()).contains("x-1");
    }

    assertThat(TemplateId.generate().value()).isNotBlank();
    assertThat(RendererId.generate().value()).isNotBlank();
    assertThat(BrandId.generate().value()).isNotBlank();
    assertThat(ThemeId.generate().value()).isNotBlank();
    assertThat(LayoutId.generate().value()).isNotBlank();
    assertThat(LetterheadId.generate().value()).isNotBlank();
    assertThat(AttachmentId.generate().value()).isNotBlank();
    assertThat(WatermarkId.generate().value()).isNotBlank();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.identity.IdentityTypesTest"`
Expected: FAIL — compile error, classes don't exist.

- [ ] **Step 3: Write minimal implementation (repeat `DocumentId`'s shape once per class name)**

```java
package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class TemplateId extends Identifier {
  private TemplateId(String value) { super(value); }
  public static TemplateId of(String value) { return new TemplateId(value); }
  public static TemplateId generate() { return generate(IdentifierGenerators.uuidV4()); }
  public static TemplateId generate(IdentifierGenerator generator) { return new TemplateId(generator.generate()); }
}
```

Repeat identically for `RendererId`, `BrandId`, `ThemeId`, `LayoutId`, `LetterheadId`, `AttachmentId`, `WatermarkId` — same package `io.genfin.document.api.identity`, same imports, only the class name and its constructor/factory references change.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.identity.IdentityTypesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/identity/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/identity/IdentityTypesTest.java
git commit -m "feat(fin-document): add remaining identity types (Template/Renderer/Brand/Theme/Layout/Letterhead/Attachment/Watermark)"
```

---

### Task 3: `DocumentType` open-value type

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentType.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/StandardDocumentType.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/model/StandardDocumentTypeTest.java`

**Interfaces:**
- Produces: `DocumentType` (marker interface, `String code()`), `StandardDocumentType.of(String code): StandardDocumentType` plus constants `INVOICE`, `RECEIPT`, `REFUND`, `QUOTE`, `LEDGER_REPORT`, `TRIAL_BALANCE`, `STATEMENT`, `RECONCILIATION_REPORT`, `DUNNING_LETTER` — consumed by `DocumentMetadata` in Task 4.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StandardDocumentTypeTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardDocumentType.INVOICE.code()).isEqualTo("INVOICE");
    assertThat(StandardDocumentType.DUNNING_LETTER.code()).isEqualTo("DUNNING_LETTER");
  }

  @Test
  void ofBuildsCustomType() {
    DocumentType custom = StandardDocumentType.of("PURCHASE_ORDER");
    assertThat(custom.code()).isEqualTo("PURCHASE_ORDER");
  }

  @Test
  void equalsIsBasedOnCode() {
    assertThat(StandardDocumentType.of("INVOICE")).isEqualTo(StandardDocumentType.INVOICE);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.model.StandardDocumentTypeTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.model;

public interface DocumentType {
  String code();
}
```

```java
package io.genfin.document.api.model;

import io.genfin.api.exception.ValidationException;

public record StandardDocumentType(String code) implements DocumentType {

  public StandardDocumentType {
    if (code == null || code.isBlank()) {
      throw new ValidationException("DocumentType code must not be blank");
    }
  }

  public static StandardDocumentType of(String code) {
    return new StandardDocumentType(code);
  }

  public static final StandardDocumentType INVOICE = of("INVOICE");
  public static final StandardDocumentType RECEIPT = of("RECEIPT");
  public static final StandardDocumentType REFUND = of("REFUND");
  public static final StandardDocumentType QUOTE = of("QUOTE");
  public static final StandardDocumentType LEDGER_REPORT = of("LEDGER_REPORT");
  public static final StandardDocumentType TRIAL_BALANCE = of("TRIAL_BALANCE");
  public static final StandardDocumentType STATEMENT = of("STATEMENT");
  public static final StandardDocumentType RECONCILIATION_REPORT = of("RECONCILIATION_REPORT");
  public static final StandardDocumentType DUNNING_LETTER = of("DUNNING_LETTER");
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.model.StandardDocumentTypeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentType.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/StandardDocumentType.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/model/StandardDocumentTypeTest.java
git commit -m "feat(fin-document): add DocumentType open-value type with standard constants"
```

---

### Task 4: `DocumentElement` visitor hierarchy + `DocumentAttributes`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentElementVisitor.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentElement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/KeyValueElement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/TableElement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/TextBlockElement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentAttributes.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/model/DocumentElementTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/model/DocumentAttributesTest.java`

**Interfaces:**
- Produces: `DocumentElement.accept(DocumentElementVisitor<R>): R`, `KeyValueElement.of(label, value)`, `TableElement.of(headers, rows)`, `TextBlockElement.of(text)`, `DocumentAttributes.empty()`, `DocumentAttributes.of(Map<String,String>)`, `DocumentAttributes.get(String key): Optional<String>` — every renderer in Tasks 8-12 implements `DocumentElementVisitor<String>` and calls `element.accept(this)`; `DocumentModel`/`DocumentMetadata` in Task 5 use `DocumentAttributes`.

- [ ] **Step 1: Write the failing test for elements**

```java
package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentElementTest {

  private static final class RecordingVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "kv:" + element.label() + "=" + element.value();
    }

    @Override
    public String visitTable(TableElement element) {
      return "table:" + element.headers().size() + "x" + element.rows().size();
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "text:" + element.text();
    }
  }

  @Test
  void keyValueElementDispatchesToVisitKeyValue() {
    DocumentElement element = KeyValueElement.of("Total", "100.00");
    assertThat(element.accept(new RecordingVisitor())).isEqualTo("kv:Total=100.00");
  }

  @Test
  void tableElementDispatchesToVisitTable() {
    DocumentElement element =
        TableElement.of(List.of("Item", "Qty"), List.of(List.of("Widget", "2")));
    assertThat(element.accept(new RecordingVisitor())).isEqualTo("table:2x1");
  }

  @Test
  void textBlockElementDispatchesToVisitTextBlock() {
    DocumentElement element = TextBlockElement.of("Thank you for your business.");
    assertThat(element.accept(new RecordingVisitor()))
        .isEqualTo("text:Thank you for your business.");
  }
}
```

- [ ] **Step 2: Write the failing test for attributes**

```java
package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentAttributesTest {

  @Test
  void emptyHasNoValues() {
    assertThat(DocumentAttributes.empty().get("anything")).isEmpty();
  }

  @Test
  void ofExposesGivenValues() {
    DocumentAttributes attributes = DocumentAttributes.of(Map.of("region", "IN"));
    assertThat(attributes.get("region")).contains("IN");
  }

  @Test
  void getReturnsEmptyForMissingKey() {
    DocumentAttributes attributes = DocumentAttributes.of(Map.of("region", "IN"));
    assertThat(attributes.get("language")).isEqualTo(Optional.empty());
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.model.DocumentElementTest" --tests "io.genfin.document.api.model.DocumentAttributesTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 4: Write minimal implementation**

```java
package io.genfin.document.api.model;

public interface DocumentElementVisitor<R> {
  R visitKeyValue(KeyValueElement element);
  R visitTable(TableElement element);
  R visitTextBlock(TextBlockElement element);
}
```

```java
package io.genfin.document.api.model;

public interface DocumentElement {
  <R> R accept(DocumentElementVisitor<R> visitor);
}
```

```java
package io.genfin.document.api.model;

import io.genfin.api.exception.ValidationException;

public final class KeyValueElement implements DocumentElement {

  private final String label;
  private final String value;

  private KeyValueElement(String label, String value) {
    if (label == null || label.isBlank()) {
      throw new ValidationException("KeyValueElement label must not be blank");
    }
    this.label = label;
    this.value = value == null ? "" : value;
  }

  public static KeyValueElement of(String label, String value) {
    return new KeyValueElement(label, value);
  }

  public String label() {
    return label;
  }

  public String value() {
    return value;
  }

  @Override
  public <R> R accept(DocumentElementVisitor<R> visitor) {
    return visitor.visitKeyValue(this);
  }
}
```

```java
package io.genfin.document.api.model;

import java.util.List;

public final class TableElement implements DocumentElement {

  private final List<String> headers;
  private final List<List<String>> rows;

  private TableElement(List<String> headers, List<List<String>> rows) {
    this.headers = List.copyOf(headers);
    this.rows = rows.stream().map(List::copyOf).toList();
  }

  public static TableElement of(List<String> headers, List<List<String>> rows) {
    return new TableElement(headers, rows);
  }

  public List<String> headers() {
    return headers;
  }

  public List<List<String>> rows() {
    return rows;
  }

  @Override
  public <R> R accept(DocumentElementVisitor<R> visitor) {
    return visitor.visitTable(this);
  }
}
```

```java
package io.genfin.document.api.model;

public final class TextBlockElement implements DocumentElement {

  private final String text;

  private TextBlockElement(String text) {
    this.text = text == null ? "" : text;
  }

  public static TextBlockElement of(String text) {
    return new TextBlockElement(text);
  }

  public String text() {
    return text;
  }

  @Override
  public <R> R accept(DocumentElementVisitor<R> visitor) {
    return visitor.visitTextBlock(this);
  }
}
```

```java
package io.genfin.document.api.model;

import java.util.Map;
import java.util.Optional;

public final class DocumentAttributes {

  private static final DocumentAttributes EMPTY = new DocumentAttributes(Map.of());

  private final Map<String, String> values;

  private DocumentAttributes(Map<String, String> values) {
    this.values = Map.copyOf(values);
  }

  public static DocumentAttributes empty() {
    return EMPTY;
  }

  public static DocumentAttributes of(Map<String, String> values) {
    return new DocumentAttributes(values);
  }

  public Optional<String> get(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.model.DocumentElementTest" --tests "io.genfin.document.api.model.DocumentAttributesTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentElementVisitor.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentElement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/KeyValueElement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/TableElement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/TextBlockElement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentAttributes.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/model/DocumentElementTest.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/model/DocumentAttributesTest.java
git commit -m "feat(fin-document): add DocumentElement visitor hierarchy and DocumentAttributes"
```

---

### Task 5: `DocumentMetadata`, `DocumentSection`, `DocumentModel`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentMetadata.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentSection.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentModel.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/model/DocumentModelTest.java`

**Interfaces:**
- Consumes: `DocumentId` (Task 1), `DocumentType`/`StandardDocumentType` (Task 3), `DocumentElement`/`DocumentAttributes` (Task 4).
- Produces: `DocumentMetadata.of(DocumentId, DocumentType, String locale, String currency, Instant createdAt)`, `DocumentSection.of(String title, List<DocumentElement> elements)`, `DocumentModel.of(DocumentMetadata, List<DocumentSection>, DocumentAttributes)` — consumed by `ComposedDocument`/`DocumentComposer` (Task 6) and every renderer (Tasks 8-12).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.DocumentId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentModelTest {

  @Test
  void modelExposesMetadataSectionsAndAttributes() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    DocumentSection section =
        DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")));
    DocumentAttributes attributes = DocumentAttributes.of(java.util.Map.of("region", "IN"));

    DocumentModel model = DocumentModel.of(metadata, List.of(section), attributes);

    assertThat(model.metadata().id()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(model.metadata().type()).isEqualTo(StandardDocumentType.INVOICE);
    assertThat(model.sections()).hasSize(1);
    assertThat(model.sections().get(0).title()).isEqualTo("Summary");
    assertThat(model.attributes().get("region")).contains("IN");
  }

  @Test
  void sectionsListIsImmutable() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    DocumentModel model = DocumentModel.of(metadata, List.of(), DocumentAttributes.empty());

    assertThat(model.sections()).isUnmodifiable();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.model.DocumentModelTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.model;

import io.genfin.document.api.identity.DocumentId;
import java.time.Instant;

public final class DocumentMetadata {

  private final DocumentId id;
  private final DocumentType type;
  private final String locale;
  private final String currency;
  private final Instant createdAt;

  private DocumentMetadata(
      DocumentId id, DocumentType type, String locale, String currency, Instant createdAt) {
    this.id = id;
    this.type = type;
    this.locale = locale;
    this.currency = currency;
    this.createdAt = createdAt;
  }

  public static DocumentMetadata of(
      DocumentId id, DocumentType type, String locale, String currency, Instant createdAt) {
    return new DocumentMetadata(id, type, locale, currency, createdAt);
  }

  public DocumentId id() {
    return id;
  }

  public DocumentType type() {
    return type;
  }

  public String locale() {
    return locale;
  }

  public String currency() {
    return currency;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
```

```java
package io.genfin.document.api.model;

import java.util.List;

public final class DocumentSection {

  private final String title;
  private final List<DocumentElement> elements;

  private DocumentSection(String title, List<DocumentElement> elements) {
    this.title = title;
    this.elements = List.copyOf(elements);
  }

  public static DocumentSection of(String title, List<DocumentElement> elements) {
    return new DocumentSection(title, elements);
  }

  public String title() {
    return title;
  }

  public List<DocumentElement> elements() {
    return elements;
  }
}
```

```java
package io.genfin.document.api.model;

import java.util.List;

public final class DocumentModel {

  private final DocumentMetadata metadata;
  private final List<DocumentSection> sections;
  private final DocumentAttributes attributes;

  private DocumentModel(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    this.metadata = metadata;
    this.sections = List.copyOf(sections);
    this.attributes = attributes;
  }

  public static DocumentModel of(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    return new DocumentModel(metadata, sections, attributes);
  }

  public DocumentMetadata metadata() {
    return metadata;
  }

  public List<DocumentSection> sections() {
    return sections;
  }

  public DocumentAttributes attributes() {
    return attributes;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.model.DocumentModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentMetadata.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentSection.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/model/DocumentModel.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/model/DocumentModelTest.java
git commit -m "feat(fin-document): add DocumentMetadata, DocumentSection, DocumentModel aggregate"
```

---

### Task 6: `ComposedDocument` + `DocumentComposer` + `NoOpDocumentComposer` + `DocumentComposers` factory

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/compose/ComposedDocument.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/compose/DocumentComposer.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/compose/DocumentComposers.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/NoOpDocumentComposer.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/compose/DocumentComposersTest.java`

**Interfaces:**
- Consumes: `DocumentModel`/`DocumentMetadata`/`DocumentSection`/`DocumentAttributes` (Task 5).
- Produces: `ComposedDocument.of(DocumentMetadata, List<DocumentSection>, DocumentAttributes)`, `DocumentComposer.compose(DocumentModel): ComposedDocument`, `DocumentComposers.noOp(): DocumentComposer` — every renderer (Tasks 8-12) is written against `ComposedDocument`, not `DocumentModel`.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.compose;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentComposersTest {

  @Test
  void noOpComposerCopiesModelUnchanged() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    DocumentSection section =
        DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")));
    DocumentModel model =
        DocumentModel.of(
            metadata, List.of(section), DocumentAttributes.of(java.util.Map.of("region", "IN")));

    DocumentComposer composer = DocumentComposers.noOp();
    ComposedDocument composed = composer.compose(model);

    assertThat(composed.metadata().id()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(composed.sections()).hasSize(1);
    assertThat(composed.sections().get(0).title()).isEqualTo("Summary");
    assertThat(composed.attributes().get("region")).contains("IN");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.compose.DocumentComposersTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.compose;

import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import java.util.List;

public final class ComposedDocument {

  private final DocumentMetadata metadata;
  private final List<DocumentSection> sections;
  private final DocumentAttributes attributes;

  private ComposedDocument(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    this.metadata = metadata;
    this.sections = List.copyOf(sections);
    this.attributes = attributes;
  }

  public static ComposedDocument of(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    return new ComposedDocument(metadata, sections, attributes);
  }

  public DocumentMetadata metadata() {
    return metadata;
  }

  public List<DocumentSection> sections() {
    return sections;
  }

  public DocumentAttributes attributes() {
    return attributes;
  }
}
```

```java
package io.genfin.document.api.compose;

import io.genfin.document.api.model.DocumentModel;

public interface DocumentComposer {
  ComposedDocument compose(DocumentModel model);
}
```

```java
package io.genfin.document.internal;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.compose.DocumentComposer;
import io.genfin.document.api.model.DocumentModel;

public final class NoOpDocumentComposer implements DocumentComposer {

  @Override
  public ComposedDocument compose(DocumentModel model) {
    return ComposedDocument.of(model.metadata(), model.sections(), model.attributes());
  }
}
```

```java
package io.genfin.document.api.compose;

import io.genfin.document.internal.NoOpDocumentComposer;

public final class DocumentComposers {

  private DocumentComposers() {}

  public static DocumentComposer noOp() {
    return new NoOpDocumentComposer();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.compose.DocumentComposersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/compose/ \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/NoOpDocumentComposer.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/compose/DocumentComposersTest.java
git commit -m "feat(fin-document): add ComposedDocument, DocumentComposer, NoOpDocumentComposer"
```

---

### Task 7: `RenderResult`, `RenderException`, `RendererConfiguration`, `RendererCapabilities`, `DocumentRenderer` port

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/result/RenderResult.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/exception/RenderException.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/RendererConfiguration.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/RendererCapabilities.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentRenderer.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/result/RenderResultTest.java`

**Interfaces:**
- Consumes: `DocumentAttributes` (Task 4), `ComposedDocument` (Task 6), `RendererId` (Task 2).
- Produces: `RenderResult.of(RendererId, byte[] content, String mimeType)`, `RendererConfiguration.defaults()`/`of(DocumentAttributes)`, `RendererCapabilities.mimeType(): String`, `DocumentRenderer.id()/capabilities()/render(ComposedDocument, RendererConfiguration): RenderResult throws RenderException` — implemented by every built-in renderer (Tasks 8-12) and by `DefaultRendererRegistry`/`DefaultRendererResolver` (Task 9).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.RendererId;
import org.junit.jupiter.api.Test;

class RenderResultTest {

  @Test
  void ofExposesRendererIdContentAndMimeType() {
    RenderResult result =
        RenderResult.of(RendererId.of("json"), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");

    assertThat(result.rendererId()).isEqualTo(RendererId.of("json"));
    assertThat(new String(result.content(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("{}");
    assertThat(result.mimeType()).isEqualTo("application/json");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.result.RenderResultTest"`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.result;

import io.genfin.document.api.identity.RendererId;
import java.util.Arrays;

public final class RenderResult {

  private final RendererId rendererId;
  private final byte[] content;
  private final String mimeType;

  private RenderResult(RendererId rendererId, byte[] content, String mimeType) {
    this.rendererId = rendererId;
    this.content = Arrays.copyOf(content, content.length);
    this.mimeType = mimeType;
  }

  public static RenderResult of(RendererId rendererId, byte[] content, String mimeType) {
    return new RenderResult(rendererId, content, mimeType);
  }

  public RendererId rendererId() {
    return rendererId;
  }

  public byte[] content() {
    return Arrays.copyOf(content, content.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
```

```java
package io.genfin.document.api.exception;

import io.genfin.document.api.identity.RendererId;

public final class RenderException extends Exception {

  private final RendererId rendererId;

  public RenderException(RendererId rendererId, String message) {
    super(message);
    this.rendererId = rendererId;
  }

  public RenderException(RendererId rendererId, String message, Throwable cause) {
    super(message, cause);
    this.rendererId = rendererId;
  }

  public RendererId rendererId() {
    return rendererId;
  }
}
```

```java
package io.genfin.document.port;

import io.genfin.document.api.model.DocumentAttributes;

public final class RendererConfiguration {

  private final DocumentAttributes options;

  private RendererConfiguration(DocumentAttributes options) {
    this.options = options;
  }

  public static RendererConfiguration defaults() {
    return new RendererConfiguration(DocumentAttributes.empty());
  }

  public static RendererConfiguration of(DocumentAttributes options) {
    return new RendererConfiguration(options);
  }

  public DocumentAttributes options() {
    return options;
  }
}
```

```java
package io.genfin.document.port;

public interface RendererCapabilities {
  String mimeType();
}
```

```java
package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.result.RenderResult;

public interface DocumentRenderer extends Extension {

  RendererId id();

  RendererCapabilities capabilities();

  RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.result.RenderResultTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/result/ \
  fin-core/fin-document/src/main/java/io/genfin/document/api/exception/ \
  fin-core/fin-document/src/main/java/io/genfin/document/port/RendererConfiguration.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/RendererCapabilities.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentRenderer.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/result/RenderResultTest.java
git commit -m "feat(fin-document): add RenderResult, RenderException, and DocumentRenderer SPI"
```

---

### Task 8: `RendererNotFoundException` + `RendererResolver` port + `DefaultRendererRegistry`/`DefaultRendererResolver`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/exception/RendererNotFoundException.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/RendererResolver.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/DefaultRendererRegistry.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/DefaultRendererResolver.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/DefaultRendererResolverTest.java`

**Interfaces:**
- Consumes: `DocumentRenderer`, `RendererCapabilities` (Task 7), `RendererId` (Task 2).
- Produces: `RendererResolver.resolve(RendererId): DocumentRenderer`, `DefaultRendererRegistry.register(DocumentRenderer)`/`get(RendererId): DocumentRenderer`, `DefaultRendererResolver(DefaultRendererRegistry)` — consumed by `DocumentRenderers` factory in Task 13.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RendererNotFoundException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import org.junit.jupiter.api.Test;

class DefaultRendererResolverTest {

  private static final class StubRenderer implements DocumentRenderer {
    @Override
    public RendererId id() {
      return RendererId.of("stub");
    }

    @Override
    public RendererCapabilities capabilities() {
      return () -> "text/plain";
    }

    @Override
    public RenderResult render(ComposedDocument document, RendererConfiguration configuration) {
      return RenderResult.of(id(), new byte[0], capabilities().mimeType());
    }
  }

  @Test
  void resolvesRegisteredRenderer() {
    DefaultRendererRegistry registry = new DefaultRendererRegistry();
    StubRenderer renderer = new StubRenderer();
    registry.register(renderer);
    DefaultRendererResolver resolver = new DefaultRendererResolver(registry);

    assertThat(resolver.resolve(RendererId.of("stub"))).isSameAs(renderer);
  }

  @Test
  void throwsForUnregisteredRenderer() {
    DefaultRendererResolver resolver = new DefaultRendererResolver(new DefaultRendererRegistry());

    assertThatThrownBy(() -> resolver.resolve(RendererId.of("missing")))
        .isInstanceOf(RendererNotFoundException.class);
  }

  @Test
  void rejectsDuplicateRegistration() {
    DefaultRendererRegistry registry = new DefaultRendererRegistry();
    registry.register(new StubRenderer());

    assertThatThrownBy(() -> registry.register(new StubRenderer()))
        .isInstanceOf(IllegalStateException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.DefaultRendererResolverTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.exception;

import io.genfin.document.api.identity.RendererId;

public final class RendererNotFoundException extends RuntimeException {

  public RendererNotFoundException(RendererId rendererId) {
    super("No renderer registered for id: " + rendererId);
  }
}
```

```java
package io.genfin.document.port;

import io.genfin.document.api.identity.RendererId;

public interface RendererResolver {
  DocumentRenderer resolve(RendererId id);
}
```

```java
package io.genfin.document.internal;

import io.genfin.document.api.exception.RendererNotFoundException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.port.DocumentRenderer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultRendererRegistry {

  private final Map<RendererId, DocumentRenderer> renderers = new ConcurrentHashMap<>();

  public void register(DocumentRenderer renderer) {
    DocumentRenderer existing = renderers.putIfAbsent(renderer.id(), renderer);
    if (existing != null) {
      throw new IllegalStateException("Renderer already registered for id: " + renderer.id());
    }
  }

  public DocumentRenderer get(RendererId id) {
    DocumentRenderer renderer = renderers.get(id);
    if (renderer == null) {
      throw new RendererNotFoundException(id);
    }
    return renderer;
  }
}
```

```java
package io.genfin.document.internal;

import io.genfin.document.api.identity.RendererId;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererResolver;

public final class DefaultRendererResolver implements RendererResolver {

  private final DefaultRendererRegistry registry;

  public DefaultRendererResolver(DefaultRendererRegistry registry) {
    this.registry = registry;
  }

  @Override
  public DocumentRenderer resolve(RendererId id) {
    return registry.get(id);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.DefaultRendererResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/exception/RendererNotFoundException.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/RendererResolver.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/DefaultRendererRegistry.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/DefaultRendererResolver.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/DefaultRendererResolverTest.java
git commit -m "feat(fin-document): add RendererResolver port and default registry/resolver"
```

---

### Task 9: `JsonDocumentRenderer`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/JsonDocumentRenderer.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/JsonDocumentRendererTest.java`

**Interfaces:**
- Consumes: `ComposedDocument`/`DocumentSection`/`DocumentElementVisitor` (Tasks 4-6), `DocumentRenderer`/`RenderResult` (Task 7).
- Produces: `new JsonDocumentRenderer()` implementing `DocumentRenderer`, `id() == RendererId.of("json")`, `capabilities().mimeType() == "application/json"` — registered by `DocumentRenderers.standard()` in Task 13.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonDocumentRendererTest {

  @Test
  void rendersKeyValueSectionAsJson() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    String json =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(json).contains("\"documentId\":\"doc-1\"");
    assertThat(json).contains("\"Total\":\"500.00\"");
  }

  @Test
  void idAndCapabilitiesAreJson() {
    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    assertThat(renderer.id().toString()).contains("json");
    assertThat(renderer.capabilities().mimeType()).isEqualTo("application/json");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.renderer.JsonDocumentRendererTest"`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;

public final class JsonDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("json");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "application/json";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"documentId\":\"").append(document.metadata().id().value()).append("\",");
    json.append("\"documentType\":\"").append(document.metadata().type().code()).append("\",");
    json.append("\"sections\":[");

    ElementJsonVisitor visitor = new ElementJsonVisitor();
    for (int i = 0; i < document.sections().size(); i++) {
      DocumentSection section = document.sections().get(i);
      json.append("{\"title\":\"").append(section.title()).append("\",\"elements\":{");
      for (int j = 0; j < section.elements().size(); j++) {
        json.append(section.elements().get(j).accept(visitor));
        if (j < section.elements().size() - 1) {
          json.append(",");
        }
      }
      json.append("}}");
      if (i < document.sections().size() - 1) {
        json.append(",");
      }
    }
    json.append("]}");

    return RenderResult.of(id(), json.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static final class ElementJsonVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "\"" + element.label() + "\":\"" + element.value() + "\"";
    }

    @Override
    public String visitTable(TableElement element) {
      return "\"table\":\"" + element.headers().size() + "x" + element.rows().size() + "\"";
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "\"text\":\"" + element.text() + "\"";
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.renderer.JsonDocumentRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/JsonDocumentRenderer.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/JsonDocumentRendererTest.java
git commit -m "feat(fin-document): add JsonDocumentRenderer"
```

---

### Task 10: `PlainTextDocumentRenderer` and `MarkdownDocumentRenderer`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/PlainTextDocumentRenderer.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/MarkdownDocumentRenderer.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/PlainTextDocumentRendererTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/MarkdownDocumentRendererTest.java`

**Interfaces:**
- Consumes: same as Task 9.
- Produces: `PlainTextDocumentRenderer` (`id() == RendererId.of("text")`, mimeType `text/plain`), `MarkdownDocumentRenderer` (`id() == RendererId.of("markdown")`, mimeType `text/markdown`) — registered in Task 13.

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.internal.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlainTextDocumentRendererTest {

  @Test
  void rendersSectionTitleAndKeyValuePairs() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    PlainTextDocumentRenderer renderer = new PlainTextDocumentRenderer();
    String text =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(text).contains("Summary");
    assertThat(text).contains("Total: 500.00");
  }
}
```

```java
package io.genfin.document.internal.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownDocumentRendererTest {

  @Test
  void rendersSectionAsMarkdownHeadingAndList() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    MarkdownDocumentRenderer renderer = new MarkdownDocumentRenderer();
    String markdown =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(markdown).contains("## Summary");
    assertThat(markdown).contains("- **Total**: 500.00");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.renderer.PlainTextDocumentRendererTest" --tests "io.genfin.document.internal.renderer.MarkdownDocumentRendererTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;

public final class PlainTextDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("text");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "text/plain";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder text = new StringBuilder();
    ElementTextVisitor visitor = new ElementTextVisitor();
    for (DocumentSection section : document.sections()) {
      text.append(section.title()).append('\n');
      for (var element : section.elements()) {
        text.append(element.accept(visitor)).append('\n');
      }
    }
    return RenderResult.of(id(), text.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static final class ElementTextVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return element.label() + ": " + element.value();
    }

    @Override
    public String visitTable(TableElement element) {
      return String.join(" | ", element.headers());
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return element.text();
    }
  }
}
```

```java
package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;

public final class MarkdownDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("markdown");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "text/markdown";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder markdown = new StringBuilder();
    ElementMarkdownVisitor visitor = new ElementMarkdownVisitor();
    for (var section : document.sections()) {
      markdown.append("## ").append(section.title()).append('\n');
      for (var element : section.elements()) {
        markdown.append(element.accept(visitor)).append('\n');
      }
    }
    return RenderResult.of(
        id(), markdown.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static final class ElementMarkdownVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "- **" + element.label() + "**: " + element.value();
    }

    @Override
    public String visitTable(TableElement element) {
      return "| " + String.join(" | ", element.headers()) + " |";
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return element.text();
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.renderer.PlainTextDocumentRendererTest" --tests "io.genfin.document.internal.renderer.MarkdownDocumentRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/PlainTextDocumentRenderer.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/MarkdownDocumentRenderer.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/PlainTextDocumentRendererTest.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/MarkdownDocumentRendererTest.java
git commit -m "feat(fin-document): add PlainTextDocumentRenderer and MarkdownDocumentRenderer"
```

---

### Task 11: `CsvDocumentRenderer` and `XmlDocumentRenderer`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/CsvDocumentRenderer.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/XmlDocumentRenderer.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/CsvDocumentRendererTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/XmlDocumentRendererTest.java`

**Interfaces:**
- Consumes: same as Task 9.
- Produces: `CsvDocumentRenderer` (`id() == RendererId.of("csv")`, mimeType `text/csv`, non-tabular elements skipped), `XmlDocumentRenderer` (`id() == RendererId.of("xml")`, mimeType `application/xml`) — registered in Task 13.

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.internal.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvDocumentRendererTest {

  @Test
  void rendersTableElementAsCsvRowsAndSkipsNonTabularElements() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(
                DocumentSection.of(
                    "Lines",
                    List.of(
                        TableElement.of(
                            List.of("Item", "Qty"), List.of(List.of("Widget", "2"))),
                        KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    CsvDocumentRenderer renderer = new CsvDocumentRenderer();
    String csv =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(csv).contains("Item,Qty");
    assertThat(csv).contains("Widget,2");
    assertThat(csv).doesNotContain("Total");
  }
}
```

```java
package io.genfin.document.internal.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class XmlDocumentRendererTest {

  @Test
  void rendersSectionAndElementAsXml() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    XmlDocumentRenderer renderer = new XmlDocumentRenderer();
    String xml =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(xml).contains("<document id=\"doc-1\">");
    assertThat(xml).contains("<field label=\"Total\">500.00</field>");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.renderer.CsvDocumentRendererTest" --tests "io.genfin.document.internal.renderer.XmlDocumentRendererTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;

public final class CsvDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("csv");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "text/csv";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder csv = new StringBuilder();
    ElementCsvVisitor visitor = new ElementCsvVisitor();
    for (var section : document.sections()) {
      for (var element : section.elements()) {
        String row = element.accept(visitor);
        if (!row.isEmpty()) {
          csv.append(row).append('\n');
        }
      }
    }
    return RenderResult.of(id(), csv.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static final class ElementCsvVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "";
    }

    @Override
    public String visitTable(TableElement element) {
      StringBuilder csv = new StringBuilder(String.join(",", element.headers()));
      for (var row : element.rows()) {
        csv.append('\n').append(String.join(",", row));
      }
      return csv.toString();
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "";
    }
  }
}
```

```java
package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;

public final class XmlDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("xml");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "application/xml";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder xml = new StringBuilder();
    xml.append("<document id=\"").append(document.metadata().id().value()).append("\">");
    ElementXmlVisitor visitor = new ElementXmlVisitor();
    for (var section : document.sections()) {
      xml.append("<section title=\"").append(section.title()).append("\">");
      for (var element : section.elements()) {
        xml.append(element.accept(visitor));
      }
      xml.append("</section>");
    }
    xml.append("</document>");
    return RenderResult.of(id(), xml.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static final class ElementXmlVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "<field label=\"" + element.label() + "\">" + element.value() + "</field>";
    }

    @Override
    public String visitTable(TableElement element) {
      return "<table columns=\"" + element.headers().size() + "\" rows=\"" + element.rows().size() + "\"/>";
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "<text>" + element.text() + "</text>";
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.renderer.CsvDocumentRendererTest" --tests "io.genfin.document.internal.renderer.XmlDocumentRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/CsvDocumentRenderer.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/renderer/XmlDocumentRenderer.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/CsvDocumentRendererTest.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/renderer/XmlDocumentRendererTest.java
git commit -m "feat(fin-document): add CsvDocumentRenderer and XmlDocumentRenderer"
```

---

### Task 12: `DocumentRenderers` factory (wires all 5 built-ins into a resolver)

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentRenderers.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/port/DocumentRrenderersTest.java`

**Interfaces:**
- Consumes: `RendererResolver`, `DefaultRendererRegistry`, `DefaultRendererResolver` (Task 8), all 5 renderers (Tasks 9-11).
- Produces: `DocumentRenderers.standard(): RendererResolver` pre-registered with `RendererId.of("json"/"text"/"markdown"/"csv"/"xml")` — this is the single entry point applications call in Stage 1.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.RendererId;
import org.junit.jupiter.api.Test;

class DocumentRenderersTest {

  @Test
  void standardResolverHasAllFiveBuiltInRenderers() {
    RendererResolver resolver = DocumentRenderers.standard();

    assertThat(resolver.resolve(RendererId.of("json")).capabilities().mimeType())
        .isEqualTo("application/json");
    assertThat(resolver.resolve(RendererId.of("text")).capabilities().mimeType())
        .isEqualTo("text/plain");
    assertThat(resolver.resolve(RendererId.of("markdown")).capabilities().mimeType())
        .isEqualTo("text/markdown");
    assertThat(resolver.resolve(RendererId.of("csv")).capabilities().mimeType())
        .isEqualTo("text/csv");
    assertThat(resolver.resolve(RendererId.of("xml")).capabilities().mimeType())
        .isEqualTo("application/xml");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.DocumentRenderersTest"`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.document.internal.DefaultRendererRegistry;
import io.genfin.document.internal.DefaultRendererResolver;
import io.genfin.document.internal.renderer.CsvDocumentRenderer;
import io.genfin.document.internal.renderer.JsonDocumentRenderer;
import io.genfin.document.internal.renderer.MarkdownDocumentRenderer;
import io.genfin.document.internal.renderer.PlainTextDocumentRenderer;
import io.genfin.document.internal.renderer.XmlDocumentRenderer;

public final class DocumentRenderers {

  private DocumentRenderers() {}

  public static RendererResolver standard() {
    DefaultRendererRegistry registry = new DefaultRendererRegistry();
    registry.register(new JsonDocumentRenderer());
    registry.register(new PlainTextDocumentRenderer());
    registry.register(new MarkdownDocumentRenderer());
    registry.register(new CsvDocumentRenderer());
    registry.register(new XmlDocumentRenderer());
    return new DefaultRendererResolver(registry);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.DocumentRenderersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentRenderers.java \
  fin-core/fin-document/src/test/java/io/genfin/document/port/DocumentRenderersTest.java
git commit -m "feat(fin-document): add DocumentRenderers factory wiring all built-in renderers"
```

---

### Task 13: `DocumentMapper<T>` port

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentMapper.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/port/DocumentMapperTest.java`

**Interfaces:**
- Consumes: `DocumentModel` (Task 5).
- Produces: `DocumentMapper<T>.map(T source): DocumentModel` — the contract future stages/consumers implement (`InvoiceDocumentMapper`, etc.); no concrete implementation ships in this module.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.model.StandardDocumentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentMapperTest {

  private record TestInvoice(String id, String total) {}

  @Test
  void mapperContractProducesDocumentModelFromArbitrarySource() {
    DocumentMapper<TestInvoice> mapper =
        invoice ->
            DocumentModel.of(
                DocumentMetadata.of(
                    DocumentId.of(invoice.id()),
                    StandardDocumentType.INVOICE,
                    "en-IN",
                    "INR",
                    Instant.EPOCH),
                List.of(),
                DocumentAttributes.of(java.util.Map.of("total", invoice.total())));

    DocumentModel model = mapper.map(new TestInvoice("inv-1", "500.00"));

    assertThat(model.metadata().id()).isEqualTo(DocumentId.of("inv-1"));
    assertThat(model.attributes().get("total")).contains("500.00");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.DocumentMapperTest"`
Expected: FAIL — `DocumentMapper` doesn't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.model.DocumentModel;

public interface DocumentMapper<T> extends Extension {
  DocumentModel map(T source);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.DocumentMapperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentMapper.java \
  fin-core/fin-document/src/test/java/io/genfin/document/port/DocumentMapperTest.java
git commit -m "feat(fin-document): add generic DocumentMapper SPI contract"
```

---

### Task 14: `module-info.java` exports + `DocumentArchitectureTest`

**Files:**
- Modify: `fin-core/fin-document/src/main/java/module-info.java`
- Create: `fin-core/fin-document/src/test/java/io/genfin/document/architecture/DocumentArchitectureTest.java`

**Interfaces:**
- Consumes: every package created in Tasks 1-13.
- Produces: none (verification-only task) — this is the gate that proves Stage 1's isolation/structure promises hold.

- [ ] **Step 1: Update `module-info.java` to export the public packages**

```java
module io.genfin.document {
  requires transitive io.genfin.api;

  exports io.genfin.document.api.identity;
  exports io.genfin.document.api.model;
  exports io.genfin.document.api.compose;
  exports io.genfin.document.api.result;
  exports io.genfin.document.api.exception;
  exports io.genfin.document.port;
}
```

- [ ] **Step 2: Write the failing architecture test**

```java
package io.genfin.document.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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

import static org.assertj.core.api.Assertions.assertThat;

class DocumentArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.genfin.document");
  }

  @Test
  void portClassesDoNotDependOnInternalClasses() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..port..")
            .and()
            .haveSimpleNameNotEndingWith("Renderers")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..internal..");
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
    ArchRule rule = SlicesRuleDefinition.slices().matching("io.genfin.document.(*)..").should().beFreeOfCycles();
    rule.check(classes);
  }

  @Test
  void moduleInfoDoesNotExportInternalPackages() throws IOException {
    Path moduleInfo =
        Path.of("src/main/java/module-info.java");
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
```

- [ ] **Step 3: Run test to verify current state**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.architecture.DocumentArchitectureTest"`
Expected: PASS immediately if Tasks 1-13 were followed exactly (no forbidden imports, no `instanceof`/`switch`, no internal exports were introduced). If any check fails, fix the violating file before proceeding — do not weaken the rule.

- [ ] **Step 4: Commit**

```bash
git add fin-core/fin-document/src/main/java/module-info.java \
  fin-core/fin-document/src/test/java/io/genfin/document/architecture/DocumentArchitectureTest.java
git commit -m "test(fin-document): export public packages and add DocumentArchitectureTest"
```

---

### Task 15: Full module build gate

**Files:**
- None (verification-only).

**Interfaces:**
- Consumes: everything from Tasks 1-14.
- Produces: nothing — this is Stage 1's exit gate.

- [ ] **Step 1: Run the full module quality gate**

Run: `./gradlew :fin-document:check`
Expected: PASS — compile, Spotless, Checkstyle, PMD, Error Prone, JUnit, JaCoCo all green.

- [ ] **Step 2: If Spotless fails on formatting only, auto-fix and re-run**

Run: `./gradlew :fin-document:spotlessApply && ./gradlew :fin-document:check`
Expected: PASS.

- [ ] **Step 3: Run the full repo build to confirm no cross-module breakage**

Run: `./gradlew build`
Expected: PASS across all modules (fin-document is additive — it must not break any existing module).

- [ ] **Step 4: Commit any Spotless-driven formatting fixes**

```bash
git status
git add -A
git commit -m "chore(fin-document): apply formatting fixes from Stage 1 build gate" --allow-empty
```

(Use `--allow-empty` only if `git status` shows no changes — otherwise omit it and let the commit include the real diff.)

---

## Self-Review Notes

- **Spec coverage:** All of Stage 1's spec sections have a task — identity (Tasks 1-2), core aggregate (Tasks 3-5), composer spine (Task 6), renderer SPI + built-ins (Tasks 7-12), mapper contract (Task 13), architecture/exports (Task 14), build gate (Task 15). `DocumentPage`/`DocumentAttachment`/`DocumentContext`/`DocumentProperties`/`DocumentSummary` and PDF/HTML renderers are explicitly out of scope per the Stage 1 design spec — no task references them.
- **Type consistency:** `RendererId.of("json"/"text"/"markdown"/"csv"/"xml")` is used identically across Tasks 9-13. `ComposedDocument`/`DocumentModel` field names (`metadata()`, `sections()`, `attributes()`) are consistent from Task 5 through Task 12. `DocumentElementVisitor<R>` method names (`visitKeyValue`/`visitTable`/`visitTextBlock`) match across every renderer implementation.
- **No placeholders:** every step has real, complete code — no "similar to Task N" shortcuts; the 8 near-identical identity classes in Task 2 are each shown or trivially derived from the one fully-shown template with only the class name substituted, per that task's explicit instruction.
