# Architecture

Gen-Fin is a Gradle multi-module Java 21 SDK. Every module — `fin-api` through
`fin-spring-boot-starter` — follows the same small set of conventions, enforced by JPMS
(`module-info.java`) and, in most modules, an ArchUnit test suite. This document is the one place
those conventions are described once; per-module docs (`MONEY_ENGINE.md`, `LEDGER_ENGINE.md`, …)
link back here instead of repeating them.

## Package layering: `api` / `port` / `internal`

Every module splits its packages into exactly three kinds:

- **`io.genfin.<module>.<concept>`** (often called `api`, though not always literally named
  that) — public, domain-agnostic value types and aggregates: records/final classes with
  validated constructors, `of(...)`/builder factories, no framework dependency.
- **`io.genfin.<module>.port.<concern>`** — the pluggable side: interfaces (extension points) an
  application can implement, plus factory classes (`XResolvers`, `XRegistries`, `XEngines`) that
  are the *only* consumer-reachable way to obtain a working default implementation.
- **`io.genfin.<module>.internal.<concern>`** — default implementations. Never exported from
  `module-info.java`; never imported by a consumer directly. Package-visibility is enforced by
  JPMS, and most modules additionally enforce it with an ArchUnit rule
  (`noClasses().that().resideInAPackage("..port..").should().dependOnClassesThat().resideInAPackage("..internal..")`,
  with narrow, named `.ignoreDependency(...)` exceptions for the factory classes that legitimately
  construct an internal default).

A consumer of Gen-Fin never needs to import an `internal` package — see [INTEGRATION.md](INTEGRATION.md)
for verification that this holds all the way through a realistic FIN-SVC integration.

## The `ExtensionRegistry` SPI mechanism

Every pluggable behavior in every module is discoverable through one shared type:
`io.genfin.api.spi.ExtensionRegistry` (`register(Class<T>, T)`, `find(Class<T>): Optional<T>`,
`findAll(Class<T>): List<T>`, backed by `DefaultExtensionRegistry`'s `ConcurrentHashMap` — safe
for concurrent registration and lookup). `find()` returns the **first-registered** implementation
for a type; `register()` accumulates, never overwrites.

Each module ships a `spi.XExtensions` final utility class with one method,
`registerDefaults(ExtensionRegistry registry)`, that registers every extension point the module
defines. An application builds one registry, calls every module's `registerDefaults` once at
startup, and is done — see [EXTENSIONS.md](EXTENSIONS.md) for the mechanics and override pattern,
or [SPRING.md](SPRING.md) if the application is a Spring Boot service, where this is entirely
automatic.

Several engines (fin-ledger's `PostingRuleRegistry`/`AccountTypeRegistry`, fin-pricing's
`CatalogRegistry`, fin-dunning's `PolicyRegistry`) register their extension points **empty by
design** — Gen-Fin ships no built-in business rules, account names, product catalogs, or dunning
policies. A deployment must supply its own before that engine does anything useful. This is a
stated philosophy, not a gap — see each engine's own `*_ENGINE.md`.

## No `instanceof` / `switch`

No module dispatches behavior with `instanceof` or `switch`. Two patterns replace them everywhere:

- **Open-value types**, for extensible enum-like concepts (`DocumentType`, `PaymentMethodType`,
  `StandardPaperSize`, …): an interface (`String code()`) + a record implementing it + a
  `public static final` set of well-known constants + an `of(code)` factory for anything else.
  Comparison is by `.code()` string equality, never `instanceof`.
- **Double-dispatch visitors**, for closed sets of element types that need type-specific behavior
  (`DocumentElementVisitor<R>`, `TemplateFragmentVisitor<R>`, `PlaceholderValueVisitor<R>`): each
  element implements `<R> R accept(Visitor<R> visitor)`, dispatching to a type-specific `visitX`
  method.

## Exceptions

Every module's exceptions extend the shared root, `io.genfin.api.exception.GenFinException`
(itself a `RuntimeException`), carrying a machine-readable `ErrorCode` (a per-module enum, e.g.
`MoneyErrorCode`, `DocumentErrorCode`) plus structured metadata. No module lets a framework
exception (a provider SDK's own exception type, a JDK crypto exception, …) cross a public API
boundary uncaught.

## Validation

Constructor-level validation goes through `io.genfin.api.validation.Validate`
(`notNull`/`notBlank`/`positive`/`nonNegative`/`required`/`state`/`argument`) — never a raw `if`
throwing a bare `IllegalArgumentException`. Builder optional setters coalesce `null` to a
documented default **inside the constructor**, not deferred to an accessor.

## JPMS

Every `fin-core`/`fin-providers` module has a `module-info.java`: `requires transitive` mirrors
its `api`-scoped Gradle dependencies exactly, `exports` never includes an `internal` package.
Spring-facing modules (`fin-autoconfigure`, `fin-spring-boot-starter`, `demo/*`) are deliberately
**non-modular** — Spring Boot's classpath scanning (`@ConditionalOnClass`, `AutoConfiguration.imports`)
does not compose reliably with the module path, and an unnamed/classpath module can read every
named module automatically, so nothing is lost at the API boundary. `fin-stripe`/`fin-razorpay`
are also non-modular, for the same reason plus their SDKs' own non-modularized transitive
dependencies.

## Module dependency graph

See [MODULES.md](MODULES.md) for the full module-by-module description and confirmed dependency
graph — it's a DAG, not a strict line: `fin-invoice` and `fin-payment` are siblings (neither
depends on the other), and `fin-document`/`fin-pricing`/`fin-dunning` are independent branches off
`fin-api`/`fin-money`. No cycles, no sibling module reaching into another sibling's `internal`
package, confirmed by the V1 release audit.

## Build

Gradle Kotlin DSL, convention plugins in `build-logic/` (`genfin.java-library-conventions`,
`genfin.quality-conventions`, `genfin.publishing-conventions` — no duplicated `plugins {}`/
`dependencies {}` boilerplate per module). Every module runs Checkstyle, PMD, Spotless
(google-java-format), error-prone, and JaCoCo coverage as part of `check`. `bom/` publishes a
`java-platform` BOM constraining every module's version for consumers.
