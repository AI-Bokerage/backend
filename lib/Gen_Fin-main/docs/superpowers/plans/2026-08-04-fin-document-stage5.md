# fin-document Stage 5 Implementation Plan — Layout Engine (Minimal) + Watermark

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pure declarative value types for page layout (paper size, orientation, margins, header/footer text, page numbering) and watermark (label, placement, opacity) — zero rendering logic, zero SPI (unlike Letterhead/QR/Brand, nothing external needs fetching). Stage 6's PDF renderer is the only consumer.

**Architecture:** Everything lives in `api.layout`/`api.watermark`. No `port`/`internal` packages this stage — no provider, no registry, no resolver needed.

**Tech Stack:** Java, JUnit 5 + AssertJ. Only dependency: `fin-api`.

## Global Constraints

- Module `fin-document` depends on `fin-api` only.
- Forbidden imports: `org.springframework..`, `javax.persistence..`, `jakarta.persistence..`, `com.fasterxml.jackson..`, `com.google.gson..`, any PDF/image/HTTP/cloud-SDK library.
- No `instanceof`, no `switch`/`switch(` anywhere in `fin-document` source.
- Package layout exactly: `io.genfin.document.api.layout`, `io.genfin.document.api.watermark`.
- Do not modify any existing Stage 1-4 file's public signature.
- Reuse `LayoutId`/`WatermarkId` (already exist: `io.genfin.document.api.identity.*`) — not used by any type in THIS stage's plan (Layout/Watermark here are pure value types, not registered/resolved entities), but do not redeclare them if a future stage needs them.
- **`io.genfin.api.validation.Validate`'s ACTUAL methods are: `notNull(T, String)`, `notBlank(String, String)`, `positive(int/long, String)`, `nonNegative(int/long, String)`, `required(boolean, String)`, `state(boolean, String)`, `argument(boolean, String)`. Use these exact methods — do not invent a method that doesn't exist (e.g. there is no `Validate.range(...)`; use `Validate.required(condition, message)` for range checks).**
- **This module has caught the same defect twice already: a builder's optional setters must coalesce `null` to a non-null default INSIDE THE CONSTRUCTOR, never store `null` directly (Stage 3's `BrandProfile` fix, commit 49eb917). Apply this from day one in `PageLayout.Builder` — do not ship the unvalidated version and wait for review to catch it a third time.**
- **This module has also caught a class of bug where a value type's constructor accepts data it cannot use, only to fail later downstream (Stage 4's `Letterhead`/`QrCodeContent` fixes). Apply constructor validation to every new type in this stage from day one.**
- Test stack: JUnit 5 + AssertJ. Build/verify command every task: `./gradlew :fin-document:check`.

---

### Task 1: `PaperSize`/`StandardPaperSize`, `Orientation`/`StandardOrientation`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/PaperSize.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/StandardPaperSize.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/Orientation.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/StandardOrientation.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/layout/StandardPaperSizeTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/layout/StandardOrientationTest.java`

**Interfaces:**
- Produces: `PaperSize` (marker, `String code()`), `StandardPaperSize.of(String code)` + constants `A4`, `LETTER`. `Orientation` (marker, `String code()`), `StandardOrientation.of(String code)` + constants `PORTRAIT`, `LANDSCAPE`. Mirror `io.genfin.document.api.model.StandardDocumentType`'s exact pattern (record implementing marker interface, `Validate.notBlank` in compact constructor, `of(code)` factory, `public static final` constants) — read that file first.
- Consumed by: Task 5 (`PageLayout`).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardPaperSizeTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardPaperSize.A4.code()).isEqualTo("A4");
    assertThat(StandardPaperSize.LETTER.code()).isEqualTo("LETTER");
  }

  @Test
  void ofBuildsCustomPaperSize() {
    assertThat(StandardPaperSize.of("LEGAL").code()).isEqualTo("LEGAL");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardPaperSize.of("")).isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardPaperSize.of(null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void equalsIsBasedOnCode() {
    assertThat(StandardPaperSize.of("A4")).isEqualTo(StandardPaperSize.A4);
  }
}
```

```java
package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardOrientationTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardOrientation.PORTRAIT.code()).isEqualTo("PORTRAIT");
    assertThat(StandardOrientation.LANDSCAPE.code()).isEqualTo("LANDSCAPE");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardOrientation.of(" ")).isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.StandardPaperSizeTest" --tests "io.genfin.document.api.layout.StandardOrientationTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.layout;

public interface PaperSize {
  String code();
}
```

```java
package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public record StandardPaperSize(String code) implements PaperSize {

  public StandardPaperSize {
    Validate.notBlank(code, "PaperSize code must not be blank");
  }

  public static StandardPaperSize of(String code) {
    return new StandardPaperSize(code);
  }

  public static final StandardPaperSize A4 = of("A4");
  public static final StandardPaperSize LETTER = of("LETTER");
}
```

```java
package io.genfin.document.api.layout;

public interface Orientation {
  String code();
}
```

```java
package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public record StandardOrientation(String code) implements Orientation {

  public StandardOrientation {
    Validate.notBlank(code, "Orientation code must not be blank");
  }

  public static StandardOrientation of(String code) {
    return new StandardOrientation(code);
  }

  public static final StandardOrientation PORTRAIT = of("PORTRAIT");
  public static final StandardOrientation LANDSCAPE = of("LANDSCAPE");
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.StandardPaperSizeTest" --tests "io.genfin.document.api.layout.StandardOrientationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/layout/PaperSize.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/layout/StandardPaperSize.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/layout/Orientation.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/layout/StandardOrientation.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/layout/
git commit -m "feat(fin-document): add PaperSize and Orientation open-value types"
```

---

### Task 2: `Margins`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/Margins.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/layout/MarginsTest.java`

**Interfaces:**
- Produces: `Margins.of(int top, int bottom, int left, int right)` + `topPoints()`/`bottomPoints()`/`leftPoints()`/`rightPoints()`, `Margins.uniform(int allSides)`, `Margins.none()`. Each of the 4 ints validated non-negative via `Validate.nonNegative`.
- Consumed by: Task 5 (`PageLayout`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class MarginsTest {

  @Test
  void ofExposesGivenValues() {
    Margins margins = Margins.of(10, 20, 30, 40);
    assertThat(margins.topPoints()).isEqualTo(10);
    assertThat(margins.bottomPoints()).isEqualTo(20);
    assertThat(margins.leftPoints()).isEqualTo(30);
    assertThat(margins.rightPoints()).isEqualTo(40);
  }

  @Test
  void uniformAppliesSameValueToAllSides() {
    Margins margins = Margins.uniform(15);
    assertThat(margins.topPoints()).isEqualTo(15);
    assertThat(margins.bottomPoints()).isEqualTo(15);
    assertThat(margins.leftPoints()).isEqualTo(15);
    assertThat(margins.rightPoints()).isEqualTo(15);
  }

  @Test
  void noneIsAllZero() {
    Margins margins = Margins.none();
    assertThat(margins.topPoints()).isZero();
    assertThat(margins.bottomPoints()).isZero();
    assertThat(margins.leftPoints()).isZero();
    assertThat(margins.rightPoints()).isZero();
  }

  @Test
  void rejectsNegativeTop() {
    assertThatThrownBy(() -> Margins.of(-1, 0, 0, 0)).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNegativeBottom() {
    assertThatThrownBy(() -> Margins.of(0, -1, 0, 0)).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNegativeLeft() {
    assertThatThrownBy(() -> Margins.of(0, 0, -1, 0)).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNegativeRight() {
    assertThatThrownBy(() -> Margins.of(0, 0, 0, -1)).isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.MarginsTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public final class Margins {

  private final int topPoints;
  private final int bottomPoints;
  private final int leftPoints;
  private final int rightPoints;

  private Margins(int topPoints, int bottomPoints, int leftPoints, int rightPoints) {
    this.topPoints = Validate.nonNegative(topPoints, "topPoints must not be negative");
    this.bottomPoints = Validate.nonNegative(bottomPoints, "bottomPoints must not be negative");
    this.leftPoints = Validate.nonNegative(leftPoints, "leftPoints must not be negative");
    this.rightPoints = Validate.nonNegative(rightPoints, "rightPoints must not be negative");
  }

  public static Margins of(int topPoints, int bottomPoints, int leftPoints, int rightPoints) {
    return new Margins(topPoints, bottomPoints, leftPoints, rightPoints);
  }

  public static Margins uniform(int allSides) {
    return new Margins(allSides, allSides, allSides, allSides);
  }

  public static Margins none() {
    return new Margins(0, 0, 0, 0);
  }

  public int topPoints() {
    return topPoints;
  }

  public int bottomPoints() {
    return bottomPoints;
  }

  public int leftPoints() {
    return leftPoints;
  }

  public int rightPoints() {
    return rightPoints;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.MarginsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/layout/Margins.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/layout/MarginsTest.java
git commit -m "feat(fin-document): add Margins value type with non-negative validation"
```

---

### Task 3: `LayoutHeader`, `LayoutFooter`, `PageNumberConfiguration`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/LayoutHeader.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/LayoutFooter.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/PageNumberConfiguration.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/layout/PageNumberConfigurationTest.java`

**Interfaces:**
- Produces: `LayoutHeader.of(String text)` + `text()` (null becomes `""`). `LayoutFooter.of(String text)` + `text()` (same). `PageNumberConfiguration.disabled()` (the default), `PageNumberConfiguration.enabled(String format)` + `enabled(): boolean`/`format(): String`.
- Consumed by: Task 5 (`PageLayout`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageNumberConfigurationTest {

  @Test
  void disabledHasEmptyFormat() {
    PageNumberConfiguration config = PageNumberConfiguration.disabled();
    assertThat(config.enabled()).isFalse();
    assertThat(config.format()).isEmpty();
  }

  @Test
  void enabledExposesGivenFormat() {
    PageNumberConfiguration config = PageNumberConfiguration.enabled("Page {n} of {total}");
    assertThat(config.enabled()).isTrue();
    assertThat(config.format()).isEqualTo("Page {n} of {total}");
  }
}
```

(No dedicated `LayoutHeader`/`LayoutFooter` test file — their behavior is identical to Stage 3's `BrandHeader`/`BrandFooter`, already covered by that pattern's precedent; a single shared assertion each is folded into Task 5's `PageLayoutTest` instead of duplicating trivial coverage here.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.PageNumberConfigurationTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.layout;

public final class LayoutHeader {

  private final String text;

  private LayoutHeader(String text) {
    this.text = text == null ? "" : text;
  }

  public static LayoutHeader of(String text) {
    return new LayoutHeader(text);
  }

  public String text() {
    return text;
  }
}
```

```java
package io.genfin.document.api.layout;

public final class LayoutFooter {

  private final String text;

  private LayoutFooter(String text) {
    this.text = text == null ? "" : text;
  }

  public static LayoutFooter of(String text) {
    return new LayoutFooter(text);
  }

  public String text() {
    return text;
  }
}
```

```java
package io.genfin.document.api.layout;

public final class PageNumberConfiguration {

  private static final PageNumberConfiguration DISABLED = new PageNumberConfiguration(false, "");

  private final boolean enabled;
  private final String format;

  private PageNumberConfiguration(boolean enabled, String format) {
    this.enabled = enabled;
    this.format = format == null ? "" : format;
  }

  public static PageNumberConfiguration disabled() {
    return DISABLED;
  }

  public static PageNumberConfiguration enabled(String format) {
    return new PageNumberConfiguration(true, format);
  }

  public boolean enabled() {
    return enabled;
  }

  public String format() {
    return format;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.PageNumberConfigurationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/layout/LayoutHeader.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/layout/LayoutFooter.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/layout/PageNumberConfiguration.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/layout/PageNumberConfigurationTest.java
git commit -m "feat(fin-document): add LayoutHeader, LayoutFooter, PageNumberConfiguration"
```

---

### Task 4: `PageLayout` (with builder, null-coalescing from day one)

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/layout/PageLayout.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/layout/PageLayoutTest.java`

**Interfaces:**
- Consumes: `PaperSize`/`Orientation` (Task 1), `Margins` (Task 2), `LayoutHeader`/`LayoutFooter`/`PageNumberConfiguration` (Task 3).
- Produces: `PageLayout.builder(PaperSize paperSize, Orientation orientation): Builder` with `.margins(Margins)`, `.header(LayoutHeader)`, `.footer(LayoutFooter)`, `.pageNumbers(PageNumberConfiguration)`, `.build(): PageLayout`. `PageLayout.paperSize()`/`orientation()`/`margins()`/`header()`/`footer()`/`pageNumbers()`.
- **Every optional setter's stored value MUST be coalesced to its non-null default inside the private constructor** — this is the exact defect class Stage 3's `BrandProfile` had to be fixed for after review; this task ships it correctly from the start, so the constructor itself (not the builder field defaults alone) must null-check and substitute, because a caller can still call `.margins(null)` explicitly.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class PageLayoutTest {

  @Test
  void buildsWithOnlyRequiredFieldsAndDefaultsTheRest() {
    PageLayout layout = PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();

    assertThat(layout.paperSize()).isEqualTo(StandardPaperSize.A4);
    assertThat(layout.orientation()).isEqualTo(StandardOrientation.PORTRAIT);
    assertThat(layout.margins().topPoints()).isZero();
    assertThat(layout.header().text()).isEmpty();
    assertThat(layout.footer().text()).isEmpty();
    assertThat(layout.pageNumbers().enabled()).isFalse();
  }

  @Test
  void buildsWithAllFieldsSet() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.LETTER, StandardOrientation.LANDSCAPE)
            .margins(Margins.uniform(20))
            .header(LayoutHeader.of("Header text"))
            .footer(LayoutFooter.of("Footer text"))
            .pageNumbers(PageNumberConfiguration.enabled("Page {n}"))
            .build();

    assertThat(layout.margins().topPoints()).isEqualTo(20);
    assertThat(layout.header().text()).isEqualTo("Header text");
    assertThat(layout.footer().text()).isEqualTo("Footer text");
    assertThat(layout.pageNumbers().format()).isEqualTo("Page {n}");
  }

  @Test
  void explicitNullOptionalSettersCoalesceToDefaultsRatherThanNull() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
            .margins(null)
            .header(null)
            .footer(null)
            .pageNumbers(null)
            .build();

    assertThat(layout.margins()).isNotNull();
    assertThat(layout.margins().topPoints()).isZero();
    assertThat(layout.header()).isNotNull();
    assertThat(layout.footer()).isNotNull();
    assertThat(layout.pageNumbers()).isNotNull();
    assertThat(layout.pageNumbers().enabled()).isFalse();
  }

  @Test
  void nullPaperSizeThrowsValidationException() {
    assertThatThrownBy(() -> PageLayout.builder(null, StandardOrientation.PORTRAIT).build())
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullOrientationThrowsValidationException() {
    assertThatThrownBy(() -> PageLayout.builder(StandardPaperSize.A4, null).build())
        .isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.PageLayoutTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public final class PageLayout {

  private final PaperSize paperSize;
  private final Orientation orientation;
  private final Margins margins;
  private final LayoutHeader header;
  private final LayoutFooter footer;
  private final PageNumberConfiguration pageNumbers;

  private PageLayout(Builder builder) {
    Validate.notNull(builder.paperSize, "paperSize must not be null");
    Validate.notNull(builder.orientation, "orientation must not be null");
    this.paperSize = builder.paperSize;
    this.orientation = builder.orientation;
    this.margins = builder.margins == null ? Margins.none() : builder.margins;
    this.header = builder.header == null ? LayoutHeader.of("") : builder.header;
    this.footer = builder.footer == null ? LayoutFooter.of("") : builder.footer;
    this.pageNumbers = builder.pageNumbers == null ? PageNumberConfiguration.disabled() : builder.pageNumbers;
  }

  public static Builder builder(PaperSize paperSize, Orientation orientation) {
    return new Builder(paperSize, orientation);
  }

  public PaperSize paperSize() {
    return paperSize;
  }

  public Orientation orientation() {
    return orientation;
  }

  public Margins margins() {
    return margins;
  }

  public LayoutHeader header() {
    return header;
  }

  public LayoutFooter footer() {
    return footer;
  }

  public PageNumberConfiguration pageNumbers() {
    return pageNumbers;
  }

  public static final class Builder {

    private final PaperSize paperSize;
    private final Orientation orientation;
    private Margins margins;
    private LayoutHeader header;
    private LayoutFooter footer;
    private PageNumberConfiguration pageNumbers;

    private Builder(PaperSize paperSize, Orientation orientation) {
      this.paperSize = paperSize;
      this.orientation = orientation;
    }

    public Builder margins(Margins margins) {
      this.margins = margins;
      return this;
    }

    public Builder header(LayoutHeader header) {
      this.header = header;
      return this;
    }

    public Builder footer(LayoutFooter footer) {
      this.footer = footer;
      return this;
    }

    public Builder pageNumbers(PageNumberConfiguration pageNumbers) {
      this.pageNumbers = pageNumbers;
      return this;
    }

    public PageLayout build() {
      return new PageLayout(this);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.layout.PageLayoutTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/layout/PageLayout.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/layout/PageLayoutTest.java
git commit -m "feat(fin-document): add PageLayout aggregate with builder (null-coalescing optional fields from day one)"
```

---

### Task 5: `WatermarkLabel`/`StandardWatermarkLabel`, `WatermarkPlacement`/`StandardWatermarkPlacement`, `WatermarkOpacity`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/WatermarkLabel.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/StandardWatermarkLabel.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/WatermarkPlacement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/StandardWatermarkPlacement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/WatermarkOpacity.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/watermark/StandardWatermarkLabelTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/watermark/StandardWatermarkPlacementTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/watermark/WatermarkOpacityTest.java`

**Interfaces:**
- Produces: `WatermarkLabel` (marker, `String code()`), `StandardWatermarkLabel.of(String code)` + constants `DRAFT`, `PAID`, `VOID`, `COPY`, `CONFIDENTIAL`. `WatermarkPlacement` (marker, `String code()`), `StandardWatermarkPlacement.of(String code)` + constant `DIAGONAL_CENTER`. `WatermarkOpacity.of(double value)` + `value(): double`, `WatermarkOpacity.DEFAULT` (0.3 constant), range-validated `[0.0, 1.0]` via `Validate.required`.
- Consumed by: Task 6 (`Watermark`).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardWatermarkLabelTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardWatermarkLabel.DRAFT.code()).isEqualTo("DRAFT");
    assertThat(StandardWatermarkLabel.PAID.code()).isEqualTo("PAID");
    assertThat(StandardWatermarkLabel.VOID.code()).isEqualTo("VOID");
    assertThat(StandardWatermarkLabel.COPY.code()).isEqualTo("COPY");
    assertThat(StandardWatermarkLabel.CONFIDENTIAL.code()).isEqualTo("CONFIDENTIAL");
  }

  @Test
  void ofBuildsCustomLabel() {
    assertThat(StandardWatermarkLabel.of("SAMPLE").code()).isEqualTo("SAMPLE");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardWatermarkLabel.of("")).isInstanceOf(ValidationException.class);
  }
}
```

```java
package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardWatermarkPlacementTest {

  @Test
  void diagonalCenterConstantExposesExpectedCode() {
    assertThat(StandardWatermarkPlacement.DIAGONAL_CENTER.code()).isEqualTo("DIAGONAL_CENTER");
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardWatermarkPlacement.of(null)).isInstanceOf(ValidationException.class);
  }
}
```

```java
package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class WatermarkOpacityTest {

  @Test
  void exposesGivenValue() {
    assertThat(WatermarkOpacity.of(0.5).value()).isEqualTo(0.5);
  }

  @Test
  void defaultConstantIsPointThree() {
    assertThat(WatermarkOpacity.DEFAULT.value()).isEqualTo(0.3);
  }

  @Test
  void boundaryValuesAreAccepted() {
    assertThat(WatermarkOpacity.of(0.0).value()).isEqualTo(0.0);
    assertThat(WatermarkOpacity.of(1.0).value()).isEqualTo(1.0);
  }

  @Test
  void belowZeroIsRejected() {
    assertThatThrownBy(() -> WatermarkOpacity.of(-0.01)).isInstanceOf(ValidationException.class);
  }

  @Test
  void aboveOneIsRejected() {
    assertThatThrownBy(() -> WatermarkOpacity.of(1.01)).isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.watermark.*"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.watermark;

public interface WatermarkLabel {
  String code();
}
```

```java
package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

public record StandardWatermarkLabel(String code) implements WatermarkLabel {

  public StandardWatermarkLabel {
    Validate.notBlank(code, "WatermarkLabel code must not be blank");
  }

  public static StandardWatermarkLabel of(String code) {
    return new StandardWatermarkLabel(code);
  }

  public static final StandardWatermarkLabel DRAFT = of("DRAFT");
  public static final StandardWatermarkLabel PAID = of("PAID");
  public static final StandardWatermarkLabel VOID = of("VOID");
  public static final StandardWatermarkLabel COPY = of("COPY");
  public static final StandardWatermarkLabel CONFIDENTIAL = of("CONFIDENTIAL");
}
```

```java
package io.genfin.document.api.watermark;

public interface WatermarkPlacement {
  String code();
}
```

```java
package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

public record StandardWatermarkPlacement(String code) implements WatermarkPlacement {

  public StandardWatermarkPlacement {
    Validate.notBlank(code, "WatermarkPlacement code must not be blank");
  }

  public static StandardWatermarkPlacement of(String code) {
    return new StandardWatermarkPlacement(code);
  }

  public static final StandardWatermarkPlacement DIAGONAL_CENTER = of("DIAGONAL_CENTER");
}
```

```java
package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

public final class WatermarkOpacity {

  public static final WatermarkOpacity DEFAULT = new WatermarkOpacity(0.3);

  private final double value;

  private WatermarkOpacity(double value) {
    Validate.required(value >= 0.0 && value <= 1.0, "opacity must be between 0.0 and 1.0 inclusive");
    this.value = value;
  }

  public static WatermarkOpacity of(double value) {
    return new WatermarkOpacity(value);
  }

  public double value() {
    return value;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.watermark.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/WatermarkLabel.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/StandardWatermarkLabel.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/WatermarkPlacement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/StandardWatermarkPlacement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/WatermarkOpacity.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/watermark/
git commit -m "feat(fin-document): add WatermarkLabel, WatermarkPlacement open-value types and WatermarkOpacity"
```

---

### Task 6: `Watermark` + `module-info.java` exports + full build gate

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/Watermark.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/watermark/WatermarkTest.java`
- Modify: `fin-core/fin-document/src/main/java/module-info.java` (add `exports io.genfin.document.api.layout;` and `exports io.genfin.document.api.watermark;`)

**Interfaces:**
- Consumes: `WatermarkLabel`/`WatermarkPlacement`/`WatermarkOpacity` (Task 5).
- Produces: `Watermark.of(WatermarkLabel label, WatermarkPlacement placement, WatermarkOpacity opacity)` + `label()`/`placement()`/`opacity()`. Validates all three non-null.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class WatermarkTest {

  @Test
  void exposesGivenFields() {
    Watermark watermark =
        Watermark.of(StandardWatermarkLabel.DRAFT, StandardWatermarkPlacement.DIAGONAL_CENTER, WatermarkOpacity.DEFAULT);

    assertThat(watermark.label()).isEqualTo(StandardWatermarkLabel.DRAFT);
    assertThat(watermark.placement()).isEqualTo(StandardWatermarkPlacement.DIAGONAL_CENTER);
    assertThat(watermark.opacity()).isEqualTo(WatermarkOpacity.DEFAULT);
  }

  @Test
  void nullLabelThrowsValidationException() {
    assertThatThrownBy(
            () -> Watermark.of(null, StandardWatermarkPlacement.DIAGONAL_CENTER, WatermarkOpacity.DEFAULT))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullPlacementThrowsValidationException() {
    assertThatThrownBy(() -> Watermark.of(StandardWatermarkLabel.DRAFT, null, WatermarkOpacity.DEFAULT))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullOpacityThrowsValidationException() {
    assertThatThrownBy(
            () -> Watermark.of(StandardWatermarkLabel.DRAFT, StandardWatermarkPlacement.DIAGONAL_CENTER, null))
        .isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.watermark.WatermarkTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

public final class Watermark {

  private final WatermarkLabel label;
  private final WatermarkPlacement placement;
  private final WatermarkOpacity opacity;

  private Watermark(WatermarkLabel label, WatermarkPlacement placement, WatermarkOpacity opacity) {
    Validate.notNull(label, "label must not be null");
    Validate.notNull(placement, "placement must not be null");
    Validate.notNull(opacity, "opacity must not be null");
    this.label = label;
    this.placement = placement;
    this.opacity = opacity;
  }

  public static Watermark of(WatermarkLabel label, WatermarkPlacement placement, WatermarkOpacity opacity) {
    return new Watermark(label, placement, opacity);
  }

  public WatermarkLabel label() {
    return label;
  }

  public WatermarkPlacement placement() {
    return placement;
  }

  public WatermarkOpacity opacity() {
    return opacity;
  }
}
```

Add to `module-info.java`:

```java
  exports io.genfin.document.api.layout;
  exports io.genfin.document.api.watermark;
```

- [ ] **Step 4: Run test, then the full gate**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.watermark.WatermarkTest"`
Expected: PASS.

Run: `./gradlew :fin-document:check`
Expected: PASS — compile, Spotless, Checkstyle, PMD, JUnit, JaCoCo, ArchUnit. This stage adds no new `port`/`internal` package and no factory reaching into `internal`, so no new `.ignoreDependency(...)` exception should be needed — if one somehow is, add it the established narrow-additive way in `DocumentArchitectureTest.java`, never via a package move.

Run: `./gradlew build`
Expected: PASS across all modules.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/watermark/Watermark.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/watermark/WatermarkTest.java \
  fin-core/fin-document/src/main/java/module-info.java
git commit -m "feat(fin-document): add Watermark aggregate, export layout/watermark packages, verify architecture constraints hold for Stage 5"
```

## Self-Review Notes

- **Spec coverage:** Layout Engine (Group 3) fully covered — `PageLayout`, `PaperSize`, `Orientation`, `Margins`, `LayoutHeader`/`LayoutFooter`, `PageNumberConfiguration`. Watermark (Group 4) fully covered — `Watermark`, `WatermarkLabel`, `WatermarkPlacement`, `WatermarkOpacity`. Both explicitly stop short of actual rendering (Stage 6).
- **Lessons applied proactively, not reactively:** `PageLayout.Builder`'s null-coalescing is specified in Task 4 from the start (the exact fix Stage 3 needed after review). `Margins`/`WatermarkOpacity`/`Watermark` all validate at construction from the start (the exact fix Stage 4 needed after review, twice).
- **No placeholders:** every step has complete, real code.
