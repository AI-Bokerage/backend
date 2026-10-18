import { describe, it, expect, vi } from "vitest";
import { PrismaOutboxWriter } from "./outbox-writer.ts";

describe("PrismaOutboxWriter", () => {
  it("enqueue writes a PENDING row with retryCount 0", async () => {
    const create = vi.fn(async () => undefined);
    const prisma = { slaOutboxEvent: { create } } as any;
    const writer = new PrismaOutboxWriter(prisma);

    await writer.enqueue({ tenantId: "t1", eventType: "sla.instance.created", exchange: "gen-sla.events", routingKey: "sla.instance.created", payload: { a: 1 } });

    expect(create).toHaveBeenCalledWith({
      data: { tenantId: "t1", eventType: "sla.instance.created", exchange: "gen-sla.events", routingKey: "sla.instance.created", payload: { a: 1 }, status: "PENDING", retryCount: 0 },
    });
  });
});
