import { PrismaClient } from "@prisma/client";
import { requireEnv } from "../../config/env.ts";

let client: PrismaClient | undefined;
let adminClient: PrismaClient | undefined;

export function getPrismaClient(): PrismaClient {
  if (!client) {
    client = new PrismaClient();
  }
  return client;
}

// Second, admin-only connection used solely by the cross-tenant admin
// listing path (overrides listAll). It is bound to GEN_FMM_ADMIN_DATABASE_URL,
// a role with RLS-bypass privilege, instead of the genfmm_app connection
// getPrismaClient() above returns — genfmm_app deliberately has no
// BYPASSRLS grant, so it can never see rows outside the tenant set via
// SET LOCAL app.tenant_id. Never use this for regular tenant-scoped queries.
export function getAdminPrismaClient(): PrismaClient {
  if (!adminClient) {
    adminClient = new PrismaClient({
      datasources: { db: { url: requireEnv("GEN_FMM_ADMIN_DATABASE_URL") } },
    });
  }
  return adminClient;
}
