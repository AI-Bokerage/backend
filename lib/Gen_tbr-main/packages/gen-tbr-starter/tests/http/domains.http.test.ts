import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { createDomainsRouter } from "../../src/modules/domains/v1/domain.router.ts";
import { DomainService } from "../../src/modules/domains/v1/domain.service.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ITenantDomainRepo, TenantDomainRecord } from "../../src/domain/ports/tenant-domain.repository.port.ts";
import type { IDnsVerifier } from "../../src/domain/ports/dns-verifier.port.ts";

function buildApp() {
  const store = new Map<string, TenantDomainRecord>();
  let seq = 0;

  const domainRepo: ITenantDomainRepo = {
    create: vi.fn(async (input) => {
      const rec: TenantDomainRecord = {
        id: `domain-${++seq}`, tenantId: input.tenantId, domain: input.domain,
        verificationToken: input.verificationToken, status: "PENDING",
        verificationMethod: input.verificationMethod, verifiedAt: null, lastCheckedAt: null,
        isPrimary: false, createdAt: new Date(), updatedAt: new Date(),
      };
      store.set(rec.id, rec);
      return rec;
    }),
    findById: vi.fn(async (id) => store.get(id) ?? null),
    findByDomain: vi.fn(async (domain) => [...store.values()].find((r) => r.domain === domain) ?? null),
    listByTenant: vi.fn(async (tenantId, opts) =>
      [...store.values()].filter((r) => r.tenantId === tenantId && (opts?.includeDetached || r.status !== "DETACHED")),
    ),
    updateStatus: vi.fn(async (id, status, fields) => {
      const rec = store.get(id)!;
      const updated = { ...rec, status, ...fields };
      store.set(id, updated);
      return updated;
    }),
    setPrimary: vi.fn(async (tenantId, id) => {
      for (const [key, rec] of store) {
        if (rec.tenantId === tenantId) store.set(key, { ...rec, isPrimary: key === id });
      }
      return store.get(id)!;
    }),
    touchLastChecked: vi.fn(async (id, at) => {
      const rec = store.get(id)!;
      store.set(id, { ...rec, lastCheckedAt: at });
    }),
  };

  const dnsVerifier: IDnsVerifier = {
    resolveTxt: vi.fn(async () => {
      const rec = [...store.values()][0];
      return [[`gen-tbr-verify=${rec.verificationToken}`]];
    }),
    resolveCname: vi.fn(async () => []),
  };

  const service = new DomainService(domainRepo, dnsVerifier);
  const app = express();
  app.use(express.json());
  app.use("/api/v1/domains", createDomainsRouter({ domainService: service, internalSecretValue: "s3cret" }));
  app.use(errorHandler);
  return { app };
}

describe("domains router", () => {
  it("runs the full claim -> verify -> activate -> primary -> detach lifecycle", async () => {
    const { app } = buildApp();
    const auth = (req: any) => req.set("X-Internal-Secret", "s3cret");

    const create = await auth(request(app).post("/api/v1/domains")).send({
      tenantId: "11111111-1111-1111-1111-111111111111",
      domain: "Example.COM",
    });
    expect(create.status).toBe(201);
    expect(create.body.status).toBe("PENDING");
    expect(create.body.domain).toBe("example.com");
    const id = create.body.id;

    const verify = await auth(
      request(app).post(`/api/v1/domains/${id}/verify`).send({ tenantId: "11111111-1111-1111-1111-111111111111" }),
    );
    expect(verify.status).toBe(200);
    expect(verify.body.status).toBe("VERIFIED");

    const activate = await auth(
      request(app).post(`/api/v1/domains/${id}/activate`).send({ tenantId: "11111111-1111-1111-1111-111111111111" }),
    );
    expect(activate.status).toBe(200);
    expect(activate.body.status).toBe("ACTIVE");

    const primary = await auth(
      request(app).post(`/api/v1/domains/${id}/primary`).send({ tenantId: "11111111-1111-1111-1111-111111111111" }),
    );
    expect(primary.status).toBe(200);
    expect(primary.body.isPrimary).toBe(true);

    const detach = await auth(
      request(app).post(`/api/v1/domains/${id}/detach`).send({ tenantId: "11111111-1111-1111-1111-111111111111" }),
    );
    expect(detach.status).toBe(200);
    expect(detach.body.status).toBe("DETACHED");

    const list = await auth(
      request(app).get("/api/v1/domains").query({ tenantId: "11111111-1111-1111-1111-111111111111" }),
    );
    expect(list.body).toEqual([]);
  });

  it("requires the internal secret on every mutating route", async () => {
    const { app } = buildApp();
    const res = await request(app).post("/api/v1/domains").send({ tenantId: "t1", domain: "x.com" });
    expect(res.status).toBe(401);
  });

  it("returns 404 when detach is called with a tenantId that doesn't own the domain", async () => {
    const { app } = buildApp();
    const auth = (req: any) => req.set("X-Internal-Secret", "s3cret");

    const create = await auth(request(app).post("/api/v1/domains")).send({
      tenantId: "11111111-1111-1111-1111-111111111111",
      domain: "example.com",
    });
    const id = create.body.id;

    const detach = await auth(
      request(app).post(`/api/v1/domains/${id}/detach`).send({ tenantId: "22222222-2222-2222-2222-222222222222" }),
    );
    expect(detach.status).toBe(404);
  });
});
