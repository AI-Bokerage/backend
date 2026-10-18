import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type { IPaymentProvider, PaymentProviderKind, PaymentCheckoutResult } from "../../../domain/ports/payment-provider.port.ts";
import { findPlanByCode } from "../../../common/plans.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PlanNotFoundError, PaymentProviderNotConfiguredError } from "../../../common/errors.ts";
import type { CreateCheckoutInput } from "./schema.ts";

export class CheckoutService {
  constructor(
    private readonly repo: ISignupSessionRepo,
    private readonly paymentProviders: Partial<Record<PaymentProviderKind, IPaymentProvider>>,
    private readonly defaultProvider: PaymentProviderKind,
  ) {}

  async createSession(input: CreateCheckoutInput): Promise<PaymentCheckoutResult> {
    const session = await this.repo.findById(input.sessionId);
    if (!session) throw new SignupSessionNotFoundError(input.sessionId);

    if (session.state !== SignupState.PLAN_SELECTED) {
      throw new SignupStepOutOfOrderError(SignupState.PLAN_SELECTED, session.state);
    }

    const planCode = session.selectedPlanCode!;
    const plan = findPlanByCode(planCode);
    if (!plan) throw new PlanNotFoundError(planCode);

    const billingCycle = (session.selectedBillingCycle as "MONTHLY" | "ANNUAL" | null) ?? "MONTHLY";
    const isAnnual = billingCycle === "ANNUAL";
    const amountCentsUsd = isAnnual ? plan.annualPriceCentsUsd : plan.monthlyPriceCentsUsd;
    const amountPaiseInr = isAnnual ? plan.annualPricePaiseInr : plan.monthlyPricePaiseInr;

    const kind = input.paymentProvider ?? this.defaultProvider;
    const provider = this.paymentProviders[kind];
    if (!provider) throw new PaymentProviderNotConfiguredError(kind);

    const result = await provider.createCheckoutSession({
      sessionId: session.id,
      email: session.email,
      planCode,
      billingCycle,
      amountCentsUsd,
      amountPaiseInr,
      successUrl: input.successUrl,
      cancelUrl: input.cancelUrl,
      trialDays: plan.trialDays,
    });

    await this.repo.updateState(session.id, SignupState.PAYMENT_PENDING, {
      checkoutSessionId: result.sessionId,
      paymentProvider: kind,
    });

    return result;
  }
}
