import "dotenv/config";
import { pathToFileURL } from "node:url";
import { createGenSup } from "@gen-ms/gen-sup-starter";
import { sampleTenantMetricsPort } from "./sample-tenant-metrics-port.ts";

const PORT = Number(process.env.PORT ?? 3900);

export function startDemo() {
  const { app } = createGenSup({ tenantMetricsPort: sampleTenantMetricsPort, modules: { tenants: false, featureFlags: false, announcements: false, analytics: false } });
  const server = app.listen(PORT, () => {
    console.log(`Gen_SUP demo listening on port ${PORT}`);
  });
  return { app, server };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startDemo();
}
