# fin-document Stage 3 Implementation Plan — Brand Profile

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a generic, provider-agnostic `BrandProfile` value model (company identity, logo, contact info, registration numbers, header/footer text) plus its registry/resolver, following the exact register/resolve pattern established by Stage 2's `DocumentTemplate`/`TemplateResolver`.

**Architecture:** `api.brand` holds domain-agnostic value types. `port` holds `BrandResolver` (facade) and `BrandResolvers` (consumer-reachable factory). `internal.brand` holds `DefaultBrandRegistry`/`DefaultBrandResolver`.

**Tech Stack:** Java, JUnit 5 + AssertJ, ArchUnit. Only dependency: `fin-api`.

## Global Constraints

- Module `fin-document` depends on `fin-api` only. No CPMS/Invoice/Payment/Refund/Project/Tenant/Customer reference anywhere.
- Forbidden imports: `org.springframework..`, `javax.persistence..`, `jakarta.persistence..`, `com.fasterxml.jackson..`, `com.google.gson..`, any PDF/HTML/image library, any HTTP/cloud-SDK package.
- No `instanceof`, no `switch`/`switch(` anywhere in `fin-document` source.
- Package layout exactly: `io.genfin.document.api.brand`, `io.genfin.document.port`, `io.genfin.document.internal.brand`. Nothing under `internal` is ever exported from `module-info.java`.
- Do not modify any existing Stage 1/2 file's public signature.
- **This module has had five prior incidents of implementers moving classes across api/port/internal boundaries to dodge ArchUnit cycles instead of adding a targeted `.ignoreDependency(...)` exception in `DocumentArchitectureTest.java` (following the exact pattern of the existing entries there). If a cycle check fails, the fix is always a narrow additive exception, never a package move.**
- Reuse `BrandId` (already exists: `io.genfin.document.api.identity.BrandId`) — do not redeclare.
- Validation via `io.genfin.api.validation.Validate` where it fits (reuse, don't hand-roll blank/null checks).
- `BrandLogo`'s byte array must be defensively copied on construction AND on return — write the mutation-based test (mutate source/returned array, confirm no effect), not an equality-only test.
- Test stack: JUnit 5 + AssertJ. Build/verify command every task: `./gradlew :fin-document:check`.

---

### Task 1: `BrandContactInformation`, `BrandRegistrationInformation`, `BrandHeader`, `BrandFooter`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandContactInformation.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandRegistrationInformation.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandHeader.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandFooter.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandContactInformationTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandRegistrationInformationTest.java`

**Interfaces:**
- Produces: `BrandContactInformation.of(String phone, String email, String website)` + `phone()`/`email()`/`website()`, null inputs become `""`. `BrandRegistrationInformation.of(String taxRegistrationNumber, String businessRegistrationNumber)` + accessors, same null-to-empty rule. `BrandHeader.of(String text)` + `text()`. `BrandFooter.of(String text)` + `text()`. All null-safe (no `""` becomes `""`, not an exception — these are optional data fields, not identifiers).
- Consumed by: Task 4 (`BrandProfile` builder).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrandContactInformationTest {

  @Test
  void exposesGivenValues() {
    BrandContactInformation info = BrandContactInformation.of("+1-555-0100", "hi@acme.test", "acme.test");
    assertThat(info.phone()).isEqualTo("+1-555-0100");
    assertThat(info.email()).isEqualTo("hi@acme.test");
    assertThat(info.website()).isEqualTo("acme.test");
  }

  @Test
  void nullInputsBecomeEmptyStrings() {
    BrandContactInformation info = BrandContactInformation.of(null, null, null);
    assertThat(info.phone()).isEmpty();
    assertThat(info.email()).isEmpty();
    assertThat(info.website()).isEmpty();
  }
}
```

```java
package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrandRegistrationInformationTest {

  @Test
  void exposesGivenValues() {
    BrandRegistrationInformation info = BrandRegistrationInformation.of("TAX-1", "BIZ-1");
    assertThat(info.taxRegistrationNumber()).isEqualTo("TAX-1");
    assertThat(info.businessRegistrationNumber()).isEqualTo("BIZ-1");
  }

  @Test
  void nullInputsBecomeEmptyStrings() {
    BrandRegistrationInformation info = BrandRegistrationInformation.of(null, null);
    assertThat(info.taxRegistrationNumber()).isEmpty();
    assertThat(info.businessRegistrationNumber()).isEmpty();
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandContactInformationTest" --tests "io.genfin.document.api.brand.BrandRegistrationInformationTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.brand;

public final class BrandContactInformation {

  private final String phone;
  private final String email;
  private final String website;

  private BrandContactInformation(String phone, String email, String website) {
    this.phone = phone == null ? "" : phone;
    this.email = email == null ? "" : email;
    this.website = website == null ? "" : website;
  }

  public static BrandContactInformation of(String phone, String email, String website) {
    return new BrandContactInformation(phone, email, website);
  }

  public String phone() {
    return phone;
  }

  public String email() {
    return email;
  }

  public String website() {
    return website;
  }
}
```

```java
package io.genfin.document.api.brand;

public final class BrandRegistrationInformation {

  private final String taxRegistrationNumber;
  private final String businessRegistrationNumber;

  private BrandRegistrationInformation(String taxRegistrationNumber, String businessRegistrationNumber) {
    this.taxRegistrationNumber = taxRegistrationNumber == null ? "" : taxRegistrationNumber;
    this.businessRegistrationNumber = businessRegistrationNumber == null ? "" : businessRegistrationNumber;
  }

  public static BrandRegistrationInformation of(String taxRegistrationNumber, String businessRegistrationNumber) {
    return new BrandRegistrationInformation(taxRegistrationNumber, businessRegistrationNumber);
  }

  public String taxRegistrationNumber() {
    return taxRegistrationNumber;
  }

  public String businessRegistrationNumber() {
    return businessRegistrationNumber;
  }
}
```

```java
package io.genfin.document.api.brand;

public final class BrandHeader {

  private final String text;

  private BrandHeader(String text) {
    this.text = text == null ? "" : text;
  }

  public static BrandHeader of(String text) {
    return new BrandHeader(text);
  }

  public String text() {
    return text;
  }
}
```

```java
package io.genfin.document.api.brand;

public final class BrandFooter {

  private final String text;

  private BrandFooter(String text) {
    this.text = text == null ? "" : text;
  }

  public static BrandFooter of(String text) {
    return new BrandFooter(text);
  }

  public String text() {
    return text;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandContactInformationTest" --tests "io.genfin.document.api.brand.BrandRegistrationInformationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandContactInformation.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandRegistrationInformation.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandHeader.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandFooter.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/brand/
git commit -m "feat(fin-document): add BrandContactInformation, BrandRegistrationInformation, BrandHeader, BrandFooter"
```

---

### Task 2: `BrandIdentity`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandIdentity.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandIdentityTest.java`

**Interfaces:**
- Produces: `BrandIdentity.of(String companyName, String addressLine)` + `companyName()`/`addressLine()`. `companyName` is required (throws `ValidationException` if null/blank via `io.genfin.api.validation.Validate.notBlank`); `addressLine` is optional (null becomes `""`).
- Consumed by: Task 4 (`BrandProfile`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class BrandIdentityTest {

  @Test
  void exposesGivenValues() {
    BrandIdentity identity = BrandIdentity.of("Acme Inc.", "123 Main St");
    assertThat(identity.companyName()).isEqualTo("Acme Inc.");
    assertThat(identity.addressLine()).isEqualTo("123 Main St");
  }

  @Test
  void nullAddressLineBecomesEmptyString() {
    BrandIdentity identity = BrandIdentity.of("Acme Inc.", null);
    assertThat(identity.addressLine()).isEmpty();
  }

  @Test
  void blankCompanyNameThrowsValidationException() {
    assertThatThrownBy(() -> BrandIdentity.of(" ", "addr")).isInstanceOf(ValidationException.class);
  }

  @Test
  void nullCompanyNameThrowsValidationException() {
    assertThatThrownBy(() -> BrandIdentity.of(null, "addr")).isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandIdentityTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

First check `io.genfin.api.validation.Validate`'s exact method signature (used by `StandardDocumentType` in this same module) before writing this — use `Validate.notBlank(companyName, "companyName must not be blank")` or whatever the actual signature is.

```java
package io.genfin.document.api.brand;

import io.genfin.api.validation.Validate;

public final class BrandIdentity {

  private final String companyName;
  private final String addressLine;

  private BrandIdentity(String companyName, String addressLine) {
    Validate.notBlank(companyName, "companyName must not be blank");
    this.companyName = companyName;
    this.addressLine = addressLine == null ? "" : addressLine;
  }

  public static BrandIdentity of(String companyName, String addressLine) {
    return new BrandIdentity(companyName, addressLine);
  }

  public String companyName() {
    return companyName;
  }

  public String addressLine() {
    return addressLine;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandIdentityTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandIdentity.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandIdentityTest.java
git commit -m "feat(fin-document): add BrandIdentity"
```

---

### Task 3: `BrandLogo` + `BrandAssets`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandLogo.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandAssets.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandLogoTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandAssetsTest.java`

**Interfaces:**
- Produces: `BrandLogo.of(byte[] imageBytes, String mimeType)` + `imageBytes(): byte[]` (defensively copied on construction and return) + `mimeType(): String`. `BrandAssets.of(BrandLogo logo)` + `logo(): Optional<BrandLogo>`, `BrandAssets.empty()` (no logo).
- Consumed by: Task 4 (`BrandProfile`).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrandLogoTest {

  @Test
  void exposesGivenBytesAndMimeType() {
    byte[] source = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
    BrandLogo logo = BrandLogo.of(source, "image/png");

    assertThat(logo.imageBytes()).isEqualTo(source);
    assertThat(logo.mimeType()).isEqualTo("image/png");
  }

  @Test
  void contentIsDefensivelyCopiedOnConstructionAndReturn() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    BrandLogo logo = BrandLogo.of(source, "image/png");

    source[0] = (byte) 0xFF;
    assertThat(logo.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));

    byte[] returned = logo.imageBytes();
    returned[0] = (byte) 0xFF;
    assertThat(logo.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
  }
}
```

```java
package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrandAssetsTest {

  @Test
  void emptyHasNoLogo() {
    assertThat(BrandAssets.empty().logo()).isEmpty();
  }

  @Test
  void ofExposesGivenLogo() {
    BrandLogo logo = BrandLogo.of("x".getBytes(StandardCharsets.UTF_8), "image/png");
    assertThat(BrandAssets.of(logo).logo()).contains(logo);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandLogoTest" --tests "io.genfin.document.api.brand.BrandAssetsTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.brand;

import java.util.Arrays;

public final class BrandLogo {

  private final byte[] imageBytes;
  private final String mimeType;

  private BrandLogo(byte[] imageBytes, String mimeType) {
    this.imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
    this.mimeType = mimeType;
  }

  public static BrandLogo of(byte[] imageBytes, String mimeType) {
    return new BrandLogo(imageBytes, mimeType);
  }

  public byte[] imageBytes() {
    return Arrays.copyOf(imageBytes, imageBytes.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
```

```java
package io.genfin.document.api.brand;

import java.util.Optional;

public final class BrandAssets {

  private static final BrandAssets EMPTY = new BrandAssets(null);

  private final BrandLogo logo;

  private BrandAssets(BrandLogo logo) {
    this.logo = logo;
  }

  public static BrandAssets empty() {
    return EMPTY;
  }

  public static BrandAssets of(BrandLogo logo) {
    return new BrandAssets(logo);
  }

  public Optional<BrandLogo> logo() {
    return Optional.ofNullable(logo);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandLogoTest" --tests "io.genfin.document.api.brand.BrandAssetsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandLogo.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandAssets.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandLogoTest.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandAssetsTest.java
git commit -m "feat(fin-document): add BrandLogo (with defensive byte copy) and BrandAssets"
```

---

### Task 4: `BrandProfile` (with builder)

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandProfile.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandProfileTest.java`

**Interfaces:**
- Consumes: `BrandId` (Stage 1 identity), `BrandIdentity`/`BrandAssets`/`BrandContactInformation`/`BrandRegistrationInformation`/`BrandHeader`/`BrandFooter` (Tasks 1-3).
- Produces: `BrandProfile.builder(BrandId id, BrandIdentity identity): Builder` with chainable `.assets(BrandAssets)`, `.contact(BrandContactInformation)`, `.registration(BrandRegistrationInformation)`, `.header(BrandHeader)`, `.footer(BrandFooter)`, `.build(): BrandProfile`. `BrandProfile.id()`/`identity()`/`assets()`/`contact()`/`registration()`/`header()`/`footer()`. Unset optional fields default to empty-valued instances.
- Consumed by: Task 5 (registry), Task 6 (resolver factory).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.BrandId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrandProfileTest {

  @Test
  void buildsWithOnlyRequiredFieldsAndDefaultsTheRest() {
    BrandProfile profile =
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme Inc.", "123 Main St")).build();

    assertThat(profile.id()).isEqualTo(BrandId.of("brand-1"));
    assertThat(profile.identity().companyName()).isEqualTo("Acme Inc.");
    assertThat(profile.assets().logo()).isEmpty();
    assertThat(profile.contact().phone()).isEmpty();
    assertThat(profile.registration().taxRegistrationNumber()).isEmpty();
    assertThat(profile.header().text()).isEmpty();
    assertThat(profile.footer().text()).isEmpty();
  }

  @Test
  void buildsWithAllFieldsSet() {
    BrandLogo logo = BrandLogo.of("x".getBytes(StandardCharsets.UTF_8), "image/png");
    BrandProfile profile =
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme Inc.", "123 Main St"))
            .assets(BrandAssets.of(logo))
            .contact(BrandContactInformation.of("+1-555-0100", "hi@acme.test", "acme.test"))
            .registration(BrandRegistrationInformation.of("TAX-1", "BIZ-1"))
            .header(BrandHeader.of("Trusted since 1990"))
            .footer(BrandFooter.of("All rights reserved"))
            .build();

    assertThat(profile.assets().logo()).contains(logo);
    assertThat(profile.contact().phone()).isEqualTo("+1-555-0100");
    assertThat(profile.registration().taxRegistrationNumber()).isEqualTo("TAX-1");
    assertThat(profile.header().text()).isEqualTo("Trusted since 1990");
    assertThat(profile.footer().text()).isEqualTo("All rights reserved");
  }

  @Test
  void nullIdThrowsValidationException() {
    assertThatThrownBy(() -> BrandProfile.builder(null, BrandIdentity.of("Acme", "addr")).build())
        .isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandProfileTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.brand;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.BrandId;

public final class BrandProfile {

  private final BrandId id;
  private final BrandIdentity identity;
  private final BrandAssets assets;
  private final BrandContactInformation contact;
  private final BrandRegistrationInformation registration;
  private final BrandHeader header;
  private final BrandFooter footer;

  private BrandProfile(Builder builder) {
    Validate.notNull(builder.id, "id must not be null");
    Validate.notNull(builder.identity, "identity must not be null");
    this.id = builder.id;
    this.identity = builder.identity;
    this.assets = builder.assets;
    this.contact = builder.contact;
    this.registration = builder.registration;
    this.header = builder.header;
    this.footer = builder.footer;
  }

  public static Builder builder(BrandId id, BrandIdentity identity) {
    return new Builder(id, identity);
  }

  public BrandId id() {
    return id;
  }

  public BrandIdentity identity() {
    return identity;
  }

  public BrandAssets assets() {
    return assets;
  }

  public BrandContactInformation contact() {
    return contact;
  }

  public BrandRegistrationInformation registration() {
    return registration;
  }

  public BrandHeader header() {
    return header;
  }

  public BrandFooter footer() {
    return footer;
  }

  public static final class Builder {

    private final BrandId id;
    private final BrandIdentity identity;
    private BrandAssets assets = BrandAssets.empty();
    private BrandContactInformation contact = BrandContactInformation.of("", "", "");
    private BrandRegistrationInformation registration = BrandRegistrationInformation.of("", "");
    private BrandHeader header = BrandHeader.of("");
    private BrandFooter footer = BrandFooter.of("");

    private Builder(BrandId id, BrandIdentity identity) {
      this.id = id;
      this.identity = identity;
    }

    public Builder assets(BrandAssets assets) {
      this.assets = assets;
      return this;
    }

    public Builder contact(BrandContactInformation contact) {
      this.contact = contact;
      return this;
    }

    public Builder registration(BrandRegistrationInformation registration) {
      this.registration = registration;
      return this;
    }

    public Builder header(BrandHeader header) {
      this.header = header;
      return this;
    }

    public Builder footer(BrandFooter footer) {
      this.footer = footer;
      return this;
    }

    public BrandProfile build() {
      return new BrandProfile(this);
    }
  }
}
```

Check `io.genfin.api.validation.Validate`'s exact available methods before use (it should have both `notBlank` and `notNull` — if `notNull` doesn't exist, use whatever the module's established null-check convention is, e.g. `java.util.Objects.requireNonNull`, and note the gap rather than inventing a method that doesn't exist).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.brand.BrandProfileTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/brand/BrandProfile.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/brand/BrandProfileTest.java
git commit -m "feat(fin-document): add BrandProfile aggregate with builder"
```

---

### Task 5: `BrandNotFoundException` + `BrandResolver` port + `DefaultBrandRegistry`/`DefaultBrandResolver`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/exception/BrandNotFoundException.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/BrandResolver.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/brand/DefaultBrandRegistry.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/brand/DefaultBrandResolver.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/brand/DefaultBrandResolverTest.java`

**Interfaces:**
- Consumes: `BrandProfile` (Task 4), `BrandId` (Stage 1 identity).
- Produces: `BrandResolver.resolve(BrandId): BrandProfile`. `DefaultBrandRegistry.register(BrandProfile)` (throws `IllegalStateException` on duplicate `BrandId`, mirroring `DefaultRendererRegistry`'s precedent), `get(BrandId): BrandProfile` (throws `BrandNotFoundException` on miss). `DefaultBrandResolver(DefaultBrandRegistry)` implements `BrandResolver`.
- Consumed by: Task 6 (`BrandResolvers` factory).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.brand.BrandIdentity;
import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.exception.BrandNotFoundException;
import io.genfin.document.api.identity.BrandId;
import org.junit.jupiter.api.Test;

class DefaultBrandResolverTest {

  @Test
  void resolvesRegisteredBrand() {
    DefaultBrandRegistry registry = new DefaultBrandRegistry();
    BrandProfile profile = BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme", "addr")).build();
    registry.register(profile);
    DefaultBrandResolver resolver = new DefaultBrandResolver(registry);

    assertThat(resolver.resolve(BrandId.of("brand-1"))).isSameAs(profile);
  }

  @Test
  void throwsForUnregisteredBrand() {
    DefaultBrandResolver resolver = new DefaultBrandResolver(new DefaultBrandRegistry());

    assertThatThrownBy(() -> resolver.resolve(BrandId.of("missing")))
        .isInstanceOf(BrandNotFoundException.class);
  }

  @Test
  void rejectsDuplicateRegistration() {
    DefaultBrandRegistry registry = new DefaultBrandRegistry();
    registry.register(BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme", "addr")).build());

    assertThatThrownBy(
            () ->
                registry.register(
                    BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Other", "addr")).build()))
        .isInstanceOf(IllegalStateException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.brand.DefaultBrandResolverTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.exception;

import io.genfin.document.api.identity.BrandId;

public final class BrandNotFoundException extends RuntimeException {

  public BrandNotFoundException(BrandId brandId) {
    super("No brand registered for id: " + brandId);
  }
}
```

```java
package io.genfin.document.port;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.identity.BrandId;

public interface BrandResolver {
  BrandProfile resolve(BrandId id);
}
```

```java
package io.genfin.document.internal.brand;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.exception.BrandNotFoundException;
import io.genfin.document.api.identity.BrandId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultBrandRegistry {

  private final Map<BrandId, BrandProfile> profiles = new ConcurrentHashMap<>();

  public void register(BrandProfile profile) {
    BrandProfile existing = profiles.putIfAbsent(profile.id(), profile);
    if (existing != null) {
      throw new IllegalStateException("Brand already registered for id: " + profile.id());
    }
  }

  public BrandProfile get(BrandId id) {
    BrandProfile profile = profiles.get(id);
    if (profile == null) {
      throw new BrandNotFoundException(id);
    }
    return profile;
  }
}
```

```java
package io.genfin.document.internal.brand;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.identity.BrandId;
import io.genfin.document.port.BrandResolver;

public final class DefaultBrandResolver implements BrandResolver {

  private final DefaultBrandRegistry registry;

  public DefaultBrandResolver(DefaultBrandRegistry registry) {
    this.registry = registry;
  }

  @Override
  public BrandProfile resolve(BrandId id) {
    return registry.get(id);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.brand.DefaultBrandResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/exception/BrandNotFoundException.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/BrandResolver.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/brand/ \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/brand/DefaultBrandResolverTest.java
git commit -m "feat(fin-document): add BrandResolver port and default registry/resolver"
```

---

### Task 6: `BrandResolvers` factory (consumer-reachable entry point)

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/BrandResolvers.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/port/BrandResolversTest.java`

**Interfaces:**
- Consumes: `DefaultBrandRegistry`, `DefaultBrandResolver` (Task 5).
- Produces: `BrandResolvers.standard(): Bundle` where `Bundle` has `register(BrandProfile)` and `resolver(): BrandResolver` — mirrors `TemplateEngines.standard()`'s shape exactly (registration handle + facade in one call, zero internal types in the public signature).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.brand.BrandIdentity;
import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.identity.BrandId;
import org.junit.jupiter.api.Test;

class BrandResolversTest {

  @Test
  void registerThenResolveRoundTrips() {
    BrandResolvers.Bundle bundle = BrandResolvers.standard();
    BrandProfile profile = BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme", "addr")).build();

    bundle.register(profile);

    assertThat(bundle.resolver().resolve(BrandId.of("brand-1"))).isSameAs(profile);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.BrandResolversTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.internal.brand.DefaultBrandRegistry;
import io.genfin.document.internal.brand.DefaultBrandResolver;

public final class BrandResolvers {

  private BrandResolvers() {}

  public static Bundle standard() {
    DefaultBrandRegistry registry = new DefaultBrandRegistry();
    DefaultBrandResolver resolver = new DefaultBrandResolver(registry);
    return new Bundle() {
      @Override
      public void register(BrandProfile profile) {
        registry.register(profile);
      }

      @Override
      public BrandResolver resolver() {
        return resolver;
      }
    };
  }

  public interface Bundle {
    void register(BrandProfile profile);

    BrandResolver resolver();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.port.BrandResolversTest"`
Expected: PASS. If the architecture cycle test fails because `BrandResolvers` (in `port`) constructs `internal.brand` classes, add exactly one more entry to the existing named-factory-class exception pattern already in `DocumentArchitectureTest.java` (mirroring `TemplateEngines`/`PlaceholderResolvers`'s entries exactly) — never move `BrandResolvers` out of `port` or `DefaultBrandRegistry`/`DefaultBrandResolver` out of `internal.brand` to dodge it.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/BrandResolvers.java \
  fin-core/fin-document/src/test/java/io/genfin/document/port/BrandResolversTest.java
git commit -m "feat(fin-document): add BrandResolvers factory (consumer-reachable registration + resolution)"
```

---

### Task 7: `module-info.java` export + full module build gate

**Files:**
- Modify: `fin-core/fin-document/src/main/java/module-info.java` (add `exports io.genfin.document.api.brand;`)

**Interfaces:**
- Consumes: everything from Tasks 1-6.
- Produces: none (verification-only task).

- [ ] **Step 1: Add the export**

```java
  exports io.genfin.document.api.brand;
```

- [ ] **Step 2: Run the full module gate**

Run: `./gradlew :fin-document:check`
Expected: PASS — compile, Spotless, Checkstyle, PMD, JUnit, JaCoCo, ArchUnit (including the `DocumentArchitectureTest`'s no-internal-export check, no-instanceof/switch check, and the cycle-freedom check with whatever new narrow exception Task 6 needed).

- [ ] **Step 3: Run the full repo build**

Run: `./gradlew build`
Expected: PASS across all modules.

- [ ] **Step 4: Commit**

```bash
git add fin-core/fin-document/src/main/java/module-info.java
git commit -m "test(fin-document): export api.brand package, verify architecture constraints hold for Stage 3"
```

## Self-Review Notes

- **Spec coverage:** Group 1's full type list (BrandProfile, BrandIdentity, BrandAssets, BrandLogo, BrandHeader, BrandFooter, BrandContactInformation, BrandRegistrationInformation, BrandResolver, BrandRegistry) is covered across Tasks 1-6. `BrandRegistry` from the brief maps to `DefaultBrandRegistry` (internal) plus the consumer-facing `BrandResolvers.standard()` factory — applications register through the factory's `Bundle`, never touching the internal registry class directly, consistent with how Stage 2 exposed `TemplateEngines`/`PlaceholderResolvers`.
- **Explicitly out of scope, not built:** Themes, typography, white-label, color systems, brand variants — per Phase 12C's explicit exclusion list, to be documented in Stage 8's `DOCUMENT.md` Future Roadmap section, not built here.
- **Known risk carried forward:** Task 6 explicitly names the exact package-boundary mistake this module has made five times already, so the same mistake isn't made a sixth time.
