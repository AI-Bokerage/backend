# Gen_SUP feature-flags module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `feature-flags` module to Gen_SUP — a thin, `X-Internal-Secret`-gated proxy over Gen_FMM's already-live catalog-flags and overrides HTTP endpoints, following the exact hexagonal-port shape the `tenants` module established over Gen_TNT.

**Architecture:** `FmmClientPort` interface + default `HttpFmmClient` (plain `fetch`, no secret header — Gen_FMM's public catalog/override routes are genuinely unauthenticated). `FeatureFlagsService` orchestrates client calls, maps errors via an operation-gated `mapFmmError` helper, and publishes fire-and-forget audit events to the existing `RabbitMqBus`/`platform.audit` exchange. Zero local persistence — Gen_FMM owns all flag/override storage.

**Tech Stack:** TypeScript, Express, Zod, Vitest, `fetch` (no new dependencies).

## Global Constraints

- No new npm dependencies — everything needed (`fetch`, `zod`, `express`, `pino`) is already installed.
- `GEN_FMM_BASE_URL` is the only new required env var (lazy `requireEnv`, only enforced when `modules.featureFlags` is enabled and no `fmmClient` override is supplied) — no secret header needed, matching Gen_FMM's actual (unauthenticated) contract.
- `featureFlags` defaults to `true` in `GenSupModulesConfig`, same as `dashboard`/`tenants` — every pre-existing test in `create-gen-sup.test.ts` that doesn't already disable it must be audited, not just the newest one (this exact regression class was hit twice during the `tenants` module and must not repeat here).
- Reuse the existing `RabbitMqBus` / `EventPublisher` port unchanged — `tenants` and `feature-flags` share one connection when both are enabled with no override supplied.
- Follow the `tenants` module's two hard-won lessons from its own review rounds: (1) `mapFmmError` must gate 404 mappings on which *operation* ran, not merely on which context fields happen to be populated; (2) any helper that reads a response body for logging must not itself be able to throw ahead of constructing the client error (see Task 1's `safeBody` — call `res.text()` directly, never call the helper recursively).

---

### Task 1: `FmmClientPort` + `HttpFmmClient`

**Files:**
- Create: `packages/gen-sup-starter/src/domain/ports/fmm-client.port.ts`
- Create: `packages/gen-sup-starter/src/infra/external/http-fmm-client.ts`
- Test: `packages/gen-sup-starter/src/infra/external/http-fmm-client.test.ts`

**Interfaces:**
- Produces: `FmmClientPort` (methods `listFlags`, `updateFlag`, `setOverride`, `listOverridesForTenant`, `clearOverride`), `FmmFlagResponse`, `FmmFlagPatch`, `FmmOverrideResponse`, `FmmOverrideUpsertParams`, `HttpFmmClient`, `FmmHttpError` — consumed by Task 3 (`FeatureFlagsService`) and Task 5 (`create-gen-sup.ts` wiring).

- [ ] **Step 1: Write the port interface**

```ts
// packages/gen-sup-starter/src/domain/ports/fmm-client.port.ts
export interface FmmFlagResponse {
  key: string;
  moduleCode: string | null;
  defaultEnabled: boolean;
  isGradualRollout: boolean;
  rolloutPercentage: number;
  createdAt: string;
  updatedAt: string;
}

export interface FmmFlagPatch {
  moduleCode?: string | null;
  defaultEnabled?: boolean;
  isGradualRollout?: boolean;
  rolloutPercentage?: number;
}

export interface FmmOverrideResponse {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config: Record<string, unknown>;
  reason: string | null;
  expiresAt: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FmmOverrideUpsertParams {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config?: Record<string, unknown>;
  reason?: string;
  expiresAt?: string;
}

export interface FmmClientPort {
  listFlags(): Promise<FmmFlagResponse[]>;
  updateFlag(key: string, patch: FmmFlagPatch): Promise<FmmFlagResponse>;
  setOverride(params: FmmOverrideUpsertParams): Promise<FmmOverrideResponse>;
  listOverridesForTenant(tenantId: string): Promise<FmmOverrideResponse[]>;
  clearOverride(tenantId: string, flagKey: string): Promise<boolean>;
}
```

There is no `getOverride` (single-lookup) method — no route in this module calls it (`setOverride` is an upsert, `clearOverride` relies on Gen_FMM's own 404-as-false handling, not a get-then-delete pattern). Don't add it; it would be dead code.

- [ ] **Step 2: Write the failing test for `HttpFmmClient`**

```ts
// packages/gen-sup-starter/src/infra/external/http-fmm-client.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpFmmClient, FmmHttpError } from "./http-fmm-client.ts";
import { logger } from "../../common/logger.ts";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      status,
      ok: status >= 200 && status < 300,
      json: async () => body,
      text: async () => JSON.stringify(body),
    }),
  );
}

describe("HttpFmmClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("listFlags returns the flags array on 200", async () => {
    const flags = [{ key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 100, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" }];
    mockFetchOnce(200, { flags });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.listFlags()).toEqual(flags);
  });

  it("listFlags throws FmmHttpError on a non-2xx status", async () => {
    mockFetchOnce(500, { error: "boom" });
    const client = new HttpFmmClient("http://gen-fmm");
    await expect(client.listFlags()).rejects.toMatchObject({ status: 500 });
  });

  it("updateFlag returns the updated flag on 200", async () => {
    const flag = { key: "f1", moduleCode: "billing", defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 100, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-02T00:00:00Z" };
    mockFetchOnce(200, { flag });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.updateFlag("f1", { moduleCode: "billing" })).toEqual(flag);
  });

  it("updateFlag throws FmmHttpError with status 404 when the flag doesn't exist", async () => {
    mockFetchOnce(404, {});
    const client = new HttpFmmClient("http://gen-fmm");
    await expect(client.updateFlag("missing", { defaultEnabled: true })).rejects.toMatchObject({ status: 404 });
  });

  it("setOverride returns the override on 200", async () => {
    const override = { tenantId: "t1", flagKey: "f1", enabled: true, config: {}, reason: "beta", expiresAt: null, createdBy: null, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, { override });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.setOverride({ tenantId: "t1", flagKey: "f1", enabled: true, reason: "beta" })).toEqual(override);
  });

  it("listOverridesForTenant returns the overrides array on 200", async () => {
    const overrides = [{ tenantId: "t1", flagKey: "f1", enabled: true, config: {}, reason: null, expiresAt: null, createdBy: null, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" }];
    mockFetchOnce(200, { overrides });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.listOverridesForTenant("t1")).toEqual(overrides);
  });

  it("clearOverride returns false on 404 (no override existed)", async () => {
    mockFetchOnce(404, {});
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.clearOverride("t1", "f1")).toBe(false);
  });

  it("clearOverride returns true on success", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ status: 204, ok: true, text: async () => "" }));
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.clearOverride("t1", "f1")).toBe(true);
  });

  it("does not send an X-Internal-Secret header (Gen_FMM's catalog/override routes are unauthenticated)", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({ flags: [] }), text: async () => "{}" });
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpFmmClient("http://gen-fmm");
    await client.listFlags();
    const [, opts] = fetchMock.mock.calls[0];
    expect(opts?.headers).toBeUndefined();
  });

  it("logs the actual response body text on failure (not swallowed to empty)", async () => {
    mockFetchOnce(500, { error: "boom" });
    const warnSpy = vi.spyOn(logger, "warn").mockImplementation(() => undefined as never);
    const client = new HttpFmmClient("http://gen-fmm");
    await expect(client.listFlags()).rejects.toThrow(FmmHttpError);
    expect(warnSpy).toHaveBeenCalledWith(
      expect.objectContaining({ status: 500, body: JSON.stringify({ error: "boom" }) }),
      expect.any(String),
    );
    warnSpy.mockRestore();
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- http-fmm-client`
Expected: FAIL — `Cannot find module './http-fmm-client.ts'`

- [ ] **Step 4: Implement `HttpFmmClient`**

```ts
// packages/gen-sup-starter/src/infra/external/http-fmm-client.ts
import type {
  FmmClientPort,
  FmmFlagResponse,
  FmmFlagPatch,
  FmmOverrideResponse,
  FmmOverrideUpsertParams,
} from "../../domain/ports/fmm-client.port.ts";
import { logger } from "../../common/logger.ts";

export class FmmHttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "FmmHttpError";
  }
}

export class HttpFmmClient implements FmmClientPort {
  constructor(private readonly baseUrl: string) {}

  // IMPORTANT: this must call res.text() directly, never itself. The tenants
  // module's HttpTntClient once had a version of this that called
  // `this.safeBody(res)` instead of `res.text()` — infinite recursion caught
  // by its own try/catch, silently always logging an empty body.
  private async safeBody(res: Response): Promise<string> {
    try {
      return await res.text();
    } catch {
      return "";
    }
  }

  async listFlags(): Promise<FmmFlagResponse[]> {
    const res = await fetch(`${this.baseUrl}/api/v1/catalog/flags`);
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] listFlags failed");
      throw new FmmHttpError(res.status, `Gen_FMM listFlags failed: ${res.status}`);
    }
    const { flags } = (await res.json()) as { flags: FmmFlagResponse[] };
    return flags;
  }

  async updateFlag(key: string, patch: FmmFlagPatch): Promise<FmmFlagResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/catalog/flags/${encodeURIComponent(key)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    });
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] updateFlag failed");
      throw new FmmHttpError(res.status, `Gen_FMM updateFlag failed: ${res.status}`);
    }
    const { flag } = (await res.json()) as { flag: FmmFlagResponse };
    return flag;
  }

  async setOverride(params: FmmOverrideUpsertParams): Promise<FmmOverrideResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/overrides`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params),
    });
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] setOverride failed");
      throw new FmmHttpError(res.status, `Gen_FMM setOverride failed: ${res.status}`);
    }
    const { override } = (await res.json()) as { override: FmmOverrideResponse };
    return override;
  }

  async listOverridesForTenant(tenantId: string): Promise<FmmOverrideResponse[]> {
    const res = await fetch(`${this.baseUrl}/api/v1/overrides/${encodeURIComponent(tenantId)}`);
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] listOverridesForTenant failed");
      throw new FmmHttpError(res.status, `Gen_FMM listOverridesForTenant failed: ${res.status}`);
    }
    const { overrides } = (await res.json()) as { overrides: FmmOverrideResponse[] };
    return overrides;
  }

  async clearOverride(tenantId: string, flagKey: string): Promise<boolean> {
    const res = await fetch(
      `${this.baseUrl}/api/v1/overrides/${encodeURIComponent(tenantId)}/${encodeURIComponent(flagKey)}`,
      { method: "DELETE" },
    );
    if (res.status === 404) return false;
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] clearOverride failed");
      throw new FmmHttpError(res.status, `Gen_FMM clearOverride failed: ${res.status}`);
    }
    return true;
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- http-fmm-client`
Expected: PASS (10 tests)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/domain/ports/fmm-client.port.ts packages/gen-sup-starter/src/infra/external/http-fmm-client.ts packages/gen-sup-starter/src/infra/external/http-fmm-client.test.ts
git commit -m "feat: add FmmClientPort and HttpFmmClient"
```

---

### Task 2: Error classes + audit routing keys

**Files:**
- Modify: `packages/gen-sup-starter/src/common/errors.ts`
- Modify: `packages/gen-sup-starter/src/config/constants.ts`
- Test: `packages/gen-sup-starter/src/common/errors.test.ts` (create if it doesn't exist; otherwise there may be no dedicated error test file yet — check first. If `tenants`' error classes have no dedicated test file of their own, don't add one here either; their behavior is covered indirectly through `service.test.ts` in Task 3. Skip Step 1/2/4/5 below and go straight to Step 3 in that case.)

**Interfaces:**
- Produces: `FlagNotFoundError`, `OverrideNotFoundError`, `FmmClientError` (all extend `AppError`), `FLAG_ROUTING_KEYS` — consumed by Task 3.

- [ ] **Step 1: Check for an existing errors test file**

Run: `ls packages/gen-sup-starter/src/common/errors.test.ts 2>&1 || echo "no test file"`

If no test file exists (expected — the `tenants` module's error classes were verified via `service.test.ts`, not a dedicated file), skip straight to Step 3.

- [ ] **Step 2 (only if `errors.test.ts` already exists): add cases for the 3 new classes, then re-run to confirm they fail before implementing.**

- [ ] **Step 3: Add the new error classes**

Append to `packages/gen-sup-starter/src/common/errors.ts` (do not touch existing classes):

```ts
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
```

- [ ] **Step 4: Add the routing keys constant**

Append to `packages/gen-sup-starter/src/config/constants.ts` (do not touch `PLATFORM_AUDIT_EXCHANGE`/`TENANT_ROUTING_KEYS`):

```ts
export const FLAG_ROUTING_KEYS = {
  UPDATED: "sup.flag.updated",
  OVERRIDE_SET: "sup.override.set",
  OVERRIDE_CLEARED: "sup.override.cleared",
} as const;
```

- [ ] **Step 5: Run the full test suite to confirm nothing broke**

Run: `npm test --workspace=@gen-ms/gen-sup-starter`
Expected: PASS (all existing tests, no new ones yet — these classes/constants aren't consumed until Task 3)

- [ ] **Step 6: Build to confirm no type errors**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add packages/gen-sup-starter/src/common/errors.ts packages/gen-sup-starter/src/config/constants.ts
git commit -m "feat: add feature-flags error classes and audit routing keys"
```

---

### Task 3: `FeatureFlagsService`

**Files:**
- Create: `packages/gen-sup-starter/src/modules/feature-flags/v1/types.ts`
- Create: `packages/gen-sup-starter/src/modules/feature-flags/v1/service.ts`
- Test: `packages/gen-sup-starter/src/modules/feature-flags/v1/service.test.ts`

**Interfaces:**
- Consumes: `FmmClientPort`, `FmmFlagResponse`, `FmmOverrideResponse` (Task 1); `EventPublisher`, `EventEnvelope` (existing `domain/ports/event-publisher.port.ts`); `PLATFORM_AUDIT_EXCHANGE`, `FLAG_ROUTING_KEYS` (existing/Task 2); `FlagNotFoundError`, `OverrideNotFoundError`, `FmmClientError` (Task 2).
- Produces: `FeatureFlagsService` (methods `listFlags`, `updateFlag`, `setOverride`, `listOverridesForTenant`, `clearOverride`), `UpdateFlagInput`, `SetOverrideInput`, `FeatureFlagDto`, `FeatureFlagOverrideDto` — consumed by Task 4 (controller) and Task 5 (wiring).

- [ ] **Step 1: Write `types.ts`**

```ts
// packages/gen-sup-starter/src/modules/feature-flags/v1/types.ts
export type { FmmFlagResponse as FeatureFlagDto, FmmOverrideResponse as FeatureFlagOverrideDto } from "../../../domain/ports/fmm-client.port.ts";

export interface UpdateFlagInput {
  moduleCode?: string | null;
  defaultEnabled?: boolean;
  isGradualRollout?: boolean;
  rolloutPercentage?: number;
  reason: string;
}

export interface SetOverrideInput {
  enabled: boolean;
  config?: Record<string, unknown>;
  expiresAt?: string;
  reason: string;
}
```

- [ ] **Step 2: Write the failing test**

```ts
// packages/gen-sup-starter/src/modules/feature-flags/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { FeatureFlagsService } from "./service.ts";
import { FmmHttpError } from "../../../infra/external/http-fmm-client.ts";
import { FlagNotFoundError, OverrideNotFoundError, FmmClientError } from "../../../common/errors.ts";
import type { FmmClientPort, FmmFlagResponse, FmmOverrideResponse } from "../../../domain/ports/fmm-client.port.ts";
import type { EventPublisher } from "../../../domain/ports/event-publisher.port.ts";

function baseFlag(overrides: Partial<FmmFlagResponse> = {}): FmmFlagResponse {
  return {
    key: "f1",
    moduleCode: null,
    defaultEnabled: false,
    isGradualRollout: false,
    rolloutPercentage: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function baseOverride(overrides: Partial<FmmOverrideResponse> = {}): FmmOverrideResponse {
  return {
    tenantId: "t1",
    flagKey: "f1",
    enabled: true,
    config: {},
    reason: null,
    expiresAt: null,
    createdBy: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function fakeFmmClient(overrides: Partial<FmmClientPort> = {}): FmmClientPort {
  return {
    listFlags: vi.fn(async () => [baseFlag()]),
    updateFlag: vi.fn(async () => baseFlag({ defaultEnabled: true })),
    setOverride: vi.fn(async () => baseOverride()),
    listOverridesForTenant: vi.fn(async () => [baseOverride()]),
    clearOverride: vi.fn(async () => true),
    ...overrides,
  };
}

function fakeEventPublisher(overrides: Partial<EventPublisher> = {}): EventPublisher {
  return { publish: vi.fn(async () => undefined), ...overrides };
}

describe("FeatureFlagsService", () => {
  describe("listFlags", () => {
    it("returns the flags from the client", async () => {
      const service = new FeatureFlagsService(fakeFmmClient(), fakeEventPublisher());
      const result = await service.listFlags();
      expect(result).toHaveLength(1);
      expect(result[0]!.key).toBe("f1");
    });

    it("throws FmmClientError (not misreported as FlagNotFoundError) when Gen_FMM returns a 404 on list", async () => {
      const fmmClient = fakeFmmClient({ listFlags: vi.fn(async () => { throw new FmmHttpError(404, "route missing"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.listFlags()).rejects.toThrow(FmmClientError);
    });
  });

  describe("updateFlag", () => {
    it("updates the flag, does not forward reason to the client, and publishes sup.flag.updated", async () => {
      const fmmClient = fakeFmmClient();
      const eventPublisher = fakeEventPublisher();
      const service = new FeatureFlagsService(fmmClient, eventPublisher);

      const result = await service.updateFlag("f1", { defaultEnabled: true, reason: "rolling out to everyone" });

      expect(result.defaultEnabled).toBe(true);
      expect(fmmClient.updateFlag).toHaveBeenCalledWith("f1", { defaultEnabled: true });
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.flag.updated",
        expect.objectContaining({ event_type: "sup.flag.updated", data: expect.objectContaining({ flag_key: "f1", reason: "rolling out to everyone" }) }),
      );
    });

    it("throws FlagNotFoundError when Gen_FMM returns 404", async () => {
      const fmmClient = fakeFmmClient({ updateFlag: vi.fn(async () => { throw new FmmHttpError(404, "not found"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.updateFlag("missing", { defaultEnabled: true, reason: "test" })).rejects.toThrow(FlagNotFoundError);
    });

    it("throws FmmClientError (not an uncaught error) on a 5xx", async () => {
      const fmmClient = fakeFmmClient({ updateFlag: vi.fn(async () => { throw new FmmHttpError(500, "boom"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.updateFlag("f1", { defaultEnabled: true, reason: "test" })).rejects.toThrow(FmmClientError);
    });

    it("does not fail the request when audit publishing fails", async () => {
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new FeatureFlagsService(fakeFmmClient(), eventPublisher);
      await expect(service.updateFlag("f1", { defaultEnabled: true, reason: "test" })).resolves.toBeDefined();
    });
  });

  describe("setOverride", () => {
    it("sets the override and publishes sup.override.set", async () => {
      const fmmClient = fakeFmmClient();
      const eventPublisher = fakeEventPublisher();
      const service = new FeatureFlagsService(fmmClient, eventPublisher);

      const result = await service.setOverride("t1", "f1", { enabled: true, reason: "beta cohort" });

      expect(result.enabled).toBe(true);
      expect(fmmClient.setOverride).toHaveBeenCalledWith(
        expect.objectContaining({ tenantId: "t1", flagKey: "f1", enabled: true, reason: "beta cohort" }),
      );
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.override.set",
        expect.objectContaining({ tenant_id: "t1", event_type: "sup.override.set" }),
      );
    });

    it("throws FmmClientError (not TenantNotFoundError-style leakage) on a 5xx", async () => {
      const fmmClient = fakeFmmClient({ setOverride: vi.fn(async () => { throw new FmmHttpError(500, "boom"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.setOverride("t1", "f1", { enabled: true, reason: "test" })).rejects.toThrow(FmmClientError);
    });
  });

  describe("listOverridesForTenant", () => {
    it("returns the overrides from the client", async () => {
      const service = new FeatureFlagsService(fakeFmmClient(), fakeEventPublisher());
      const result = await service.listOverridesForTenant("t1");
      expect(result).toHaveLength(1);
    });
  });

  describe("clearOverride", () => {
    it("clears the override and publishes sup.override.cleared", async () => {
      const fmmClient = fakeFmmClient();
      const eventPublisher = fakeEventPublisher();
      const service = new FeatureFlagsService(fmmClient, eventPublisher);

      await service.clearOverride("t1", "f1");

      expect(fmmClient.clearOverride).toHaveBeenCalledWith("t1", "f1");
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.override.cleared",
        expect.objectContaining({ tenant_id: "t1", event_type: "sup.override.cleared" }),
      );
    });

    it("succeeds (idempotent) even when no override existed to clear", async () => {
      const fmmClient = fakeFmmClient({ clearOverride: vi.fn(async () => false) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.clearOverride("t1", "f1")).resolves.toBeUndefined();
    });

    it("throws FmmClientError on a genuine 5xx from Gen_FMM", async () => {
      const fmmClient = fakeFmmClient({ clearOverride: vi.fn(async () => { throw new FmmHttpError(500, "boom"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.clearOverride("t1", "f1")).rejects.toThrow(FmmClientError);
    });
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- feature-flags`
Expected: FAIL — `Cannot find module './service.ts'`

- [ ] **Step 4: Implement `service.ts`**

```ts
// packages/gen-sup-starter/src/modules/feature-flags/v1/service.ts
import type { FmmClientPort, FmmFlagResponse, FmmOverrideResponse } from "../../../domain/ports/fmm-client.port.ts";
import { FmmHttpError } from "../../../infra/external/http-fmm-client.ts";
import type { EventPublisher, EventEnvelope } from "../../../domain/ports/event-publisher.port.ts";
import { PLATFORM_AUDIT_EXCHANGE, FLAG_ROUTING_KEYS } from "../../../config/constants.ts";
import { FlagNotFoundError, OverrideNotFoundError, FmmClientError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type { UpdateFlagInput, SetOverrideInput } from "./types.ts";

// Flag catalog updates aren't tenant-scoped, but EventEnvelope.tenant_id is
// required — this sentinel marks platform-level (non-tenant) audit events.
const PLATFORM_TENANT_ID = "platform";

type FmmErrorOp = "list" | "updateFlag" | "setOverride" | "listOverrides" | "clearOverride";

export class FeatureFlagsService {
  constructor(
    private readonly fmmClient: FmmClientPort,
    private readonly eventPublisher: EventPublisher,
  ) {}

  // Gated on which operation ran, not on which context fields happen to be
  // populated — the tenants module's mapTntError once mapped a create() 404
  // to TENANT_NOT_FOUND just because `context.slug` was set, which was wrong
  // for that operation. Same class of bug, avoided here by keying strictly on `op`.
  private mapFmmError(err: unknown, context: { op: FmmErrorOp; key?: string }): never {
    if (err instanceof FmmHttpError) {
      if (err.status === 404 && context.op === "updateFlag") throw new FlagNotFoundError(context.key ?? "unknown");
      throw new FmmClientError(err.message);
    }
    throw err instanceof Error ? err : new Error(String(err));
  }

  private async publishSafely(routingKey: string, tenantId: string, data: Record<string, unknown>): Promise<void> {
    const envelope: EventEnvelope = {
      event_type: routingKey,
      occurred_at: new Date().toISOString(),
      tenant_id: tenantId,
      data,
    };
    try {
      await this.eventPublisher.publish(PLATFORM_AUDIT_EXCHANGE, routingKey, envelope);
    } catch (err) {
      logger.warn({ err, routingKey, tenantId }, "[FeatureFlags] Audit event publish failed — continuing");
    }
  }

  async listFlags(): Promise<FmmFlagResponse[]> {
    let flags: FmmFlagResponse[];
    try {
      flags = await this.fmmClient.listFlags();
    } catch (err) {
      this.mapFmmError(err, { op: "list" });
    }
    return flags;
  }

  async updateFlag(key: string, input: UpdateFlagInput): Promise<FmmFlagResponse> {
    const { reason, ...patch } = input;
    let flag: FmmFlagResponse;
    try {
      flag = await this.fmmClient.updateFlag(key, patch);
    } catch (err) {
      this.mapFmmError(err, { op: "updateFlag", key });
    }
    await this.publishSafely(FLAG_ROUTING_KEYS.UPDATED, PLATFORM_TENANT_ID, { flag_key: key, ...patch, reason });
    return flag;
  }

  async setOverride(tenantId: string, flagKey: string, input: SetOverrideInput): Promise<FmmOverrideResponse> {
    let override: FmmOverrideResponse;
    try {
      override = await this.fmmClient.setOverride({
        tenantId,
        flagKey,
        enabled: input.enabled,
        config: input.config,
        expiresAt: input.expiresAt,
        reason: input.reason,
      });
    } catch (err) {
      this.mapFmmError(err, { op: "setOverride" });
    }
    await this.publishSafely(FLAG_ROUTING_KEYS.OVERRIDE_SET, tenantId, { flag_key: flagKey, enabled: input.enabled, reason: input.reason });
    return override;
  }

  async listOverridesForTenant(tenantId: string): Promise<FmmOverrideResponse[]> {
    let overrides: FmmOverrideResponse[];
    try {
      overrides = await this.fmmClient.listOverridesForTenant(tenantId);
    } catch (err) {
      this.mapFmmError(err, { op: "listOverrides" });
    }
    return overrides;
  }

  async clearOverride(tenantId: string, flagKey: string): Promise<void> {
    try {
      await this.fmmClient.clearOverride(tenantId, flagKey);
    } catch (err) {
      this.mapFmmError(err, { op: "clearOverride" });
    }
    await this.publishSafely(FLAG_ROUTING_KEYS.OVERRIDE_CLEARED, tenantId, { flag_key: flagKey });
  }
}
```

Note: `clearOverride`'s `false` return (no override existed) is intentionally ignored — DELETE is idempotent, not surfaced as `OverrideNotFoundError`. `OverrideNotFoundError` is currently unused by this service; it remains exported from `errors.ts` (Task 2) for consistency with the design spec and is available if a future single-lookup route is added, but nothing in this plan throws it — this is expected, not a gap.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- feature-flags`
Expected: PASS (12 tests)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/modules/feature-flags/v1/types.ts packages/gen-sup-starter/src/modules/feature-flags/v1/service.ts packages/gen-sup-starter/src/modules/feature-flags/v1/service.test.ts
git commit -m "feat: add TenantsService-style FeatureFlagsService orchestrating Gen_FMM calls"
```

---

### Task 4: Schema, controller, router

**Files:**
- Create: `packages/gen-sup-starter/src/modules/feature-flags/v1/schema.ts`
- Create: `packages/gen-sup-starter/src/modules/feature-flags/v1/controller.ts`
- Create: `packages/gen-sup-starter/src/modules/feature-flags/v1/router.ts`

**Interfaces:**
- Consumes: `FeatureFlagsService` (Task 3), `internalSecret` middleware (existing `middleware/internal-secret.ts`).
- Produces: `createFeatureFlagsRouter(deps: { featureFlagsService, internalSecretValue })` — consumed by Task 5.

No dedicated test file for this task — matching the `tenants` module's convention, controller/router behavior is verified via the route-level smoke tests in `create-gen-sup.test.ts` (Task 5), not a standalone `controller.test.ts`/`router.test.ts`.

- [ ] **Step 1: Write `schema.ts`**

```ts
// packages/gen-sup-starter/src/modules/feature-flags/v1/schema.ts
import { z } from "zod";

export const updateFlagSchema = z.object({
  moduleCode: z.string().max(32).nullable().optional(),
  defaultEnabled: z.boolean().optional(),
  isGradualRollout: z.boolean().optional(),
  rolloutPercentage: z.number().int().min(0).max(100).optional(),
  reason: z.string().min(3).max(500),
});

export const setOverrideSchema = z.object({
  enabled: z.boolean(),
  config: z.record(z.unknown()).optional(),
  expiresAt: z.string().datetime().optional(),
  reason: z.string().min(3).max(500),
});

export const flagKeyParamSchema = z.object({
  key: z.string().min(1).max(128),
});

export const tenantIdParamSchema = z.object({
  tenantId: z.string().uuid(),
});

export const tenantIdFlagKeyParamSchema = z.object({
  tenantId: z.string().uuid(),
  flagKey: z.string().min(1).max(128),
});
```

- [ ] **Step 2: Write `controller.ts`**

```ts
// packages/gen-sup-starter/src/modules/feature-flags/v1/controller.ts
import type { Request, Response } from "express";
import {
  updateFlagSchema,
  setOverrideSchema,
  flagKeyParamSchema,
  tenantIdParamSchema,
  tenantIdFlagKeyParamSchema,
} from "./schema.ts";
import type { FeatureFlagsService } from "./service.ts";

export function makeFeatureFlagsController(service: FeatureFlagsService) {
  return {
    async listFlags(_req: Request, res: Response) {
      const flags = await service.listFlags();
      res.json({ flags });
    },
    async updateFlag(req: Request, res: Response) {
      const { key } = flagKeyParamSchema.parse(req.params);
      const input = updateFlagSchema.parse(req.body);
      const flag = await service.updateFlag(key, input);
      res.json({ flag });
    },
    async setOverride(req: Request, res: Response) {
      const { tenantId, flagKey } = tenantIdFlagKeyParamSchema.parse(req.params);
      const input = setOverrideSchema.parse(req.body);
      const override = await service.setOverride(tenantId, flagKey, input);
      res.json({ override });
    },
    async listOverridesForTenant(req: Request, res: Response) {
      const { tenantId } = tenantIdParamSchema.parse(req.params);
      const overrides = await service.listOverridesForTenant(tenantId);
      res.json({ overrides });
    },
    async clearOverride(req: Request, res: Response) {
      const { tenantId, flagKey } = tenantIdFlagKeyParamSchema.parse(req.params);
      await service.clearOverride(tenantId, flagKey);
      res.status(204).send();
    },
  };
}
```

- [ ] **Step 3: Write `router.ts`**

```ts
// packages/gen-sup-starter/src/modules/feature-flags/v1/router.ts
import { Router } from "express";
import { makeFeatureFlagsController } from "./controller.ts";
import type { FeatureFlagsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface FeatureFlagsRouterDeps {
  featureFlagsService: FeatureFlagsService;
  internalSecretValue: string;
}

export function createFeatureFlagsRouter(deps: FeatureFlagsRouterDeps): Router {
  const router = Router();
  const controller = makeFeatureFlagsController(deps.featureFlagsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.get("/", (req, res, next) => controller.listFlags(req, res).catch(next));
  router.patch("/:key", (req, res, next) => controller.updateFlag(req, res).catch(next));
  router.put("/overrides/:tenantId/:flagKey", (req, res, next) => controller.setOverride(req, res).catch(next));
  router.get("/overrides/:tenantId", (req, res, next) => controller.listOverridesForTenant(req, res).catch(next));
  router.delete("/overrides/:tenantId/:flagKey", (req, res, next) => controller.clearOverride(req, res).catch(next));

  return router;
}
```

- [ ] **Step 4: Build to confirm no type errors (these files aren't wired in yet, so no runtime test applies)**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add packages/gen-sup-starter/src/modules/feature-flags/v1/schema.ts packages/gen-sup-starter/src/modules/feature-flags/v1/controller.ts packages/gen-sup-starter/src/modules/feature-flags/v1/router.ts
git commit -m "feat: add feature-flags controller, router, and schema"
```

---

### Task 5: Wire into `createGenSup` and the public barrel

**Files:**
- Modify: `packages/gen-sup-starter/src/create-gen-sup.ts`
- Modify: `packages/gen-sup-starter/src/index.ts`
- Modify: `.env.example`
- Modify: `packages/gen-sup-starter/src/create-gen-sup.test.ts`
- Modify: `packages/gen-sup-demo/src/index.ts`

**Interfaces:**
- Consumes: everything from Tasks 1-4.
- Produces: `GenSupConfig.fmmClient`/`fmmBaseUrl`, `GenSupModulesConfig.featureFlags` — the module is now live end-to-end.

- [ ] **Step 1: Add the env var**

Append to `.env.example`:

```
GEN_FMM_BASE_URL=http://localhost:3700
```

- [ ] **Step 2: Modify `create-gen-sup.ts`**

Add these imports alongside the existing tenants-related ones:

```ts
import type { FmmClientPort } from "./domain/ports/fmm-client.port.ts";
import { HttpFmmClient } from "./infra/external/http-fmm-client.ts";
import { FeatureFlagsService } from "./modules/feature-flags/v1/service.ts";
import { createFeatureFlagsRouter } from "./modules/feature-flags/v1/router.ts";
```

Extend `GenSupModulesConfig`:

```ts
export interface GenSupModulesConfig {
  dashboard?: boolean;
  tenants?: boolean;
  featureFlags?: boolean;
}
```

Extend `GenSupConfig`:

```ts
export interface GenSupConfig {
  tenantMetricsPort?: TenantMetricsPort;
  valkeyUrl?: string;
  internalSecret?: string;
  tntClient?: TntClientPort;
  tntBaseUrl?: string;
  tntInternalSecret?: string;
  fmmClient?: FmmClientPort;
  fmmBaseUrl?: string;
  eventPublisher?: EventPublisher;
  rabbitMqUrl?: string;
  modules?: GenSupModulesConfig;
}
```

Add `resolveFmmClient` alongside `resolveTntClient`:

```ts
function resolveFmmClient(override: FmmClientPort | undefined, baseUrlOverride: string | undefined): FmmClientPort {
  if (override) return override;
  const baseUrl = baseUrlOverride ?? requireEnv("GEN_FMM_BASE_URL");
  return new HttpFmmClient(baseUrl);
}
```

In `createGenSup`, add `featureFlags` to the resolved `modules` object:

```ts
  const modules: Required<GenSupModulesConfig> = {
    dashboard: config.modules?.dashboard ?? true,
    tenants: config.modules?.tenants ?? true,
    featureFlags: config.modules?.featureFlags ?? true,
  };
```

Replace the existing inline `eventPublisher` resolution inside the `if (modules.tenants)` block with a shared helper, so `tenants` and `feature-flags` reuse one `RabbitMqBus` connection when both are enabled with no override supplied. Replace this existing block:

```ts
  let eventPublisher: RabbitMqBus | undefined;
  if (modules.tenants) {
    const tntClient = resolveTntClient(config.tntClient, config.tntBaseUrl, config.tntInternalSecret);

    let publisher: EventPublisher;
    if (config.eventPublisher) {
      publisher = config.eventPublisher;
    } else {
      const bus = new RabbitMqBus(config.rabbitMqUrl ?? requireEnv("RABBITMQ_URL"));
      eventPublisher = bus;
      publisher = bus;
    }

    const tenantsService = new TenantsService(tntClient, publisher);
    app.use("/api/v1/tenants", createTenantsRouter({ tenantsService, internalSecretValue }));
  }
```

with:

```ts
  let eventPublisher: RabbitMqBus | undefined;
  function resolvePublisher(): EventPublisher {
    if (config.eventPublisher) return config.eventPublisher;
    if (eventPublisher) return eventPublisher;
    const bus = new RabbitMqBus(config.rabbitMqUrl ?? requireEnv("RABBITMQ_URL"));
    eventPublisher = bus;
    return bus;
  }

  if (modules.tenants) {
    const tntClient = resolveTntClient(config.tntClient, config.tntBaseUrl, config.tntInternalSecret);
    const publisher = resolvePublisher();
    const tenantsService = new TenantsService(tntClient, publisher);
    app.use("/api/v1/tenants", createTenantsRouter({ tenantsService, internalSecretValue }));
  }

  if (modules.featureFlags) {
    const fmmClient = resolveFmmClient(config.fmmClient, config.fmmBaseUrl);
    const publisher = resolvePublisher();
    const featureFlagsService = new FeatureFlagsService(fmmClient, publisher);
    app.use("/api/v1/feature-flags", createFeatureFlagsRouter({ featureFlagsService, internalSecretValue }));
  }
```

Update the `GenSupInstance.eventPublisher` doc comment to mention both modules:

```ts
  // Present only when at least one of tenants/featureFlags is enabled and no
  // eventPublisher override was supplied (an override is the caller's own
  // resource to manage). Exposed so a host can close the RabbitMQ connection
  // on shutdown, e.g. `instance.eventPublisher?.close()`.
  eventPublisher?: RabbitMqBus;
```

- [ ] **Step 3: Update `index.ts` barrel**

Add:

```ts
export type { FmmClientPort, FmmFlagResponse, FmmFlagPatch, FmmOverrideResponse, FmmOverrideUpsertParams } from "./domain/ports/fmm-client.port.ts";
export { HttpFmmClient, FmmHttpError } from "./infra/external/http-fmm-client.ts";
export { FLAG_ROUTING_KEYS } from "./config/constants.ts";
export type { FeatureFlagDto, FeatureFlagOverrideDto, UpdateFlagInput, SetOverrideInput } from "./modules/feature-flags/v1/types.ts";
export { FeatureFlagsService } from "./modules/feature-flags/v1/service.ts";
```

And extend the existing errors export block to include the 3 new classes:

```ts
export {
  AppError,
  GenSupConfigError,
  TenantMetricsUnavailableError,
  TenantSlugTakenError,
  TenantNotFoundError,
  TenantTransitionConflictError,
  TntClientError,
  FlagNotFoundError,
  OverrideNotFoundError,
  FmmClientError,
} from "./common/errors.ts";
```

- [ ] **Step 4: Audit every pre-existing test in `create-gen-sup.test.ts`**

Read the current file's 5 tests. Every one that constructs `createGenSup(...)` without disabling `featureFlags` will now fail (missing `GEN_FMM_BASE_URL`, no `fmmClient` override), *except* the first (`throws GenSupConfigError...`, which fails before any module resolution runs). Update the other 4:

1. `"exposes GET /api/v1/dashboard/kpis..."` — change `modules: { tenants: false }` to `modules: { tenants: false, featureFlags: false }`.
2. `"exposes GET /health...with every optional module disabled"` — change `modules: { dashboard: false, tenants: false }` to `modules: { dashboard: false, tenants: false, featureFlags: false }`. Update its comment to mention `GEN_FMM_BASE_URL` alongside the other env vars it's avoiding.
3. `"exposes POST /api/v1/tenants..."` — change `modules: { dashboard: false }` to `modules: { dashboard: false, featureFlags: false }`.
4. `"returns 400 VALIDATION_ERROR...:id path param"` — change `modules: { dashboard: false }` to `modules: { dashboard: false, featureFlags: false }`.

- [ ] **Step 5: Add new tests for the feature-flags module**

Append to `create-gen-sup.test.ts`:

```ts
  it("exposes GET /api/v1/feature-flags gated on X-Internal-Secret when an fmmClient is supplied", async () => {
    const fmmClient = {
      listFlags: async () => [{ key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 100, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" }],
      updateFlag: async () => { throw new Error("not used in this test"); },
      setOverride: async () => { throw new Error("not used in this test"); },
      listOverridesForTenant: async () => { throw new Error("not used in this test"); },
      clearOverride: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      fmmClient,
      eventPublisher,
      modules: { dashboard: false, tenants: false },
    });

    const unauthorized = await request(app).get("/api/v1/feature-flags");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/feature-flags").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.flags).toHaveLength(1);
  });

  it("returns 400 VALIDATION_ERROR (not a 500) when PATCH /api/v1/feature-flags/:key is missing reason", async () => {
    const fmmClient = {
      listFlags: async () => { throw new Error("not used in this test"); },
      updateFlag: async () => { throw new Error("not used in this test"); },
      setOverride: async () => { throw new Error("not used in this test"); },
      listOverridesForTenant: async () => { throw new Error("not used in this test"); },
      clearOverride: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      fmmClient,
      eventPublisher,
      modules: { dashboard: false, tenants: false },
    });

    const res = await request(app)
      .patch("/api/v1/feature-flags/f1")
      .set("X-Internal-Secret", "s3cret")
      .send({ defaultEnabled: true });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });
```

- [ ] **Step 6: Run the full test suite**

Run: `npm test --workspace=@gen-ms/gen-sup-starter`
Expected: PASS (all tests, including the 2 new ones and the 4 updated ones)

- [ ] **Step 7: Build**

Run: `npm run build`
Expected: PASS (both `gen-sup-starter` and `gen-sup-demo` workspaces)

- [ ] **Step 8: Update `gen-sup-demo`**

Read `packages/gen-sup-demo/src/index.ts`. It currently calls `createGenSup({ tenantMetricsPort: sampleTenantMetricsPort, modules: { tenants: false } })` (or similar — check the current state first, since Task 7 of the `tenants` plan already added a `tenants: false` override here). Add `featureFlags: false` to the same `modules` object, since the demo doesn't wire an `fmmClient`:

```ts
export function startDemo() {
  const { app } = createGenSup({ tenantMetricsPort: sampleTenantMetricsPort, modules: { tenants: false, featureFlags: false } });
  const server = app.listen(PORT, () => {
    console.log(`Gen_SUP demo listening on port ${PORT}`);
  });
  return { app, server };
}
```

- [ ] **Step 9: Run the full test suite and build once more to confirm the demo change didn't break anything**

Run: `npm test && npm run build`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add packages/gen-sup-starter/src/create-gen-sup.ts packages/gen-sup-starter/src/index.ts .env.example packages/gen-sup-starter/src/create-gen-sup.test.ts packages/gen-sup-demo/src/index.ts
git commit -m "feat: wire feature-flags module into createGenSup and public barrel"
```

---

### Task 6: Documentation

**Files:**
- Modify: `docs/integration-guide.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing (docs only).
- Produces: nothing consumed by later tasks — this is the final task.

- [ ] **Step 1: Add a "Feature Flags" API reference section to `docs/integration-guide.md`**

Insert after the existing "Tenants" section, before "## Error codes":

```markdown
### Feature flags — `/api/v1/feature-flags`

Thin proxy over Gen_FMM's catalog-flags and overrides endpoints — Gen_SUP does
not store flag state itself. Requires `GEN_FMM_BASE_URL` (no secret header —
Gen_FMM's `/api/v1/catalog/*` and `/api/v1/overrides*` routes have no
authentication of their own).

```
GET /api/v1/feature-flags
X-Internal-Secret: <secret>
```

→ `200`, `{ "flags": [...] }` — the full Gen_FMM flag catalog.

```
PATCH /api/v1/feature-flags/:key
X-Internal-Secret: <secret>
Content-Type: application/json

{ "defaultEnabled": true, "reason": "rolling out to all plans" }
```

→ `200` with the updated flag. `reason` (3-500 chars) is **required** and
Gen_SUP-only — Gen_FMM's own update schema has no such field, so it's never
forwarded; it exists purely for the `sup.flag.updated` audit event.
`404 FLAG_NOT_FOUND` if the key doesn't exist.

```
PUT /api/v1/feature-flags/overrides/:tenantId/:flagKey
X-Internal-Secret: <secret>
Content-Type: application/json

{ "enabled": true, "expiresAt": "2026-09-01T00:00:00Z", "reason": "beta cohort" }
```

→ `200` with the override (upsert — always succeeds, no 404 case). `reason`
is required at the Gen_SUP layer and, unlike the flag-update reason, **is**
forwarded into Gen_FMM's own optional `reason` field, since Gen_FMM persists
it on the override row.

```
GET /api/v1/feature-flags/overrides/:tenantId
```

→ `200`, `{ "overrides": [...] }` — every override for that tenant.

```
DELETE /api/v1/feature-flags/overrides/:tenantId/:flagKey
```

→ `204`. Idempotent — clearing a non-existent override still returns `204`,
not a `404`.

Every update/set/clear publishes a fire-and-forget audit event to the
`platform.audit` exchange (`sup.flag.updated`/`sup.override.set`/
`sup.override.cleared`) — publish failures are logged and do not fail the
request.
```

- [ ] **Step 2: Update the Error codes table**

Add 3 rows to the existing table in `docs/integration-guide.md`:

```markdown
| `FLAG_NOT_FOUND` | 404 | No flag with this key (Gen_FMM catalog) |
| `OVERRIDE_NOT_FOUND` | 404 | Reserved — not currently thrown by any route in this module |
| `FMM_CLIENT_ERROR` | 502 | Gen_FMM returned an unexpected error |
```

- [ ] **Step 3: Update Known limitations**

In the existing "Known limitations" section, update the module list and add the auth-gap note:

```markdown
- `dashboard`, `tenants`, and `feature-flags` are the only modules built so
  far. `announcements`, `analytics`, `tickets`, and `impersonate` are planned.
- Gen_FMM's `/api/v1/catalog/*` and `/api/v1/overrides*` endpoints have no
  authentication of their own (Gen_FMM's own documented limitation — "the
  host fronts every route with its own auth"). Gen_SUP's `X-Internal-Secret`
  gate protects requests that go through Gen_SUP, but cannot prevent direct
  network access to Gen_FMM bypassing Gen_SUP entirely. This is Gen_FMM's
  deployment-topology responsibility, not fixable from Gen_SUP's side.
```

- [ ] **Step 4: Add an "Upgrading" note**

In the same breaking-change-callout style already used for the `tenants` module's `true` default, add a note for `featureFlags` defaulting to `true`:

```markdown
`featureFlags` also now defaults to `true` — an existing embedder must pass
`modules: { featureFlags: false }` or supply `GEN_FMM_BASE_URL` (or an
`fmmClient` override) before upgrading, the same as the `tenants` module's
own breaking-change note above.
```

- [ ] **Step 5: Update the local-development docker-compose comment reference if it mentions the module list**

Check `docs/integration-guide.md`'s "Local development" section — if it lists modules by name anywhere, add `feature-flags`. No `docker-compose.yml` change is needed (Gen_FMM runs as its own separate service outside this repo's compose file).

- [ ] **Step 6: Update `README.md`**

Update the Gen_SUP status line to mention all three modules (`dashboard`, `tenants`, `feature-flags`).

- [ ] **Step 7: Run the full test suite and build once more (docs shouldn't break anything, but confirm)**

Run: `npm test && npm run build`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add docs/integration-guide.md README.md
git commit -m "docs: document the feature-flags module in the integration guide and README"
```

---

## Plan Self-Review Notes

- **Spec coverage:** All 5 in-scope operations (list flags, update flag, set override, list overrides for tenant, clear override) have a port method, service method, route, and test. The out-of-scope items (create/delete flags, plan-module management, resolution/entitlement calls) are explicitly not implemented anywhere in this plan.
- **Scope correction from the approved design:** the design's method list included a `getOverride` (single-lookup) port method, but no route in the approved route list ever calls it. Since nothing in this plan needs it, it's dropped entirely (Task 1) rather than implemented as dead code — a YAGNI cut, not a functional scope change. `OverrideNotFoundError` (Task 2) is kept since it's cheap and future-facing per the design, but Task 3 explicitly notes it's currently unused.
- **Type consistency:** `FmmFlagResponse`/`FmmOverrideResponse` (Task 1) flow unchanged through `FeatureFlagDto`/`FeatureFlagOverrideDto` (Task 3, re-exported not redefined) into the controller (Task 4) — checked no field-name drift across tasks.
- **Regression-class carryover:** Task 5 explicitly walks all 5 pre-existing `create-gen-sup.test.ts` tests one at a time (not just "the newest one") — this exact class of bug was missed once by the plan and once more by an implementer during the `tenants` module.
