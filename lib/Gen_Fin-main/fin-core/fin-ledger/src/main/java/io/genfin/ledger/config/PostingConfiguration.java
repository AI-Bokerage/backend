package io.genfin.ledger.config;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.ledger.port.posting.PostingPolicy;
import io.genfin.ledger.port.posting.PostingRuleRegistry;
import io.genfin.ledger.port.posting.PostingRuleResolver;
import io.genfin.ledger.port.posting.PostingValidator;

/** The posting policy set for a Ledger-engine deployment. */
public final class PostingConfiguration {

  private final PostingPolicy policy;
  private final PostingValidator validator;
  private final PostingRuleRegistry ruleRegistry;
  private final PostingRuleResolver ruleResolver;
  private final PostingEngine engine;

  private PostingConfiguration(Builder builder) {
    this.policy = Validate.notNull(builder.policy, "policy must not be null.");
    this.validator = Validate.notNull(builder.validator, "validator must not be null.");
    this.ruleRegistry = Validate.notNull(builder.ruleRegistry, "ruleRegistry must not be null.");
    this.ruleResolver = Validate.notNull(builder.ruleResolver, "ruleResolver must not be null.");
    this.engine = Validate.notNull(builder.engine, "engine must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public PostingPolicy policy() {
    return policy;
  }

  public PostingValidator validator() {
    return validator;
  }

  public PostingRuleRegistry ruleRegistry() {
    return ruleRegistry;
  }

  public PostingRuleResolver ruleResolver() {
    return ruleResolver;
  }

  public PostingEngine engine() {
    return engine;
  }

  public static final class Builder {

    private PostingPolicy policy;
    private PostingValidator validator;
    private PostingRuleRegistry ruleRegistry;
    private PostingRuleResolver ruleResolver;
    private PostingEngine engine;

    public Builder policy(PostingPolicy policy) {
      this.policy = policy;
      return this;
    }

    public Builder validator(PostingValidator validator) {
      this.validator = validator;
      return this;
    }

    public Builder ruleRegistry(PostingRuleRegistry ruleRegistry) {
      this.ruleRegistry = ruleRegistry;
      return this;
    }

    public Builder ruleResolver(PostingRuleResolver ruleResolver) {
      this.ruleResolver = ruleResolver;
      return this;
    }

    public Builder engine(PostingEngine engine) {
      this.engine = engine;
      return this;
    }

    public PostingConfiguration build() {
      return new PostingConfiguration(this);
    }
  }
}
