import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { PrismaClient } from "@prisma/client";
import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { getPrismaClient } from "../../src/infra/persistence/prisma-client.ts";

export interface TestPostgres {
  prisma: PrismaClient;
  stop: () => Promise<void>;
}

// ponytail: Testcontainers' bootstrap user is a Postgres superuser, and
// superusers always bypass row-level security (even FORCE RLS) — connecting
// as that user would make every RLS policy silently inert. The
// 20260728091000_create_app_role migration creates an unprivileged
// `genusg_app` role as part of `prisma migrate deploy` below; connect
// through that instead, same as a correctly configured real deployment
// should (see .env.example at the repo root for the same split).
const APP_ROLE = "genusg_app";
const APP_ROLE_PASSWORD = "genusg_app";

export async function startPostgresContainer(): Promise<TestPostgres> {
  const container: StartedPostgreSqlContainer = await new PostgreSqlContainer("postgres:15").start();
  const adminUrl = container.getConnectionUri();

  execSync("npx prisma migrate deploy", {
    // ponytail: fileURLToPath, not `.pathname` — `.pathname` leaves a leading
    // slash ("/C:/...") on Windows, which is not a valid cwd.
    cwd: fileURLToPath(new URL("../..", import.meta.url)),
    env: { ...process.env, DATABASE_URL: adminUrl },
    stdio: "inherit",
  });

  const appUrl = new URL(adminUrl);
  appUrl.username = APP_ROLE;
  appUrl.password = APP_ROLE_PASSWORD;

  // getPrismaClient() is a lazily-initialized singleton read by every repo;
  // DATABASE_URL must point at the unprivileged role before it's first
  // constructed so repos under test are actually subject to RLS.
  process.env.DATABASE_URL = appUrl.toString();
  const prisma = getPrismaClient();

  return {
    prisma,
    stop: async () => {
      await prisma.$disconnect();
      await container.stop();
    },
  };
}
