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

// Boot-time configuration error — thrown by requireEnv() when createGenSup()
// needs an env var for a default adapter that was never supplied. Deliberately
// does NOT extend AppError: never thrown during request handling, never reaches
// errorHandler, has no HTTP status code.
export class GenSupConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenSupConfigError";
  }
}

export class TenantMetricsUnavailableError extends AppError {
  constructor(cause: unknown) {
    super(502, "TENANT_METRICS_UNAVAILABLE", `TenantMetricsPort failed: ${cause instanceof Error ? cause.message : String(cause)}`);
  }
}

export class TenantSlugTakenError extends AppError {
  constructor(slug: string) {
    super(409, "TENANT_SLUG_TAKEN", `Slug "${slug}" is already in use`);
  }
}

export class TenantNotFoundError extends AppError {
  constructor(idOrSlug: string) {
    super(404, "TENANT_NOT_FOUND", `Tenant "${idOrSlug}" not found`);
  }
}

export class TenantTransitionConflictError extends AppError {
  constructor(id: string, transition: string) {
    super(409, "TENANT_TRANSITION_CONFLICT", `Tenant "${id}" cannot ${transition} from its current status`);
  }
}

export class TntClientError extends AppError {
  constructor(message: string) {
    super(502, "TNT_CLIENT_ERROR", `Gen_TNT request failed: ${message}`);
  }
}

export class FlagNotFoundError extends AppError {
  constructor(key: string) {
    super(404, "FLAG_NOT_FOUND", `Flag "${key}" not found`);
  }
}

export class OverrideNotFoundError extends AppError {
  constructor(tenantId: string, flagKey: string) {
    super(404, "OVERRIDE_NOT_FOUND", `No override for tenant "${tenantId}" / flag "${flagKey}"`);
  }
}

export class FmmClientError extends AppError {
  constructor(message: string) {
    super(502, "FMM_CLIENT_ERROR", `Gen_FMM request failed: ${message}`);
  }
}

export class UsgClientError extends AppError {
  constructor(message: string) {
    super(502, "USG_CLIENT_ERROR", `Gen_USG request failed: ${message}`);
  }
}
