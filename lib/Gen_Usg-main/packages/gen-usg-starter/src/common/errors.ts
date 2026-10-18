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

export class GenUsgConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenUsgConfigError";
  }
}

export class MeterNotRegisteredError extends AppError {
  constructor(code: string) {
    super(400, "METER_NOT_REGISTERED", `Metric "${code}" was never registered via registerMeter()`);
  }
}

export class IncrementDeltaInvalidError extends AppError {
  constructor() {
    super(400, "INCREMENT_DELTA_INVALID", "delta must not be zero");
  }
}

export class UsageEventOutOfWindowError extends AppError {
  constructor(backdateDays: number) {
    super(422, "USAGE_EVENT_OUT_OF_WINDOW", `occurredAt is older than the ${backdateDays}-day backdate window`);
  }
}

export class ResourceIdRequiredError extends AppError {
  constructor(metric: string) {
    super(400, "RESOURCE_ID_REQUIRED", `Metric "${metric}" is mode:"resource" and requires resourceId on every call`);
  }
}

export class InvalidTenantIdError extends AppError {
  constructor(tenantId: string) {
    super(400, "INVALID_TENANT_ID", `Invalid tenantId format: ${tenantId}`);
  }
}
