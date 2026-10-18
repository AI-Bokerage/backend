package io.genfin.refund.reason;

import io.genfin.refund.port.reason.RefundReasonProvider;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link RefundReasonRegistry} from an explicit set of reasons, or from {@link
 * RefundReasonProvider}s (e.g. {@link RefundReasonRegistries#standardCatalog()}). Preferred over
 * populating a registry by hand at call sites.
 */
public final class RefundReasonBuilder {

  private final List<RefundReasonDescriptor> descriptors = new ArrayList<>();

  private RefundReasonBuilder() {}

  public static RefundReasonBuilder newRegistry() {
    return new RefundReasonBuilder();
  }

  public RefundReasonBuilder reason(RefundReason reason, String displayName) {
    descriptors.add(new RefundReasonDescriptor(reason, displayName));
    return this;
  }

  public RefundReasonBuilder provider(RefundReasonProvider provider) {
    descriptors.addAll(provider.provide());
    return this;
  }

  public RefundReasonBuilder standardCatalog() {
    return provider(RefundReasonRegistries.standardCatalog());
  }

  public RefundReasonRegistry build() {
    RefundReasonRegistry registry = RefundReasonRegistries.empty();
    descriptors.forEach(registry::register);
    return registry;
  }
}
