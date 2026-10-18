export const openApiSpec = {
  openapi: "3.0.3",
  info: {
    title: "Gen_FMM API",
    version: "0.1.0",
    description:
      "Feature-gate/entitlement engine — 4-tier resolution (override -> plan-entitlement -> default -> rollout), catalog/override/telemetry CRUD. See docs/integration-guide.md for the full guide.",
  },
  components: {
    securitySchemes: {
      internalSecret: {
        type: "apiKey",
        in: "header",
        name: "x-internal-secret",
        description: "Value of GEN_FMM_INTERNAL_SECRET",
      },
    },
    schemas: {
      Error: {
        type: "object",
        properties: { error: { type: "string" }, message: { type: "string" } },
      },
      EntitlementCheckResult: {
        type: "object",
        properties: {
          tenantId: { type: "string", format: "uuid" },
          flagKey: { type: "string" },
          moduleCode: { type: "string", nullable: true },
          enabled: { type: "boolean" },
          reason: { type: "string", enum: ["TENANT_OVERRIDE", "PLAN_ENTITLEMENT", "FLAG_DEFAULT", "ROLLOUT", "FLAG_NOT_FOUND"] },
          cacheHit: { type: "string", enum: ["l1", "l2", "miss"] },
          latencyMs: { type: "number" },
        },
      },
      Module: {
        type: "object",
        properties: {
          code: { type: "string" },
          name: { type: "string" },
          description: { type: "string", nullable: true },
          category: { type: "string", nullable: true },
          isActive: { type: "boolean" },
          displayOrder: { type: "integer" },
          iconKey: { type: "string", nullable: true },
        },
      },
      FeatureFlag: {
        type: "object",
        properties: {
          key: { type: "string" },
          moduleCode: { type: "string", nullable: true },
          defaultEnabled: { type: "boolean" },
          isGradualRollout: { type: "boolean" },
          rolloutPercentage: { type: "integer", minimum: 0, maximum: 100 },
        },
      },
      TenantOverride: {
        type: "object",
        properties: {
          tenantId: { type: "string", format: "uuid" },
          flagKey: { type: "string" },
          enabled: { type: "boolean" },
          config: { type: "object", additionalProperties: true },
          reason: { type: "string", nullable: true },
          expiresAt: { type: "string", format: "date-time", nullable: true },
          createdBy: { type: "string", nullable: true },
        },
      },
    },
  },
  paths: {
    "/health": {
      get: { summary: "Health check", tags: ["health"], responses: { "200": { description: "OK" } } },
    },
    "/api/v1/entitlement/{tenantId}/{flagKey}": {
      get: {
        summary: "Resolve one flag for a tenant (public, host fronts auth)",
        tags: ["entitlement"],
        parameters: [
          { name: "tenantId", in: "path", required: true, schema: { type: "string", format: "uuid" } },
          { name: "flagKey", in: "path", required: true, schema: { type: "string" } },
          { name: "planCode", in: "query", schema: { type: "string" } },
        ],
        responses: {
          "200": { description: "OK", content: { "application/json": { schema: { $ref: "#/components/schemas/EntitlementCheckResult" } } } },
        },
      },
    },
    "/internal/v1/fmm/check/{tenantId}/{flagKey}": {
      get: {
        summary: "Resolve one flag for a tenant (internal, X-Internal-Secret gated)",
        tags: ["internal"],
        security: [{ internalSecret: [] }],
        parameters: [
          { name: "tenantId", in: "path", required: true, schema: { type: "string", format: "uuid" } },
          { name: "flagKey", in: "path", required: true, schema: { type: "string" } },
          { name: "planCode", in: "query", schema: { type: "string" } },
        ],
        responses: {
          "200": { description: "OK", content: { "application/json": { schema: { $ref: "#/components/schemas/EntitlementCheckResult" } } } },
          "401": { description: "Missing/invalid X-Internal-Secret", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
        },
      },
    },
    "/api/v1/catalog/flags": {
      get: { summary: "List feature flags", tags: ["catalog"], responses: { "200": { description: "OK" } } },
      post: {
        summary: "Create a feature flag",
        tags: ["catalog"],
        requestBody: { required: true, content: { "application/json": { schema: { $ref: "#/components/schemas/FeatureFlag" } } } },
        responses: { "201": { description: "Created" }, "409": { description: "Already exists" } },
      },
    },
    "/api/v1/overrides": {
      post: {
        summary: "Upsert a tenant override",
        tags: ["overrides"],
        requestBody: { required: true, content: { "application/json": { schema: { $ref: "#/components/schemas/TenantOverride" } } } },
        responses: { "200": { description: "OK" }, "422": { description: "expiresAt in the past" } },
      },
    },
    "/api/v1/telemetry/{tenantId}": {
      get: {
        summary: "Query recorded feature-check usage events for a tenant",
        tags: ["telemetry"],
        parameters: [{ name: "tenantId", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        responses: { "200": { description: "OK" } },
      },
    },
  },
} as const;
