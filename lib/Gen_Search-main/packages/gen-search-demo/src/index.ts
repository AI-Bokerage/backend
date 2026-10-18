import "dotenv/config";
import { pathToFileURL } from "node:url";
import { createGenSearch } from "@gen-ms/gen-search-starter";

const PORT = Number(process.env.PORT ?? 3800);

// Illustrative only — a real host streams rows from its own services here
// (see sample-event-handler.ts). Yielding nothing is a valid reindexSource
// for a demo that never calls /reindex with real data.
async function* emptyReindexSource() {}

export function startDemo() {
  const { app, worker } = createGenSearch({
    entities: { lead: { backend: "pg" } },
    reindexSource: emptyReindexSource,
    modules: { worker: process.env.GEN_SEARCH_DEMO_WORKER === "true" },
    worker: {
      exchange: "gen-search-demo.events",
      queue: "gen-search-demo.indexer",
      routingKeys: ["*.*.created", "*.*.updated"],
      resolveDocument: (envelope) => ({
        tenantId: envelope.tenant_id,
        entityType: "lead",
        entityId: envelope.data.id as string,
        title: envelope.data.name as string,
      }),
    },
  });

  const server = app.listen(PORT, () => {
    console.log(`Gen_SEARCH demo listening on port ${PORT}`);
  });

  return { app, server, worker };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startDemo();
}
