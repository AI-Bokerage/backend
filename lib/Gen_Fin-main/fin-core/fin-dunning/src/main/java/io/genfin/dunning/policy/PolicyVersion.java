package io.genfin.dunning.policy;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * The revision number of a {@code DunningPolicy}. An application bumps this whenever it changes a
 * registered policy's rules, so cases already resolved against an older revision can keep it if the
 * application chooses - fin-dunning itself attaches no meaning to the number beyond ordering.
 */
public record PolicyVersion(int number) implements ValueObject {

  public PolicyVersion {
    Validate.positive(number, "number must be positive.");
  }

  public static PolicyVersion of(int number) {
    return new PolicyVersion(number);
  }

  /** The first revision of a newly authored policy. */
  public static PolicyVersion initial() {
    return new PolicyVersion(1);
  }

  public PolicyVersion next() {
    return new PolicyVersion(number + 1);
  }

  public boolean isNewerThan(PolicyVersion other) {
    Validate.notNull(other, "other must not be null.");
    return number > other.number;
  }
}
