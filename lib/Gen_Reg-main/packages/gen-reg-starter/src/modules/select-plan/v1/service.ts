// src/modules/select-plan/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { findPlanByCode } from "../../../common/plans.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PlanNotFoundError } from "../../../common/errors.ts";
import type { SelectPlanInput } from "./schema.ts";

export interface SelectPlanResult {
  sessionId: string;
  planCode: string;
  billingCycle: "MONTHLY" | "ANNUAL";
  nextStep: "CHECKOUT";
}

export class SelectPlanService {
  constructor(private readonly repo: ISignupSessionRepo) {}

  async selectPlan(input: SelectPlanInput): Promise<SelectPlanResult> {
    const session = await this.repo.findById(input.sessionId);
    if (!session) throw new SignupSessionNotFoundError(input.sessionId);

    if (session.state !== SignupState.EMAIL_VERIFIED) {
      throw new SignupStepOutOfOrderError(SignupState.EMAIL_VERIFIED, session.state);
    }

    if (!findPlanByCode(input.planCode)) {
      throw new PlanNotFoundError(input.planCode);
    }

    await this.repo.updateState(session.id, SignupState.PLAN_SELECTED, {
      selectedPlanCode: input.planCode,
      selectedBillingCycle: input.billingCycle,
    });

    return {
      sessionId: session.id,
      planCode: input.planCode,
      billingCycle: input.billingCycle,
      nextStep: "CHECKOUT",
    };
  }
}
