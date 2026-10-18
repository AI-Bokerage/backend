# API Stability

## Public API

`bsm-core`'s `application.service.*` interfaces, `domain.model.*`, `domain.enums.*`,
`domain.exception.*`. Consumers call these interfaces and catch these exceptions. Changing a
method signature here is a breaking change.

Covered by `bsm-spring-boot-starter` auto-configuration today: `TenantBillingProfileService`,
`PaymentMethodService`, `PaymentService`, `InvoiceGenerationService`, `InvoiceService`,
`SubscriptionSynchronizationService`, `SubscriptionService`, `InvoiceRenewalService`,
`DunningService`, `TenantOnboardingService`.

**Not yet covered by the starter** (exist in `bsm-core`, fully functional, must be wired
manually — see `docs/examples/add-on-purchase.md` and `plan-upgrade.md`): `SubscriptionAddOnService`,
`SubscriptionChangeService`. Also not covered, and not expected to be added to the starter without
a separate design decision: `CreditNoteService`, `MigrationPlanService`, `PpmSnapshotIntegrityService`,
`RefundService`, `RefundRecoveryService`, `PaymentReconciliationService`.

## SPI (Service Provider Interface)

`bsm-core`'s `domain.port.*` interfaces (~40 of them) and `libs/security-spi`'s
`AuthenticatedPrincipal`. Consumers implement these as beans; `bsm-core` and the starter depend
on the interface, never a specific implementation. Adding a new method to a port interface is a
breaking change for every existing implementer — treat these more conservatively than the
service layer above.

## Internal

`bsm-core`'s `application.impl.*` classes, `domain.service.*` helper classes
(`SubscriptionLifecycleMapper`, `TenantOwnershipValidator`, `PpmProrationEngine`, etc.), and
`bsm-spring-boot-starter`'s `config`/`validation` packages. Consumers should never construct or
extend these directly — only rely on the beans they produce. Internal classes can change shape
between releases without notice; only the bean *type* they're registered under (a Public API
interface) is guaranteed stable.

## Experimental

- `CommercialEngineService` and `BillingDashboardService` live in `bsm-svc`, not `bsm-core` —
  `CommercialEngineServiceImpl` depends on a real cross-service tier registry
  (`io.cpms.common.plan.PpmTierRegistry`, shared with ppm-svc); `BillingDashboardServiceImpl`
  queries via raw `JdbcTemplate` SQL rather than a repository port. Neither is portable in its
  current form — see `ARCHITECTURE_CERTIFICATION.md`'s "known, documented deviations". Not part
  of the library surface at all right now, experimental in the sense that they'd need a real
  design (a `BillingSummaryQueryPort`, at minimum) before they could become Public API.
- `SubscriptionAddOnService` / `SubscriptionChangeService` — Public API in the sense that they're
  stable, tested interfaces in `bsm-core`, but Experimental in that the starter doesn't
  auto-configure them yet, so their integration story is "wire it yourself" rather than "add the
  dependency and go."
- `libs/java-common`'s `io.cpms.common.messaging.*` cross-service event catalog and
  `io.cpms.common.plan.PpmTierRegistry` — real, load-bearing, but explicitly platform-specific
  (CPMS's own shared contracts), not part of the portable Billing library surface at all. Don't
  depend on these from a non-CPMS consumer.
