package io.genfin.payment.method;

import io.genfin.payment.metadata.PaymentMetadata;

/**
 * Builds {@link PaymentMethod}s. Preferred over the canonical constructor for readability at call
 * sites.
 */
public final class MethodBuilder {

  private PaymentMethodType type;
  private String maskedIdentifier;
  private PaymentMetadata metadata = PaymentMetadata.empty();

  private MethodBuilder() {}

  public static MethodBuilder newMethod() {
    return new MethodBuilder();
  }

  public MethodBuilder type(PaymentMethodType type) {
    this.type = type;
    return this;
  }

  public MethodBuilder maskedIdentifier(String maskedIdentifier) {
    this.maskedIdentifier = maskedIdentifier;
    return this;
  }

  public MethodBuilder metadata(PaymentMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public PaymentMethod build() {
    return new PaymentMethod(type, maskedIdentifier, metadata);
  }
}
