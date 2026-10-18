# Gen_SUP Tenants Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `tenants` module to `@gen-ms/gen-sup-starter` — `create`/`suspend`/`reactivate`/`getById`/`getBySlug` orchestrated over Gen_TNT's real HTTP API, with RabbitMQ audit events, following the same "thin wrapper, no local state" shape as the already-shipped `dashboard` module.

**Architecture:** A `TntClientPort` (real `HttpTntClient` implementation, `fetch`-based, mirrors Gen_REG's proven client) talks to Gen_TNT over HTTP. An `EventPublisher` port (real `RabbitMqBus` implementation, mirrors Gen_SLA's proven bus, publish-only — no consume, no outbox, since this module has no local DB writes to be transactional with) publishes fire-and-forget audit events after each successful Gen_TNT call. `TenantsService` orchestrates both and maps Gen_TNT's HTTP status codes to typed `AppError`s.

**Tech Stack:** TypeScript, Express, Zod, `amqplib` (new), same `X-Internal-Secret` auth gate as `dashboard`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-10-gen-sup-tenants-design.md` — every requirement below traces back to it.
- No local Prisma table for this module — same "thin wrapper" shape as `dashboard`.
- In scope: `create`, `suspend`, `reactivate`, `getById`, `getBySlug`. Out of scope: `list`, `cancel`, `purge`.
- New env vars, all lazy via `requireEnv` (NOT in the Zod `EnvSchema`, only required when the `tenants` module is enabled and no override is supplied): `GEN_TNT_BASE_URL`, `GEN_TNT_INTERNAL_SECRET`, `RABBITMQ_URL`.
- Ports: RabbitMQ AMQP `5676`, management UI `15676` (next free after Gen_SEARCH's `5675`/`15675` — verified against every sibling).
- `region` is a free string (no enum) — Gen_TNT itself does zero region validation.
- `primaryOwnerUserId` is generated server-side via `randomUUID()` — Gen_TNT requires it but does not generate it itself.
- Gen_TNT's `suspend`/`reactivate` endpoints take no request body — `reason`/`note` are Gen_SUP-only metadata, embedded only in the audit event, never sent to Gen_TNT.
- Audit-publish failure is non-fatal: caught, logged, does not fail the request (Gen_TNT's call already succeeded).
- Every route is gated on `X-Internal-Secret` (same middleware as `dashboard`, `internalSecret` from `src/middleware/internal-secret.ts`).
- Exchange name `platform.audit` (topic), routing keys `sup.tenant.created`/`sup.tenant.suspended`/`sup.tenant.reactivated`.

---

### Task 1: RabbitMQ infra — event publisher port and bus

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `packages/gen-sup-starter/package.json`
- Create: `packages/gen-sup-starter/src/config/constants.ts`
- Create: `packages/gen-sup-starter/src/domain/ports/event-publisher.port.ts`
- Create: `packages/gen-sup-starter/src/infra/messaging/rabbitmq-bus.ts`

**Interfaces:**
- Produces: `EventEnvelope` type, `EventPublisher` interface (`publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void>`) from `event-publisher.port.ts` — consumed by Task 3 (`TenantsService`) and Task 5 (`create-gen-sup.ts`).
- Produces: `RabbitMqBus implements EventPublisher` from `rabbitmq-bus.ts` — consumed by Task 5.
- Produces: `PLATFORM_AUDIT_EXCHANGE`, `TENANT_ROUTING_KEYS` from `constants.ts` — consumed by Task 3.

No dedicated test file for `rabbitmq-bus.ts` in this task — it's a thin `amqplib` wrapper, matching Gen_SLA's own convention of not unit-testing this class directly (its behavior is exercised indirectly via `TenantsService`'s tests in Task 3, which mock the `EventPublisher` interface rather than the bus itself).

- [ ] **Step 1: Add the `rabbitmq` service to `docker-compose.yml`**

Current file:
```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: gensup
      POSTGRES_PASSWORD: postgres
    ports:
      - "5443:5432"   # next free after Gen_SEARCH (5442) — verified against every sibling
    volumes:
      - gensup_postgres_data:/var/lib/postgresql/data

  valkey:
    image: valkey/valkey:8-alpine
    ports:
      - "6387:6379"   # next free after Gen_SEARCH (6386)

volumes:
  gensup_postgres_data:
```

Replace with:
```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: gensup
      POSTGRES_PASSWORD: postgres
    ports:
      - "5443:5432"   # next free after Gen_SEARCH (5442) — verified against every sibling
    volumes:
      - gensup_postgres_data:/var/lib/postgresql/data

  valkey:
    image: valkey/valkey:8-alpine
    ports:
      - "6387:6379"   # next free after Gen_SEARCH (6386)

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5676:5672"    # AMQP — next free after Gen_SEARCH (5675)
      - "15676:15672"  # management UI — http://localhost:15676 (guest/guest)

volumes:
  gensup_postgres_data:
```

- [ ] **Step 2: Add `RABBITMQ_URL` to `.env.example`**

Current file:
```
DATABASE_URL=postgresql://postgres:postgres@localhost:5443/gensup
GEN_SUP_INTERNAL_SECRET=demo-secret
VALKEY_URL=redis://localhost:6387
PORT=3900
```

Replace with:
```
DATABASE_URL=postgresql://postgres:postgres@localhost:5443/gensup
GEN_SUP_INTERNAL_SECRET=demo-secret
VALKEY_URL=redis://localhost:6387
RABBITMQ_URL=amqp://localhost:5676
GEN_TNT_BASE_URL=http://localhost:8201
GEN_TNT_INTERNAL_SECRET=dev-secret
PORT=3900
```

- [ ] **Step 3: Add `amqplib` to `packages/gen-sup-starter/package.json`**

In `dependencies`, add (alongside the existing entries, order doesn't matter):
```json
"amqplib": "^0.10.0",
```

In `devDependencies`, add:
```json
"@types/amqplib": "^0.10.0",
```

- [ ] **Step 4: Write `src/config/constants.ts`**

```ts
export const PLATFORM_AUDIT_EXCHANGE = "platform.audit";

export const TENANT_ROUTING_KEYS = {
  CREATED: "sup.tenant.created",
  SUSPENDED: "sup.tenant.suspended",
  REACTIVATED: "sup.tenant.reactivated",
} as const;
```

- [ ] **Step 5: Write `src/domain/ports/event-publisher.port.ts`**

```ts
export interface EventEnvelope {
  event_id?: string;
  event_type: string;
  event_version?: number;
  occurred_at: string;
  tenant_id: string;
  data: Record<string, unknown>;
}

export interface EventPublisher {
  publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void>;
}
```

- [ ] **Step 6: Write `src/infra/messaging/rabbitmq-bus.ts`**

```ts
import amqp, { type ChannelModel, type Channel } from "amqplib";
import type { EventPublisher, EventEnvelope } from "../../domain/ports/event-publisher.port.ts";
import { logger } from "../../common/logger.ts";

export class RabbitMqBus implements EventPublisher {
  private connection: ChannelModel | undefined;
  private channel: Channel | undefined;

  constructor(private readonly url: string) {}

  async connect(): Promise<void> {
    if (this.channel) return;
    const connection = await amqp.connect(this.url);
    connection.on("error", (err) => logger.error({ err }, "[rabbitmq-bus] connection error"));
    connection.on("close", () => {
      logger.warn("[rabbitmq-bus] connection closed");
      this.connection = undefined;
      this.channel = undefined;
    });

    const channel = await connection.createChannel();
    channel.on("error", (err) => logger.error({ err }, "[rabbitmq-bus] channel error"));
    channel.on("close", () => {
      logger.warn("[rabbitmq-bus] channel closed");
      this.channel = undefined;
    });

    this.connection = connection;
    this.channel = channel;
  }

  private requireChannel(): Channel {
    if (!this.channel) throw new Error("RabbitMqBus.connect() must be called before use");
    return this.channel;
  }

  async publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void> {
    if (!this.channel) {
      try {
        await this.connect();
      } catch (err) {
        logger.error({ err }, "[rabbitmq-bus] reconnect attempt before publish failed");
      }
    }
    const channel = this.requireChannel();
    await channel.assertExchange(exchange, "topic", { durable: true });
    channel.publish(exchange, routingKey, Buffer.from(JSON.stringify(envelope)), {
      contentType: "application/json",
      persistent: true,
    });
  }

  async close(): Promise<void> {
    await this.channel?.close();
    await this.connection?.close();
    this.channel = undefined;
    this.connection = undefined;
  }
}
```

- [ ] **Step 7: Run `npm install` and confirm the workspace still resolves**

Run: `npm install`
Expected: completes without error, `amqplib` present under `node_modules`.

- [ ] **Step 8: Confirm the package still builds**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: succeeds (these new files compile cleanly; nothing wires them up yet).

- [ ] **Step 9: Commit**

```bash
git add docker-compose.yml .env.example packages/gen-sup-starter/package.json package-lock.json packages/gen-sup-starter/src/config/constants.ts packages/gen-sup-starter/src/domain/ports/event-publisher.port.ts packages/gen-sup-starter/src/infra/messaging/rabbitmq-bus.ts
git commit -m "feat: add RabbitMQ bus, event publisher port, and platform-audit constants"
```

(If `package-lock.json` is gitignored in this repo, as it was for the initial scaffold, omit it from the `git add` — check `git status` first; don't fight an established `.gitignore` convention.)

---

### Task 2: Gen_TNT client and its error vocabulary

**Files:**
- Create: `packages/gen-sup-starter/src/domain/ports/tnt-client.port.ts`
- Create: `packages/gen-sup-starter/src/infra/external/http-tnt-client.ts`
- Create: `packages/gen-sup-starter/src/infra/external/http-tnt-client.test.ts`
- Modify: `packages/gen-sup-starter/src/common/errors.ts`

**Interfaces:**
- Consumes: `AppError` from `common/errors.ts` (already exists).
- Produces: `TntTenantResponse`, `CreateTenantParams`, `TntClientPort` from `tnt-client.port.ts` — consumed by Task 3 (`TenantsService`) and Task 5 (`create-gen-sup.ts`).
- Produces: `HttpTntClient implements TntClientPort`, `TntHttpError` (internal transport-error class, carries `.status: number`) from `http-tnt-client.ts` — consumed by Task 3 and Task 5.
- Produces: `TenantSlugTakenError`, `TenantNotFoundError`, `TenantTransitionConflictError`, `TntClientError` (all `extends AppError`) from `common/errors.ts` — consumed by Task 3.

- [ ] **Step 1: Write `src/domain/ports/tnt-client.port.ts`**

```ts
export interface TntTenantResponse {
  id: string;
  slug: string;
  name: string;
  status: string; // PROVISIONING | ACTIVE | SUSPENDED | CANCELLED | PURGED
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

export interface CreateTenantParams {
  name: string;
  slug: string;
  region: string;
  primaryOwnerUserId: string;
  idempotencyKey?: string;
}

export interface TntClientPort {
  createTenant(params: CreateTenantParams): Promise<TntTenantResponse>;
  getTenant(id: string): Promise<TntTenantResponse | null>;
  getTenantBySlug(slug: string): Promise<TntTenantResponse | null>;
  suspendTenant(id: string): Promise<TntTenantResponse>;
  reactivateTenant(id: string): Promise<TntTenantResponse>;
}
```

- [ ] **Step 2: Add the new error classes to `src/common/errors.ts`**

Current file ends with `TenantMetricsUnavailableError`. Append:

```ts

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
```

- [ ] **Step 3: Write the failing test `src/infra/external/http-tnt-client.test.ts`**

```ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpTntClient, TntHttpError } from "./http-tnt-client.ts";

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

describe("HttpTntClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("createTenant returns the tenant on 202", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "PROVISIONING", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: "j1", createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(202, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    const result = await client.createTenant({ name: "Acme", slug: "acme", region: "us-east-1", primaryOwnerUserId: "u1" });
    expect(result).toEqual(tenant);
  });

  it("createTenant throws TntHttpError with status 409 on a duplicate slug", async () => {
    mockFetchOnce(409, { error: "duplicate_slug" });
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(
      client.createTenant({ name: "Acme", slug: "acme", region: "us-east-1", primaryOwnerUserId: "u1" }),
    ).rejects.toMatchObject({ status: 409 });
  });

  it("getTenant returns null on 404", async () => {
    mockFetchOnce(404, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.getTenant("missing")).toBeNull();
  });

  it("getTenant returns the tenant on 200", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "ACTIVE", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: null, createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.getTenant("t1")).toEqual(tenant);
  });

  it("getTenantBySlug returns null on 404", async () => {
    mockFetchOnce(404, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.getTenantBySlug("free-slug")).toBeNull();
  });

  it("suspendTenant returns the tenant on 200", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "SUSPENDED", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: null, createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.suspendTenant("t1")).toEqual(tenant);
  });

  it("suspendTenant throws TntHttpError with status 404 when the tenant doesn't exist", async () => {
    mockFetchOnce(404, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(client.suspendTenant("missing")).rejects.toMatchObject({ status: 404 });
  });

  it("suspendTenant throws TntHttpError with status 409 on an illegal transition", async () => {
    mockFetchOnce(409, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(client.suspendTenant("t1")).rejects.toMatchObject({ status: 409 });
  });

  it("reactivateTenant returns the tenant on 200", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "ACTIVE", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: null, createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.reactivateTenant("t1")).toEqual(tenant);
  });

  it("sends X-Internal-Secret on every request", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({}), text: async () => "{}" });
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpTntClient("http://gen-tnt", "s3cret");
    await client.getTenant("t1");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gen-tnt/api/v1/tenants/t1",
      expect.objectContaining({ headers: expect.objectContaining({ "X-Internal-Secret": "s3cret" }) }),
    );
  });
});
```

- [ ] **Step 4: Run the test, verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- http-tnt-client`
Expected: FAIL — `./http-tnt-client.ts` does not exist yet.

- [ ] **Step 5: Write `src/infra/external/http-tnt-client.ts`**

```ts
import type { TntClientPort, TntTenantResponse, CreateTenantParams } from "../../domain/ports/tnt-client.port.ts";

export class TntHttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "TntHttpError";
  }
}

export class HttpTntClient implements TntClientPort {
  constructor(
    private readonly baseUrl: string,
    private readonly internalSecret: string,
  ) {}

  private headers(): Record<string, string> {
    return { "X-Internal-Secret": this.internalSecret, "Content-Type": "application/json" };
  }

  async createTenant(params: CreateTenantParams): Promise<TntTenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify(params),
    });
    if (res.status !== 202) {
      throw new TntHttpError(res.status, `Gen_TNT createTenant failed: ${res.status} ${await res.text()}`);
    }
    return res.json() as Promise<TntTenantResponse>;
  }

  async getTenant(id: string): Promise<TntTenantResponse | null> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${encodeURIComponent(id)}`, { headers: this.headers() });
    if (res.status === 404) return null;
    if (!res.ok) throw new TntHttpError(res.status, `Gen_TNT getTenant failed: ${res.status}`);
    return res.json() as Promise<TntTenantResponse>;
  }

  async getTenantBySlug(slug: string): Promise<TntTenantResponse | null> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/by-slug/${encodeURIComponent(slug)}`, { headers: this.headers() });
    if (res.status === 404) return null;
    if (!res.ok) throw new TntHttpError(res.status, `Gen_TNT getTenantBySlug failed: ${res.status}`);
    return res.json() as Promise<TntTenantResponse>;
  }

  async suspendTenant(id: string): Promise<TntTenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${encodeURIComponent(id)}/suspend`, {
      method: "PATCH",
      headers: this.headers(),
    });
    if (!res.ok) throw new TntHttpError(res.status, `Gen_TNT suspendTenant failed: ${res.status}`);
    return res.json() as Promise<TntTenantResponse>;
  }

  async reactivateTenant(id: string): Promise<TntTenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${encodeURIComponent(id)}/reactivate`, {
      method: "PATCH",
      headers: this.headers(),
    });
    if (!res.ok) throw new TntHttpError(res.status, `Gen_TNT reactivateTenant failed: ${res.status}`);
    return res.json() as Promise<TntTenantResponse>;
  }
}
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- http-tnt-client`
Expected: 10 passed.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-sup-starter/src/domain/ports/tnt-client.port.ts packages/gen-sup-starter/src/infra/external/http-tnt-client.ts packages/gen-sup-starter/src/infra/external/http-tnt-client.test.ts packages/gen-sup-starter/src/common/errors.ts
git commit -m "feat: add Gen_TNT HTTP client and tenant error classes"
```

---

### Task 3: TenantsService

**Files:**
- Create: `packages/gen-sup-starter/src/modules/tenants/v1/types.ts`
- Create: `packages/gen-sup-starter/src/modules/tenants/v1/service.ts`
- Create: `packages/gen-sup-starter/src/modules/tenants/v1/service.test.ts`

**Interfaces:**
- Consumes: `TntClientPort`, `TntTenantResponse`, `CreateTenantParams` (Task 2); `TntHttpError` (Task 2); `EventPublisher`, `EventEnvelope` (Task 1); `PLATFORM_AUDIT_EXCHANGE`, `TENANT_ROUTING_KEYS` (Task 1); `TenantSlugTakenError`, `TenantNotFoundError`, `TenantTransitionConflictError`, `TntClientError` (Task 2); `logger` (existing, `common/logger.ts`).
- Produces: `CreateTenantInput`, `TenantDto`, `CreateTenantResultDto` from `types.ts`; `TenantsService` class with methods `create(input: CreateTenantInput): Promise<CreateTenantResultDto>`, `getById(id: string): Promise<TenantDto>`, `getBySlug(slug: string): Promise<TenantDto>`, `suspend(id: string, reason: string): Promise<TenantDto>`, `reactivate(id: string, note?: string): Promise<TenantDto>` — consumed by Task 4 (controller) and Task 5 (factory wiring).

- [ ] **Step 1: Write `src/modules/tenants/v1/types.ts`**

```ts
export interface TenantDto {
  id: string;
  slug: string;
  name: string;
  status: string;
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

export interface CreateTenantResultDto extends TenantDto {
  ownerEmail: string;
  ownerFirstName: string | null;
  ownerLastName: string | null;
}

export interface CreateTenantInput {
  name: string;
  slug: string;
  region: string;
  ownerEmail: string;
  ownerFirstName?: string | null;
  ownerLastName?: string | null;
  idempotencyKey?: string;
}
```

- [ ] **Step 2: Write the failing test `src/modules/tenants/v1/service.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import { TenantsService } from "./service.ts";
import { TntHttpError } from "../../../infra/external/http-tnt-client.ts";
import { TenantSlugTakenError, TenantNotFoundError, TenantTransitionConflictError, TntClientError } from "../../../common/errors.ts";
import type { TntClientPort, TntTenantResponse } from "../../../domain/ports/tnt-client.port.ts";
import type { EventPublisher } from "../../../domain/ports/event-publisher.port.ts";

function baseTenant(overrides: Partial<TntTenantResponse> = {}): TntTenantResponse {
  return {
    id: "t1",
    slug: "acme",
    name: "Acme",
    status: "PROVISIONING",
    region: "us-east-1",
    primaryOwnerUserId: "generated-uuid",
    provisioningJobId: "j1",
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function fakeTntClient(overrides: Partial<TntClientPort> = {}): TntClientPort {
  return {
    createTenant: vi.fn(async () => baseTenant()),
    getTenant: vi.fn(async () => baseTenant()),
    getTenantBySlug: vi.fn(async () => baseTenant()),
    suspendTenant: vi.fn(async () => baseTenant({ status: "SUSPENDED" })),
    reactivateTenant: vi.fn(async () => baseTenant({ status: "ACTIVE" })),
    ...overrides,
  };
}

function fakeEventPublisher(overrides: Partial<EventPublisher> = {}): EventPublisher {
  return {
    publish: vi.fn(async () => undefined),
    ...overrides,
  };
}

describe("TenantsService", () => {
  describe("create", () => {
    it("creates a tenant, generates primaryOwnerUserId, and publishes sup.tenant.created", async () => {
      const tntClient = fakeTntClient();
      const eventPublisher = fakeEventPublisher();
      const service = new TenantsService(tntClient, eventPublisher);

      const result = await service.create({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" });

      expect(result.ownerEmail).toBe("owner@acme.com");
      expect(result.id).toBe("t1");
      expect(tntClient.createTenant).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Acme", slug: "acme", region: "us-east-1", primaryOwnerUserId: expect.any(String) }),
      );
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.tenant.created",
        expect.objectContaining({ event_type: "sup.tenant.created", tenant_id: "t1" }),
      );
    });

    it("throws TenantSlugTakenError when Gen_TNT returns 409", async () => {
      const tntClient = fakeTntClient({ createTenant: vi.fn(async () => { throw new TntHttpError(409, "duplicate"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(
        service.create({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" }),
      ).rejects.toThrow(TenantSlugTakenError);
    });

    it("throws TntClientError when Gen_TNT returns an unexpected status", async () => {
      const tntClient = fakeTntClient({ createTenant: vi.fn(async () => { throw new TntHttpError(500, "boom"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(
        service.create({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" }),
      ).rejects.toThrow(TntClientError);
    });

    it("does not fail the request when audit publishing fails", async () => {
      const tntClient = fakeTntClient();
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new TenantsService(tntClient, eventPublisher);
      await expect(
        service.create({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" }),
      ).resolves.toBeDefined();
    });
  });

  describe("getById / getBySlug", () => {
    it("getById returns the tenant when found", async () => {
      const service = new TenantsService(fakeTntClient(), fakeEventPublisher());
      const result = await service.getById("t1");
      expect(result.id).toBe("t1");
    });

    it("getById throws TenantNotFoundError when Gen_TNT returns null", async () => {
      const tntClient = fakeTntClient({ getTenant: vi.fn(async () => null) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.getById("missing")).rejects.toThrow(TenantNotFoundError);
    });

    it("getBySlug throws TenantNotFoundError when Gen_TNT returns null", async () => {
      const tntClient = fakeTntClient({ getTenantBySlug: vi.fn(async () => null) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.getBySlug("free-slug")).rejects.toThrow(TenantNotFoundError);
    });
  });

  describe("suspend / reactivate", () => {
    it("suspends a tenant and publishes sup.tenant.suspended with the reason", async () => {
      const tntClient = fakeTntClient();
      const eventPublisher = fakeEventPublisher();
      const service = new TenantsService(tntClient, eventPublisher);

      const result = await service.suspend("t1", "Non-payment for 90 days");

      expect(result.status).toBe("SUSPENDED");
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.tenant.suspended",
        expect.objectContaining({ tenant_id: "t1", data: expect.objectContaining({ reason: "Non-payment for 90 days" }) }),
      );
    });

    it("suspend throws TenantNotFoundError when Gen_TNT returns 404", async () => {
      const tntClient = fakeTntClient({ suspendTenant: vi.fn(async () => { throw new TntHttpError(404, "not found"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.suspend("missing", "reason here")).rejects.toThrow(TenantNotFoundError);
    });

    it("suspend throws TenantTransitionConflictError when Gen_TNT returns 409", async () => {
      const tntClient = fakeTntClient({ suspendTenant: vi.fn(async () => { throw new TntHttpError(409, "illegal transition"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.suspend("t1", "reason here")).rejects.toThrow(TenantTransitionConflictError);
    });

    it("reactivates a tenant and publishes sup.tenant.reactivated", async () => {
      const tntClient = fakeTntClient();
      const eventPublisher = fakeEventPublisher();
      const service = new TenantsService(tntClient, eventPublisher);

      const result = await service.reactivate("t1", "billing resolved");

      expect(result.status).toBe("ACTIVE");
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.tenant.reactivated",
        expect.objectContaining({ tenant_id: "t1" }),
      );
    });

    it("reactivate does not fail the request when audit publishing fails", async () => {
      const tntClient = fakeTntClient();
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new TenantsService(tntClient, eventPublisher);
      await expect(service.reactivate("t1")).resolves.toBeDefined();
    });
  });
});
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- modules/tenants`
Expected: FAIL — `./service.ts` does not exist yet.

- [ ] **Step 4: Write `src/modules/tenants/v1/service.ts`**

```ts
import { randomUUID } from "node:crypto";
import type { TntClientPort } from "../../../domain/ports/tnt-client.port.ts";
import { TntHttpError } from "../../../infra/external/http-tnt-client.ts";
import type { EventPublisher, EventEnvelope } from "../../../domain/ports/event-publisher.port.ts";
import { PLATFORM_AUDIT_EXCHANGE, TENANT_ROUTING_KEYS } from "../../../config/constants.ts";
import { TenantSlugTakenError, TenantNotFoundError, TenantTransitionConflictError, TntClientError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type { CreateTenantInput, TenantDto, CreateTenantResultDto } from "./types.ts";

export class TenantsService {
  constructor(
    private readonly tntClient: TntClientPort,
    private readonly eventPublisher: EventPublisher,
  ) {}

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
      logger.warn({ err, routingKey, tenantId }, "[Tenants] Audit event publish failed — continuing");
    }
  }

  async create(input: CreateTenantInput): Promise<CreateTenantResultDto> {
    const primaryOwnerUserId = randomUUID();

    let tenant;
    try {
      tenant = await this.tntClient.createTenant({
        name: input.name,
        slug: input.slug,
        region: input.region,
        primaryOwnerUserId,
        idempotencyKey: input.idempotencyKey,
      });
    } catch (err) {
      if (err instanceof TntHttpError) {
        if (err.status === 409) throw new TenantSlugTakenError(input.slug);
        throw new TntClientError(err.message);
      }
      throw err;
    }

    await this.publishSafely(TENANT_ROUTING_KEYS.CREATED, tenant.id, {
      tenant_slug: tenant.slug,
      tenant_name: tenant.name,
      region: tenant.region,
      owner_email: input.ownerEmail,
    });

    return {
      ...tenant,
      ownerEmail: input.ownerEmail,
      ownerFirstName: input.ownerFirstName ?? null,
      ownerLastName: input.ownerLastName ?? null,
    };
  }

  async getById(id: string): Promise<TenantDto> {
    const tenant = await this.tntClient.getTenant(id);
    if (!tenant) throw new TenantNotFoundError(id);
    return tenant;
  }

  async getBySlug(slug: string): Promise<TenantDto> {
    const tenant = await this.tntClient.getTenantBySlug(slug);
    if (!tenant) throw new TenantNotFoundError(slug);
    return tenant;
  }

  async suspend(id: string, reason: string): Promise<TenantDto> {
    let tenant;
    try {
      tenant = await this.tntClient.suspendTenant(id);
    } catch (err) {
      if (err instanceof TntHttpError) {
        if (err.status === 404) throw new TenantNotFoundError(id);
        if (err.status === 409) throw new TenantTransitionConflictError(id, "suspend");
        throw new TntClientError(err.message);
      }
      throw err;
    }

    await this.publishSafely(TENANT_ROUTING_KEYS.SUSPENDED, id, { reason });
    return tenant;
  }

  async reactivate(id: string, note?: string): Promise<TenantDto> {
    let tenant;
    try {
      tenant = await this.tntClient.reactivateTenant(id);
    } catch (err) {
      if (err instanceof TntHttpError) {
        if (err.status === 404) throw new TenantNotFoundError(id);
        if (err.status === 409) throw new TenantTransitionConflictError(id, "reactivate");
        throw new TntClientError(err.message);
      }
      throw err;
    }

    await this.publishSafely(TENANT_ROUTING_KEYS.REACTIVATED, id, { note: note ?? null });
    return tenant;
  }
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- modules/tenants`
Expected: 12 passed.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/modules/tenants
git commit -m "feat: add TenantsService orchestrating Gen_TNT calls and audit events"
```

---

### Task 4: Controller, router, and schema

**Files:**
- Create: `packages/gen-sup-starter/src/modules/tenants/v1/schema.ts`
- Create: `packages/gen-sup-starter/src/modules/tenants/v1/controller.ts`
- Create: `packages/gen-sup-starter/src/modules/tenants/v1/router.ts`

**Interfaces:**
- Consumes: `TenantsService` (Task 3); `internalSecret` from `middleware/internal-secret.ts` (existing).
- Produces: `createTenantsRouter(deps: { tenantsService: TenantsService; internalSecretValue: string }): Router` — consumed by Task 5's `create-gen-sup.ts`.

- [ ] **Step 1: Write `src/modules/tenants/v1/schema.ts`**

```ts
import { z } from "zod";

const SLUG_REGEX = /^[a-z][a-z0-9-]*[a-z0-9]$/;

export const createTenantSchema = z.object({
  name: z.string().min(2).max(255),
  slug: z.string().min(3).max(63).regex(SLUG_REGEX, "slug must start with a letter, end with a letter or digit, and contain only lowercase letters, digits, and hyphens"),
  region: z.string().min(1).max(100),
  ownerEmail: z.string().email(),
  ownerFirstName: z.string().max(100).optional().nullable(),
  ownerLastName: z.string().max(100).optional().nullable(),
  idempotencyKey: z.string().uuid().optional(),
});

export const suspendTenantSchema = z.object({
  reason: z.string().min(10).max(1000),
});

export const reactivateTenantSchema = z.object({
  note: z.string().max(500).optional(),
});

export const tenantIdParamSchema = z.object({
  id: z.string().min(1),
});

export const tenantSlugParamSchema = z.object({
  slug: z.string().min(1),
});
```

- [ ] **Step 2: Write `src/modules/tenants/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { createTenantSchema, suspendTenantSchema, reactivateTenantSchema, tenantIdParamSchema, tenantSlugParamSchema } from "./schema.ts";
import type { TenantsService } from "./service.ts";

export function makeTenantsController(service: TenantsService) {
  return {
    async create(req: Request, res: Response) {
      const input = createTenantSchema.parse(req.body);
      const result = await service.create(input);
      res.status(201).json(result);
    },
    async getById(req: Request, res: Response) {
      const { id } = tenantIdParamSchema.parse(req.params);
      const result = await service.getById(id);
      res.json(result);
    },
    async getBySlug(req: Request, res: Response) {
      const { slug } = tenantSlugParamSchema.parse(req.params);
      const result = await service.getBySlug(slug);
      res.json(result);
    },
    async suspend(req: Request, res: Response) {
      const { id } = tenantIdParamSchema.parse(req.params);
      const { reason } = suspendTenantSchema.parse(req.body);
      const result = await service.suspend(id, reason);
      res.json(result);
    },
    async reactivate(req: Request, res: Response) {
      const { id } = tenantIdParamSchema.parse(req.params);
      const { note } = reactivateTenantSchema.parse(req.body);
      const result = await service.reactivate(id, note);
      res.json(result);
    },
  };
}
```

- [ ] **Step 3: Write `src/modules/tenants/v1/router.ts`**

```ts
import { Router } from "express";
import { makeTenantsController } from "./controller.ts";
import type { TenantsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface TenantsRouterDeps {
  tenantsService: TenantsService;
  internalSecretValue: string;
}

export function createTenantsRouter(deps: TenantsRouterDeps): Router {
  const router = Router();
  const controller = makeTenantsController(deps.tenantsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.post("/", (req, res, next) => controller.create(req, res).catch(next));
  router.get("/by-slug/:slug", (req, res, next) => controller.getBySlug(req, res).catch(next));
  router.get("/:id", (req, res, next) => controller.getById(req, res).catch(next));
  router.patch("/:id/suspend", (req, res, next) => controller.suspend(req, res).catch(next));
  router.patch("/:id/reactivate", (req, res, next) => controller.reactivate(req, res).catch(next));

  return router;
}
```

Route order matters: `/by-slug/:slug` is registered before `/:id` so Express doesn't match `by-slug` as an `:id` value.

- [ ] **Step 4: Confirm the package builds**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: succeeds (no test file for this task — controller/router are thin dispatch, exercised end-to-end in Task 5's factory tests).

- [ ] **Step 5: Commit**

```bash
git add packages/gen-sup-starter/src/modules/tenants/v1/schema.ts packages/gen-sup-starter/src/modules/tenants/v1/controller.ts packages/gen-sup-starter/src/modules/tenants/v1/router.ts
git commit -m "feat: add tenants controller, router, and schema"
```

---

### Task 5: Wire into createGenSup and the public barrel

**Files:**
- Modify: `packages/gen-sup-starter/src/create-gen-sup.ts`
- Modify: `packages/gen-sup-starter/src/create-gen-sup.test.ts`
- Modify: `packages/gen-sup-starter/src/index.ts`

**Interfaces:**
- Consumes: everything produced by Tasks 1–4.
- Produces: `GenSupConfig.tenants?: { tntClient?: TntClientPort; eventPublisher?: EventPublisher; tntBaseUrl?: string; tntInternalSecret?: string; rabbitMqUrl?: string }`, `GenSupModulesConfig.tenants?: boolean`, `GenSupInstance.eventPublisher?: RabbitMqBus` — the public shape a consumer or `gen-sup-demo` (already shipped, not touched by this plan) would use.

- [ ] **Step 1: Modify `src/create-gen-sup.ts`**

Current file (reproduced in full so the diff is unambiguous):
```ts
import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import type { Redis } from "ioredis";
import { env, requireEnv } from "./config/env.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { createValkeyClient } from "./infra/cache/valkey-client.ts";
import { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import { DashboardService } from "./modules/dashboard/v1/service.ts";
import { createDashboardRouter } from "./modules/dashboard/v1/router.ts";

export interface GenSupModulesConfig {
  dashboard?: boolean;
}

export interface GenSupConfig {
  tenantMetricsPort?: TenantMetricsPort;
  valkeyUrl?: string;
  internalSecret?: string;
  modules?: GenSupModulesConfig;
}

export interface GenSupInstance {
  app: Express;
  // Present only when the dashboard module is enabled (the only module that
  // constructs a Valkey client today). Exposed so a host can close the
  // connection on shutdown, e.g. `instance.valkey?.quit()`.
  valkey?: Redis;
}

function resolveInternalSecret(override: string | undefined): string {
  return override || requireEnv("GEN_SUP_INTERNAL_SECRET");
}

function resolveValkeyUrl(override: string | undefined): string {
  return override ?? requireEnv("VALKEY_URL");
}

export function createGenSup(config: GenSupConfig): GenSupInstance {
  const modules: Required<GenSupModulesConfig> = {
    dashboard: config.modules?.dashboard ?? true,
  };

  const internalSecretValue = resolveInternalSecret(config.internalSecret);
  const tenantMetricsPort = config.tenantMetricsPort ?? noopTenantMetricsPort;

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  let valkey: Redis | undefined;
  if (modules.dashboard) {
    valkey = createValkeyClient(resolveValkeyUrl(config.valkeyUrl));
    const dashboardService = new DashboardService(tenantMetricsPort, valkey, env.DASHBOARD_CACHE_TTL_SEC);
    app.use("/api/v1/dashboard", createDashboardRouter({ dashboardService, internalSecretValue }));
  }

  app.use(errorHandler);

  return { app, valkey };
}
```

Replace it in full with:
```ts
import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import type { Redis } from "ioredis";
import { env, requireEnv } from "./config/env.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { createValkeyClient } from "./infra/cache/valkey-client.ts";
import { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import { DashboardService } from "./modules/dashboard/v1/service.ts";
import { createDashboardRouter } from "./modules/dashboard/v1/router.ts";
import type { TntClientPort } from "./domain/ports/tnt-client.port.ts";
import { HttpTntClient } from "./infra/external/http-tnt-client.ts";
import type { EventPublisher } from "./domain/ports/event-publisher.port.ts";
import { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
import { TenantsService } from "./modules/tenants/v1/service.ts";
import { createTenantsRouter } from "./modules/tenants/v1/router.ts";

export interface GenSupModulesConfig {
  dashboard?: boolean;
  tenants?: boolean;
}

export interface GenSupConfig {
  tenantMetricsPort?: TenantMetricsPort;
  valkeyUrl?: string;
  internalSecret?: string;
  tntClient?: TntClientPort;
  tntBaseUrl?: string;
  tntInternalSecret?: string;
  eventPublisher?: EventPublisher;
  rabbitMqUrl?: string;
  modules?: GenSupModulesConfig;
}

export interface GenSupInstance {
  app: Express;
  // Present only when the dashboard module is enabled (the only module that
  // constructs a Valkey client today). Exposed so a host can close the
  // connection on shutdown, e.g. `instance.valkey?.quit()`.
  valkey?: Redis;
  // Present only when the tenants module is enabled and no eventPublisher
  // override was supplied (an override is the caller's own resource to
  // manage). Exposed so a host can close the RabbitMQ connection on
  // shutdown, e.g. `instance.eventPublisher?.close()`.
  eventPublisher?: RabbitMqBus;
}

function resolveInternalSecret(override: string | undefined): string {
  return override || requireEnv("GEN_SUP_INTERNAL_SECRET");
}

function resolveValkeyUrl(override: string | undefined): string {
  return override ?? requireEnv("VALKEY_URL");
}

function resolveTntClient(override: TntClientPort | undefined, baseUrlOverride: string | undefined, secretOverride: string | undefined): TntClientPort {
  if (override) return override;
  const baseUrl = baseUrlOverride ?? requireEnv("GEN_TNT_BASE_URL");
  const secret = secretOverride ?? requireEnv("GEN_TNT_INTERNAL_SECRET");
  return new HttpTntClient(baseUrl, secret);
}

export function createGenSup(config: GenSupConfig): GenSupInstance {
  const modules: Required<GenSupModulesConfig> = {
    dashboard: config.modules?.dashboard ?? true,
    tenants: config.modules?.tenants ?? true,
  };

  const internalSecretValue = resolveInternalSecret(config.internalSecret);
  const tenantMetricsPort = config.tenantMetricsPort ?? noopTenantMetricsPort;

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  let valkey: Redis | undefined;
  if (modules.dashboard) {
    valkey = createValkeyClient(resolveValkeyUrl(config.valkeyUrl));
    const dashboardService = new DashboardService(tenantMetricsPort, valkey, env.DASHBOARD_CACHE_TTL_SEC);
    app.use("/api/v1/dashboard", createDashboardRouter({ dashboardService, internalSecretValue }));
  }

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

  app.use(errorHandler);

  return { app, valkey, eventPublisher };
}
```

- [ ] **Step 2: Extend `src/create-gen-sup.test.ts`**

Read the existing file first — it has **three** existing tests: `throws GenSupConfigError when no internalSecret override...`, `exposes GET /api/v1/dashboard/kpis...`, and `exposes GET /health with no auth required, even with dashboard disabled`. That third test only sets `modules: { dashboard: false }` — since `tenants` now defaults to `true`, this test would start requiring `GEN_TNT_BASE_URL`/`GEN_TNT_INTERNAL_SECRET`/`RABBITMQ_URL` and break. Fix it first, then add one new test.

**2a. Modify the third existing test** to also disable `tenants` (it was written to mean "no optional module needs to be enabled for health to work" — keep that intent, it just needs both modules named now):

Current:
```ts
  it("exposes GET /health with no auth required, even with dashboard disabled", async () => {
    // dashboard: false means no VALKEY_URL is required to construct the app —
    // this also exercises the modules toggle itself.
    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      modules: { dashboard: false },
    });
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
  });
```

Replace with:
```ts
  it("exposes GET /health with no auth required, with every optional module disabled", async () => {
    // dashboard:false / tenants:false means no VALKEY_URL / GEN_TNT_BASE_URL /
    // GEN_TNT_INTERNAL_SECRET / RABBITMQ_URL is required to construct the app —
    // this also exercises the modules toggle itself.
    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      modules: { dashboard: false, tenants: false },
    });
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
  });
```

**2b. Add one new test** to the same `describe("createGenSup", ...)` block, after the others:

```ts
  it("exposes POST /api/v1/tenants gated on X-Internal-Secret when a tntClient and eventPublisher are supplied", async () => {
    const tntClient = {
      createTenant: async () => ({ id: "t1", slug: "acme", name: "Acme", status: "PROVISIONING", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: "j1", createdAt: "2026-01-01T00:00:00Z" }),
      getTenant: async () => null,
      getTenantBySlug: async () => null,
      suspendTenant: async () => { throw new Error("not used in this test"); },
      reactivateTenant: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      tntClient,
      eventPublisher,
      modules: { dashboard: false },
    });

    const unauthorized = await request(app).post("/api/v1/tenants").send({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" });
    expect(unauthorized.status).toBe(401);

    const ok = await request(app)
      .post("/api/v1/tenants")
      .set("X-Internal-Secret", "s3cret")
      .send({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" });
    expect(ok.status).toBe(201);
    expect(ok.body.slug).toBe("acme");
  });
```

Note `modules: { dashboard: false }` here leaves `tenants` at its default (`true`), and since both `tntClient` and `eventPublisher` overrides are supplied, no env var is required for this test either.

- [ ] **Step 3: Run the test, verify it passes**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- create-gen-sup`
Expected: 4 passed (3 existing, one modified in place, plus 1 new).

- [ ] **Step 4: Update the public barrel `src/index.ts`**

Current file:
```ts
export { createGenSup } from "./create-gen-sup.ts";
export type { GenSupConfig, GenSupModulesConfig, GenSupInstance } from "./create-gen-sup.ts";

export type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
export { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";

export type { DashboardKpis } from "./modules/dashboard/v1/types.ts";
export { DashboardService } from "./modules/dashboard/v1/service.ts";

export { createValkeyClient } from "./infra/cache/valkey-client.ts";
export { errorHandler } from "./middleware/error-handler.ts";
export { internalSecret } from "./middleware/internal-secret.ts";
export { AppError, GenSupConfigError, TenantMetricsUnavailableError } from "./common/errors.ts";
```

Replace with:
```ts
export { createGenSup } from "./create-gen-sup.ts";
export type { GenSupConfig, GenSupModulesConfig, GenSupInstance } from "./create-gen-sup.ts";

export type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
export { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";

export type { DashboardKpis } from "./modules/dashboard/v1/types.ts";
export { DashboardService } from "./modules/dashboard/v1/service.ts";

export type { TntClientPort, TntTenantResponse, CreateTenantParams } from "./domain/ports/tnt-client.port.ts";
export { HttpTntClient, TntHttpError } from "./infra/external/http-tnt-client.ts";
export type { EventPublisher, EventEnvelope } from "./domain/ports/event-publisher.port.ts";
export { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
export type { TenantDto, CreateTenantResultDto, CreateTenantInput } from "./modules/tenants/v1/types.ts";
export { TenantsService } from "./modules/tenants/v1/service.ts";

export { createValkeyClient } from "./infra/cache/valkey-client.ts";
export { errorHandler } from "./middleware/error-handler.ts";
export { internalSecret } from "./middleware/internal-secret.ts";
export {
  AppError,
  GenSupConfigError,
  TenantMetricsUnavailableError,
  TenantSlugTakenError,
  TenantNotFoundError,
  TenantTransitionConflictError,
  TntClientError,
} from "./common/errors.ts";
```

- [ ] **Step 5: Full test run for the starter package**

Run: `npm test --workspace=@gen-ms/gen-sup-starter`
Expected: all tests across every file pass (dashboard's 5 existing test files plus this module's `http-tnt-client.test.ts`, `service.test.ts`, and the extended `create-gen-sup.test.ts`).

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/create-gen-sup.ts packages/gen-sup-starter/src/create-gen-sup.test.ts packages/gen-sup-starter/src/index.ts
git commit -m "feat: wire tenants module into createGenSup and public barrel"
```

---

### Task 6: Documentation

**Files:**
- Modify: `docs/integration-guide.md`
- Modify: `README.md`

**Interfaces:** None (docs-only).

- [ ] **Step 1: Add a Tenants section to `docs/integration-guide.md`**

Read the existing file first — it has a "## API reference" section with a "### Dashboard — `GET /api/v1/dashboard/kpis`" subsection. Add a new subsection immediately after it, before "## Error codes":

```markdown
### Tenants — `/api/v1/tenants`

```
POST /api/v1/tenants
X-Internal-Secret: <secret>
Content-Type: application/json

{
  "name": "Acme Corp",
  "slug": "acme",
  "region": "us-east-1",
  "ownerEmail": "owner@acme.com",
  "ownerFirstName": "Ada",
  "ownerLastName": "Lovelace",
  "idempotencyKey": "optional-client-generated-uuid"
}
```

→ `201 Created`, body includes Gen_TNT's tenant fields plus `ownerEmail`/`ownerFirstName`/
`ownerLastName` (Gen_SUP-only metadata, not sent to or returned by Gen_TNT itself).
`primaryOwnerUserId` is generated server-side — Gen_TNT requires it but does not generate it.
`409 TENANT_SLUG_TAKEN` if the slug is already in use.

```
GET /api/v1/tenants/:id
GET /api/v1/tenants/by-slug/:slug
```

→ `200` with the tenant, or `404 TENANT_NOT_FOUND`.

```
PATCH /api/v1/tenants/:id/suspend
Content-Type: application/json

{ "reason": "Non-payment for 90 days" }
```

→ `200` with the updated tenant. `reason` is Gen_SUP-only audit metadata — Gen_TNT's suspend
endpoint takes no request body, so it's never forwarded. `404 TENANT_NOT_FOUND` if the tenant
doesn't exist, `409 TENANT_TRANSITION_CONFLICT` if suspend isn't legal from the tenant's
current status.

```
PATCH /api/v1/tenants/:id/reactivate
Content-Type: application/json

{ "note": "optional" }
```

→ Same shape as suspend, publishes `sup.tenant.reactivated`.

Every create/suspend/reactivate call publishes a fire-and-forget audit event to the
`platform.audit` RabbitMQ exchange (`sup.tenant.created`/`sup.tenant.suspended`/
`sup.tenant.reactivated`) — publish failures are logged and do not fail the request, since
the underlying Gen_TNT call already succeeded by that point.

`list`, `cancel`, and `purge` are not implemented in this module (Gen_TNT itself has no
list/search endpoint at all; `cancel`/`purge` are deferred pending a real use case).
```

Also add these two error codes to the existing "## Error codes" table:

```markdown
| `TENANT_SLUG_TAKEN` | 409 | Slug already in use (Gen_TNT create returned 409) |
| `TENANT_NOT_FOUND` | 404 | No tenant with this id/slug |
| `TENANT_TRANSITION_CONFLICT` | 409 | Suspend/reactivate isn't legal from the tenant's current status |
| `TNT_CLIENT_ERROR` | 502 | Gen_TNT returned an unexpected error |
```

And update the "## Local development" `docker compose up -d` comment to mention the new port:
change `docker compose up -d   # Postgres on 5443, Valkey on 6387` to
`docker compose up -d   # Postgres on 5443, Valkey on 6387, RabbitMQ on 5676 (mgmt UI on 15676)`.

And add `GEN_TNT_BASE_URL`/`GEN_TNT_INTERNAL_SECRET`/`RABBITMQ_URL` to the Quick Start example's
`createGenSup({...})` call if that example is shown fully in the file — extend it with:
```ts
  tntBaseUrl: process.env.GEN_TNT_BASE_URL,
  tntInternalSecret: process.env.GEN_TNT_INTERNAL_SECRET,
  rabbitMqUrl: process.env.RABBITMQ_URL,
```

- [ ] **Step 1a: Update the "Known limitations" section in `docs/integration-guide.md`**

Current:
```markdown
## Known limitations

- Only the `dashboard` module exists so far. `tenants`, `feature-flags`,
  `announcements`, `analytics`, `tickets`, and `impersonate` are planned
  (see the design spec for build order).
- No local persistence in this module — `TenantMetricsPort` is the only
  source of truth for tenant metrics.
```

Replace with:
```markdown
## Known limitations

- `dashboard` and `tenants` are the only modules built so far. `feature-flags`,
  `announcements`, `analytics`, `tickets`, and `impersonate` are planned
  (see the design specs for build order).
- No local persistence in either module — `dashboard` relies entirely on
  `TenantMetricsPort`, and `tenants` relies entirely on Gen_TNT as the
  source of truth.
- `tenants` does not implement `list`/search (Gen_TNT itself has no such
  endpoint) or `cancel`/`purge` (deferred pending a real use case).
```

- [ ] **Step 2: Update `README.md`**

Read the existing file — find the line `| Gen_SUP | TypeScript/Express/Prisma | this repo — \`dashboard\` module in progress |` in the Gen_MS family table and change it to:

```markdown
| Gen_SUP | TypeScript/Express/Prisma | this repo — `dashboard` + `tenants` modules built |
```

- [ ] **Step 3: Commit**

```bash
git add docs/integration-guide.md README.md
git commit -m "docs: document the tenants module in the integration guide and README"
```

---

### Task 7: Final review pass

**Files:** Any file from Tasks 1–6, as needed to fix findings.

**Interfaces:** None new — this task only verifies and repairs existing interfaces.

- [ ] **Step 1: Full workspace build**

Run: `npm run build`
Expected: both `gen-sup-starter` and `gen-sup-demo` compile with zero TypeScript errors.

- [ ] **Step 2: Full test suite**

Run: `npm test`
Expected: all tests pass — the pre-existing dashboard tests (unaffected by this plan) and every new test from Tasks 2, 3, and 5.

- [ ] **Step 3: Re-read every file against this plan's Global Constraints**

Check specifically:
- No file in the `tenants` module imports Prisma's generated client (per the "no local state" constraint).
- `region` is validated as a free string, not an enum, in `schema.ts`.
- `reason`/`note` never appear in any payload sent to Gen_TNT (`http-tnt-client.ts`'s `suspendTenant`/`reactivateTenant` send no body).
- `GEN_TNT_BASE_URL`, `GEN_TNT_INTERNAL_SECRET`, `RABBITMQ_URL` are not present in `config/env.ts`'s `EnvSchema` (they must stay lazy via `requireEnv`).
- `.env.example`'s new values match `docker-compose.yml`'s new port numbers exactly (`5676`/`15676`).
- Every tenants route except none (there is no unauthenticated route in this module, unlike `/health`) passes through `internalSecret`.
- The `dashboard` module's existing behavior and tests are unchanged — this plan only adds files and extends `create-gen-sup.ts`/`index.ts`/`create-gen-sup.test.ts`, it does not modify any dashboard-specific file.

- [ ] **Step 4: Manual verification (or the demo-app equivalent)**

The `gen-sup-demo` app was not modified by this plan and does not wire up the `tenants` module (it only calls `createGenSup({ tenantMetricsPort: sampleTenantMetricsPort })`, which defaults `modules.tenants` to `true` and will now fail to boot without `GEN_TNT_BASE_URL`/`GEN_TNT_INTERNAL_SECRET`/`RABBITMQ_URL`). Confirm this is expected and not a regression:

Run: `GEN_SUP_INTERNAL_SECRET=demo-secret VALKEY_URL=redis://localhost:1 npm run dev --workspace=@gen-ms/gen-sup-demo`
Expected: `GenSupConfigError: Missing required environment variable "GEN_TNT_BASE_URL"` — this is correct given `gen-sup-demo`'s `index.ts` doesn't pass `modules: { tenants: false }` or supply tenant config.

Then fix `packages/gen-sup-demo/src/index.ts` to disable the module it doesn't demo yet, changing:
```ts
const { app } = createGenSup({ tenantMetricsPort: sampleTenantMetricsPort });
```
to:
```ts
const { app } = createGenSup({ tenantMetricsPort: sampleTenantMetricsPort, modules: { tenants: false } });
```

Re-run the smoke script to confirm the demo still boots and serves the dashboard:
```bash
GEN_SUP_INTERNAL_SECRET=demo-secret VALKEY_URL=redis://localhost:1 npm run dev --workspace=@gen-ms/gen-sup-demo &
sleep 3
./scripts/smoke-dashboard.sh
```
Expected: "Smoke test passed." (Docker/a real Valkey instance may be unavailable in your environment — the dashboard module tolerates that by design, same as when this was last verified for the `dashboard` module.)

- [ ] **Step 5: Commit any fixes found in Steps 1–4**

```bash
git add -A
git commit -m "fix: final review pass for Gen_SUP tenants module"
```

If Step 4's `gen-sup-demo` fix is the only change needed, that alone is enough to commit here. If Steps 1–3 found nothing else to fix, don't invent additional changes.
