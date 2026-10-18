import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { execSync } from "node:child_process";

export async function startTestPostgres(): Promise<{ container: StartedPostgreSqlContainer; databaseUrl: string }> {
  const container = await new PostgreSqlContainer("postgres:15").start();
  const databaseUrl = container.getConnectionUri();

  execSync("npx prisma migrate deploy", { env: { ...process.env, DATABASE_URL: databaseUrl }, stdio: "inherit" });

  return { container, databaseUrl };
}
