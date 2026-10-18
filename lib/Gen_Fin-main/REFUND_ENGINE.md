# Refund Engine (`fin-refund`)

A reusable Refund Engine: partial/multiple refunds, refund windows, approval workflow, and gateway
interaction — reusing `fin-payment`'s `PaymentGateway` rather than inventing a second gateway
interface. Depends on `fin-api`, `fin-money`, and `fin-payment`.

**Full documentation:** [`fin-core/fin-refund/REFUND.md`](fin-core/fin-refund/REFUND.md) —
package layout, the Refund aggregate, lifecycle, partial/multiple refunds, refund window/policy,
approval workflow, gateway reuse, extension points, and configuration.

## At a glance

| Concern | Key types |
|---|---|
| Construction | `Refund`'s constructor is genuinely public — no factory indirection required |
| Linking to a Payment | `Reference.payment(String paymentId)` — `fin-refund`'s own `Reference` type, deliberately a separate copy from `fin-payment`'s (see [INTEGRATION.md](INTEGRATION.md)) |
| Gateway reuse | Refunds call the same `PaymentGateway.refund(GatewayRequest)` used for the original payment |
| Approval | `ApprovalPolicy`, `ApprovalWorkflow` |
| SPI registration | `io.genfin.refund.spi.RefundExtensions.registerDefaults(registry)` |

## See also

[ARCHITECTURE.md](ARCHITECTURE.md), [EXTENSIONS.md](EXTENSIONS.md), [INTEGRATION.md](INTEGRATION.md),
[PAYMENT_ENGINE.md](PAYMENT_ENGINE.md), [SPRING.md](SPRING.md).
