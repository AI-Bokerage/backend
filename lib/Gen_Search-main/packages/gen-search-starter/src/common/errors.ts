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

export class SearchInvalidQueryError extends AppError {
  constructor(reason: string) {
    super(400, "SEARCH_INVALID_QUERY", `Invalid search query: ${reason}`);
  }
}

export class SearchQueryTooBroadError extends AppError {
  constructor() {
    super(422, "SEARCH_QUERY_TOO_BROAD", "Wildcard-only queries are rejected — provide at least one non-wildcard term");
  }
}

export class SearchTimeoutError extends AppError {
  constructor(timeoutMs: number) {
    super(504, "SEARCH_TIMEOUT", `Search exceeded the ${timeoutMs}ms timeout`);
  }
}

export class SearchIndexUnavailableError extends AppError {
  constructor(backend: string, cause?: unknown) {
    super(503, "SEARCH_INDEX_UNAVAILABLE", `Search backend "${backend}" is unavailable${cause instanceof Error ? `: ${cause.message}` : ""}`);
  }
}

export class IndexDocumentInvalidError extends AppError {
  constructor(reason: string) {
    super(400, "INDEX_DOCUMENT_INVALID", `Invalid index document: ${reason}`);
  }
}

export class ReindexAlreadyRunningError extends AppError {
  constructor(tenantId: string) {
    super(409, "REINDEX_ALREADY_RUNNING", `A reindex is already running for tenant "${tenantId}"`);
  }
}

// Boot-time configuration error — thrown by requireEnv() or createGenSearch()
// when an enabled module needs an adapter/env var that was never supplied.
// Deliberately does NOT extend AppError: never thrown during request
// handling, never reaches errorHandler, has no HTTP status code.
export class GenSearchConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenSearchConfigError";
  }
}
