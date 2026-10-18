import { describe, it, expect } from "vitest";
import request from "supertest";
import { createGenSla } from "@gen-ms/gen-sla-starter";

describe("gen-sla-demo wiring", () => {
  it("createGenSla({}) mounts a working /health endpoint with no config", () => {
    process.env.DATABASE_URL ??= "postgresql://user:pass@localhost:5440/gensla";
    process.env.GEN_SLA_INTERNAL_SECRET ??= "demo-secret";
    process.env.VALKEY_URL ??= "redis://localhost:6384";
    process.env.RABBITMQ_URL ??= "amqp://localhost:5674";

    const { app } = createGenSla({ modules: { worker: false } });
    return request(app).get("/health").expect(200, { status: "ok" });
  });
});
