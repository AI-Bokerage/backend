import type { PrismaClient } from "@prisma/client";
import { getPrismaClient } from "./prisma-client.ts";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function withTenant<T>(
  tenantId: string,
  fn: (tx: PrismaClient) => Promise<T>,
): Promise<T> {
  if (!UUID_RE.test(tenantId)) {
    throw new Error(`Invalid tenantId format: ${tenantId}`);
  }
  return getPrismaClient().$transaction(async (tx) => {
    // SET LOCAL doesn't accept parameterized placeholders in Postgres;
    // injection is closed off by the UUID_RE guard above, not by
    // parameterization — this must run before any relaxation of that guard.
    await (tx as unknown as PrismaClient).$executeRawUnsafe(`SET LOCAL app.tenant_id = '${tenantId}'`);
    return fn(tx as PrismaClient);
  });
}
