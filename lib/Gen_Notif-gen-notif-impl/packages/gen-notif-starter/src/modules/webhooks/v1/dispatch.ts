import type { WebhookEndpointRecord } from "../../../domain/ports/webhook-endpoint.repository.port.ts";
import type { NotificationContent } from "../../templates/v1/registry.ts";
import { signWebhookPayload } from "./hmac.ts";

const RETRY_DELAYS_MS = [0, 1_000, 4_000];
const TIMEOUT_MS = 10_000;

export interface DispatchWebhookResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function dispatchWebhookToEndpoint(
  endpoint: WebhookEndpointRecord,
  eventType: string,
  content: NotificationContent,
): Promise<DispatchWebhookResult> {
  const body = JSON.stringify({ eventType, title: content.title, body: content.body });
  let lastError = "unknown error";

  for (const delay of RETRY_DELAYS_MS) {
    if (delay > 0) await sleep(delay);
    const timestamp = Math.floor(Date.now() / 1000);
    const signature = signWebhookPayload(endpoint.secret, timestamp, body);
    try {
      const res = await fetch(endpoint.url, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-gen-notif-signature": signature,
          "x-gen-notif-timestamp": String(timestamp),
        },
        body,
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (res.ok) return { status: "SENT" };
      lastError = `HTTP ${res.status}`;
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
    }
  }
  return { status: "FAILED", lastError };
}

export async function dispatchWebhook(
  endpoints: WebhookEndpointRecord[],
  eventType: string,
  content: NotificationContent,
): Promise<Array<{ endpointId: string; result: DispatchWebhookResult }>> {
  const settled = await Promise.allSettled(
    endpoints.map(async (endpoint) => ({
      endpointId: endpoint.id,
      result: await dispatchWebhookToEndpoint(endpoint, eventType, content),
    })),
  );
  return settled.map((s, i) =>
    s.status === "fulfilled" ? s.value : { endpointId: endpoints[i]!.id, result: { status: "FAILED" as const, lastError: String(s.reason) } },
  );
}
