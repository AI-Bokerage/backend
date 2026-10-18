# Integration Guide

```
   Your Spring Boot app
          │
          ├── depends on ──▶ bsm-spring-boot-starter
          │                          │
          │                          ▼
          │                     bsm-core (ports + application services)
          │                          ▲
          └── implements ─────────────┘
              (your @Component adapters satisfy the ports)
```

You never call an adapter directly — you implement the port interface, register it as a
`@Component`/`@Bean`, and the starter's auto-configuration wires it into the application service
that needs it.

## Repository ports

~20 of `bsm-core`'s ports are repository ports (`SubscriptionRepositoryPort`,
`PlatformInvoiceRepositoryPort`, `PaymentRepositoryPort`, etc.) — CRUD + a handful of
domain-specific finders, expressed entirely in domain types (no JPA/Spring Data leaks through the
interface). A real implementation is typically a thin adapter over your own persistence:

```java
@Repository
@RequiredArgsConstructor
public class JpaSubscriptionRepository implements SubscriptionRepositoryPort {
    private final SubscriptionJpaRepository jpaRepository; // your own Spring Data repository
    private final SubscriptionEntityMapper mapper;          // your own entity <-> domain mapper

    @Override
    public Subscription save(Subscription subscription) {
        try {
            return mapper.toDomain(jpaRepository.save(mapper.toEntity(subscription)));
        } catch (OptimisticLockingFailureException e) {
            // Translate YOUR persistence framework's concurrency exception into bsm-core's
            // domain-level one — bsm-core never imports jakarta.persistence or Spring Data.
            throw new ConcurrentUpdateException("Subscription " + subscription.getId()
                + " was updated concurrently", e);
        }
    }
    // ... findById, findCurrentByTenantId, etc.
}
```

`bsm-demo`'s `InMemorySubscriptionRepository` (and the other 11 `InMemory*Repository` classes in
`bsm-demo/src/main/java/com/example/bsmdemo/adapter/`) show the exact method set for every
repository port — same shape, just backed by a `ConcurrentHashMap` instead of a real database.

## Payment gateway (`PaymentGatewayPort` + `PaymentGatewayResolver`)

`PaymentGatewayResolver.resolve(PaymentProvider)` picks a `PaymentGatewayPort` implementation by
provider. If you only support one gateway, resolve unconditionally to it (see
`bsm-demo`'s `DemoPaymentGatewayResolver`). `PaymentGatewayPort` itself wraps your actual SDK
(Stripe, Razorpay, or whatever you use) — `bsm-core` never imports a payment SDK directly.

```java
@Component
public class StripeGatewayAdapter implements PaymentGatewayPort {
    private final StripeClient stripeClient; // your own SDK wrapper

    @Override
    public PaymentIntentResult createPaymentIntent(CreatePaymentIntentCommand command) {
        // call the real Stripe API, map the response into PaymentIntentResult
    }
    // ...
}
```

## Event publishers (`EventPublisherPort`, `SubscriptionEventPublisherPort`,
`InvoiceEventPublisher`, `DunningEventPublisher`)

Four separate ports, one per event family. `bsm-core` only knows that a business event
happened — it never knows or cares whether you publish to Kafka, RabbitMQ, an outbox table, or
just a log line:

```java
@Component
public class LoggingEventPublisher implements EventPublisherPort {
    @Override
    public void publish(String eventType, UUID tenantId, String aggregateType, UUID aggregateId,
                         UUID actorId, Map<String, Object> data) {
        log.info("event={} tenant={} aggregate={}:{} data={}", eventType, tenantId, aggregateType, aggregateId, data);
        // a real implementation would write to an outbox table / message broker instead
    }
}
```

## Authorization / tenant scope (`TenantScopePort`, `BsmAuthorizationPort`)

`TenantScopePort` is the one every `bsm-core` service actually calls (`assertTenantAccess`,
`resolveEffectiveTenantId`). Tie it into whatever authentication mechanism your app already has:

```java
@Component
public class MyTenantScopeAdapter implements TenantScopePort {
    @Override
    public void assertTenantAccess(UUID requestedTenantId) {
        AuthenticatedPrincipal principal = currentPrincipal(); // however you resolve it
        if (principal != null && !principal.bypassesTenantScoping()
                && !requestedTenantId.equals(principal.tenantId())) {
            throw new AccessDeniedException("tenant mismatch");
        }
    }

    @Override
    public UUID resolveEffectiveTenantId(UUID requestedTenantId) {
        AuthenticatedPrincipal principal = currentPrincipal();
        if (principal == null || principal.bypassesTenantScoping()) return requestedTenantId;
        return principal.tenantId();
    }
}
```

## `AuthenticatedPrincipal`

Not a Spring bean — a plain value object your authentication layer constructs per request (from a
JWT, a session, whatever) and exposes via `SecurityContextHolder` or your own equivalent. See
`GETTING_STARTED.md` for the minimal implementation.

## Storage

**`bsm-core` has no storage port of its own.** `InvoiceDocumentStoragePort` lives in
`bsm-svc/storage/`, not `bsm-core/domain/port/` — it's a host-internal abstraction between
`bsm-svc`'s own PDF-generation consumer and its S3 adapter, never referenced by any `bsm-core`
service (see `ARCHITECTURE_CERTIFICATION.md`'s "known deviations"). If you need invoice PDF
storage, build your own — there's nothing in the library to plug into yet.

## Pricing (`PpmPricingService`, `PpmAddOnPricingService`, `PpmVersionService`)

Three separate ports abstract over an external plan-pricing catalog (in the reference host, PPM-
SVC — but `bsm-core` has no idea what PPM-SVC is; it only sees these interfaces). All three are
read-only lookups, called synchronously during checkout/add-on-purchase/tenant-onboarding flows —
their Javadoc explicitly documents a fail-closed contract: a `PpmIntegrationException` on failure,
never a silent fallback to a stale or made-up price.

```java
@Component
public class MyPricingCatalogAdapter implements PpmPricingService {
    private final PricingCatalogClient client; // your own pricing catalog's client

    @Override
    public PpmResolvePriceResult resolvePrice(UUID planId, String region, String currency, String cycle) {
        try {
            return client.lookupPrice(planId, region, currency, cycle);
        } catch (PricingCatalogUnavailableException e) {
            throw new PpmIntegrationException("pricing catalog unavailable", e);
        }
    }
}
```

`PpmVersionService.getLatestVersion` and `PpmAddOnPricingService.resolveActivePrice` follow the
same shape — see `bsm-demo`'s `DemoPricingAdapter` (implements `PpmPricingService` only; the demo
does not exercise the other two, see `PORT_REFERENCE.md`'s coverage table) for a minimal working
reference.

## Promotion (`PpmPromoService`)

A single port validating a promo code against a plan. Per its Javadoc, `bsm-core` treats whatever
this port returns as authoritative — it never re-implements promo rules (date ranges, usage caps,
plan eligibility) itself, and `valid=false` is a normal business outcome, not an error:

```java
@Component
public class MyPromoValidationAdapter implements PpmPromoService {
    private final PromoCatalogClient client;

    @Override
    public PpmValidatePromoResult validatePromo(String code, UUID planId) {
        try {
            return client.validate(code, planId); // may legitimately return valid=false
        } catch (PromoCatalogUnavailableException e) {
            // fail-closed: unavailability must NEVER be treated as "promo valid"
            throw new PpmIntegrationException("promo catalog unavailable", e);
        }
    }
}
```

## Scheduler / recurring execution

`bsm-core` has no scheduler port and does not schedule anything itself — `DunningService
.processDueAttempts()`, `SubscriptionScheduleExecutorService`'s due-schedule execution, and
`InvoiceRenewalService`'s renewal sweep are plain methods that something in your host application
must call periodically. `bsm-svc`'s own implementation uses Spring's `@Scheduled`:

```java
@Component
@RequiredArgsConstructor
public class DunningScheduler {
    private final DunningService dunningService;

    @Scheduled(fixedDelayString = "${dunning.scheduler.retry-interval-ms:300000}")
    public void run() {
        dunningService.processDueAttempts();
    }
}
```

This is intentionally not part of the starter's auto-configuration — scheduling frequency,
distributed-lock/leader-election concerns (if you run multiple instances), and whether to use
`@Scheduled`, Quartz, or an external cron trigger are all deployment decisions the library
shouldn't make for you.

## Multi-tenant isolation

Every `bsm-core` application service method that accepts a `tenantId` argument calls
`TenantScopePort.assertTenantAccess`/`resolveEffectiveTenantId` before touching data — see the
"Authorization / tenant scope" section above for the port itself. There is no separate
"multi-tenant" port; tenant isolation is threaded through `TenantScopePort` plus the fact that
every repository port's finder methods are scoped by `tenantId` as an explicit parameter (never an
implicit thread-local or filter). This means:

- Your `TenantScopePort` implementation is the single place tenant-isolation bugs would originate
  from — get its `assertTenantAccess`/`resolveEffectiveTenantId` logic right and every aggregate
  inherits correct isolation.
- Repository port implementations must still honor the `tenantId` parameter in their queries
  (e.g. `SubscriptionRepositoryPort.findCurrentByTenantId`) — `TenantScopePort` guards which
  `tenantId` a caller is *allowed* to ask about, it does not filter query results for you.

## Error handling

`bsm-core` defines its own exception hierarchy so that adapters never leak framework-specific
exception types across the port boundary:

- **`ConcurrentUpdateException`** — the domain-level equivalent of an optimistic-locking conflict.
  Repository adapters must catch their persistence framework's own exception (e.g. Spring Data's
  `OptimisticLockingFailureException`, JPA's `OptimisticLockException`) and re-throw this instead —
  see the Repository ports example above.
- **`PpmIntegrationException`** — thrown by pricing/promotion/version ports (see above) when the
  upstream catalog is unavailable or a circuit breaker is open. Always fail-closed; never swallow
  this to fall back to a guessed price or an auto-approved promo.
- Domain validation failures (e.g. invalid state transitions) throw plain
  `IllegalStateException`/`IllegalArgumentException` from application services — these are not
  wrapped in a custom hierarchy since they represent programming errors in the caller, not
  integration failures.

Adapters should never let their own framework's exceptions (JPA, Spring Data, HTTP client
exceptions, provider SDK exceptions) escape across the port boundary unwrapped — translate them at
the adapter, the same way the Repository ports example does.

## Extension points — summary

Everything a consumer can plug into is one of:

1. **The 40 ports in `domain.port`** plus `PaymentGatewayResolver` in `application.service` — see
   `PORT_REFERENCE.md` for the exhaustive, per-port reference (purpose, required/optional,
   example implementation).
2. **`AuthenticatedPrincipal`** (from `libs/security-spi`) — not a port, but the value object your
   authentication layer must produce for `TenantScopePort` implementations to consume.
3. **Spring bean overrides** — every starter-provided default bean (e.g.
   `InvoiceNumberGenerator`'s default implementation) can be overridden by registering your own
   `@Bean` of the same type; `@ConditionalOnMissingBean` on the starter's default backs off
   automatically. See `STARTER_GUIDE.md`'s "Overriding a default bean" section.

There is no plugin/SPI-discovery mechanism beyond standard Spring bean registration — no
`ServiceLoader`, no classpath scanning for annotated extension classes. This is a deliberate
simplicity choice; see `CONTRIBUTING.md` for why speculative extension mechanisms aren't added
without a concrete need.
