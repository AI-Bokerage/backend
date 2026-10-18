import type { ISmsSender } from "../../../../domain/ports/sms-sender.port.ts";
import type { NotificationContent } from "../../../templates/v1/registry.ts";

export interface DispatchSmsResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

export async function dispatchSms(
  sender: ISmsSender,
  to: string,
  content: NotificationContent,
): Promise<DispatchSmsResult> {
  try {
    await sender.send({ to, body: `${content.title}: ${content.body}` });
    return { status: "SENT" };
  } catch (err) {
    return { status: "FAILED", lastError: err instanceof Error ? err.message : String(err) };
  }
}
