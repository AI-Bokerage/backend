export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = new.target.name;
  }
}

export class TenantIdInvalidError extends AppError {
  constructor(got: string) {
    super(400, "TENANT_ID_INVALID", `tenantId must be a UUID, got "${got}"`);
  }
}

export class SlaPolicyNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "SLA_POLICY_NOT_FOUND", `SLA policy "${id}" not found`);
  }
}

export class SlaPolicyConflictError extends AppError {
  constructor(entityType: string, slaType: string) {
    super(409, "SLA_POLICY_CONFLICT", `An SLA policy for entityType=${entityType} slaType=${slaType} already exists`);
  }
}

export class SlaPolicyHasActiveInstancesError extends AppError {
  constructor(id: string, activeCount: number) {
    super(
      409,
      "SLA_POLICY_HAS_ACTIVE_INSTANCES",
      `Cannot delete policy ${id} — ${activeCount} active SLA instance(s) still reference it. Resolve or cancel them first.`,
    );
  }
}

export class SlaInvalidTimingError extends AppError {
  constructor() {
    super(400, "SLA_INVALID_TIMING", "warningMins must be less than durationMins");
  }
}

export class SlaInstanceNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "SLA_INSTANCE_NOT_FOUND", `SLA instance "${id}" not found`);
  }
}

// Boot-time configuration error — thrown by requireEnv() when createGenSla()
// needs an env var for a default adapter that was never supplied. Deliberately
// does NOT extend AppError: never thrown during request handling, never reaches
// errorHandler, has no HTTP status code.
export class GenSlaConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenSlaConfigError";
  }
}
