import type { IIdempotencyRepo } from "../../domain/ports/idempotency.repository.port.ts";
import { getPrismaClient } from "./prisma-client.ts";

export class PrismaIdempotencyRepo implements IIdempotencyRepo {
  async exists(eventId: string): Promise<boolean> {
    const row = await getPrismaClient().usageIdempotencyLedger.findUnique({ where: { eventId } });
    return row !== null;
  }

  async insert(eventId: string): Promise<void> {
    // No withTenant() — this table has no tenant_id column and no RLS policy.
    await getPrismaClient().usageIdempotencyLedger.createMany({
      data: [{ eventId }],
      skipDuplicates: true,
    });
  }
}
