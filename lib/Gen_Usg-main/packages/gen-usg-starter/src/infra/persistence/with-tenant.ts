import type { PrismaClient } from "../../../__generated__/prisma/index.js";
import { getPrismaClient } from "./prisma-client.ts";
import { InvalidTenantIdError } from "../../common/errors.ts";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function withTenant<T>(
  tenantId: string,
  fn: (tx: PrismaClient) => Promise<T>,
): Promise<T> {
  if (!UUID_RE.test(tenantId)) {
    throw new InvalidTenantIdError(tenantId);
  }
  return getPrismaClient().$transaction(async (tx) => {
    // SET LOCAL doesn't accept parameterized placeholders in Postgres;
    // injection is closed off by the UUID_RE guard above, not by
    // parameterization — this must run before any relaxation of that guard.
    await (tx as unknown as PrismaClient).$executeRawUnsafe(`SET LOCAL app.tenant_id = '${tenantId}'`);
    return fn(tx as PrismaClient);
  });
}
