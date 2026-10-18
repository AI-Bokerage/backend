import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";

let container: StartedPostgreSqlContainer | undefined;

export async function startTestPostgres(): Promise<string> {
  container = await new PostgreSqlContainer("postgres:15").start();
  const url = container.getConnectionUri();
  execSync("npx prisma migrate deploy", {
    // ponytail: fileURLToPath, not `.pathname` — `.pathname` leaves a leading
    // slash ("/C:/...") on Windows, which is not a valid cwd.
    cwd: fileURLToPath(new URL("../..", import.meta.url)),
    env: { ...process.env, DATABASE_URL: url },
    stdio: "inherit",
  });
  return url;
}

export async function stopTestPostgres(): Promise<void> {
  await container?.stop();
  container = undefined;
}
