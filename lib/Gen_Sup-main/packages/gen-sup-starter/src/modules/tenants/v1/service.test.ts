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

    it("throws TntClientError (not TenantNotFoundError) when Gen_TNT returns a 404 on create", async () => {
      const tntClient = fakeTntClient({ createTenant: vi.fn(async () => { throw new TntHttpError(404, "route missing"); }) });
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

    it("getById throws TntClientError (not an uncaught error) when Gen_TNT returns a 5xx", async () => {
      const tntClient = fakeTntClient({ getTenant: vi.fn(async () => { throw new TntHttpError(500, "boom"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.getById("t1")).rejects.toThrow(TntClientError);
    });

    it("getById throws TenantNotFoundError when Gen_TNT returns a 404 TntHttpError", async () => {
      const tntClient = fakeTntClient({ getTenant: vi.fn(async () => { throw new TntHttpError(404, "not found"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.getById("t1")).rejects.toThrow(TenantNotFoundError);
    });

    it("getBySlug throws TntClientError (not an uncaught error) when Gen_TNT returns a 5xx", async () => {
      const tntClient = fakeTntClient({ getTenantBySlug: vi.fn(async () => { throw new TntHttpError(502, "bad gateway"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.getBySlug("acme")).rejects.toThrow(TntClientError);
    });

    it("getById throws TntClientError (not TenantSlugTakenError) when Gen_TNT returns a 409", async () => {
      const tntClient = fakeTntClient({ getTenant: vi.fn(async () => { throw new TntHttpError(409, "conflict"); }) });
      const service = new TenantsService(tntClient, fakeEventPublisher());
      await expect(service.getById("t1")).rejects.toThrow(TntClientError);
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
