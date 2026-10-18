# Take a payment

**Precondition, discovered during the second-consumer validation exercise (see
`SECOND_CONSUMER_VALIDATION.md`): `PaymentService.createPaymentIntent` and
`createCheckoutSession` both require a gateway-customer record to already exist for the tenant.**
Skip this step and you get `BusinessRuleViolationException: No provider customer for tenant`.

```java
@Autowired PaymentMethodService paymentMethodService;
@Autowired PaymentService paymentService;
@Autowired InvoiceService invoiceService;

// 1. Register the customer + payment method FIRST
paymentMethodService.createCustomerIfRequired(tenantId, "Acme Corp", "billing@acme.example");
paymentMethodService.addPaymentMethod(
    tenantId, "tok_from_your_gateway_sdk", PaymentMethodType.CARD,
    "VISA", "4242", 12, 2030, /* makeDefault */ true);

// 2. Now a payment intent can be created
PaymentIntentResult intent = paymentService.createPaymentIntent(tenantId, invoiceId);

// 3. Once the gateway confirms the charge (webhook, or synchronous confirmation, depending on
//    your PaymentGatewayPort implementation), apply it to the invoice
PlatformInvoice paid = invoiceService.applyPayment(invoiceId, invoice.getAmountDue(), null);
assert paid.getStatus() == InvoiceStatus.PAID;
```

`applyPayment` does not itself check invoice status — passing an amount less than the full
amount due leaves the invoice `PARTIALLY_PAID` rather than `PAID`.
