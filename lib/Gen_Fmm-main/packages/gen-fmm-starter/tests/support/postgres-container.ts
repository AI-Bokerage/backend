import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { PrismaClient } from "@prisma/client";
import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { getPrismaClient, getAdminPrismaClient } from "../../src/infra/persistence/prisma-client.ts";

export interface TestPostgres {
  prisma: PrismaClient;
  stop: () => Promise<void>;
}

const APP_ROLE = "genfmm_app";
const APP_ROLE_PASSWORD = "genfmm_app";

export async function startPostgresContainer(): Promise<TestPostgres> {
  const container: StartedPostgreSqlContainer = await new PostgreSqlContainer("postgres:15").start();
  const adminUrl = container.getConnectionUri();

  execSync("npx prisma migrate deploy", {
    cwd: fileURLToPath(new URL("../..", import.meta.url)),
    env: { ...process.env, DATABASE_URL: adminUrl },
    stdio: "inherit",
  });

  const appUrl = new URL(adminUrl);
  appUrl.username = APP_ROLE;
  appUrl.password = APP_ROLE_PASSWORD;

  // getAdminPrismaClient() (used by overrides listAll) needs an RLS-bypassing
  // connection; the superuser adminUrl already used to run migrations above
  // is exactly that, so reuse it rather than provisioning a third role.
  process.env.GEN_FMM_ADMIN_DATABASE_URL = adminUrl;
  process.env.DATABASE_URL = appUrl.toString();
  const prisma = getPrismaClient();

  return {
    prisma,
    stop: async () => {
      await prisma.$disconnect();
      await getAdminPrismaClient().$disconnect();
      await container.stop();
    },
  };
}
