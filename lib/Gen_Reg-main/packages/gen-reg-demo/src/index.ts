// src/index.ts
import "dotenv/config";
import { pathToFileURL } from "node:url";
import { createGenReg } from "@gen-ms/gen-reg-starter";

const PORT = Number(process.env.PORT ?? 3200);

export function startDemo() {
  const { app, worker } = createGenReg({});
  app.listen(PORT, () => {
    console.log(`Gen_REG demo listening on port ${PORT}`);
  });
  worker?.start();
  return { app, worker };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startDemo();
}
