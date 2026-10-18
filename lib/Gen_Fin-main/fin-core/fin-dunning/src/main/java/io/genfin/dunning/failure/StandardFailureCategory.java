package io.genfin.dunning.failure;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of illustrative failure categories. Not exhaustive - fin-dunning is
 * classification-scheme-agnostic, so applications are free to {@link #of(String)} any category code
 * their own failure taxonomy actually uses.
 */
public record StandardFailureCategory(String code) implements FailureCategory {

  public StandardFailureCategory {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardFailureCategory of(String code) {
    return new StandardFailureCategory(code);
  }

  public static final StandardFailureCategory TEMPORARY = of("TEMPORARY");
  public static final StandardFailureCategory PERMANENT = of("PERMANENT");
  public static final StandardFailureCategory GATEWAY = of("GATEWAY");
  public static final StandardFailureCategory CUSTOMER = of("CUSTOMER");
  public static final StandardFailureCategory FRAUD = of("FRAUD");
  public static final StandardFailureCategory BUSINESS = of("BUSINESS");
}
