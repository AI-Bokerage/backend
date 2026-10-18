# fin-document Stage 2 Implementation Plan — Template Engine + Placeholder Engine

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a hand-rolled `{{placeholder}}`/`{{#if}}`/`{{#each}}`/`{{include}}`/inheritance template engine and the generic placeholder-resolution engine it depends on, plugging into `DocumentComposer` via a new `TemplateDocumentComposer` without touching any Stage 1 public signature.

**Architecture:** `api.placeholder` and `api.template` hold domain-agnostic value types (all dispatched via visitor, never `instanceof`/`switch`). `port` holds the SPI apps implement (`PlaceholderProvider`, `PlaceholderFormatter`) and the two facades apps call (`TemplateEngine`, `PlaceholderResolver`, `TemplateResolver`). `internal.template`/`internal.placeholder` hold the parser, compiler, registries, and default implementations.

**Tech Stack:** Java, JUnit 5 + AssertJ, ArchUnit (extending the existing `DocumentArchitectureTest`). Only dependency: `fin-api`. No template-engine library (Mustache/Handlebars/etc.) — the parser is hand-written.

## Global Constraints

- Module `fin-document` still depends on `fin-api` only. No CPMS/FIN-SVC/Invoice/Project/Tenant/Milestone reference anywhere in this module.
- Forbidden imports: `org.springframework..`, `javax.persistence..`, `jakarta.persistence..`, `com.fasterxml.jackson..`, `com.google.gson..`, any templating library (Mustache/Handlebars/Thymeleaf/FreeMarker/Velocity), any PDF/HTML library, any HTTP/cloud-SDK package.
- **No `instanceof` and no `switch`/`switch(` anywhere in `fin-document` source** — every new multi-variant type (`PlaceholderValue`, `TemplateFragment`) uses the same visitor-dispatch pattern as Stage 1's `DocumentElement`/`DocumentElementVisitor`.
- Package layout exactly: `io.genfin.document.api.placeholder`, `io.genfin.document.api.template`, `io.genfin.document.port`, `io.genfin.document.internal.placeholder`, `io.genfin.document.internal.template`. Nothing under `internal` is ever exported from `module-info.java`.
- Do not modify any existing Stage 1 file's public signature. `DocumentComposers` gains one new method; `NoOpDocumentComposer`, `DocumentModel`, `ComposedDocument`, `DocumentRenderer`, all 5 built-in renderers stay byte-identical.
- Typed IDs follow the `Identifier` base-class pattern already used throughout the module (`TemplateId` already exists from Stage 1 — reuse it, do not redeclare).
- SPI interfaces apps implement extend `io.genfin.api.port.spi.Extension` and live under `port`.
- Validation failures use `io.genfin.api.exception.ValidationException` (reuse `io.genfin.api.validation.Validate` where it fits, per the Stage 1 final-review lesson — don't hand-roll a check that utility already provides).
- Test stack: JUnit 5 + AssertJ. Build/verify command every task: `./gradlew :fin-document:check` (run `./gradlew :fin-document:spotlessApply` first if Spotless fails on formatting alone).

---

### Task 1: `Placeholder` + `PlaceholderValue` visitor hierarchy

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/Placeholder.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/PlaceholderValueVisitor.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/PlaceholderValue.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/ScalarPlaceholderValue.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/ListPlaceholderValue.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/MissingPlaceholderValue.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/placeholder/PlaceholderValueTest.java`

**Interfaces:**
- Produces: `Placeholder.of(String key): Placeholder` + `key(): String`. `PlaceholderValue.accept(PlaceholderValueVisitor<R>): R`. `ScalarPlaceholderValue.of(String value)` + `value(): String`. `ListPlaceholderValue.of(List<PlaceholderContext> items)` + `items(): List<PlaceholderContext>` (forward-reference to `PlaceholderContext`, added in Task 3 — this task may declare the field/method against the not-yet-existing type only if Task 3 lands before compilation; **implement Task 1 and Task 3 together in this task** if the ordering is inconvenient, or stub `ListPlaceholderValue` to hold `List<PlaceholderContext>` and let the module fail to compile only within this one task's scope — resolve by implementing Tasks 1 and 3 as a single combined change; see note in Task 3). `MissingPlaceholderValue.instance(): MissingPlaceholderValue` (singleton).
- Consumed by: Task 4 (fragment rendering), Task 3 (`PlaceholderContext`/`PlaceholderResolver`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceholderValueTest {

  private static final class RecordingVisitor implements PlaceholderValueVisitor<String> {
    @Override
    public String visitScalar(ScalarPlaceholderValue value) {
      return "scalar:" + value.value();
    }

    @Override
    public String visitList(ListPlaceholderValue value) {
      return "list:" + value.items().size();
    }

    @Override
    public String visitMissing(MissingPlaceholderValue value) {
      return "missing";
    }
  }

  @Test
  void placeholderWrapsDottedKey() {
    assertThat(Placeholder.of("invoice.number").key()).isEqualTo("invoice.number");
  }

  @Test
  void scalarDispatchesToVisitScalar() {
    PlaceholderValue value = ScalarPlaceholderValue.of("500.00");
    assertThat(value.accept(new RecordingVisitor())).isEqualTo("scalar:500.00");
  }

  @Test
  void listDispatchesToVisitList() {
    PlaceholderValue value = ListPlaceholderValue.of(List.of());
    assertThat(value.accept(new RecordingVisitor())).isEqualTo("list:0");
  }

  @Test
  void missingDispatchesToVisitMissingAndIsSingleton() {
    PlaceholderValue value = MissingPlaceholderValue.instance();
    assertThat(value.accept(new RecordingVisitor())).isEqualTo("missing");
    assertThat(MissingPlaceholderValue.instance()).isSameAs(value);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.placeholder.PlaceholderValueTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.placeholder;

import io.genfin.api.exception.ValidationException;

public final class Placeholder {

  private final String key;

  private Placeholder(String key) {
    if (key == null || key.isBlank()) {
      throw new ValidationException("Placeholder key must not be blank");
    }
    this.key = key;
  }

  public static Placeholder of(String key) {
    return new Placeholder(key);
  }

  public String key() {
    return key;
  }
}
```

```java
package io.genfin.document.api.placeholder;

public interface PlaceholderValueVisitor<R> {
  R visitScalar(ScalarPlaceholderValue value);
  R visitList(ListPlaceholderValue value);
  R visitMissing(MissingPlaceholderValue value);
}
```

```java
package io.genfin.document.api.placeholder;

public interface PlaceholderValue {
  <R> R accept(PlaceholderValueVisitor<R> visitor);
}
```

```java
package io.genfin.document.api.placeholder;

public final class ScalarPlaceholderValue implements PlaceholderValue {

  private final String value;

  private ScalarPlaceholderValue(String value) {
    this.value = value == null ? "" : value;
  }

  public static ScalarPlaceholderValue of(String value) {
    return new ScalarPlaceholderValue(value);
  }

  public String value() {
    return value;
  }

  @Override
  public <R> R accept(PlaceholderValueVisitor<R> visitor) {
    return visitor.visitScalar(this);
  }
}
```

```java
package io.genfin.document.api.placeholder;

import java.util.List;

public final class ListPlaceholderValue implements PlaceholderValue {

  private final List<PlaceholderContext> items;

  private ListPlaceholderValue(List<PlaceholderContext> items) {
    this.items = List.copyOf(items);
  }

  public static ListPlaceholderValue of(List<PlaceholderContext> items) {
    return new ListPlaceholderValue(items);
  }

  public List<PlaceholderContext> items() {
    return items;
  }

  @Override
  public <R> R accept(PlaceholderValueVisitor<R> visitor) {
    return visitor.visitList(this);
  }
}
```

```java
package io.genfin.document.api.placeholder;

public final class MissingPlaceholderValue implements PlaceholderValue {

  private static final MissingPlaceholderValue INSTANCE = new MissingPlaceholderValue();

  private MissingPlaceholderValue() {}

  public static MissingPlaceholderValue instance() {
    return INSTANCE;
  }

  @Override
  public <R> R accept(PlaceholderValueVisitor<R> visitor) {
    return visitor.visitMissing(this);
  }
}
```

**Note on the forward reference:** `ListPlaceholderValue` references `PlaceholderContext`, which does not exist until Task 3. Implement Task 1's `ListPlaceholderValue` and Task 3's `PlaceholderContext` together as part of this task (create the file `PlaceholderContext.java` now with just enough shape — a class with a private constructor and a package-private or minimal public surface — so the module compiles; Task 3 will flesh out `PlaceholderContext`'s real behavior). Alternatively, implement Tasks 1 and 3 as one combined task if that's cleaner — the important thing is the module compiles at the end of whichever task(s) you're committing.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.placeholder.PlaceholderValueTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/placeholder/PlaceholderValueTest.java
git commit -m "feat(fin-document): add Placeholder and PlaceholderValue visitor hierarchy"
```

---

### Task 2: `PlaceholderProvider` SPI + `PlaceholderRegistry`/`PlaceholderResolver`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/PlaceholderProvider.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/PlaceholderResolver.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/placeholder/DefaultPlaceholderRegistry.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/placeholder/DefaultPlaceholderResolver.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/placeholder/DefaultPlaceholderResolverTest.java`

**Interfaces:**
- Consumes: `PlaceholderValue` (Task 1).
- Produces: `PlaceholderProvider.supports(String key): boolean` + `resolve(String key): PlaceholderValue`. `PlaceholderResolver.resolve(String key): PlaceholderValue`. `DefaultPlaceholderRegistry.register(PlaceholderProvider)`, `DefaultPlaceholderResolver(DefaultPlaceholderRegistry)` implements `PlaceholderResolver`.
- Consumed by: Task 3 (`PlaceholderContext` wraps a `PlaceholderResolver`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.placeholder.MissingPlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderValue;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.port.PlaceholderProvider;
import org.junit.jupiter.api.Test;

class DefaultPlaceholderResolverTest {

  private static final class StubProvider implements PlaceholderProvider {
    private final String key;
    private final String value;

    StubProvider(String key, String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public boolean supports(String candidateKey) {
      return key.equals(candidateKey);
    }

    @Override
    public PlaceholderValue resolve(String candidateKey) {
      return ScalarPlaceholderValue.of(value);
    }
  }

  @Test
  void resolvesThroughFirstSupportingProvider() {
    DefaultPlaceholderRegistry registry = new DefaultPlaceholderRegistry();
    registry.register(new StubProvider("company.name", "Acme"));
    DefaultPlaceholderResolver resolver = new DefaultPlaceholderResolver(registry);

    PlaceholderValue value = resolver.resolve("company.name");

    assertThat(value.accept(
            new io.genfin.document.api.placeholder.PlaceholderValueVisitor<String>() {
              @Override
              public String visitScalar(ScalarPlaceholderValue v) {
                return v.value();
              }

              @Override
              public String visitList(io.genfin.document.api.placeholder.ListPlaceholderValue v) {
                return "list";
              }

              @Override
              public String visitMissing(MissingPlaceholderValue v) {
                return "missing";
              }
            }))
        .isEqualTo("Acme");
  }

  @Test
  void returnsMissingWhenNoProviderSupportsKey() {
    DefaultPlaceholderResolver resolver =
        new DefaultPlaceholderResolver(new DefaultPlaceholderRegistry());

    assertThat(resolver.resolve("unknown.key")).isInstanceOf(MissingPlaceholderValue.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.placeholder.DefaultPlaceholderResolverTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.placeholder.PlaceholderValue;

public interface PlaceholderProvider extends Extension {
  boolean supports(String key);

  PlaceholderValue resolve(String key);
}
```

```java
package io.genfin.document.port;

import io.genfin.document.api.placeholder.PlaceholderValue;

public interface PlaceholderResolver {
  PlaceholderValue resolve(String key);
}
```

```java
package io.genfin.document.internal.placeholder;

import io.genfin.document.port.PlaceholderProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultPlaceholderRegistry {

  private final List<PlaceholderProvider> providers = new CopyOnWriteArrayList<>();

  public void register(PlaceholderProvider provider) {
    providers.add(provider);
  }

  public List<PlaceholderProvider> providers() {
    return List.copyOf(providers);
  }
}
```

```java
package io.genfin.document.internal.placeholder;

import io.genfin.document.api.placeholder.MissingPlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderValue;
import io.genfin.document.port.PlaceholderProvider;
import io.genfin.document.port.PlaceholderResolver;

public final class DefaultPlaceholderResolver implements PlaceholderResolver {

  private final DefaultPlaceholderRegistry registry;

  public DefaultPlaceholderResolver(DefaultPlaceholderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public PlaceholderValue resolve(String key) {
    for (PlaceholderProvider provider : registry.providers()) {
      if (provider.supports(key)) {
        return provider.resolve(key);
      }
    }
    return MissingPlaceholderValue.instance();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.placeholder.DefaultPlaceholderResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/PlaceholderProvider.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/PlaceholderResolver.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/placeholder/ \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/placeholder/DefaultPlaceholderResolverTest.java
git commit -m "feat(fin-document): add PlaceholderProvider SPI and default resolver/registry"
```

---

### Task 3: `PlaceholderFormatter` SPI + `PlaceholderContext`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/PlaceholderFormatter.java`
- Modify (flesh out from Task 1's stub, or create if Task 1 deferred it here): `fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/PlaceholderContext.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/placeholder/PlaceholderContextTest.java`

**Interfaces:**
- Consumes: `PlaceholderValue`/`ScalarPlaceholderValue`/`MissingPlaceholderValue` (Task 1), `PlaceholderResolver`, `PlaceholderFormatter` (this task + Task 2).
- Produces: `PlaceholderFormatter.format(String rawValue): String`. `PlaceholderContext.of(PlaceholderResolver resolver): PlaceholderContext`, `withLocal(String name, PlaceholderValue value): PlaceholderContext`, `withFormatter(String key, PlaceholderFormatter formatter): PlaceholderContext`, `resolveValue(String key): PlaceholderValue` (local override checked first, else delegates to wrapped resolver), `resolveDisplay(String key): String` (resolves then applies a registered formatter for that key if any, else raw scalar value or empty string for missing/list).
- Consumed by: Task 8 (`DefaultTemplateEngine` renders against a `PlaceholderContext`), Task 4 (`TemplateContext` wraps or produces one).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.port.PlaceholderResolver;
import org.junit.jupiter.api.Test;

class PlaceholderContextTest {

  @Test
  void resolvesThroughWrappedResolverWhenNoLocalOverride() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("Acme");
    PlaceholderContext context = PlaceholderContext.of(resolver);

    assertThat(context.resolveDisplay("company.name")).isEqualTo("Acme");
  }

  @Test
  void localOverrideShadowsWrappedResolver() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("Acme");
    PlaceholderContext context =
        PlaceholderContext.of(resolver).withLocal("company.name", ScalarPlaceholderValue.of("Override"));

    assertThat(context.resolveDisplay("company.name")).isEqualTo("Override");
  }

  @Test
  void missingValueDisplaysAsEmptyString() {
    PlaceholderResolver resolver = key -> MissingPlaceholderValue.instance();
    PlaceholderContext context = PlaceholderContext.of(resolver);

    assertThat(context.resolveDisplay("unknown")).isEmpty();
  }

  @Test
  void registeredFormatterAppliesToDisplay() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("1000");
    PlaceholderContext context =
        PlaceholderContext.of(resolver).withFormatter("total", raw -> "$" + raw);

    assertThat(context.resolveDisplay("total")).isEqualTo("$1000");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.placeholder.PlaceholderContextTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;

public interface PlaceholderFormatter extends Extension {
  String format(String rawValue);
}
```

```java
package io.genfin.document.api.placeholder;

import io.genfin.document.port.PlaceholderFormatter;
import io.genfin.document.port.PlaceholderResolver;
import java.util.Map;

public final class PlaceholderContext {

  private final PlaceholderResolver resolver;
  private final Map<String, PlaceholderValue> locals;
  private final Map<String, PlaceholderFormatter> formatters;

  private PlaceholderContext(
      PlaceholderResolver resolver,
      Map<String, PlaceholderValue> locals,
      Map<String, PlaceholderFormatter> formatters) {
    this.resolver = resolver;
    this.locals = Map.copyOf(locals);
    this.formatters = Map.copyOf(formatters);
  }

  public static PlaceholderContext of(PlaceholderResolver resolver) {
    return new PlaceholderContext(resolver, Map.of(), Map.of());
  }

  public PlaceholderContext withLocal(String name, PlaceholderValue value) {
    Map<String, PlaceholderValue> merged = new java.util.HashMap<>(locals);
    merged.put(name, value);
    return new PlaceholderContext(resolver, merged, formatters);
  }

  public PlaceholderContext withFormatter(String key, PlaceholderFormatter formatter) {
    Map<String, PlaceholderFormatter> merged = new java.util.HashMap<>(formatters);
    merged.put(key, formatter);
    return new PlaceholderContext(resolver, locals, merged);
  }

  public PlaceholderValue resolveValue(String key) {
    PlaceholderValue local = locals.get(key);
    return local != null ? local : resolver.resolve(key);
  }

  public String resolveDisplay(String key) {
    PlaceholderValue value = resolveValue(key);
    String raw =
        value.accept(
            new PlaceholderValueVisitor<String>() {
              @Override
              public String visitScalar(ScalarPlaceholderValue v) {
                return v.value();
              }

              @Override
              public String visitList(ListPlaceholderValue v) {
                return "";
              }

              @Override
              public String visitMissing(MissingPlaceholderValue v) {
                return "";
              }
            });
    PlaceholderFormatter formatter = formatters.get(key);
    return formatter != null ? formatter.format(raw) : raw;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.placeholder.PlaceholderContextTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/PlaceholderFormatter.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/placeholder/PlaceholderContext.java \
  fin-core/fin-document/src/test/java/io/genfin/document/api/placeholder/PlaceholderContextTest.java
git commit -m "feat(fin-document): add PlaceholderFormatter SPI and PlaceholderContext"
```

---

### Task 4: `TemplateFragment` visitor hierarchy + `TemplateSection` + `DocumentTemplate` + `TemplateVariables` + `TemplateContext`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/TemplateFragmentVisitor.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/TemplateFragment.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/LiteralFragment.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/PlaceholderFragment.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/IfFragment.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/EachFragment.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/IncludeFragment.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/TemplateSection.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/DocumentTemplate.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/TemplateVariables.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/TemplateContext.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/template/TemplateFragmentTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/template/DocumentTemplateTest.java`

**Interfaces:**
- Consumes: `Placeholder` (Task 1), `TemplateId` (Stage 1 identity), `PlaceholderResolver`/`PlaceholderValue` (Tasks 1-2).
- Produces: `TemplateFragment.accept(TemplateFragmentVisitor<R>): R`; `LiteralFragment.of(String text)` + `text()`; `PlaceholderFragment.of(Placeholder placeholder)` + `placeholder()`; `IfFragment.of(Placeholder condition, List<TemplateFragment> body)` + `condition()`/`body()`; `EachFragment.of(Placeholder collection, List<TemplateFragment> body)` + `collection()`/`body()`; `IncludeFragment.of(TemplateId includedTemplateId)` + `includedTemplateId()`; `TemplateSection.of(String name, List<TemplateFragment> body)` + `name()`/`body()`; `DocumentTemplate.of(TemplateId id, String rawSource)` and `DocumentTemplate.withParent(TemplateId id, String rawSource, TemplateId parentId)` + `id()`/`rawSource()`/`parentId(): Optional<TemplateId>`; `TemplateVariables.empty()`/`of(Map<String,PlaceholderValue>)` + `get(String): Optional<PlaceholderValue>`; `TemplateContext.of(PlaceholderResolver resolver)`/`of(PlaceholderResolver resolver, TemplateVariables initial)` + `toPlaceholderContext(): PlaceholderContext` (seeds a `PlaceholderContext` with `initial`'s bindings as locals).
- Consumed by: Task 5 (parser produces these), Task 6 (compiler assembles/merges these), Task 8 (`DefaultTemplateEngine` walks these).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.placeholder.Placeholder;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateFragmentTest {

  private static final class RecordingVisitor implements TemplateFragmentVisitor<String> {
    @Override
    public String visitLiteral(LiteralFragment fragment) {
      return "literal:" + fragment.text();
    }

    @Override
    public String visitPlaceholder(PlaceholderFragment fragment) {
      return "placeholder:" + fragment.placeholder().key();
    }

    @Override
    public String visitIf(IfFragment fragment) {
      return "if:" + fragment.condition().key() + ":" + fragment.body().size();
    }

    @Override
    public String visitEach(EachFragment fragment) {
      return "each:" + fragment.collection().key() + ":" + fragment.body().size();
    }

    @Override
    public String visitInclude(IncludeFragment fragment) {
      return "include:" + fragment.includedTemplateId().value();
    }
  }

  @Test
  void literalDispatches() {
    assertThat(LiteralFragment.of("hello").accept(new RecordingVisitor())).isEqualTo("literal:hello");
  }

  @Test
  void placeholderDispatches() {
    TemplateFragment fragment = PlaceholderFragment.of(Placeholder.of("invoice.number"));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("placeholder:invoice.number");
  }

  @Test
  void ifDispatches() {
    TemplateFragment fragment =
        IfFragment.of(Placeholder.of("hasNotes"), List.of(LiteralFragment.of("x")));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("if:hasNotes:1");
  }

  @Test
  void eachDispatches() {
    TemplateFragment fragment =
        EachFragment.of(Placeholder.of("items"), List.of(LiteralFragment.of("x")));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("each:items:1");
  }

  @Test
  void includeDispatches() {
    TemplateFragment fragment = IncludeFragment.of(TemplateId.of("header"));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("include:header");
  }

  @Test
  void sectionHoldsNameAndBody() {
    TemplateSection section = TemplateSection.of("body", List.of(LiteralFragment.of("x")));
    assertThat(section.name()).isEqualTo("body");
    assertThat(section.body()).hasSize(1);
  }
}
```

```java
package io.genfin.document.api.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.placeholder.PlaceholderContext;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.port.PlaceholderResolver;
import org.junit.jupiter.api.Test;

class DocumentTemplateTest {

  @Test
  void templateWithoutParentHasEmptyParentId() {
    DocumentTemplate template = DocumentTemplate.of(TemplateId.of("invoice"), "hello {{name}}");
    assertThat(template.parentId()).isEmpty();
  }

  @Test
  void templateWithParentExposesIt() {
    DocumentTemplate template =
        DocumentTemplate.withParent(TemplateId.of("child"), "body", TemplateId.of("parent"));
    assertThat(template.parentId()).contains(TemplateId.of("parent"));
  }

  @Test
  void templateVariablesLookup() {
    TemplateVariables variables =
        TemplateVariables.of(java.util.Map.of("greeting", ScalarPlaceholderValue.of("hi")));
    assertThat(variables.get("greeting")).isPresent();
    assertThat(variables.get("missing")).isEmpty();
  }

  @Test
  void templateContextSeedsPlaceholderContextWithInitialVariables() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("fallback");
    TemplateVariables initial =
        TemplateVariables.of(java.util.Map.of("greeting", ScalarPlaceholderValue.of("hi")));
    TemplateContext context = TemplateContext.of(resolver, initial);

    PlaceholderContext placeholderContext = context.toPlaceholderContext();

    assertThat(placeholderContext.resolveDisplay("greeting")).isEqualTo("hi");
    assertThat(placeholderContext.resolveDisplay("other")).isEqualTo("fallback");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.template.TemplateFragmentTest" --tests "io.genfin.document.api.template.DocumentTemplateTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.template;

public interface TemplateFragmentVisitor<R> {
  R visitLiteral(LiteralFragment fragment);
  R visitPlaceholder(PlaceholderFragment fragment);
  R visitIf(IfFragment fragment);
  R visitEach(EachFragment fragment);
  R visitInclude(IncludeFragment fragment);
}
```

```java
package io.genfin.document.api.template;

public interface TemplateFragment {
  <R> R accept(TemplateFragmentVisitor<R> visitor);
}
```

```java
package io.genfin.document.api.template;

public final class LiteralFragment implements TemplateFragment {

  private final String text;

  private LiteralFragment(String text) {
    this.text = text == null ? "" : text;
  }

  public static LiteralFragment of(String text) {
    return new LiteralFragment(text);
  }

  public String text() {
    return text;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitLiteral(this);
  }
}
```

```java
package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.Placeholder;

public final class PlaceholderFragment implements TemplateFragment {

  private final Placeholder placeholder;

  private PlaceholderFragment(Placeholder placeholder) {
    this.placeholder = placeholder;
  }

  public static PlaceholderFragment of(Placeholder placeholder) {
    return new PlaceholderFragment(placeholder);
  }

  public Placeholder placeholder() {
    return placeholder;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitPlaceholder(this);
  }
}
```

```java
package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.Placeholder;
import java.util.List;

public final class IfFragment implements TemplateFragment {

  private final Placeholder condition;
  private final List<TemplateFragment> body;

  private IfFragment(Placeholder condition, List<TemplateFragment> body) {
    this.condition = condition;
    this.body = List.copyOf(body);
  }

  public static IfFragment of(Placeholder condition, List<TemplateFragment> body) {
    return new IfFragment(condition, body);
  }

  public Placeholder condition() {
    return condition;
  }

  public List<TemplateFragment> body() {
    return body;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitIf(this);
  }
}
```

```java
package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.Placeholder;
import java.util.List;

public final class EachFragment implements TemplateFragment {

  private final Placeholder collection;
  private final List<TemplateFragment> body;

  private EachFragment(Placeholder collection, List<TemplateFragment> body) {
    this.collection = collection;
    this.body = List.copyOf(body);
  }

  public static EachFragment of(Placeholder collection, List<TemplateFragment> body) {
    return new EachFragment(collection, body);
  }

  public Placeholder collection() {
    return collection;
  }

  public List<TemplateFragment> body() {
    return body;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitEach(this);
  }
}
```

```java
package io.genfin.document.api.template;

import io.genfin.document.api.identity.TemplateId;

public final class IncludeFragment implements TemplateFragment {

  private final TemplateId includedTemplateId;

  private IncludeFragment(TemplateId includedTemplateId) {
    this.includedTemplateId = includedTemplateId;
  }

  public static IncludeFragment of(TemplateId includedTemplateId) {
    return new IncludeFragment(includedTemplateId);
  }

  public TemplateId includedTemplateId() {
    return includedTemplateId;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitInclude(this);
  }
}
```

```java
package io.genfin.document.api.template;

import java.util.List;

public final class TemplateSection {

  private final String name;
  private final List<TemplateFragment> body;

  private TemplateSection(String name, List<TemplateFragment> body) {
    this.name = name;
    this.body = List.copyOf(body);
  }

  public static TemplateSection of(String name, List<TemplateFragment> body) {
    return new TemplateSection(name, body);
  }

  public String name() {
    return name;
  }

  public List<TemplateFragment> body() {
    return body;
  }
}
```

```java
package io.genfin.document.api.template;

import io.genfin.document.api.identity.TemplateId;
import java.util.Optional;

public final class DocumentTemplate {

  private final TemplateId id;
  private final String rawSource;
  private final TemplateId parentId;

  private DocumentTemplate(TemplateId id, String rawSource, TemplateId parentId) {
    this.id = id;
    this.rawSource = rawSource == null ? "" : rawSource;
    this.parentId = parentId;
  }

  public static DocumentTemplate of(TemplateId id, String rawSource) {
    return new DocumentTemplate(id, rawSource, null);
  }

  public static DocumentTemplate withParent(TemplateId id, String rawSource, TemplateId parentId) {
    return new DocumentTemplate(id, rawSource, parentId);
  }

  public TemplateId id() {
    return id;
  }

  public String rawSource() {
    return rawSource;
  }

  public Optional<TemplateId> parentId() {
    return Optional.ofNullable(parentId);
  }
}
```

```java
package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.PlaceholderValue;
import java.util.Map;
import java.util.Optional;

public final class TemplateVariables {

  private static final TemplateVariables EMPTY = new TemplateVariables(Map.of());

  private final Map<String, PlaceholderValue> values;

  private TemplateVariables(Map<String, PlaceholderValue> values) {
    this.values = Map.copyOf(values);
  }

  public static TemplateVariables empty() {
    return EMPTY;
  }

  public static TemplateVariables of(Map<String, PlaceholderValue> values) {
    return new TemplateVariables(values);
  }

  public Optional<PlaceholderValue> get(String key) {
    return Optional.ofNullable(values.get(key));
  }

  Map<String, PlaceholderValue> asMap() {
    return values;
  }
}
```

```java
package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.PlaceholderContext;
import io.genfin.document.port.PlaceholderResolver;

public final class TemplateContext {

  private final PlaceholderResolver resolver;
  private final TemplateVariables initialVariables;

  private TemplateContext(PlaceholderResolver resolver, TemplateVariables initialVariables) {
    this.resolver = resolver;
    this.initialVariables = initialVariables;
  }

  public static TemplateContext of(PlaceholderResolver resolver) {
    return new TemplateContext(resolver, TemplateVariables.empty());
  }

  public static TemplateContext of(PlaceholderResolver resolver, TemplateVariables initialVariables) {
    return new TemplateContext(resolver, initialVariables);
  }

  public PlaceholderContext toPlaceholderContext() {
    PlaceholderContext context = PlaceholderContext.of(resolver);
    for (Map.Entry<String, io.genfin.document.api.placeholder.PlaceholderValue> entry :
        initialVariables.asMap().entrySet()) {
      context = context.withLocal(entry.getKey(), entry.getValue());
    }
    return context;
  }
}
```

(Add `import java.util.Map;` to `TemplateContext.java`'s import list.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.template.TemplateFragmentTest" --tests "io.genfin.document.api.template.DocumentTemplateTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/template/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/template/
git commit -m "feat(fin-document): add TemplateFragment visitor hierarchy, TemplateSection, DocumentTemplate, TemplateVariables, TemplateContext"
```

---

### Task 5: `TemplateParser` — hand-rolled `{{...}}` tokenizer/parser

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/template/TemplateParser.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/template/TemplateParserTest.java`

**Interfaces:**
- Consumes: `TemplateFragment` hierarchy, `TemplateSection`, `Placeholder`, `TemplateId` (Task 4 + Stage 1 identity).
- Produces: `TemplateParser.parse(String rawSource): ParsedTemplate` where `ParsedTemplate` is a small internal record/holder exposing `List<TemplateFragment> topLevelFragments()` and `List<TemplateSection> sections()` (a template's raw source is a flat sequence of fragments and `{{#section}}` blocks at the top level — sections are extracted separately from the fragment stream so the compiler can apply overrides). Throws `io.genfin.api.exception.ValidationException` on unmatched `#if`/`/if`, unmatched `#each`/`/each`, unmatched `#section`/`/section`, or malformed `{{`/`}}` delimiters.
- Consumed by: Task 6 (`TemplateCompiler`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.template.EachFragment;
import io.genfin.document.api.template.IfFragment;
import io.genfin.document.api.template.IncludeFragment;
import io.genfin.document.api.template.LiteralFragment;
import io.genfin.document.api.template.PlaceholderFragment;
import io.genfin.document.api.template.TemplateFragment;
import org.junit.jupiter.api.Test;

class TemplateParserTest {

  @Test
  void parsesLiteralOnly() {
    ParsedTemplate parsed = TemplateParser.parse("hello world");
    assertThat(parsed.topLevelFragments()).hasSize(1);
    assertThat(((LiteralFragment) parsed.topLevelFragments().get(0)).text()).isEqualTo("hello world");
  }

  @Test
  void parsesLiteralAndPlaceholder() {
    ParsedTemplate parsed = TemplateParser.parse("Hello {{name}}!");
    assertThat(parsed.topLevelFragments()).hasSize(3);
    TemplateFragment middle = parsed.topLevelFragments().get(1);
    assertThat(((PlaceholderFragment) middle).placeholder().key()).isEqualTo("name");
  }

  @Test
  void parsesIfBlock() {
    ParsedTemplate parsed = TemplateParser.parse("{{#if hasNotes}}Notes: {{notes}}{{/if}}");
    assertThat(parsed.topLevelFragments()).hasSize(1);
    IfFragment fragment = (IfFragment) parsed.topLevelFragments().get(0);
    assertThat(fragment.condition().key()).isEqualTo("hasNotes");
    assertThat(fragment.body()).hasSize(2);
  }

  @Test
  void parsesEachBlock() {
    ParsedTemplate parsed = TemplateParser.parse("{{#each items}}{{name}}{{/each}}");
    EachFragment fragment = (EachFragment) parsed.topLevelFragments().get(0);
    assertThat(fragment.collection().key()).isEqualTo("items");
    assertThat(fragment.body()).hasSize(1);
  }

  @Test
  void parsesIncludeDirective() {
    ParsedTemplate parsed = TemplateParser.parse("{{include \"header\"}}");
    IncludeFragment fragment = (IncludeFragment) parsed.topLevelFragments().get(0);
    assertThat(fragment.includedTemplateId().value()).isEqualTo("header");
  }

  @Test
  void parsesNamedSectionSeparatelyFromTopLevelFragments() {
    ParsedTemplate parsed = TemplateParser.parse("{{#section \"body\"}}Hello{{/section}}");
    assertThat(parsed.sections()).hasSize(1);
    assertThat(parsed.sections().get(0).name()).isEqualTo("body");
    assertThat(parsed.topLevelFragments()).isEmpty();
  }

  @Test
  void unmatchedIfThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse("{{#if x}}unterminated"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void unmatchedEachThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse("{{#each x}}unterminated"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void malformedDelimiterThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse("{{unterminated"))
        .isInstanceOf(ValidationException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.TemplateParserTest"`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Write minimal implementation**

Implement `ParsedTemplate` as a small package-private holder and `TemplateParser` as a hand-written recursive-descent-style parser. Suggested approach: first tokenize into a flat list of `{ LITERAL(text) | TAG(rawTagContent) }` tokens by scanning for `{{`/`}}` pairs (throw `ValidationException` if an opening `{{` has no matching `}}`), then walk the token list building the fragment tree recursively — a tag starting with `#if ` opens an `IfFragment` whose body consumes tokens until a `/if` tag; `#each ` similarly until `/each`; `#section "name"` until `/section`; `include "id"` is a leaf; anything else is treated as a bare placeholder key. Track section blocks separately from the returned top-level fragment list (a template is expected to be composed primarily of named `{{#section}}` blocks for inheritance, with any fragments outside all sections also being valid — both cases must work, per the test above where a section-only template yields an empty `topLevelFragments()` and the section content lives in `sections()`).

```java
package io.genfin.document.internal.template;

import io.genfin.document.api.template.TemplateFragment;
import io.genfin.document.api.template.TemplateSection;
import java.util.List;

final class ParsedTemplate {

  private final List<TemplateFragment> topLevelFragments;
  private final List<TemplateSection> sections;

  ParsedTemplate(List<TemplateFragment> topLevelFragments, List<TemplateSection> sections) {
    this.topLevelFragments = List.copyOf(topLevelFragments);
    this.sections = List.copyOf(sections);
  }

  List<TemplateFragment> topLevelFragments() {
    return topLevelFragments;
  }

  List<TemplateSection> sections() {
    return sections;
  }
}
```

Write `TemplateParser` using whatever internal token representation you find cleanest (a private nested token type is fine) — the class must expose exactly one public method, `static ParsedTemplate parse(String rawSource)`, and must not use `instanceof`/`switch` for dispatch on tag kind (use a small internal enum-free dispatch via string prefix matching and early returns, or a `Map<String, TagHandler>`-style lookup — string `.equals`/`.startsWith` checks are fine, they are not `instanceof`/`switch`). Throw `io.genfin.api.exception.ValidationException` with a descriptive message for every malformed-input case in the test above.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.TemplateParserTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/internal/template/ \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/template/TemplateParserTest.java
git commit -m "feat(fin-document): add hand-rolled TemplateParser for {{}}/#if/#each/#section/include syntax"
```

---

### Task 6: `CompiledTemplate` + `TemplateCompiler` (parent chain + section overrides + include inlining)

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/template/CompiledTemplate.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/template/TemplateCompiler.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/template/TemplateCompilerTest.java`

**Interfaces:**
- Consumes: `DocumentTemplate`, `ParsedTemplate`/`TemplateParser` (Tasks 4-5), `TemplateId` (Stage 1 identity).
- Produces: `CompiledTemplate.of(TemplateId id, List<TemplateFragment> fragments)` + `id()`/`fragments()`. `TemplateCompiler.compile(DocumentTemplate template, java.util.function.Function<TemplateId, DocumentTemplate> templateLookup): CompiledTemplate` — `templateLookup` is how the compiler resolves a parent or an `{{include}}` target without depending on the registry type directly (kept as a plain function to avoid a circular dependency between `internal.template.TemplateCompiler` and the registry class built in Task 7 — Task 7's registry passes `this::getRegistered` or equivalent as the lookup function when it calls the compiler).
- Consumed by: Task 7 (`TemplateResolver`/registry calls the compiler and caches the result).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.api.template.DocumentTemplate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateCompilerTest {

  @Test
  void compilesFlatTemplateWithNoParent() {
    DocumentTemplate template = DocumentTemplate.of(TemplateId.of("simple"), "Hello {{name}}");
    CompiledTemplate compiled = TemplateCompiler.compile(template, id -> null);

    assertThat(compiled.id()).isEqualTo(TemplateId.of("simple"));
    assertThat(compiled.fragments()).hasSize(2);
  }

  @Test
  void childSectionOverridesMatchingParentSection() {
    DocumentTemplate parent =
        DocumentTemplate.of(
            TemplateId.of("parent"),
            "{{#section \"header\"}}ParentHeader{{/section}}{{#section \"body\"}}ParentBody{{/section}}");
    DocumentTemplate child =
        DocumentTemplate.withParent(
            TemplateId.of("child"), "{{#section \"body\"}}ChildBody{{/section}}", TemplateId.of("parent"));

    Map<TemplateId, DocumentTemplate> registry = Map.of(TemplateId.of("parent"), parent);
    CompiledTemplate compiled = TemplateCompiler.compile(child, registry::get);

    String rendered =
        compiled.fragments().stream()
            .map(f -> f.accept(new LiteralConcatenatingVisitor()))
            .reduce("", String::concat);

    assertThat(rendered).contains("ParentHeader");
    assertThat(rendered).contains("ChildBody");
    assertThat(rendered).doesNotContain("ParentBody");
  }

  @Test
  void includeIsInlinedAtCompileTime() {
    DocumentTemplate included = DocumentTemplate.of(TemplateId.of("footer"), "FooterText");
    DocumentTemplate main = DocumentTemplate.of(TemplateId.of("main"), "{{include \"footer\"}}");

    Map<TemplateId, DocumentTemplate> registry = Map.of(TemplateId.of("footer"), included);
    CompiledTemplate compiled = TemplateCompiler.compile(main, registry::get);

    String rendered =
        compiled.fragments().stream()
            .map(f -> f.accept(new LiteralConcatenatingVisitor()))
            .reduce("", String::concat);

    assertThat(rendered).contains("FooterText");
  }

  @Test
  void missingParentThrowsValidationException() {
    DocumentTemplate child =
        DocumentTemplate.withParent(TemplateId.of("child"), "body", TemplateId.of("missing-parent"));

    assertThatThrownBy(() -> TemplateCompiler.compile(child, id -> null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void cyclicIncludeThrowsValidationException() {
    DocumentTemplate a = DocumentTemplate.of(TemplateId.of("a"), "{{include \"b\"}}");
    DocumentTemplate b = DocumentTemplate.of(TemplateId.of("b"), "{{include \"a\"}}");
    Map<TemplateId, DocumentTemplate> registry = Map.of(TemplateId.of("a"), a, TemplateId.of("b"), b);

    assertThatThrownBy(() -> TemplateCompiler.compile(a, registry::get))
        .isInstanceOf(ValidationException.class);
  }
}
```

Add a small test-local helper visitor (in the same test file, package-private nested class) that concatenates `LiteralFragment` text and recurses into `IfFragment`/`EachFragment` bodies, ignoring other fragment kinds — used only to assert rendered content contains expected literal substrings without needing the full `TemplateEngine` (Task 8) to exist yet:

```java
  private static final class LiteralConcatenatingVisitor
      implements io.genfin.document.api.template.TemplateFragmentVisitor<String> {
    @Override
    public String visitLiteral(io.genfin.document.api.template.LiteralFragment fragment) {
      return fragment.text();
    }

    @Override
    public String visitPlaceholder(io.genfin.document.api.template.PlaceholderFragment fragment) {
      return "";
    }

    @Override
    public String visitIf(io.genfin.document.api.template.IfFragment fragment) {
      return fragment.body().stream().map(f -> f.accept(this)).reduce("", String::concat);
    }

    @Override
    public String visitEach(io.genfin.document.api.template.EachFragment fragment) {
      return fragment.body().stream().map(f -> f.accept(this)).reduce("", String::concat);
    }

    @Override
    public String visitInclude(io.genfin.document.api.template.IncludeFragment fragment) {
      return "";
    }
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.TemplateCompilerTest"`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.template;

import io.genfin.document.api.identity.TemplateId;
import java.util.List;

public final class CompiledTemplate {

  private final TemplateId id;
  private final List<TemplateFragment> fragments;

  private CompiledTemplate(TemplateId id, List<TemplateFragment> fragments) {
    this.id = id;
    this.fragments = List.copyOf(fragments);
  }

  public static CompiledTemplate of(TemplateId id, List<TemplateFragment> fragments) {
    return new CompiledTemplate(id, fragments);
  }

  public TemplateId id() {
    return id;
  }

  public List<TemplateFragment> fragments() {
    return fragments;
  }
}
```

`TemplateCompiler.compile(DocumentTemplate template, Function<TemplateId, DocumentTemplate> lookup)`:
1. Parse `template.rawSource()` via `TemplateParser.parse(...)` into a `ParsedTemplate`.
2. If `template.parentId()` is empty: the compiled result is the parsed template's `topLevelFragments()` with any `sections()` content appended in declaration order (a template with only sections and no parent still needs to render — treat its own sections as its content when there's no parent to merge into). If `template.parentId()` is present: look up the parent via `lookup`, throwing `ValidationException` if `lookup.apply(parentId)` returns `null`; recursively compile the parent (**note**: recursive compilation without a visited-set will loop forever on a parent-cycle — track visited `TemplateId`s through the recursion and throw `ValidationException` on revisit, exactly as you must for include-cycles below); then merge: walk the parent's compiled fragment list, and for each of the parent's *originally named* sections whose name matches one the child declared, substitute the child's section body in place of the parent's; sections the child didn't override stay as the parent produced them. (This means the compiler needs to track, alongside the parent's flat compiled fragment list, which contiguous fragment ranges came from which named section — the simplest correct approach: compile section bodies as literal fragment lists, and represent "this part of the parent's output came from section X" via a lightweight internal marker fragment kind used only inside the compiler, never exposed outside `internal.template` — or, simpler still, treat a parent's `sections()` as compiled independently and re-assembled: **compile the parent's `ParsedTemplate` sections in their original relative order, substituting per-name overrides before concatenating into the final fragment list, and append the parent's own `topLevelFragments()` — whichever ordering rule you pick, make it consistent and covered by the section-override test above**.)
3. Walk the resulting fragment list and inline every `IncludeFragment`: look up the included template via `lookup`, throwing `ValidationException` if not found; recursively compile it (tracking visited `TemplateId`s through this recursion too, throwing `ValidationException` on a revisit — this is the cycle guard the test exercises); replace the `IncludeFragment` with the included template's compiled fragment list (splice in place, preserving surrounding fragments' order). Recurse into `IfFragment`/`EachFragment` bodies to inline includes nested inside them too.
4. Return `CompiledTemplate.of(template.id(), finalFragments)`.

Implement this as a small instance-based helper (a package-private class holding the `lookup` function and a mutable visited-set field) invoked by the public static `TemplateCompiler.compile(...)` entry point, so the two independent cycle-guards (parent chain, include chain) can share one straightforward "already visiting this id" check without threading an extra parameter through every call.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.TemplateCompilerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/template/CompiledTemplate.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/template/TemplateCompiler.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/template/TemplateCompilerTest.java
git commit -m "feat(fin-document): add CompiledTemplate and TemplateCompiler with inheritance and include resolution"
```

---

### Task 7: `TemplateResolver` port + `DefaultTemplateRegistry`/`DefaultTemplateResolver` (with compiled-template caching)

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/TemplateResolver.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/template/DefaultTemplateRegistry.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/template/DefaultTemplateResolver.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/template/DefaultTemplateResolverTest.java`

**Interfaces:**
- Consumes: `DocumentTemplate`, `CompiledTemplate`, `TemplateCompiler` (Tasks 4 & 6), `TemplateId` (Stage 1 identity).
- Produces: `TemplateResolver.resolve(TemplateId): CompiledTemplate`. `DefaultTemplateRegistry.register(DocumentTemplate)`, `get(TemplateId): DocumentTemplate` (throws an unchecked "not found" exception on miss, mirroring `RendererNotFoundException`'s pattern — name it `TemplateNotFoundException`, placed in `api.exception`). `DefaultTemplateResolver(DefaultTemplateRegistry)` implements `TemplateResolver`, compiling on first resolve and caching the `CompiledTemplate` for subsequent resolves of the same `TemplateId`.
- Consumed by: Task 8 (`DefaultTemplateEngine`), Task 9 (`TemplateDocumentComposer`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.exception.TemplateNotFoundException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.api.template.DocumentTemplate;
import org.junit.jupiter.api.Test;

class DefaultTemplateResolverTest {

  @Test
  void resolvesRegisteredTemplate() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("simple"), "Hello {{name}}"));
    DefaultTemplateResolver resolver = new DefaultTemplateResolver(registry);

    CompiledTemplate compiled = resolver.resolve(TemplateId.of("simple"));

    assertThat(compiled.id()).isEqualTo(TemplateId.of("simple"));
    assertThat(compiled.fragments()).hasSize(2);
  }

  @Test
  void secondResolveReturnsCachedInstance() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("simple"), "Hello {{name}}"));
    DefaultTemplateResolver resolver = new DefaultTemplateResolver(registry);

    CompiledTemplate first = resolver.resolve(TemplateId.of("simple"));
    CompiledTemplate second = resolver.resolve(TemplateId.of("simple"));

    assertThat(first).isSameAs(second);
  }

  @Test
  void throwsForUnregisteredTemplate() {
    DefaultTemplateResolver resolver = new DefaultTemplateResolver(new DefaultTemplateRegistry());

    assertThatThrownBy(() -> resolver.resolve(TemplateId.of("missing")))
        .isInstanceOf(TemplateNotFoundException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.DefaultTemplateResolverTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.api.exception;

import io.genfin.document.api.identity.TemplateId;

public final class TemplateNotFoundException extends RuntimeException {

  public TemplateNotFoundException(TemplateId templateId) {
    super("No template registered for id: " + templateId);
  }
}
```

```java
package io.genfin.document.port;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;

public interface TemplateResolver {
  CompiledTemplate resolve(TemplateId id);
}
```

```java
package io.genfin.document.internal.template;

import io.genfin.document.api.exception.TemplateNotFoundException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.DocumentTemplate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultTemplateRegistry {

  private final Map<TemplateId, DocumentTemplate> templates = new ConcurrentHashMap<>();

  public void register(DocumentTemplate template) {
    templates.put(template.id(), template);
  }

  public DocumentTemplate get(TemplateId id) {
    DocumentTemplate template = templates.get(id);
    if (template == null) {
      throw new TemplateNotFoundException(id);
    }
    return template;
  }

  DocumentTemplate lookup(TemplateId id) {
    return templates.get(id);
  }
}
```

```java
package io.genfin.document.internal.template;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.port.TemplateResolver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultTemplateResolver implements TemplateResolver {

  private final DefaultTemplateRegistry registry;
  private final Map<TemplateId, CompiledTemplate> cache = new ConcurrentHashMap<>();

  public DefaultTemplateResolver(DefaultTemplateRegistry registry) {
    this.registry = registry;
  }

  @Override
  public CompiledTemplate resolve(TemplateId id) {
    return cache.computeIfAbsent(
        id,
        templateId -> {
          io.genfin.document.api.template.DocumentTemplate template = registry.get(templateId);
          return TemplateCompiler.compile(template, registry::lookup);
        });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.DefaultTemplateResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/exception/TemplateNotFoundException.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/TemplateResolver.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/template/DefaultTemplateRegistry.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/template/DefaultTemplateResolver.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/template/DefaultTemplateResolverTest.java
git commit -m "feat(fin-document): add TemplateResolver port and default registry/resolver with compiled-template caching"
```

---

### Task 8: `TemplateEngine` port + `DefaultTemplateEngine` (renders fragment tree → `DocumentSection`/`DocumentElement`)

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/TemplateEngine.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/template/DefaultTemplateEngine.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/template/DefaultTemplateEngineTest.java`

**Interfaces:**
- Consumes: `TemplateResolver`, `CompiledTemplate`, `TemplateFragment` hierarchy, `TemplateContext` (Tasks 4, 6-7); Stage 1's `DocumentSection`, `TextBlockElement`, `KeyValueElement`.
- Produces: `TemplateEngine.render(TemplateId templateId, TemplateContext context): List<DocumentSection>`. `DefaultTemplateEngine(TemplateResolver resolver)` implements `TemplateEngine`.
- Consumed by: Task 9 (`TemplateDocumentComposer`).

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.placeholder.ListPlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderContext;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.api.template.TemplateContext;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.TemplateEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultTemplateEngineTest {

  @Test
  void rendersLiteralAndPlaceholderIntoSection() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("greeting"), "Hello {{name}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("World");
    List<DocumentSection> sections =
        engine.render(TemplateId.of("greeting"), TemplateContext.of(resolver));

    assertThat(sections).isNotEmpty();
    String flattened =
        sections.stream()
            .flatMap(s -> s.elements().stream())
            .map(e -> e.accept(new TextExtractingVisitor()))
            .reduce("", String::concat);
    assertThat(flattened).contains("Hello ");
    assertThat(flattened).contains("World");
  }

  @Test
  void ifBlockIncludedOnlyWhenConditionTruthy() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(
        DocumentTemplate.of(TemplateId.of("conditional"), "{{#if show}}Visible{{/if}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver truthy = key -> ScalarPlaceholderValue.of("yes");
    PlaceholderResolver falsy = key -> ScalarPlaceholderValue.of("");

    String withTruthy = flatten(engine.render(TemplateId.of("conditional"), TemplateContext.of(truthy)));
    String withFalsy = flatten(engine.render(TemplateId.of("conditional"), TemplateContext.of(falsy)));

    assertThat(withTruthy).contains("Visible");
    assertThat(withFalsy).doesNotContain("Visible");
  }

  @Test
  void eachBlockRepeatsBodyPerItem() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("list"), "{{#each items}}{{item}};{{/each}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver resolver =
        key ->
            "items".equals(key)
                ? ListPlaceholderValue.of(
                    List.of(
                        PlaceholderContext.of(k -> ScalarPlaceholderValue.of("a")),
                        PlaceholderContext.of(k -> ScalarPlaceholderValue.of("b"))))
                : ScalarPlaceholderValue.of("");

    String flattened = flatten(engine.render(TemplateId.of("list"), TemplateContext.of(resolver)));

    assertThat(flattened).contains("a;");
    assertThat(flattened).contains("b;");
  }

  private static String flatten(List<DocumentSection> sections) {
    return sections.stream()
        .flatMap(s -> s.elements().stream())
        .map(e -> e.accept(new TextExtractingVisitor()))
        .reduce("", String::concat);
  }

  private static final class TextExtractingVisitor
      implements io.genfin.document.api.model.DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(io.genfin.document.api.model.KeyValueElement element) {
      return element.value();
    }

    @Override
    public String visitTable(io.genfin.document.api.model.TableElement element) {
      return "";
    }

    @Override
    public String visitTextBlock(io.genfin.document.api.model.TextBlockElement element) {
      return element.text();
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.DefaultTemplateEngineTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.port;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.template.TemplateContext;
import java.util.List;

public interface TemplateEngine {
  List<DocumentSection> render(TemplateId templateId, TemplateContext context);
}
```

`DefaultTemplateEngine` implements `TemplateEngine`: on `render(templateId, context)`, resolve the `CompiledTemplate` via the wrapped `TemplateResolver`, obtain a `PlaceholderContext` from `context.toPlaceholderContext()`, then walk `compiled.fragments()` with a `TemplateFragmentVisitor<List<TextBlockElement_or_similar>>` — concretely: walk the fragments producing a flat `List<String>` of literal/resolved text pieces via visitor dispatch (a `LiteralFragment` contributes its text; a `PlaceholderFragment` contributes `context.resolveDisplay(fragment.placeholder().key())`; an `IfFragment` contributes its body's rendering only if `context.resolveValue(fragment.condition().key())` is truthy — truthy means: dispatch the resolved `PlaceholderValue` through a small local visitor where `ScalarPlaceholderValue` is truthy iff its value is non-blank, `ListPlaceholderValue` is truthy iff non-empty, `MissingPlaceholderValue` is always falsy; an `EachFragment` contributes, for each `PlaceholderContext` item in the resolved `ListPlaceholderValue`'s `items()`, the body's rendering evaluated against that item's `PlaceholderContext` merged as the active context (simplest correct approach: build a per-item `TemplateContext`-equivalent by rendering the body fragments directly against the item's `PlaceholderContext` rather than against the outer one, matching the test's expectation that `{{item}}` inside `#each` resolves through the per-item context); an `IncludeFragment` should not appear here since `TemplateCompiler` already inlined every include at compile time — treat encountering one as a defensive no-op returning empty, it should be unreachable). Join the resulting text pieces into a single string, and wrap it as one `DocumentSection` containing one `TextBlockElement` (`DocumentSection.of("", List.of(TextBlockElement.of(joinedText)))`) — this satisfies the test's expectation of finding all rendered text within `sections().stream().flatMap(s -> s.elements().stream())`. (A richer per-element mapping — e.g. producing a `KeyValueElement` per placeholder rather than flattening to one text blob — is a reasonable alternative if you find it cleaner; the binding requirement is only that the rendered output, once flattened via the test's `DocumentElementVisitor`, contains the expected substrings in the expected order.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.DefaultTemplateEngineTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/port/TemplateEngine.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/template/DefaultTemplateEngine.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/template/DefaultTemplateEngineTest.java
git commit -m "feat(fin-document): add TemplateEngine port and DefaultTemplateEngine rendering into DocumentSection/DocumentElement"
```

---

### Task 9: `TemplateDocumentComposer` + `DocumentComposers` factory addition

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/template/TemplateDocumentComposer.java`
- Modify: `fin-core/fin-document/src/main/java/io/genfin/document/api/compose/DocumentComposers.java` (add one new static factory method — do not change `noOp()`)
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/template/TemplateDocumentComposerTest.java`

**Interfaces:**
- Consumes: `TemplateEngine`, `DocumentComposer`, `DocumentModel`, `ComposedDocument`, `DocumentAttributes` (Stage 1 + Task 8).
- Produces: `TemplateDocumentComposer(TemplateEngine engine)` implements `DocumentComposer`. `DocumentComposers.templated(TemplateEngine engine): DocumentComposer` (new additive factory method).
- This is the seam Stage 2 was built to prove: a `DocumentModel` with a `"templateId"` attribute gets its sections replaced by the rendered template output; without that attribute, behavior is identical to `NoOpDocumentComposer`.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.api.template.TemplateContext;
import io.genfin.document.port.DocumentComposer;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.TemplateEngine;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateDocumentComposerTest {

  @Test
  void passthroughWhenNoTemplateIdAttribute() {
    TemplateEngine engine = (templateId, context) -> List.of();
    DocumentComposer composer = new TemplateDocumentComposer(engine);

    DocumentModel model =
        DocumentModel.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
            List.of(DocumentSection.of("Original", List.of(KeyValueElement.of("k", "v")))),
            DocumentAttributes.empty());

    ComposedDocument composed = composer.compose(model);

    assertThat(composed.sections()).hasSize(1);
    assertThat(composed.sections().get(0).title()).isEqualTo("Original");
  }

  @Test
  void rendersTemplateWhenTemplateIdAttributePresent() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("greeting"), "Hello {{name}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));
    DocumentComposer composer = new TemplateDocumentComposer(engine);

    DocumentModel model =
        DocumentModel.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
            List.of(DocumentSection.of("Original", List.of())),
            DocumentAttributes.of(Map.of("templateId", "greeting", "name", "World")));

    ComposedDocument composed = composer.compose(model);

    boolean containsRenderedGreeting =
        composed.sections().stream()
            .flatMap(s -> s.elements().stream())
            .anyMatch(
                e ->
                    e.accept(
                            new io.genfin.document.api.model.DocumentElementVisitor<String>() {
                              @Override
                              public String visitKeyValue(KeyValueElement element) {
                                return element.value();
                              }

                              @Override
                              public String visitTable(
                                  io.genfin.document.api.model.TableElement element) {
                                return "";
                              }

                              @Override
                              public String visitTextBlock(
                                  io.genfin.document.api.model.TextBlockElement element) {
                                return element.text();
                              }
                            })
                        .contains("World"));

    assertThat(containsRenderedGreeting).isTrue();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.TemplateDocumentComposerTest"`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package io.genfin.document.internal.template;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.template.TemplateContext;
import io.genfin.document.port.DocumentComposer;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.TemplateEngine;
import java.util.Optional;

public final class TemplateDocumentComposer implements DocumentComposer {

  private final TemplateEngine engine;

  public TemplateDocumentComposer(TemplateEngine engine) {
    this.engine = engine;
  }

  @Override
  public ComposedDocument compose(DocumentModel model) {
    Optional<String> templateId = model.attributes().get("templateId");
    if (templateId.isEmpty()) {
      return ComposedDocument.of(model.metadata(), model.sections(), model.attributes());
    }

    PlaceholderResolver resolver =
        key -> ScalarPlaceholderValue.of(model.attributes().get(key).orElse(""));
    var renderedSections =
        engine.render(TemplateId.of(templateId.get()), TemplateContext.of(resolver));

    return ComposedDocument.of(model.metadata(), renderedSections, model.attributes());
  }
}
```

Add to `DocumentComposers.java` (do not remove or modify `noOp()`):

```java
  public static DocumentComposer templated(io.genfin.document.port.TemplateEngine engine) {
    return new io.genfin.document.internal.template.TemplateDocumentComposer(engine);
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.internal.template.TemplateDocumentComposerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/internal/template/TemplateDocumentComposer.java \
  fin-core/fin-document/src/main/java/io/genfin/document/api/compose/DocumentComposers.java \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/template/TemplateDocumentComposerTest.java
git commit -m "feat(fin-document): add TemplateDocumentComposer and DocumentComposers.templated() factory method"
```

---

### Task 10: `module-info.java` exports + extend `DocumentArchitectureTest` + full module build gate

**Files:**
- Modify: `fin-core/fin-document/src/main/java/module-info.java` (add exports for `io.genfin.document.api.placeholder`, `io.genfin.document.api.template` — `port` is already exported from Stage 1 and needs no change since new port types live in the same already-exported package)
- Modify: `fin-core/fin-document/src/test/java/io/genfin/document/architecture/DocumentArchitectureTest.java` (no new test methods required — the existing `importPackages("io.genfin.document")` call already covers the new packages; verify this is true rather than assuming, and add a new `.ignoreDependency(...)` cycle exception only if the new `TemplateDocumentComposer`/`DocumentComposers.templated()` wiring introduces a genuine port↔internal cycle analogous to Stage 1's `DocumentRenderers` — check before adding, do not add speculatively)

**Interfaces:**
- Consumes: everything from Tasks 1-9.
- Produces: none (verification-only task).

- [ ] **Step 1: Update `module-info.java`**

Add two `exports` lines (alongside the six already there from Stage 1):

```java
  exports io.genfin.document.api.placeholder;
  exports io.genfin.document.api.template;
```

- [ ] **Step 2: Run the full architecture test and module build gate**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.architecture.DocumentArchitectureTest"`
Expected: PASS. If the cycle-freedom check fails because `DocumentComposers.templated()` (in `api.compose`) constructs `TemplateDocumentComposer` (in `internal.template`), add exactly one more entry to the existing `.ignoreDependency(...)` exception list (the same pattern already used for `DocumentComposers`/`DocumentRenderers` from Stage 1's Task 14 fix) — do not weaken the rule generally, and do not move `TemplateDocumentComposer` out of `internal` or `DocumentComposers`/`templated()` out of the exported `api.compose` package to dodge the cycle (that was the exact mistake caught and reverted in Stage 1).

Run: `./gradlew :fin-document:check`
Expected: PASS — compile, Spotless, Checkstyle, PMD, JUnit, JaCoCo.

- [ ] **Step 3: Run the full repo build**

Run: `./gradlew build`
Expected: PASS across all modules — Stage 2 is additive to Stage 1, must not break any existing module.

- [ ] **Step 4: Commit**

```bash
git add fin-core/fin-document/src/main/java/module-info.java
# include the architecture test file only if it was actually modified in Step 2
git commit -m "test(fin-document): export placeholder/template packages, verify architecture constraints hold for Stage 2"
```

---

## Self-Review Notes

- **Spec coverage:** Template Engine (Group 1: Tasks 4-8) and Placeholder Engine (Group 2: Tasks 1-3) both fully covered. The `DocumentComposer` integration point (Task 9) proves the pipeline seam Stage 1 was designed for. Architecture verification (Task 10) closes the stage the same way Stage 1's Task 14/15 did.
- **Type consistency:** `PlaceholderValue`/`TemplateFragment` visitor method names are used identically across every task that touches them (Tasks 1, 3, 4, 6, 8, 9). `TemplateResolver`/`PlaceholderResolver` naming and shape mirror Stage 1's `RendererResolver` precisely, per the Global Constraints' instruction to follow established patterns.
- **Known risk carried forward explicitly:** Task 10's Step 2 calls out, by name, the exact mistake Stage 1's Task 14 made (moving a class out of its intended exported/hidden package to dodge an ArchUnit cycle) so the same regression is not repeated here.
- **No placeholders:** every step has complete, real code except the two compiler-internals paragraphs in Task 6 (parent/section merge, include inlining) and the render-walk in Task 8, which are described precisely enough to implement correctly (exact test-observable behavior specified) and are intentionally left as prose-plus-constraints rather than full code because the internal data structure choice (how to track section provenance through compilation) doesn't affect any other task's public interface — implementers have latitude on the internal approach as long as the task's tests pass.
