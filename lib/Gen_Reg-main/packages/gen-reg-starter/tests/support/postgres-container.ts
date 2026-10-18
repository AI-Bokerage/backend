// tests/support/postgres-container.ts
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { execSync } from "node:child_process";

export async function startTestPostgres(): Promise<{
  container: StartedPostgreSqlContainer;
  databaseUrl: string;
}> {
  const container = await new PostgreSqlContainer("postgres:15").start();
  const databaseUrl = container.getConnectionUri();

  // Apply the schema via `prisma migrate deploy` against the ephemeral container
  // instead of hand-maintaining a second copy of the DDL for tests.
  execSync("npx prisma migrate deploy", {
    env: { ...process.env, DATABASE_URL: databaseUrl },
    stdio: "inherit",
  });

  return { container, databaseUrl };
}
