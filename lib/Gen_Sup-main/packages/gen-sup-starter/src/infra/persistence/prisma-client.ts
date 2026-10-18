// packages/gen-sup-starter/src/infra/persistence/prisma-client.ts
import { PrismaClient, Prisma } from "../../../__generated__/prisma/index.js";

export { PrismaClient, Prisma };
export type { Announcement, RevenueSnapshot } from "../../../__generated__/prisma/index.js";

export function createPrismaClient(databaseUrl: string): PrismaClient {
  return new PrismaClient({ datasources: { db: { url: databaseUrl } } });
}
