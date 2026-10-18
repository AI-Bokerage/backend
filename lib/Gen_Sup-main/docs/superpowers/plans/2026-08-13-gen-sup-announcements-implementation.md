# Gen_SUP announcements module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an "announcements" module to Gen_SUP — a platform-wide broadcast feature (create + list, immediate-or-scheduled), Gen_SUP's first module with real local persistence (a Prisma-backed Postgres table), matching CPMS `sup-svc`'s own store-and-fire-audit-event behavior (no real delivery — that capability doesn't exist anywhere in the Gen_MS family yet).

**Architecture:** `AnnouncementsService(prisma, eventPublisher)` — no external client port (unlike `tenants`/`feature-flags`, this data has no sibling-service owner), talks to a new local `Announcement` Prisma model directly. Publishes fire-and-forget audit events (`sup.announcement.created` on every create, `sup.announcement.broadcast` on actual send) via the existing `RabbitMqBus`/`EventPublisher`. A host-driven `dispatchScheduled()` sweep (no internal scheduler) finds due scheduled announcements and sends them.

**Tech Stack:** TypeScript, Express, Zod, Prisma/PostgreSQL, Vitest.

## Global Constraints

- No new npm dependencies beyond what's already installed — `@prisma/client`/`prisma` are already in `packages/gen-sup-starter/package.json` (`^5.19.0`), wired but pointed at an empty schema until this plan.
- `announcements` defaults to `true` in `GenSupModulesConfig`, same as the other three modules — every pre-existing test in `create-gen-sup.test.ts` that doesn't already disable it must be audited individually (this regression class has now hit this project twice; Task 5 must check the CURRENT file, not assume a test count from this plan's description of it).
- Reuse the existing `RabbitMqBus`/`EventPublisher`/`resolvePublisher()` machinery unchanged — `announcements` becomes a third consumer sharing the one connection when enabled alongside `tenants`/`feature-flags` with no override.
- No new `AppError` subclasses — there is no get-by-id/update/delete route that could 404 (see design spec's Error handling section); unexpected Prisma failures fall through to the existing generic `errorHandler`'s 500 path, same as any other unmodeled failure elsewhere in this codebase.
- Tests mock `PrismaClient` entirely — no live Postgres connection is assumed or required to run `npm test`. A real migration is still created (Task 1) so the schema is deployable, but nothing in the test suite depends on a running database.
- `scheduledFor`/`expiresAt`-style datetime fields use `z.string().datetime({ offset: true })`, not the bare `.datetime()` — a lesson carried directly from the `feature-flags` module's own review-round fix (a bare `.datetime()` rejects valid non-UTC-offset timestamps).

---

### Task 1: Prisma model, migration, client wrapper, and routing-key constant

**Files:**
- Modify: `packages/gen-sup-starter/prisma/schema.prisma`
- Create: `packages/gen-sup-starter/prisma/migrations/migration_lock.toml`
- Create: `packages/gen-sup-starter/prisma/migrations/20260813120000_add_announcements/migration.sql`
- Create: `packages/gen-sup-starter/src/infra/persistence/prisma-client.ts`
- Modify: `packages/gen-sup-starter/src/config/constants.ts`

**Interfaces:**
- Produces: `PrismaClient` (re-exported type + value), `createPrismaClient(databaseUrl: string): PrismaClient`, `ANNOUNCEMENT_ROUTING_KEYS` — consumed by Task 2 (service), Task 5 (`create-gen-sup.ts` wiring).

- [ ] **Step 1: Add the `Announcement` model to `schema.prisma`**

Current file (`packages/gen-sup-starter/prisma/schema.prisma`) has only `generator`/`datasource` blocks. Append this model (this is the first model in the file — do not remove or alter the existing `generator`/`datasource` blocks):

```prisma
model Announcement {
  id            String    @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  title         String    @db.VarChar(500)
  body          String
  type          String    @default("INFO") @db.VarChar(24)
  targetSegment String    @default("ALL") @map("target_segment") @db.VarChar(64)
  channels      String[]
  scheduledFor  DateTime? @map("scheduled_for") @db.Timestamptz(6)
  sentAt        DateTime? @map("sent_at") @db.Timestamptz(6)
  createdBy     String    @map("created_by") @db.Uuid
  createdAt     DateTime  @default(now()) @map("created_at") @db.Timestamptz(6)

  @@index([sentAt])
  @@index([scheduledFor])
  @@map("announcements")
}
```

- [ ] **Step 2: Generate the Prisma client (no live database connection needed for this step)**

Run, from `packages/gen-sup-starter/`:
```bash
npx prisma generate --schema=prisma/schema.prisma
```
Expected: succeeds and writes generated client code to `packages/gen-sup-starter/__generated__/prisma/` (the `output` path already configured in `schema.prisma`). This step needs no database connection — it only reads the schema file.

- [ ] **Step 3: Create the migration (try live-DB first, fall back to hand-written SQL)**

This is the **first migration ever** for this schema — no `prisma/migrations/` directory exists yet in this repo.

Try, from `packages/gen-sup-starter/`:
```bash
npx prisma migrate dev --name add_announcements --schema=prisma/schema.prisma --skip-generate
```
(`--skip-generate` avoids redundantly re-running Step 2). This needs a reachable Postgres — `docker compose up -d postgres` from the repo root first, using the `DATABASE_URL` in `.env.example` (`postgresql://postgres:postgres@localhost:5443/gensup`) if you have Docker available in this environment.

**If Docker/a live Postgres is not available in this environment**, create the migration files by hand instead — this produces an identical result to what `prisma migrate dev` would generate, without needing a live connection:

Create `packages/gen-sup-starter/prisma/migrations/migration_lock.toml`:
```toml
# Please do not edit this file manually
# It should be added in your version-control system (e.g., Git)
provider = "postgresql"
```

Create `packages/gen-sup-starter/prisma/migrations/20260813120000_add_announcements/migration.sql`:
```sql
-- CreateTable
CREATE TABLE "announcements" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "title" VARCHAR(500) NOT NULL,
    "body" TEXT NOT NULL,
    "type" VARCHAR(24) NOT NULL DEFAULT 'INFO',
    "target_segment" VARCHAR(64) NOT NULL DEFAULT 'ALL',
    "channels" TEXT[],
    "scheduled_for" TIMESTAMPTZ(6),
    "sent_at" TIMESTAMPTZ(6),
    "created_by" UUID NOT NULL,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "announcements_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "announcements_sent_at_idx" ON "announcements"("sent_at");

-- CreateIndex
CREATE INDEX "announcements_scheduled_for_idx" ON "announcements"("scheduled_for");
```

(Postgres 15, the version this repo's `docker-compose.yml` uses, has `gen_random_uuid()` built into core — no extension needed.)

State clearly in your task report which path you took (live migrate vs. hand-written) and why.

- [ ] **Step 4: Create the Prisma client wrapper**

```ts
// packages/gen-sup-starter/src/infra/persistence/prisma-client.ts
import { PrismaClient } from "../../../__generated__/prisma/index.js";

export { PrismaClient };
export type { Announcement } from "../../../__generated__/prisma/index.js";

export function createPrismaClient(databaseUrl: string): PrismaClient {
  return new PrismaClient({ datasources: { db: { url: databaseUrl } } });
}
```

- [ ] **Step 5: Add the routing-key constant**

Append to `packages/gen-sup-starter/src/config/constants.ts` (do not touch `PLATFORM_AUDIT_EXCHANGE`/`TENANT_ROUTING_KEYS`/`FLAG_ROUTING_KEYS`):

```ts
export const ANNOUNCEMENT_ROUTING_KEYS = {
  CREATED: "sup.announcement.created",
  BROADCAST: "sup.announcement.broadcast",
} as const;
```

- [ ] **Step 6: Build to confirm the generated Prisma types compile cleanly**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: PASS (nothing consumes the new model yet, so this just confirms `tsc` can see the generated `__generated__/prisma` types without error)

- [ ] **Step 7: Run the full test suite to confirm nothing broke**

Run: `npm test --workspace=@gen-ms/gen-sup-starter`
Expected: PASS (all existing tests, no new ones yet)

- [ ] **Step 8: Commit**

```bash
git add packages/gen-sup-starter/prisma packages/gen-sup-starter/src/infra/persistence/prisma-client.ts packages/gen-sup-starter/src/config/constants.ts
git commit -m "feat: add Announcement Prisma model, client wrapper, and routing keys"
```

Note: do NOT commit `packages/gen-sup-starter/__generated__/` — check whether `.gitignore` already excludes it (it should, since generated code is regenerated by `postinstall`/`prisma generate`); if it's not already ignored, add `__generated__/` to `packages/gen-sup-starter/.gitignore` (or the repo-root `.gitignore` if that's where other generated-output exclusions live) as part of this commit.

---

### Task 2: `AnnouncementsService` — create + list

**Files:**
- Create: `packages/gen-sup-starter/src/modules/announcements/v1/types.ts`
- Create: `packages/gen-sup-starter/src/modules/announcements/v1/service.ts`
- Test: `packages/gen-sup-starter/src/modules/announcements/v1/service.test.ts`

**Interfaces:**
- Consumes: `PrismaClient` (Task 1, via `infra/persistence/prisma-client.ts`); `EventPublisher`/`EventEnvelope` (existing `domain/ports/event-publisher.port.ts`); `PLATFORM_AUDIT_EXCHANGE`/`ANNOUNCEMENT_ROUTING_KEYS` (Task 1).
- Produces: `AnnouncementsService` (methods `create`, `list` in this task; `dispatchScheduled` added in Task 3), `AnnouncementDto`, `CreateAnnouncementInput`, `ListAnnouncementsQuery`, `ListAnnouncementsResult` — consumed by Task 3 (adds a method to the same class), Task 4 (controller), Task 5 (wiring).

- [ ] **Step 1: Write `types.ts`**

```ts
// packages/gen-sup-starter/src/modules/announcements/v1/types.ts
export interface AnnouncementDto {
  id: string;
  title: string;
  body: string;
  type: string;
  targetSegment: string;
  channels: string[];
  scheduledFor: string | null;
  sentAt: string | null;
  createdBy: string;
  createdAt: string;
}

export interface CreateAnnouncementInput {
  title: string;
  body: string;
  type: string;
  targetSegment: string;
  channels: string[];
  scheduledFor?: string;
  createdBy: string;
}

export interface ListAnnouncementsQuery {
  type?: string;
  page: number;
  pageSize: number;
}

export interface ListAnnouncementsResult {
  announcements: AnnouncementDto[];
  page: number;
  pageSize: number;
  total: number;
}

export interface DispatchScheduledResult {
  dispatched: number;
  failed: number;
}
```

- [ ] **Step 2: Write the failing test for `create`/`list`**

```ts
// packages/gen-sup-starter/src/modules/announcements/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { AnnouncementsService } from "./service.ts";
import type { PrismaClient } from "../../../infra/persistence/prisma-client.ts";
import type { EventPublisher } from "../../../domain/ports/event-publisher.port.ts";

function baseRow(overrides: Record<string, unknown> = {}) {
  return {
    id: "a1",
    title: "Scheduled maintenance",
    body: "We will be performing maintenance this weekend.",
    type: "MAINTENANCE",
    targetSegment: "ALL",
    channels: ["EMAIL", "IN_APP"],
    scheduledFor: null,
    sentAt: new Date("2026-01-01T00:00:00Z"),
    createdBy: "11111111-1111-1111-1111-111111111111",
    createdAt: new Date("2026-01-01T00:00:00Z"),
    ...overrides,
  };
}

function fakePrisma(overrides: Record<string, unknown> = {}) {
  return {
    announcement: {
      create: vi.fn(async ({ data }: { data: Record<string, unknown> }) => baseRow(data)),
      findMany: vi.fn(async () => [baseRow()]),
      count: vi.fn(async () => 1),
      update: vi.fn(async () => baseRow()),
      ...overrides,
    },
  } as unknown as PrismaClient;
}

function fakeEventPublisher(overrides: Partial<EventPublisher> = {}): EventPublisher {
  return { publish: vi.fn(async () => undefined), ...overrides };
}

describe("AnnouncementsService", () => {
  describe("create", () => {
    it("stamps sentAt and publishes both CREATED and BROADCAST when there is no scheduledFor", async () => {
      const prisma = fakePrisma();
      const eventPublisher = fakeEventPublisher();
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.create({
        title: "Scheduled maintenance",
        body: "We will be performing maintenance this weekend.",
        type: "MAINTENANCE",
        targetSegment: "ALL",
        channels: ["EMAIL", "IN_APP"],
        createdBy: "11111111-1111-1111-1111-111111111111",
      });

      expect(result.sentAt).not.toBeNull();
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.created", expect.objectContaining({ event_type: "sup.announcement.created" }),
      );
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.broadcast", expect.objectContaining({ event_type: "sup.announcement.broadcast" }),
      );
      expect(eventPublisher.publish).toHaveBeenCalledTimes(2);
    });

    it("leaves sentAt null and publishes only CREATED when scheduledFor is in the future", async () => {
      const prisma = fakePrisma({
        create: vi.fn(async ({ data }: { data: Record<string, unknown> }) => baseRow({ ...data, sentAt: null })),
      });
      const eventPublisher = fakeEventPublisher();
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.create({
        title: "New feature",
        body: "A new feature is coming next month.",
        type: "NEW_FEATURE",
        targetSegment: "ALL",
        channels: ["IN_APP"],
        scheduledFor: "2027-01-01T00:00:00Z",
        createdBy: "11111111-1111-1111-1111-111111111111",
      });

      expect(result.sentAt).toBeNull();
      expect(eventPublisher.publish).toHaveBeenCalledTimes(1);
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.created", expect.anything(),
      );
    });

    it("does not fail the request when audit publishing fails", async () => {
      const prisma = fakePrisma();
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new AnnouncementsService(prisma, eventPublisher);

      await expect(service.create({
        title: "Scheduled maintenance",
        body: "We will be performing maintenance this weekend.",
        type: "MAINTENANCE",
        targetSegment: "ALL",
        channels: ["EMAIL"],
        createdBy: "11111111-1111-1111-1111-111111111111",
      })).resolves.toBeDefined();
    });
  });

  describe("list", () => {
    it("returns paginated announcements with the requested page/pageSize/total", async () => {
      const prisma = fakePrisma();
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      const result = await service.list({ page: 1, pageSize: 20 });

      expect(result.announcements).toHaveLength(1);
      expect(result.page).toBe(1);
      expect(result.pageSize).toBe(20);
      expect(result.total).toBe(1);
    });

    it("filters by type when provided", async () => {
      const findMany = vi.fn(async () => [baseRow()]);
      const prisma = fakePrisma({ findMany });
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      await service.list({ type: "CRITICAL", page: 1, pageSize: 20 });

      expect(findMany).toHaveBeenCalledWith(expect.objectContaining({ where: { type: "CRITICAL" } }));
    });

    it("converts Date fields to ISO strings in the returned DTOs", async () => {
      const prisma = fakePrisma();
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      const result = await service.list({ page: 1, pageSize: 20 });

      expect(typeof result.announcements[0]!.sentAt).toBe("string");
      expect(typeof result.announcements[0]!.createdAt).toBe("string");
    });
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- announcements`
Expected: FAIL — `Cannot find module './service.ts'`

- [ ] **Step 4: Implement `service.ts` (create + list only — `dispatchScheduled` is added in Task 3)**

```ts
// packages/gen-sup-starter/src/modules/announcements/v1/service.ts
import type { PrismaClient } from "../../../infra/persistence/prisma-client.ts";
import type { EventPublisher, EventEnvelope } from "../../../domain/ports/event-publisher.port.ts";
import { PLATFORM_AUDIT_EXCHANGE, ANNOUNCEMENT_ROUTING_KEYS } from "../../../config/constants.ts";
import { logger } from "../../../common/logger.ts";
import type {
  AnnouncementDto,
  CreateAnnouncementInput,
  ListAnnouncementsQuery,
  ListAnnouncementsResult,
} from "./types.ts";

// Announcements aren't scoped to a single tenant — targetSegment is a coarse
// string nobody in this family resolves into real tenants/users yet — but
// EventEnvelope.tenant_id is a required field. This sentinel marks
// platform-level (non-tenant) audit events, same pattern as the feature-flags
// module's flag-update event.
const PLATFORM_TENANT_ID = "platform";

interface AnnouncementRow {
  id: string;
  title: string;
  body: string;
  type: string;
  targetSegment: string;
  channels: string[];
  scheduledFor: Date | null;
  sentAt: Date | null;
  createdBy: string;
  createdAt: Date;
}

function toDto(row: AnnouncementRow): AnnouncementDto {
  return {
    id: row.id,
    title: row.title,
    body: row.body,
    type: row.type,
    targetSegment: row.targetSegment,
    channels: row.channels,
    scheduledFor: row.scheduledFor ? row.scheduledFor.toISOString() : null,
    sentAt: row.sentAt ? row.sentAt.toISOString() : null,
    createdBy: row.createdBy,
    createdAt: row.createdAt.toISOString(),
  };
}

export class AnnouncementsService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly eventPublisher: EventPublisher,
  ) {}

  private async publishSafely(routingKey: string, data: Record<string, unknown>): Promise<void> {
    const envelope: EventEnvelope = {
      event_type: routingKey,
      occurred_at: new Date().toISOString(),
      tenant_id: PLATFORM_TENANT_ID,
      data,
    };
    try {
      await this.eventPublisher.publish(PLATFORM_AUDIT_EXCHANGE, routingKey, envelope);
    } catch (err) {
      logger.warn({ err, routingKey }, "[Announcements] Audit event publish failed — continuing");
    }
  }

  async create(input: CreateAnnouncementInput): Promise<AnnouncementDto> {
    const immediate = !input.scheduledFor;
    const row = (await this.prisma.announcement.create({
      data: {
        title: input.title,
        body: input.body,
        type: input.type,
        targetSegment: input.targetSegment,
        channels: input.channels,
        scheduledFor: input.scheduledFor ? new Date(input.scheduledFor) : null,
        sentAt: immediate ? new Date() : null,
        createdBy: input.createdBy,
      },
    })) as AnnouncementRow;

    await this.publishSafely(ANNOUNCEMENT_ROUTING_KEYS.CREATED, {
      announcement_id: row.id,
      title: row.title,
      type: row.type,
      target_segment: row.targetSegment,
    });

    if (immediate) {
      await this.publishSafely(ANNOUNCEMENT_ROUTING_KEYS.BROADCAST, {
        announcement_id: row.id,
        title: row.title,
        body: row.body,
        type: row.type,
        target_segment: row.targetSegment,
        channels: row.channels,
      });
    }

    return toDto(row);
  }

  async list(query: ListAnnouncementsQuery): Promise<ListAnnouncementsResult> {
    const where = query.type ? { type: query.type } : {};
    const [rows, total] = await Promise.all([
      this.prisma.announcement.findMany({
        where,
        orderBy: { createdAt: "desc" },
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
      }) as Promise<AnnouncementRow[]>,
      this.prisma.announcement.count({ where }),
    ]);
    return {
      announcements: rows.map(toDto),
      page: query.page,
      pageSize: query.pageSize,
      total,
    };
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- announcements`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/modules/announcements/v1/types.ts packages/gen-sup-starter/src/modules/announcements/v1/service.ts packages/gen-sup-starter/src/modules/announcements/v1/service.test.ts
git commit -m "feat: add AnnouncementsService create/list"
```

---

### Task 3: `AnnouncementsService.dispatchScheduled()` — the host-driven sweep

**Files:**
- Modify: `packages/gen-sup-starter/src/modules/announcements/v1/service.ts`
- Modify: `packages/gen-sup-starter/src/modules/announcements/v1/service.test.ts`

**Interfaces:**
- Produces: `AnnouncementsService.dispatchScheduled(): Promise<DispatchScheduledResult>` — consumed by Task 5 (exposed on `GenSupInstance` for the host to call on its own interval).

- [ ] **Step 1: Write the failing test**

Append to `service.test.ts`:

```ts
  describe("dispatchScheduled", () => {
    it("broadcasts and stamps due announcements, returning an accurate dispatched count", async () => {
      const dueRow = baseRow({ id: "a2", scheduledFor: new Date("2026-01-01T00:00:00Z"), sentAt: null });
      const findMany = vi.fn(async () => [dueRow]);
      const update = vi.fn(async () => ({ ...dueRow, sentAt: new Date() }));
      const prisma = fakePrisma({ findMany, update });
      const eventPublisher = fakeEventPublisher();
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.dispatchScheduled();

      expect(result).toEqual({ dispatched: 1, failed: 0 });
      expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
        where: { sentAt: null, scheduledFor: expect.objectContaining({ lte: expect.any(Date) }) },
      }));
      expect(update).toHaveBeenCalledWith({ where: { id: "a2" }, data: { sentAt: expect.any(Date) } });
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.broadcast", expect.objectContaining({ event_type: "sup.announcement.broadcast" }),
      );
    });

    it("returns 0/0 when nothing is due", async () => {
      const prisma = fakePrisma({ findMany: vi.fn(async () => []) });
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      expect(await service.dispatchScheduled()).toEqual({ dispatched: 0, failed: 0 });
    });

    it("counts a failed row without aborting the rest of the sweep", async () => {
      const dueRows = [
        baseRow({ id: "a2", sentAt: null }),
        baseRow({ id: "a3", sentAt: null }),
      ];
      const findMany = vi.fn(async () => dueRows);
      const update = vi.fn(async ({ where }: { where: { id: string } }) => {
        if (where.id === "a2") throw new Error("db write failed");
        return { ...baseRow({ id: where.id }), sentAt: new Date() };
      });
      const prisma = fakePrisma({ findMany, update });
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      const result = await service.dispatchScheduled();

      expect(result).toEqual({ dispatched: 1, failed: 1 });
      expect(update).toHaveBeenCalledTimes(2);
    });

    it("does not count a failed audit-event publish as a dispatch failure — publishSafely is fire-and-forget", async () => {
      const dueRow = baseRow({ id: "a2", sentAt: null });
      const prisma = fakePrisma({ findMany: vi.fn(async () => [dueRow]) });
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.dispatchScheduled();

      // The row is still marked sent (the publish failure was swallowed by
      // publishSafely) — "failed" here tracks failure to persist sentAt, not
      // audit-publish hiccups, which are best-effort everywhere else in this
      // codebase too.
      expect(result).toEqual({ dispatched: 1, failed: 0 });
    });
  });
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- announcements`
Expected: FAIL — `service.dispatchScheduled is not a function`

- [ ] **Step 3: Implement `dispatchScheduled`**

Add this method to the `AnnouncementsService` class in `service.ts` (alongside `create`/`list`):

```ts
  async dispatchScheduled(): Promise<DispatchScheduledResult> {
    const due = (await this.prisma.announcement.findMany({
      where: { sentAt: null, scheduledFor: { lte: new Date() } },
    })) as AnnouncementRow[];

    let dispatched = 0;
    let failed = 0;
    for (const row of due) {
      try {
        // publishSafely never throws — a broker outage here is logged and
        // swallowed, matching the fire-and-forget audit pattern used
        // everywhere else in this codebase (tenants/feature-flags). It does
        // NOT count toward `failed` below; only a failure to persist
        // `sentAt` does, since that's what actually needs a retry on the
        // next sweep.
        await this.publishSafely(ANNOUNCEMENT_ROUTING_KEYS.BROADCAST, {
          announcement_id: row.id,
          title: row.title,
          body: row.body,
          type: row.type,
          target_segment: row.targetSegment,
          channels: row.channels,
        });
        await this.prisma.announcement.update({ where: { id: row.id }, data: { sentAt: new Date() } });
        dispatched++;
      } catch (err) {
        failed++;
        logger.error({ err, announcementId: row.id }, "[Announcements] Failed to mark scheduled announcement as sent");
      }
    }

    return { dispatched, failed };
  }
```

Also add the `DispatchScheduledResult` import to `service.ts`'s existing import from `./types.ts` (it's already defined in `types.ts` from Task 2 — just add it to the destructured type-only import list).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- announcements`
Expected: PASS (10 tests total: 6 from Task 2 + 4 new)

- [ ] **Step 5: Run the full suite and build**

Run: `npm test --workspace=@gen-ms/gen-sup-starter && npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/modules/announcements/v1/service.ts packages/gen-sup-starter/src/modules/announcements/v1/service.test.ts
git commit -m "feat: add AnnouncementsService.dispatchScheduled host-driven sweep"
```

---

### Task 4: Schema, controller, router

**Files:**
- Create: `packages/gen-sup-starter/src/modules/announcements/v1/schema.ts`
- Create: `packages/gen-sup-starter/src/modules/announcements/v1/controller.ts`
- Create: `packages/gen-sup-starter/src/modules/announcements/v1/router.ts`

**Interfaces:**
- Consumes: `AnnouncementsService` (Task 2/3), `internalSecret` middleware (existing `middleware/internal-secret.ts`).
- Produces: `createAnnouncementsRouter(deps: { announcementsService, internalSecretValue })` — consumed by Task 5.

No dedicated test file — matching `tenants`/`feature-flags`' convention, controller/router behavior is verified via route-level smoke tests in `create-gen-sup.test.ts` (Task 5).

- [ ] **Step 1: Write `schema.ts`**

```ts
// packages/gen-sup-starter/src/modules/announcements/v1/schema.ts
import { z } from "zod";

export const ANNOUNCEMENT_TYPES = ["INFO", "MAINTENANCE", "NEW_FEATURE", "PRICING_CHANGE", "CRITICAL"] as const;
export const ANNOUNCEMENT_CHANNELS = ["EMAIL", "IN_APP"] as const;

export const createAnnouncementSchema = z.object({
  title: z.string().min(3).max(500),
  body: z.string().min(10).max(10000),
  type: z.enum(ANNOUNCEMENT_TYPES).default("INFO"),
  targetSegment: z.string().min(1).max(64).default("ALL"),
  channels: z.array(z.enum(ANNOUNCEMENT_CHANNELS)).min(1),
  scheduledFor: z.string().datetime({ offset: true }).optional(),
  createdBy: z.string().uuid(),
});

export const listAnnouncementsQuerySchema = z.object({
  type: z.enum(ANNOUNCEMENT_TYPES).optional(),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(100).default(20),
});
```

- [ ] **Step 2: Write `controller.ts`**

```ts
// packages/gen-sup-starter/src/modules/announcements/v1/controller.ts
import type { Request, Response } from "express";
import { createAnnouncementSchema, listAnnouncementsQuerySchema } from "./schema.ts";
import type { AnnouncementsService } from "./service.ts";

export function makeAnnouncementsController(service: AnnouncementsService) {
  return {
    async create(req: Request, res: Response) {
      const input = createAnnouncementSchema.parse(req.body);
      const announcement = await service.create(input);
      res.status(201).json({ announcement });
    },
    async list(req: Request, res: Response) {
      const query = listAnnouncementsQuerySchema.parse(req.query);
      const result = await service.list(query);
      res.json(result);
    },
  };
}
```

- [ ] **Step 3: Write `router.ts`**

```ts
// packages/gen-sup-starter/src/modules/announcements/v1/router.ts
import { Router } from "express";
import { makeAnnouncementsController } from "./controller.ts";
import type { AnnouncementsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface AnnouncementsRouterDeps {
  announcementsService: AnnouncementsService;
  internalSecretValue: string;
}

export function createAnnouncementsRouter(deps: AnnouncementsRouterDeps): Router {
  const router = Router();
  const controller = makeAnnouncementsController(deps.announcementsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.post("/", (req, res, next) => controller.create(req, res).catch(next));
  router.get("/", (req, res, next) => controller.list(req, res).catch(next));

  return router;
}
```

- [ ] **Step 4: Build to confirm no type errors**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add packages/gen-sup-starter/src/modules/announcements/v1/schema.ts packages/gen-sup-starter/src/modules/announcements/v1/controller.ts packages/gen-sup-starter/src/modules/announcements/v1/router.ts
git commit -m "feat: add announcements controller, router, and schema"
```

---

### Task 5: Wire into `createGenSup` and the public barrel

**Files:**
- Modify: `packages/gen-sup-starter/src/create-gen-sup.ts`
- Modify: `packages/gen-sup-starter/src/index.ts`
- Modify: `packages/gen-sup-starter/src/create-gen-sup.test.ts`
- Modify: `packages/gen-sup-demo/src/index.ts`

**Interfaces:**
- Consumes: everything from Tasks 1-4.
- Produces: `GenSupConfig.prisma`/`databaseUrl`, `GenSupModulesConfig.announcements`, `GenSupInstance.prisma`/`announcementsService` — the module is now live end-to-end.

- [ ] **Step 1: Modify `create-gen-sup.ts`**

Add these imports alongside the existing feature-flags-related ones:

```ts
import { createPrismaClient, type PrismaClient } from "./infra/persistence/prisma-client.ts";
import { AnnouncementsService } from "./modules/announcements/v1/service.ts";
import { createAnnouncementsRouter } from "./modules/announcements/v1/router.ts";
```

Extend `GenSupModulesConfig`:

```ts
export interface GenSupModulesConfig {
  dashboard?: boolean;
  tenants?: boolean;
  featureFlags?: boolean;
  announcements?: boolean;
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
  prisma?: PrismaClient;
  databaseUrl?: string;
  eventPublisher?: EventPublisher;
  rabbitMqUrl?: string;
  modules?: GenSupModulesConfig;
}
```

Add `resolvePrismaClient` alongside `resolveTntClient`/`resolveFmmClient`:

```ts
function resolvePrismaClient(override: PrismaClient | undefined, databaseUrlOverride: string | undefined): PrismaClient {
  if (override) return override;
  const databaseUrl = databaseUrlOverride ?? requireEnv("DATABASE_URL");
  return createPrismaClient(databaseUrl);
}
```

Extend `GenSupInstance` (add two new optional fields with the same doc-comment style as the existing ones):

```ts
export interface GenSupInstance {
  app: Express;
  // Present only when the dashboard module is enabled (the only module that
  // constructs a Valkey client today). Exposed so a host can close the
  // connection on shutdown, e.g. `instance.valkey?.quit()`.
  valkey?: Redis;
  // Present only when at least one of tenants/featureFlags/announcements is
  // enabled and no eventPublisher override was supplied (an override is the
  // caller's own resource to manage). Exposed so a host can close the
  // RabbitMQ connection on shutdown, e.g. `instance.eventPublisher?.close()`.
  eventPublisher?: RabbitMqBus;
  // Present only when the announcements module is enabled and no prisma
  // override was supplied. Exposed so a host can close the connection on
  // shutdown, e.g. `instance.prisma?.$disconnect()`.
  prisma?: PrismaClient;
  // Present only when the announcements module is enabled. Exposed so a host
  // can call `instance.announcementsService?.dispatchScheduled()` on its own
  // interval/cron — Gen_SUP adds no internal scheduler.
  announcementsService?: AnnouncementsService;
}
```

In `createGenSup`, add `announcements` to the resolved `modules` object:

```ts
  const modules: Required<GenSupModulesConfig> = {
    dashboard: config.modules?.dashboard ?? true,
    tenants: config.modules?.tenants ?? true,
    featureFlags: config.modules?.featureFlags ?? true,
    announcements: config.modules?.announcements ?? true,
  };
```

Add the new module block after the existing `if (modules.featureFlags)` block, and declare `prismaInstance`/`announcementsService` before it (alongside the existing `let eventPublisher: RabbitMqBus | undefined;`):

```ts
  let prismaInstance: PrismaClient | undefined;
  let announcementsService: AnnouncementsService | undefined;
  if (modules.announcements) {
    const prisma = resolvePrismaClient(config.prisma, config.databaseUrl);
    if (!config.prisma) prismaInstance = prisma;
    const publisher = resolvePublisher();
    announcementsService = new AnnouncementsService(prisma, publisher);
    app.use("/api/v1/announcements", createAnnouncementsRouter({ announcementsService, internalSecretValue }));
  }
```

Update the final `return` statement:

```ts
  return { app, valkey, eventPublisher, prisma: prismaInstance, announcementsService };
```

- [ ] **Step 2: Update `index.ts` barrel**

Add:

```ts
export type { PrismaClient, Announcement } from "./infra/persistence/prisma-client.ts";
export { createPrismaClient } from "./infra/persistence/prisma-client.ts";
export { ANNOUNCEMENT_ROUTING_KEYS } from "./config/constants.ts";
export type { AnnouncementDto, CreateAnnouncementInput, ListAnnouncementsQuery, ListAnnouncementsResult, DispatchScheduledResult } from "./modules/announcements/v1/types.ts";
export { AnnouncementsService } from "./modules/announcements/v1/service.ts";
```

- [ ] **Step 3: Audit every pre-existing test in `create-gen-sup.test.ts`**

Read the CURRENT file first — do not assume its test count from this plan's description. As of the `feature-flags` module's merge it has 9 tests. Every one that constructs `createGenSup(...)` without disabling `announcements` will now fail (missing `DATABASE_URL`/`RABBITMQ_URL` or no `prisma` override), *except* the first (`throws GenSupConfigError...`, which fails before any module resolution runs). For each of the other 8, add `announcements: false` to its existing `modules: {...}` object. Update the comment on the `/health`-with-everything-disabled test to also mention `DATABASE_URL`.

- [ ] **Step 4: Add new tests for the announcements module**

Append to `create-gen-sup.test.ts`:

```ts
  it("exposes POST /api/v1/announcements gated on X-Internal-Secret when a prisma override is supplied", async () => {
    const fakePrisma = {
      announcement: {
        create: async ({ data }: { data: Record<string, unknown> }) => ({
          id: "a1", ...data, scheduledFor: null, sentAt: new Date(), createdAt: new Date(),
        }),
        findMany: async () => { throw new Error("not used in this test"); },
        count: async () => { throw new Error("not used in this test"); },
        update: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      prisma: fakePrisma,
      eventPublisher,
      modules: { dashboard: false, tenants: false, featureFlags: false },
    });

    const unauthorized = await request(app).post("/api/v1/announcements").send({
      title: "Scheduled maintenance", body: "We will be performing maintenance this weekend.",
      channels: ["EMAIL"], createdBy: "11111111-1111-1111-1111-111111111111",
    });
    expect(unauthorized.status).toBe(401);

    const ok = await request(app)
      .post("/api/v1/announcements")
      .set("X-Internal-Secret", "s3cret")
      .send({
        title: "Scheduled maintenance", body: "We will be performing maintenance this weekend.",
        channels: ["EMAIL"], createdBy: "11111111-1111-1111-1111-111111111111",
      });
    expect(ok.status).toBe(201);
    expect(ok.body.announcement.title).toBe("Scheduled maintenance");
  });

  it("exposes GET /api/v1/announcements gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      announcement: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => [],
        count: async () => 0,
        update: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      prisma: fakePrisma,
      eventPublisher,
      modules: { dashboard: false, tenants: false, featureFlags: false },
    });

    const unauthorized = await request(app).get("/api/v1/announcements");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/announcements").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.announcements).toEqual([]);
  });

  it("returns 400 VALIDATION_ERROR (not a 500) when POST /api/v1/announcements has a body shorter than 10 chars", async () => {
    const fakePrisma = {
      announcement: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
        count: async () => { throw new Error("not used in this test"); },
        update: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      prisma: fakePrisma,
      eventPublisher,
      modules: { dashboard: false, tenants: false, featureFlags: false },
    });

    const res = await request(app)
      .post("/api/v1/announcements")
      .set("X-Internal-Secret", "s3cret")
      .send({ title: "Hi", body: "too short", channels: ["EMAIL"], createdBy: "11111111-1111-1111-1111-111111111111" });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });
```

- [ ] **Step 5: Update `gen-sup-demo`**

Read `packages/gen-sup-demo/src/index.ts` first (its current `modules` override is `{ tenants: false, featureFlags: false }`). Add `announcements: false` to the same object, since the demo doesn't wire a `prisma`/`databaseUrl`:

```ts
export function startDemo() {
  const { app } = createGenSup({ tenantMetricsPort: sampleTenantMetricsPort, modules: { tenants: false, featureFlags: false, announcements: false } });
  const server = app.listen(PORT, () => {
    console.log(`Gen_SUP demo listening on port ${PORT}`);
  });
  return { app, server };
}
```

- [ ] **Step 6: Run the full test suite and build**

Run: `npm test && npm run build`
Expected: PASS (all tests, including the 3 new ones and the 8 updated ones)

- [ ] **Step 7: Commit**

```bash
git add packages/gen-sup-starter/src/create-gen-sup.ts packages/gen-sup-starter/src/index.ts packages/gen-sup-starter/src/create-gen-sup.test.ts packages/gen-sup-demo/src/index.ts
git commit -m "feat: wire announcements module into createGenSup and public barrel"
```

---

### Task 6: Documentation

**Files:**
- Modify: `docs/integration-guide.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing (docs only).
- Produces: nothing consumed by later tasks — this is the final task.

- [ ] **Step 1: Add an "Announcements" API reference section to `docs/integration-guide.md`**

Insert after the existing "Feature flags" section, before "## Error codes":

```markdown
### Announcements — `/api/v1/announcements`

Gen_SUP's first module with local persistence — announcements are stored in
Gen_SUP's own Postgres database (`DATABASE_URL`), not proxied to a sibling
service. There is **no real delivery**: `channels` is stored but inert, and
`targetSegment` (e.g. `"ALL"`, `"plan:GROWTH"`, `"region:IN"`) is a free-form
string nobody in Gen_MS resolves into real tenants/users yet — this module
matches CPMS `sup-svc`'s own behavior, which never delivered anything either.

```
POST /api/v1/announcements
X-Internal-Secret: <secret>
Content-Type: application/json

{
  "title": "Scheduled maintenance",
  "body": "We will be performing maintenance this weekend.",
  "type": "MAINTENANCE",
  "targetSegment": "ALL",
  "channels": ["EMAIL", "IN_APP"],
  "scheduledFor": "2026-09-01T02:00:00Z",
  "createdBy": "11111111-1111-1111-1111-111111111111"
}
```

→ `201` with the created announcement. `createdBy` is required and
caller-supplied — Gen_SUP has no user identity of its own (only the shared
`X-Internal-Secret`). If `scheduledFor` is omitted, the announcement
broadcasts immediately (publishes `sup.announcement.broadcast` and stamps
`sentAt`); if provided, it's held until `dispatchScheduled()` picks it up.
Every create also publishes `sup.announcement.created` regardless of
scheduling. There is no update/delete/cancel — a sent announcement is
immutable history.

```
GET /api/v1/announcements?type=CRITICAL&page=1&pageSize=20
X-Internal-Secret: <secret>
```

→ `200`, `{ "announcements": [...], "page": 1, "pageSize": 20, "total": 3 }`.

**Scheduled dispatch is host-driven** — Gen_SUP adds no scheduler. Call
`instance.announcementsService?.dispatchScheduled()` on your own interval or
cron to send anything that's come due; it returns `{ dispatched, failed }`
and never throws (a single row's dispatch failure doesn't abort the rest of
the sweep).
```

- [ ] **Step 2: Update "Known limitations"**

Update the module list and add announcements-specific notes:

```markdown
- `dashboard`, `tenants`, `feature-flags`, and `announcements` are the only
  modules built so far. `analytics`, `tickets`, and `impersonate` are planned.
- `dashboard`/`tenants`/`feature-flags` have no local persistence — they rely
  entirely on `TenantMetricsPort`/Gen_TNT/Gen_FMM respectively.
  `announcements` is the first module with a local Postgres table.
- `announcements` never actually delivers anything — `channels` is stored
  but inert, and `targetSegment` resolution (which real tenants/users match
  a segment string) requires a capability (tenant listing, user directory)
  that doesn't exist anywhere in Gen_MS yet.
- No live-Postgres integration test for `announcements` in this pass — its
  test suite mocks `PrismaClient` entirely.
```

- [ ] **Step 3: Add an "Upgrading" note**

Same breaking-change-callout style as the `tenants`/`feature-flags` notes:

```markdown
`announcements` also now defaults to `true` — an existing embedder must pass
`modules: { announcements: false }` or supply both `DATABASE_URL` and
`RABBITMQ_URL` (or `prisma`/`eventPublisher` overrides) before upgrading,
the same as the `tenants`/`feature-flags` modules' own breaking-change notes
above (`RABBITMQ_URL` is shared with those modules if either is already
enabled — only newly required if both are disabled).
```

- [ ] **Step 4: Update the Local development section if it lists modules by name**

Check `docs/integration-guide.md`'s "Local development" section — if it enumerates modules, add `announcements`. No `docker-compose.yml` change is needed (Postgres is already provisioned there).

- [ ] **Step 5: Update `README.md`**

Update the Gen_SUP status line to mention all four modules (`dashboard`, `tenants`, `feature-flags`, `announcements`).

- [ ] **Step 6: Run the full test suite and build once more**

Run: `npm test && npm run build`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add docs/integration-guide.md README.md
git commit -m "docs: document the announcements module in the integration guide and README"
```

---

## Plan Self-Review Notes

- **Spec coverage:** create (immediate + scheduled), list (paginated, filtered), dispatchScheduled (host-driven sweep, per-row failure isolation), audit events (CREATED always, BROADCAST on actual send), no-delivery/no-CRUD-beyond-create-list scope, first-Prisma-model persistence — all covered by a task each.
- **Type consistency:** `AnnouncementDto`/`CreateAnnouncementInput`/`ListAnnouncementsQuery`/`ListAnnouncementsResult`/`DispatchScheduledResult` (Task 2 `types.ts`) flow unchanged through `service.ts` (Tasks 2-3) into `controller.ts` (Task 4) — checked no field-name drift. `PrismaClient`/`Announcement` re-exported once from `infra/persistence/prisma-client.ts` (Task 1) and imported from there everywhere else — no file reaches into `__generated__/prisma` directly except that one wrapper.
- **Regression-class carryover:** Task 5 explicitly walks all 8 pre-existing `create-gen-sup.test.ts` tests (not just "the newest one") — the same class of bug missed once by a plan and once more by an implementer during the `tenants` module, and now a standing checklist item for every module's wiring task.
- **`dispatchScheduled`'s `failed` count is deliberately narrow** (persistence failure only, not audit-publish failure) — called out explicitly in Task 3's Step 3 code comment and Step 1's fourth test, so neither the implementer nor a later reviewer mistakes it for a gap.
- **First-Prisma-model risk**: Task 1 explicitly handles the case where no live Postgres is available in the execution environment (hand-written migration SQL as a fallback to `prisma migrate dev`), so this task can complete without assuming Docker is running.
