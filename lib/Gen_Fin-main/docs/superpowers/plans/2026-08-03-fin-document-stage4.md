# fin-document Stage 4 Implementation Plan — Letterhead Engine + QR Code Support

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a provider-agnostic Letterhead value model (PDF/PNG/JPEG/SVG/Blank formats, placement, overlay policy) and QR Code value model (provider-supplied image content, placement), each with a provider SPI + registry/resolver + consumer-reachable factory, following the exact pattern established by Stage 2/3 (`PlaceholderProvider`/`TemplateEngines`, `BrandResolver`/`BrandResolvers`).

**Architecture:** `api.letterhead`/`api.qr` hold domain-agnostic value types. `port` holds the two SPIs (`LetterheadProvider`, `QrCodeProvider`) and their facades/factories. `internal.letterhead`/`internal.qr` hold default registries/resolvers. Neither wires into `DocumentComposer` yet — that's Stage 6.

**Tech Stack:** Java, JUnit 5 + AssertJ, ArchUnit. Only dependency: `fin-api`.

## Global Constraints

- Module `fin-document` depends on `fin-api` only. No filesystem/S3/blob-storage/database/cloud-API code anywhere — providers are 100% application-supplied.
- Forbidden imports: `org.springframework..`, `javax.persistence..`, `jakarta.persistence..`, `com.fasterxml.jackson..`, `com.google.gson..`, any PDF/image/HTTP/cloud-SDK library.
- No `instanceof`, no `switch`/`switch(` anywhere in `fin-document` source.
- Package layout exactly: `io.genfin.document.api.letterhead`, `io.genfin.document.api.qr`, `io.genfin.document.port`, `io.genfin.document.internal.letterhead`, `io.genfin.document.internal.qr`. Nothing under `internal` is ever exported.
- Do not modify any existing Stage 1-3 file's public signature.
- **This module has had five prior incidents of implementers moving classes across api/port/internal boundaries to dodge ArchUnit cycles instead of adding a targeted `.ignoreDependency(...)` exception to `DocumentArchitectureTest.java`'s factory-class pattern (which already has entries for DocumentRenderers, TemplateEngines, PlaceholderResolvers, DocumentComposers, BrandResolvers). If a cycle check fails, add one more narrow entry — never move a class.**
- Reuse `LetterheadId` (already exists: `io.genfin.document.api.identity.LetterheadId`) — do not redeclare. QR has no pre-existing identity type and needs none (resolved by app-defined `String` key, per design spec).
- `Letterhead`/`QrCodeContent`'s byte arrays must be defensively copied on construction AND return, with a mutation-based test (not equality-only) written from the start.
- SPI interfaces (`LetterheadProvider`, `QrCodeProvider`) extend `io.genfin.api.port.spi.Extension`.
- Validation via `io.genfin.api.validation.Validate` where it fits.
- Test stack: JUnit 5 + AssertJ. Build/verify command every task: `./gradlew :fin-document:check`.

---

### Task 1: `LetterheadFormat` + `StandardLetterheadFormat`, `LetterheadPlacement` + `StandardLetterheadPlacement`, `LetterheadOverlayPolicy` + `StandardLetterheadOverlayPolicy`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/LetterheadFormat.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/StandardLetterheadFormat.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/LetterheadPlacement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/StandardLetterheadPlacement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/LetterheadOverlayPolicy.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/StandardLetterheadOverlayPolicy.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/letterhead/StandardLetterheadFormatTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/letterhead/StandardLetterheadPlacementTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/letterhead/StandardLetterheadOverlayPolicyTest.java`

**Interfaces:**
- Produces: `LetterheadFormat` (marker interface, `String code()`), `StandardLetterheadFormat.of(String code)` + constants `PDF`, `PNG`, `JPEG`, `SVG`, `BLANK`. `LetterheadPlacement` (marker interface, `String code()`), `StandardLetterheadPlacement.of(String code)` + constant `FULL_PAGE`. `LetterheadOverlayPolicy` (marker interface, `String code()`), `StandardLetterheadOverlayPolicy.of(String code)` + constants `CONTENT_ON_TOP`, `LETTERHEAD_ON_TOP`. Use the exact pattern of `io.genfin.document.api.model.StandardDocumentType` (record implementing marker interface, validates via `io.genfin.api.validation.Validate.notBlank`, `of(code)` factory, `public static final` constants) — read that file first to copy the pattern precisely, including its use of `Validate` (not a hand-rolled check — Stage 1's own final review caught and fixed exactly this mistake in `StandardDocumentType` itself, so there is no excuse to reintroduce it here).
- Consumed by: Task 2 (`Letterhead`), Task 5 (`QrCodePlacement` follows the identical pattern).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardLetterheadFormatTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardLetterheadFormat.PDF.code()).isEqualTo("PDF");
    assertThat(StandardLetterheadFormat.PNG.code()).isEqualTo("PNG");
    assertThat(StandardLetterheadFormat.JPEG.code()).isEqualTo("JPEG");
    assertThat(StandardLetterheadFormat.SVG.code()).isEqualTo("SVG");
    assertThat(StandardLetterheadFormat.BLANK.code()).isEqualTo("BLANK");
  }

  @Test
  void ofBuildsCustomFormat() {
    assertThat(StandardLetterheadFormat.of("WEBP").code()).isEqualTo("WEBP");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardLetterheadFormat.of("")).isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardLetterheadFormat.of(null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void equalsIsBasedOnCode() {
    assertThat(StandardLetterheadFormat.of("PDF")).isEqualTo(StandardLetterheadFormat.PDF);
  }
}
```

```java
package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardLetterheadPlacementTest {

  @Test
  void fullPageConstantExposesExpectedCode() {
    assertThat(StandardLetterheadPlacement.FULL_PAGE.code()).isEqualTo("FULL_PAGE");
  }

  @Test
  void ofBuildsCustomPlacement() {
    assertThat(StandardLetterheadPlacement.of("HEADER_ONLY").code()).isEqualTo("HEADER_ONLY");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardLetterheadPlacement.of(" ")).isInstanceOf(ValidationException.class);
  }
}
```

```java
package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardLetterheadOverlayPolicyTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardLetterheadOverlayPolicy.CONTENT_ON_TOP.code()).isEqualTo("CONTENT_ON_TOP");
    assertThat(StandardLetterheadOverlayPolicy.LETTERHEAD_ON_TOP.code()).isEqualTo("LETTERHEAD_ON_TOP");
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardLetterheadOverlayPolicy.of(null)).isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.letterhead.*"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

Mirror `io.genfin.document.api.model.StandardDocumentType` and its `DocumentType` marker interface exactly, three times, once per type family:

```java
package io.genfin.document.api.letterhead;

public interface LetterheadFormat {
  String code();
}
```

```java
package io.genfin.document.api.letterhead;

import io.genfin.api.validation.Validate;

public record StandardLetterheadFormat(String code) implements LetterheadFormat {

  public StandardLetterheadFormat {
    Validate.notBlank(code, "LetterheadFormat code must not be blank");
  }

  public static StandardLetterheadFormat of(String code) {
    return new StandardLetterheadFormat(code);
  }

  public static final StandardLetterheadFormat PDF = of("PDF");
  public static final StandardLetterheadFormat PNG = of("PNG");
  public static final StandardLetterheadFormat JPEG = of("JPEG");
  public static final StandardLetterheadFormat SVG = of("SVG");
  public static final StandardLetterheadFormat BLANK = of("BLANK");
}
```

```java
package io.genfin.document.api.letterhead;

public interface LetterheadPlacement {
  String code();
}
```

```java
package io.genfin.document.api.letterhead;

import io.genfin.api.validation.Validate;

public record StandardLetterheadPlacement(String code) implements LetterheadPlacement {

  public StandardLetterheadPlacement {
    Validate.notBlank(code, "LetterheadPlacement code must not be blank");
  }

  public static StandardLetterheadPlacement of(String code) {
    return new StandardLetterheadPlacement(code);
  }

  public static final StandardLetterheadPlacement FULL_PAGE = of("FULL_PAGE");
}
```

```java
package io.genfin.document.api.letterhead;

public interface LetterheadOverlayPolicy {
  String code();
}
```

```java
package io.genfin.document.api.letterhead;

import io.genfin.api.validation.Validate;

public record StandardLetterheadOverlayPolicy(String code) implements LetterheadOverlayPolicy {

  public StandardLetterheadOverlayPolicy {
    Validate.notBlank(code, "LetterheadOverlayPolicy code must not be blank");
  }

  public static StandardLetterheadOverlayPolicy of(String code) {
    return new StandardLetterheadOverlayPolicy(code);
  }

  public static final StandardLetterheadOverlayPolicy CONTENT_ON_TOP = of("CONTENT_ON_TOP");
  public static final StandardLetterheadOverlayPolicy LETTERHEAD_ON_TOP = of("LETTERHEAD_ON_TOP");
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.letterhead.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/LetterheadFormat.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/StandardLetterheadFormat.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/LetterheadPlacement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/StandardLetterheadPlacement.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/LetterheadOverlayPolicy.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/StandardLetterheadOverlayPolicy.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/letterhead/
git commit -m "feat(fin-document): add LetterheadFormat, LetterheadPlacement, LetterheadOverlayPolicy open-value types"
```

---

### Task 2: `Letterhead`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/Letterhead.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/letterhead/LetterheadTest.java`

**Interfaces:**
- Consumes: `LetterheadId` (Stage 1 identity), `LetterheadFormat` (Task 1).
- Produces: `Letterhead.of(LetterheadId id, LetterheadFormat format, byte[] content, String mimeType)` + `id()`/`format()`/`content(): byte[]` (defensively copied on construction and return) + `mimeType()`.
- Consumed by: Task 3 (`LetterheadProvider`/`LetterheadResolver`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.LetterheadId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LetterheadTest {

  @Test
  void exposesGivenFields() {
    byte[] source = "fake-pdf-bytes".getBytes(StandardCharsets.UTF_8);
    Letterhead letterhead =
        Letterhead.of(LetterheadId.of("lh-1"), StandardLetterheadFormat.PDF, source, "application/pdf");

    assertThat(letterhead.id()).isEqualTo(LetterheadId.of("lh-1"));
    assertThat(letterhead.format()).isEqualTo(StandardLetterheadFormat.PDF);
    assertThat(letterhead.content()).isEqualTo(source);
    assertThat(letterhead.mimeType()).isEqualTo("application/pdf");
  }

  @Test
  void contentIsDefensivelyCopiedOnConstructionAndReturn() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    Letterhead letterhead =
        Letterhead.of(LetterheadId.of("lh-1"), StandardLetterheadFormat.PDF, source, "application/pdf");

    source[0] = (byte) 0xFF;
    assertThat(letterhead.content()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));

    byte[] returned = letterhead.content();
    returned[0] = (byte) 0xFF;
    assertThat(letterhead.content()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void blankFormatSupportsEmptyContentArray() {
    Letterhead letterhead =
        Letterhead.of(LetterheadId.of("lh-blank"), StandardLetterheadFormat.BLANK, new byte[0], "");
    assertThat(letterhead.content()).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.letterhead.LetterheadTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.letterhead;

import io.genfin.document.api.identity.LetterheadId;
import java.util.Arrays;

public final class Letterhead {

  private final LetterheadId id;
  private final LetterheadFormat format;
  private final byte[] content;
  private final String mimeType;

  private Letterhead(LetterheadId id, LetterheadFormat format, byte[] content, String mimeType) {
    this.id = id;
    this.format = format;
    this.content = Arrays.copyOf(content, content.length);
    this.mimeType = mimeType;
  }

  public static Letterhead of(LetterheadId id, LetterheadFormat format, byte[] content, String mimeType) {
    return new Letterhead(id, format, content, mimeType);
  }

  public LetterheadId id() {
    return id;
  }

  public LetterheadFormat format() {
    return format;
  }

  public byte[] content() {
    return Arrays.copyOf(content, content.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.letterhead.LetterheadTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/letterhead/Letterhead.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/letterhead/LetterheadTest.java
git commit -m "feat(fin-document): add Letterhead value type with defensive byte-array copy"
```

---

### Task 3: `LetterheadNotFoundException` + `LetterheadProvider` SPI + `LetterheadResolver` port + `DefaultLetterheadRegistry`/`DefaultLetterheadResolver`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/exception/LetterheadNotFoundException.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/LetterheadProvider.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/LetterheadResolver.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/letterhead/DefaultLetterheadRegistry.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/letterhead/DefaultLetterheadResolver.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/letterhead/DefaultLetterheadResolverTest.java`

**Interfaces:**
- Consumes: `Letterhead` (Task 2), `LetterheadId` (Stage 1 identity).
- Produces: `LetterheadProvider.supports(LetterheadId id): boolean` + `resolve(LetterheadId id): Letterhead`. `LetterheadResolver.resolve(LetterheadId): Letterhead`. `DefaultLetterheadRegistry.register(LetterheadProvider)` + `providers(): List<LetterheadProvider>`. `DefaultLetterheadResolver(DefaultLetterheadRegistry)` implements `LetterheadResolver`, delegating to the first supporting provider, throwing `LetterheadNotFoundException` if none supports the id.
- Consumed by: Task 4 (`LetterheadResolvers` factory).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.exception.LetterheadNotFoundException;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import io.genfin.document.port.LetterheadProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DefaultLetterheadResolverTest {

  private static LetterheadProvider providerFor(String supportedId, String content) {
    return new LetterheadProvider() {
      @Override
      public boolean supports(LetterheadId id) {
        return LetterheadId.of(supportedId).equals(id);
      }

      @Override
      public Letterhead resolve(LetterheadId id) {
        return Letterhead.of(
            id, StandardLetterheadFormat.PDF, content.getBytes(StandardCharsets.UTF_8), "application/pdf");
      }
    };
  }

  @Test
  void resolvesThroughFirstSupportingProviderAmongMultiple() {
    DefaultLetterheadRegistry registry = new DefaultLetterheadRegistry();
    registry.register(providerFor("other-lh", "other-content"));
    registry.register(providerFor("lh-1", "lh-1-content"));
    DefaultLetterheadResolver resolver = new DefaultLetterheadResolver(registry);

    Letterhead resolved = resolver.resolve(LetterheadId.of("lh-1"));

    assertThat(new String(resolved.content(), StandardCharsets.UTF_8)).isEqualTo("lh-1-content");
  }

  @Test
  void throwsForUnsupportedLetterheadId() {
    DefaultLetterheadResolver resolver = new DefaultLetterheadResolver(new DefaultLetterheadRegistry());

    assertThatThrownBy(() -> resolver.resolve(LetterheadId.of("missing")))
        .isInstanceOf(LetterheadNotFoundException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.letterhead.DefaultLetterheadResolverTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.exception;

import io.genfin.document.api.identity.LetterheadId;

public final class LetterheadNotFoundException extends RuntimeException {

  public LetterheadNotFoundException(LetterheadId letterheadId) {
    super("No letterhead provider supports id: " + letterheadId);
  }
}
```

```java
package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;

public interface LetterheadProvider extends Extension {
  boolean supports(LetterheadId id);

  Letterhead resolve(LetterheadId id);
}
```

```java
package io.genfin.document.port;

import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;

public interface LetterheadResolver {
  Letterhead resolve(LetterheadId id);
}
```

```java
package io.genfin.document.internal.letterhead;

import io.genfin.document.port.LetterheadProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultLetterheadRegistry {

  private final List<LetterheadProvider> providers = new CopyOnWriteArrayList<>();

  public void register(LetterheadProvider provider) {
    providers.add(provider);
  }

  public List<LetterheadProvider> providers() {
    return List.copyOf(providers);
  }
}
```

```java
package io.genfin.document.internal.letterhead;

import io.genfin.document.api.exception.LetterheadNotFoundException;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.port.LetterheadProvider;
import io.genfin.document.port.LetterheadResolver;

public final class DefaultLetterheadResolver implements LetterheadResolver {

  private final DefaultLetterheadRegistry registry;

  public DefaultLetterheadResolver(DefaultLetterheadRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Letterhead resolve(LetterheadId id) {
    for (LetterheadProvider provider : registry.providers()) {
      if (provider.supports(id)) {
        return provider.resolve(id);
      }
    }
    throw new LetterheadNotFoundException(id);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.letterhead.DefaultLetterheadResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/exception/LetterheadNotFoundException.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/LetterheadProvider.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/LetterheadResolver.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/letterhead/ \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/letterhead/DefaultLetterheadResolverTest.java
git commit -m "feat(fin-document): add LetterheadProvider SPI and default registry/resolver"
```

---

### Task 4: `LetterheadResolvers` factory

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/LetterheadResolvers.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/port/LetterheadResolversTest.java`

**Interfaces:**
- Consumes: `DefaultLetterheadRegistry`, `DefaultLetterheadResolver` (Task 3).
- Produces: `LetterheadResolvers.standard(): Bundle` where `Bundle` has `register(LetterheadProvider)` and `resolver(): LetterheadResolver` — mirrors `BrandResolvers.standard()`/`TemplateEngines.standard()` exactly.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LetterheadResolversTest {

  @Test
  void registerThenResolveRoundTrips() {
    LetterheadResolvers.Bundle bundle = LetterheadResolvers.standard();
    LetterheadProvider provider =
        new LetterheadProvider() {
          @Override
          public boolean supports(LetterheadId id) {
            return LetterheadId.of("lh-1").equals(id);
          }

          @Override
          public Letterhead resolve(LetterheadId id) {
            return Letterhead.of(
                id, StandardLetterheadFormat.PDF, "content".getBytes(StandardCharsets.UTF_8), "application/pdf");
          }
        };

    bundle.register(provider);

    Letterhead resolved = bundle.resolver().resolve(LetterheadId.of("lh-1"));
    assertThat(new String(resolved.content(), StandardCharsets.UTF_8)).isEqualTo("content");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.LetterheadResolversTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.document.internal.letterhead.DefaultLetterheadRegistry;
import io.genfin.document.internal.letterhead.DefaultLetterheadResolver;

public final class LetterheadResolvers {

  private LetterheadResolvers() {}

  public static Bundle standard() {
    DefaultLetterheadRegistry registry = new DefaultLetterheadRegistry();
    DefaultLetterheadResolver resolver = new DefaultLetterheadResolver(registry);
    return new Bundle() {
      @Override
      public void register(LetterheadProvider provider) {
        registry.register(provider);
      }

      @Override
      public LetterheadResolver resolver() {
        return resolver;
      }
    };
  }

  public interface Bundle {
    void register(LetterheadProvider provider);

    LetterheadResolver resolver();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.LetterheadResolversTest"`
Expected: PASS. If the architecture cycle test fails, add one more entry to the existing named-factory-class exception pattern in `DocumentArchitectureTest.java` (mirroring `BrandResolvers`'s entry exactly) — never move a class.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/LetterheadResolvers.java \
  fin-core/fin-document/src/test/java/io/genfin/document/port/LetterheadResolversTest.java
git commit -m "feat(fin-document): add LetterheadResolvers factory (consumer-reachable registration + resolution)"
```

---

### Task 5: `QrCodePlacement` + `StandardQrCodePlacement`, `QrCodeContent`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/qr/QrCodePlacement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/qr/StandardQrCodePlacement.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/qr/QrCodeContent.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/qr/StandardQrCodePlacementTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/qr/QrCodeContentTest.java`

**Interfaces:**
- Produces: `QrCodePlacement` (marker interface, `String code()`), `StandardQrCodePlacement.of(String code)` + constant `BOTTOM_RIGHT`. `QrCodeContent.of(byte[] imageBytes, String mimeType)` + `imageBytes(): byte[]` (defensively copied on construction and return) + `mimeType()`.
- Consumed by: Task 6 (`QrCodeProvider`/`QrCodeResolver`).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardQrCodePlacementTest {

  @Test
  void bottomRightConstantExposesExpectedCode() {
    assertThat(StandardQrCodePlacement.BOTTOM_RIGHT.code()).isEqualTo("BOTTOM_RIGHT");
  }

  @Test
  void ofBuildsCustomPlacement() {
    assertThat(StandardQrCodePlacement.of("TOP_LEFT").code()).isEqualTo("TOP_LEFT");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardQrCodePlacement.of("")).isInstanceOf(ValidationException.class);
  }
}
```

```java
package io.genfin.document.api.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QrCodeContentTest {

  @Test
  void exposesGivenBytesAndMimeType() {
    byte[] source = "fake-qr-png".getBytes(StandardCharsets.UTF_8);
    QrCodeContent content = QrCodeContent.of(source, "image/png");

    assertThat(content.imageBytes()).isEqualTo(source);
    assertThat(content.mimeType()).isEqualTo("image/png");
  }

  @Test
  void contentIsDefensivelyCopiedOnConstructionAndReturn() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    QrCodeContent content = QrCodeContent.of(source, "image/png");

    source[0] = (byte) 0xFF;
    assertThat(content.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));

    byte[] returned = content.imageBytes();
    returned[0] = (byte) 0xFF;
    assertThat(content.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void nullImageBytesThrowsValidationException() {
    assertThatThrownBy(() -> QrCodeContent.of(null, "image/png"))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }

  @Test
  void blankMimeTypeThrowsValidationException() {
    assertThatThrownBy(() -> QrCodeContent.of("x".getBytes(StandardCharsets.UTF_8), ""))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.qr.*"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.qr;

public interface QrCodePlacement {
  String code();
}
```

```java
package io.genfin.document.api.qr;

import io.genfin.api.validation.Validate;

public record StandardQrCodePlacement(String code) implements QrCodePlacement {

  public StandardQrCodePlacement {
    Validate.notBlank(code, "QrCodePlacement code must not be blank");
  }

  public static StandardQrCodePlacement of(String code) {
    return new StandardQrCodePlacement(code);
  }

  public static final StandardQrCodePlacement BOTTOM_RIGHT = of("BOTTOM_RIGHT");
}
```

```java
package io.genfin.document.api.qr;

import io.genfin.api.validation.Validate;
import java.util.Arrays;

public final class QrCodeContent {

  private final byte[] imageBytes;
  private final String mimeType;

  private QrCodeContent(byte[] imageBytes, String mimeType) {
    Validate.notNull(imageBytes, "imageBytes must not be null");
    Validate.notBlank(mimeType, "mimeType must not be blank");
    this.imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
    this.mimeType = mimeType;
  }

  public static QrCodeContent of(byte[] imageBytes, String mimeType) {
    return new QrCodeContent(imageBytes, mimeType);
  }

  public byte[] imageBytes() {
    return Arrays.copyOf(imageBytes, imageBytes.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.qr.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/qr/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/qr/
git commit -m "feat(fin-document): add QrCodePlacement open-value type and QrCodeContent with defensive byte-array copy"
```

---

### Task 6: `QrCodeNotFoundException` + `QrCodeProvider` SPI + `QrCodeResolver` port + `DefaultQrCodeRegistry`/`DefaultQrCodeResolver`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/exception/QrCodeNotFoundException.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/QrCodeProvider.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/QrCodeResolver.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/qr/DefaultQrCodeRegistry.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/qr/DefaultQrCodeResolver.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/qr/DefaultQrCodeResolverTest.java`

**Interfaces:**
- Consumes: `QrCodeContent` (Task 5).
- Produces: `QrCodeProvider.supports(String key): boolean` + `resolve(String key): QrCodeContent`. `QrCodeResolver.resolve(String key): QrCodeContent`. `DefaultQrCodeRegistry.register(QrCodeProvider)` + `providers(): List<QrCodeProvider>`. `DefaultQrCodeResolver(DefaultQrCodeRegistry)` implements `QrCodeResolver`, first-supporting-provider-wins, `QrCodeNotFoundException` on miss.
- Consumed by: Task 7 (`QrCodeResolvers` factory).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.exception.QrCodeNotFoundException;
import io.genfin.document.api.qr.QrCodeContent;
import io.genfin.document.port.QrCodeProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DefaultQrCodeResolverTest {

  private static QrCodeProvider providerFor(String supportedKey, String content) {
    return new QrCodeProvider() {
      @Override
      public boolean supports(String key) {
        return supportedKey.equals(key);
      }

      @Override
      public QrCodeContent resolve(String key) {
        return QrCodeContent.of(content.getBytes(StandardCharsets.UTF_8), "image/png");
      }
    };
  }

  @Test
  void resolvesThroughFirstSupportingProviderAmongMultiple() {
    DefaultQrCodeRegistry registry = new DefaultQrCodeRegistry();
    registry.register(providerFor("other-key", "other-content"));
    registry.register(providerFor("payment-link", "payment-content"));
    DefaultQrCodeResolver resolver = new DefaultQrCodeResolver(registry);

    QrCodeContent resolved = resolver.resolve("payment-link");

    assertThat(new String(resolved.imageBytes(), StandardCharsets.UTF_8)).isEqualTo("payment-content");
  }

  @Test
  void throwsForUnsupportedKey() {
    DefaultQrCodeResolver resolver = new DefaultQrCodeResolver(new DefaultQrCodeRegistry());

    assertThatThrownBy(() -> resolver.resolve("missing")).isInstanceOf(QrCodeNotFoundException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.qr.DefaultQrCodeResolverTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.exception;

public final class QrCodeNotFoundException extends RuntimeException {

  public QrCodeNotFoundException(String key) {
    super("No QR code provider supports key: " + key);
  }
}
```

```java
package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.qr.QrCodeContent;

public interface QrCodeProvider extends Extension {
  boolean supports(String key);

  QrCodeContent resolve(String key);
}
```

```java
package io.genfin.document.port;

import io.genfin.document.api.qr.QrCodeContent;

public interface QrCodeResolver {
  QrCodeContent resolve(String key);
}
```

```java
package io.genfin.document.internal.qr;

import io.genfin.document.port.QrCodeProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultQrCodeRegistry {

  private final List<QrCodeProvider> providers = new CopyOnWriteArrayList<>();

  public void register(QrCodeProvider provider) {
    providers.add(provider);
  }

  public List<QrCodeProvider> providers() {
    return List.copyOf(providers);
  }
}
```

```java
package io.genfin.document.internal.qr;

import io.genfin.document.api.exception.QrCodeNotFoundException;
import io.genfin.document.api.qr.QrCodeContent;
import io.genfin.document.port.QrCodeProvider;
import io.genfin.document.port.QrCodeResolver;

public final class DefaultQrCodeResolver implements QrCodeResolver {

  private final DefaultQrCodeRegistry registry;

  public DefaultQrCodeResolver(DefaultQrCodeRegistry registry) {
    this.registry = registry;
  }

  @Override
  public QrCodeContent resolve(String key) {
    for (QrCodeProvider provider : registry.providers()) {
      if (provider.supports(key)) {
        return provider.resolve(key);
      }
    }
    throw new QrCodeNotFoundException(key);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.qr.DefaultQrCodeResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/exception/QrCodeNotFoundException.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/QrCodeProvider.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/QrCodeResolver.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/qr/ \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/qr/DefaultQrCodeResolverTest.java
git commit -m "feat(fin-document): add QrCodeProvider SPI and default registry/resolver"
```

---

### Task 7: `QrCodeResolvers` factory + `module-info.java` exports + full build gate

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/QrCodeResolvers.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/port/QrCodeResolversTest.java`
- Modify: `fin-core/fin-document/src/main/java/module-info.java` (add `exports io.genfin.document.api.letterhead;` and `exports io.genfin.document.api.qr;`)

**Interfaces:**
- Consumes: `DefaultQrCodeRegistry`, `DefaultQrCodeResolver` (Task 6).
- Produces: `QrCodeResolvers.standard(): Bundle` with `register(QrCodeProvider)`/`resolver(): QrCodeResolver` — mirrors `LetterheadResolvers.standard()` exactly.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.qr.QrCodeContent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QrCodeResolversTest {

  @Test
  void registerThenResolveRoundTrips() {
    QrCodeResolvers.Bundle bundle = QrCodeResolvers.standard();
    QrCodeProvider provider =
        new QrCodeProvider() {
          @Override
          public boolean supports(String key) {
            return "payment-link".equals(key);
          }

          @Override
          public QrCodeContent resolve(String key) {
            return QrCodeContent.of("content".getBytes(StandardCharsets.UTF_8), "image/png");
          }
        };

    bundle.register(provider);

    QrCodeContent resolved = bundle.resolver().resolve("payment-link");
    assertThat(new String(resolved.imageBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.QrCodeResolversTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.document.internal.qr.DefaultQrCodeRegistry;
import io.genfin.document.internal.qr.DefaultQrCodeResolver;

public final class QrCodeResolvers {

  private QrCodeResolvers() {}

  public static Bundle standard() {
    DefaultQrCodeRegistry registry = new DefaultQrCodeRegistry();
    DefaultQrCodeResolver resolver = new DefaultQrCodeResolver(registry);
    return new Bundle() {
      @Override
      public void register(QrCodeProvider provider) {
        registry.register(provider);
      }

      @Override
      public QrCodeResolver resolver() {
        return resolver;
      }
    };
  }

  public interface Bundle {
    void register(QrCodeProvider provider);

    QrCodeResolver resolver();
  }
}
```

Add to `module-info.java`:

```java
  exports io.genfin.document.api.letterhead;
  exports io.genfin.document.api.qr;
```

- [ ] **Step 4: Run test to verify it passes, then the full gate**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.QrCodeResolversTest"`
Expected: PASS. If the architecture cycle test fails, add one more entry to the existing named-factory-class exception pattern (mirroring `LetterheadResolvers`) — never move a class.

Run: `./gradlew :fin-document:check`
Expected: PASS — compile, Spotless, Checkstyle, PMD, JUnit, JaCoCo, ArchUnit.

Run: `./gradlew build`
Expected: PASS across all modules.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/QrCodeResolvers.java \
  fin-core/fin-document/src/test/java/io/genfin/document/port/QrCodeResolversTest.java \
  fin-core/fin-document/src/main/java/module-info.java
git commit -m "feat(fin-document): add QrCodeResolvers factory, export letterhead/qr packages, verify architecture constraints hold for Stage 4"
```

## Self-Review Notes

- **Spec coverage:** Letterhead Engine (Group 2) fully covered — `Letterhead`, `LetterheadProvider`, `LetterheadResolver`, `LetterheadRegistry` (internal), `LetterheadOverlayPolicy`, `LetterheadPlacement`, formats (PDF/PNG/JPEG/SVG/Blank). QR Code Support (Group 5) fully covered — `QrCodeProvider`, `QrCodeResolver`, `QrCodeContent`, `QrCodePlacement`. Both explicitly stop short of actual rendering/overlay (Stage 6).
- **Type consistency:** Both Letterhead and QR sides use the identical first-supporting-provider-wins resolution shape, the identical `Bundle` factory shape, and the identical defensive-copy contract — verified consistent across Tasks 3/4 (Letterhead) and 6/7 (QR).
- **No placeholders:** every step has complete, real code.
