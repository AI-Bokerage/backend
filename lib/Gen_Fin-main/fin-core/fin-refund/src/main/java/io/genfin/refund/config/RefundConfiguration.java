package io.genfin.refund.config;

import io.genfin.api.validation.Validate;
import io.genfin.refund.port.calculation.RefundCalculator;
import io.genfin.refund.port.lifecycle.RefundLifecycleProvider;
import io.genfin.refund.port.policy.ApprovalPolicy;
import io.genfin.refund.port.policy.ApprovalWorkflow;
import io.genfin.refund.port.policy.MaximumRefundPolicy;
import io.genfin.refund.port.policy.ReasonPolicy;
import io.genfin.refund.port.policy.RefundWindowPolicy;
import io.genfin.refund.port.validation.RefundRequestValidator;
import io.genfin.refund.port.validation.RefundValidator;

/**
 * The full, explicit policy set for a Refund-engine deployment, mirroring {@code
 * PaymentConfiguration}.
 */
public final class RefundConfiguration {

  private final RefundLifecycleProvider lifecycleProvider;
  private final ApprovalPolicy approvalPolicy;
  private final ApprovalWorkflow approvalWorkflow;
  private final RefundWindowPolicy windowPolicy;
  private final MaximumRefundPolicy maximumRefundPolicy;
  private final ReasonPolicy reasonPolicy;
  private final RefundCalculator calculator;
  private final RefundValidator validator;
  private final RefundRequestValidator requestValidator;
  private final int maxRetryAttempts;

  private RefundConfiguration(Builder builder) {
    this.lifecycleProvider =
        Validate.notNull(builder.lifecycleProvider, "lifecycleProvider must not be null.");
    this.approvalPolicy =
        Validate.notNull(builder.approvalPolicy, "approvalPolicy must not be null.");
    this.approvalWorkflow =
        Validate.notNull(builder.approvalWorkflow, "approvalWorkflow must not be null.");
    this.windowPolicy = Validate.notNull(builder.windowPolicy, "windowPolicy must not be null.");
    this.maximumRefundPolicy =
        Validate.notNull(builder.maximumRefundPolicy, "maximumRefundPolicy must not be null.");
    this.reasonPolicy = Validate.notNull(builder.reasonPolicy, "reasonPolicy must not be null.");
    this.calculator = Validate.notNull(builder.calculator, "calculator must not be null.");
    this.validator = Validate.notNull(builder.validator, "validator must not be null.");
    this.requestValidator =
        Validate.notNull(builder.requestValidator, "requestValidator must not be null.");
    this.maxRetryAttempts =
        Validate.nonNegative(builder.maxRetryAttempts, "maxRetryAttempts must not be negative.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public RefundLifecycleProvider lifecycleProvider() {
    return lifecycleProvider;
  }

  public ApprovalPolicy approvalPolicy() {
    return approvalPolicy;
  }

  public ApprovalWorkflow approvalWorkflow() {
    return approvalWorkflow;
  }

  public RefundWindowPolicy windowPolicy() {
    return windowPolicy;
  }

  public MaximumRefundPolicy maximumRefundPolicy() {
    return maximumRefundPolicy;
  }

  public ReasonPolicy reasonPolicy() {
    return reasonPolicy;
  }

  public RefundCalculator calculator() {
    return calculator;
  }

  public RefundValidator validator() {
    return validator;
  }

  public RefundRequestValidator requestValidator() {
    return requestValidator;
  }

  /** Placeholder for a future refund retry policy — currently just a bound on attempt count. */
  public int maxRetryAttempts() {
    return maxRetryAttempts;
  }

  public static final class Builder {

    private RefundLifecycleProvider lifecycleProvider;
    private ApprovalPolicy approvalPolicy;
    private ApprovalWorkflow approvalWorkflow;
    private RefundWindowPolicy windowPolicy;
    private MaximumRefundPolicy maximumRefundPolicy;
    private ReasonPolicy reasonPolicy;
    private RefundCalculator calculator;
    private RefundValidator validator;
    private RefundRequestValidator requestValidator;
    private int maxRetryAttempts = 3;

    public Builder lifecycleProvider(RefundLifecycleProvider lifecycleProvider) {
      this.lifecycleProvider = lifecycleProvider;
      return this;
    }

    public Builder approvalPolicy(ApprovalPolicy approvalPolicy) {
      this.approvalPolicy = approvalPolicy;
      return this;
    }

    public Builder approvalWorkflow(ApprovalWorkflow approvalWorkflow) {
      this.approvalWorkflow = approvalWorkflow;
      return this;
    }

    public Builder windowPolicy(RefundWindowPolicy windowPolicy) {
      this.windowPolicy = windowPolicy;
      return this;
    }

    public Builder maximumRefundPolicy(MaximumRefundPolicy maximumRefundPolicy) {
      this.maximumRefundPolicy = maximumRefundPolicy;
      return this;
    }

    public Builder reasonPolicy(ReasonPolicy reasonPolicy) {
      this.reasonPolicy = reasonPolicy;
      return this;
    }

    public Builder calculator(RefundCalculator calculator) {
      this.calculator = calculator;
      return this;
    }

    public Builder validator(RefundValidator validator) {
      this.validator = validator;
      return this;
    }

    public Builder requestValidator(RefundRequestValidator requestValidator) {
      this.requestValidator = requestValidator;
      return this;
    }

    public Builder maxRetryAttempts(int maxRetryAttempts) {
      this.maxRetryAttempts = maxRetryAttempts;
      return this;
    }

    public RefundConfiguration build() {
      return new RefundConfiguration(this);
    }
  }
}
