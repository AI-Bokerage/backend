export const openApiSpec = {
  openapi: "3.0.3",
  info: {
    title: "Gen_NOTIF API",
    version: "0.1.0",
    description:
      "Notification delivery — email/webhook/in-app/SMS-port — with per-user preferences and digest batching. See docs/integration-guide.md for the full guide.",
  },
  components: {
    securitySchemes: {
      internalSecret: {
        type: "apiKey",
        in: "header",
        name: "x-internal-secret",
        description: "Value of GEN_NOTIF_INTERNAL_SECRET",
      },
    },
    schemas: {
      Error: {
        type: "object",
        properties: {
          code: { type: "string" },
          message: { type: "string" },
        },
      },
      NotifChannel: {
        type: "string",
        enum: ["email", "inapp", "sms", "webhook"],
      },
      Preference: {
        type: "object",
        properties: {
          id: { type: "string", format: "uuid" },
          tenantId: { type: "string", format: "uuid" },
          userId: { type: "string", format: "uuid" },
          eventType: { type: "string" },
          channel: { $ref: "#/components/schemas/NotifChannel" },
          enabled: { type: "boolean" },
          digestMode: { type: "boolean" },
          createdAt: { type: "string", format: "date-time" },
          updatedAt: { type: "string", format: "date-time" },
        },
      },
      NotificationLog: {
        type: "object",
        properties: {
          id: { type: "string", format: "uuid" },
          tenantId: { type: "string", format: "uuid" },
          userId: { type: "string", format: "uuid" },
          channel: { $ref: "#/components/schemas/NotifChannel" },
          eventType: { type: "string" },
          title: { type: "string" },
          body: { type: "string" },
          status: { type: "string", enum: ["SENT", "FAILED", "QUEUED_FOR_DIGEST", "READ"] },
          readAt: { type: "string", format: "date-time", nullable: true },
          createdAt: { type: "string", format: "date-time" },
        },
      },
      WebhookEndpoint: {
        type: "object",
        properties: {
          id: { type: "string", format: "uuid" },
          tenantId: { type: "string", format: "uuid" },
          userId: { type: "string", format: "uuid" },
          url: { type: "string", format: "uri" },
          secret: { type: "string" },
          enabled: { type: "boolean" },
          description: { type: "string", nullable: true },
        },
      },
      NotifyRecipient: {
        type: "object",
        required: ["userId"],
        properties: {
          userId: { type: "string", format: "uuid" },
          email: { type: "string", format: "email" },
          phone: { type: "string" },
        },
      },
      NotifyInput: {
        type: "object",
        required: ["tenantId", "recipients", "eventType", "data"],
        properties: {
          tenantId: { type: "string", format: "uuid" },
          recipients: { type: "array", items: { $ref: "#/components/schemas/NotifyRecipient" } },
          eventType: { type: "string" },
          data: { type: "object", additionalProperties: true },
          entityRefType: { type: "string" },
          entityRefId: { type: "string" },
        },
      },
      NotifyResult: {
        type: "object",
        properties: {
          recipients: {
            type: "array",
            items: {
              type: "object",
              properties: {
                userId: { type: "string", format: "uuid" },
                channels: {
                  type: "array",
                  items: {
                    type: "object",
                    properties: {
                      channel: { type: "string" },
                      status: { type: "string", enum: ["SENT", "FAILED", "QUEUED_FOR_DIGEST"] },
                      lastError: { type: "string" },
                    },
                  },
                },
              },
            },
          },
        },
      },
      DigestSweepResult: {
        type: "object",
        properties: {
          processed: { type: "integer" },
          sent: { type: "integer" },
          failed: { type: "integer" },
        },
      },
    },
  },
  paths: {
    "/health": {
      get: {
        summary: "Health check",
        tags: ["health"],
        responses: { "200": { description: "OK" } },
      },
    },
    "/api/v1/preferences": {
      get: {
        summary: "List a user's notification preferences",
        tags: ["preferences"],
        parameters: [
          { name: "tenantId", in: "query", required: true, schema: { type: "string", format: "uuid" } },
          { name: "userId", in: "query", required: true, schema: { type: "string", format: "uuid" } },
        ],
        responses: {
          "200": {
            description: "OK",
            content: {
              "application/json": {
                schema: { type: "object", properties: { preferences: { type: "array", items: { $ref: "#/components/schemas/Preference" } } } },
              },
            },
          },
        },
      },
      patch: {
        summary: "Upsert one notification preference",
        tags: ["preferences"],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["tenantId", "userId", "eventType", "channel", "enabled", "digestMode"],
                properties: {
                  tenantId: { type: "string", format: "uuid" },
                  userId: { type: "string", format: "uuid" },
                  eventType: { type: "string" },
                  channel: { $ref: "#/components/schemas/NotifChannel" },
                  enabled: { type: "boolean" },
                  digestMode: { type: "boolean" },
                },
              },
            },
          },
        },
        responses: {
          "200": {
            description: "OK",
            content: { "application/json": { schema: { type: "object", properties: { preference: { $ref: "#/components/schemas/Preference" } } } } },
          },
        },
      },
    },
    "/api/v1/preferences/event-types": {
      get: {
        summary: "List every registered event type",
        tags: ["preferences"],
        responses: {
          "200": {
            description: "OK",
            content: { "application/json": { schema: { type: "object", properties: { eventTypes: { type: "array", items: { type: "string" } } } } } },
          },
        },
      },
    },
    "/api/v1/notifications/unread": {
      get: {
        summary: "List unread / queued-for-digest notifications, newest first",
        tags: ["notifications"],
        parameters: [
          { name: "tenantId", in: "query", required: true, schema: { type: "string", format: "uuid" } },
          { name: "userId", in: "query", required: true, schema: { type: "string", format: "uuid" } },
          { name: "limit", in: "query", schema: { type: "integer", minimum: 1, maximum: 100, default: 50 } },
          { name: "before", in: "query", schema: { type: "string", format: "date-time" } },
        ],
        responses: {
          "200": {
            description: "OK",
            content: {
              "application/json": {
                schema: { type: "object", properties: { notifications: { type: "array", items: { $ref: "#/components/schemas/NotificationLog" } } } },
              },
            },
          },
        },
      },
    },
    "/api/v1/notifications/{id}/read": {
      patch: {
        summary: "Mark one notification read (ownership-checked)",
        tags: ["notifications"],
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["tenantId", "userId"],
                properties: { tenantId: { type: "string", format: "uuid" }, userId: { type: "string", format: "uuid" } },
              },
            },
          },
        },
        responses: {
          "200": {
            description: "OK",
            content: { "application/json": { schema: { type: "object", properties: { notification: { $ref: "#/components/schemas/NotificationLog" } } } } },
          },
        },
      },
    },
    "/api/v1/webhook-endpoints": {
      get: {
        summary: "List a user's webhook endpoints",
        tags: ["webhook-endpoints"],
        parameters: [
          { name: "tenantId", in: "query", required: true, schema: { type: "string", format: "uuid" } },
          { name: "userId", in: "query", required: true, schema: { type: "string", format: "uuid" } },
        ],
        responses: {
          "200": {
            description: "OK",
            content: {
              "application/json": {
                schema: { type: "object", properties: { endpoints: { type: "array", items: { $ref: "#/components/schemas/WebhookEndpoint" } } } },
              },
            },
          },
        },
      },
      post: {
        summary: "Register a webhook endpoint (server-generated secret)",
        tags: ["webhook-endpoints"],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["tenantId", "userId", "url"],
                properties: {
                  tenantId: { type: "string", format: "uuid" },
                  userId: { type: "string", format: "uuid" },
                  url: { type: "string", format: "uri" },
                  description: { type: "string" },
                },
              },
            },
          },
        },
        responses: {
          "201": {
            description: "Created",
            content: { "application/json": { schema: { type: "object", properties: { endpoint: { $ref: "#/components/schemas/WebhookEndpoint" } } } } },
          },
        },
      },
    },
    "/api/v1/webhook-endpoints/{id}": {
      patch: {
        summary: "Update a webhook endpoint",
        tags: ["webhook-endpoints"],
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["tenantId"],
                properties: {
                  tenantId: { type: "string", format: "uuid" },
                  url: { type: "string", format: "uri" },
                  enabled: { type: "boolean" },
                  description: { type: "string" },
                },
              },
            },
          },
        },
        responses: {
          "200": {
            description: "OK",
            content: { "application/json": { schema: { type: "object", properties: { endpoint: { $ref: "#/components/schemas/WebhookEndpoint" } } } } },
          },
        },
      },
      delete: {
        summary: "Delete a webhook endpoint",
        tags: ["webhook-endpoints"],
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: { type: "object", required: ["tenantId"], properties: { tenantId: { type: "string", format: "uuid" } } },
            },
          },
        },
        responses: { "204": { description: "No Content" } },
      },
    },
    "/internal/notify": {
      post: {
        summary: "Fan a notification out to every recipient's enabled channels",
        tags: ["internal"],
        security: [{ internalSecret: [] }],
        requestBody: {
          required: true,
          content: { "application/json": { schema: { $ref: "#/components/schemas/NotifyInput" } } },
        },
        responses: {
          "200": { description: "OK", content: { "application/json": { schema: { $ref: "#/components/schemas/NotifyResult" } } } },
          "401": { description: "Missing/invalid X-Internal-Secret", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
        },
      },
    },
    "/internal/digest/sweep": {
      post: {
        summary: "Run one digest sweep — send every due digest-mode batch (host-scheduled, no internal cron)",
        tags: ["internal"],
        security: [{ internalSecret: [] }],
        responses: {
          "200": { description: "OK", content: { "application/json": { schema: { $ref: "#/components/schemas/DigestSweepResult" } } } },
          "401": { description: "Missing/invalid X-Internal-Secret", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
        },
      },
    },
  },
} as const;
