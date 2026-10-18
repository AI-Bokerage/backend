// src/domain/enums/signup-state.enum.ts
export enum SignupState {
  STARTED = "STARTED",
  EMAIL_VERIFIED = "EMAIL_VERIFIED",
  PLAN_SELECTED = "PLAN_SELECTED",
  PAYMENT_PENDING = "PAYMENT_PENDING",
  PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED",
  PROVISIONING = "PROVISIONING",
  ACTIVE = "ACTIVE",
  PROVISION_FAILED = "PROVISION_FAILED",
  ABANDONED = "ABANDONED",
}

/** Terminal states — no further transitions allowed. */
export const TERMINAL_STATES: ReadonlySet<SignupState> = new Set([
  SignupState.ACTIVE,
  SignupState.PROVISION_FAILED,
  SignupState.ABANDONED,
]);

/** States the abandon-sweep (Task 9, Phase 1) may move to ABANDONED once expiresAt passes. */
export const ABANDONABLE_STATES: ReadonlySet<SignupState> = new Set([
  SignupState.STARTED,
  SignupState.EMAIL_VERIFIED,
]);
