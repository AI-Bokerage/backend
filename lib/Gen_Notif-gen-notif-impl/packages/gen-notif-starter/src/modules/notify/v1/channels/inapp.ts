import type { IRealtimeGateway } from "../../../../domain/ports/realtime-gateway.port.ts";
import type { NotificationContent } from "../../../templates/v1/registry.ts";

export interface DispatchInAppResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

export async function dispatchInApp(
  gateway: IRealtimeGateway,
  tenantId: string,
  userId: string,
  notifId: string,
  eventType: string,
  content: NotificationContent,
  entityRefType?: string,
  entityRefId?: string,
): Promise<DispatchInAppResult> {
  try {
    await gateway.publishInApp(tenantId, userId, {
      notifId,
      type: eventType,
      title: content.title,
      body: content.body,
      entityRefType,
      entityRefId,
      createdAt: new Date().toISOString(),
    });
    return { status: "SENT" };
  } catch (err) {
    // Unlike the source (which wrote SENT before attempting the publish and
    // only logged a warning on failure), a publish failure here is a real
    // dispatch failure — the caller writes FAILED to the log, matching what
    // actually happened.
    return { status: "FAILED", lastError: err instanceof Error ? err.message : String(err) };
  }
}
