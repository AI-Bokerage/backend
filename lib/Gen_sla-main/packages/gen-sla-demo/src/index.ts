import "dotenv/config";
import { pathToFileURL } from "node:url";
import { createGenSla } from "@gen-ms/gen-sla-starter";

const PORT = Number(process.env.PORT ?? 3202);

export function startDemo() {
  const { app, worker } = createGenSla({ modules: { worker: process.env.GEN_SLA_DEMO_WORKER === "true" } });
  const server = app.listen(PORT, () => {
    console.log(`Gen_SLA demo listening on port ${PORT}`);
  });
  return { app, server, worker };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startDemo();
}
