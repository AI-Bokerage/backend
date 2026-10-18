package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.PricingRequestId;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups the successive {@link PricingRequest}s raised while an application repeatedly reprices the
 * same commercial intent (e.g. a cart being repriced as a customer applies a coupon). Not itself a
 * pipeline input - purely a convenience for correlating requests; no dedicated identifier type
 * exists for it, so it carries an opaque token instead.
 */
public record PricingSession(String token, List<PricingRequestId> requestIds)
    implements ValueObject {

  public PricingSession {
    Validate.notBlank(token, "token must not be blank.");
    requestIds = List.copyOf(requestIds);
  }

  public static PricingSession start() {
    return new PricingSession(IdentifierGenerators.uuidV4().generate(), List.of());
  }

  public PricingSession withRequest(PricingRequestId requestId) {
    Validate.notNull(requestId, "requestId must not be null.");
    List<PricingRequestId> next = new ArrayList<>(requestIds);
    next.add(requestId);
    return new PricingSession(token, next);
  }
}
