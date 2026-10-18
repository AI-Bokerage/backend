import type { IEmailSender } from "../../../../domain/ports/email-sender.port.ts";
import type { NotificationContent } from "../../../templates/v1/registry.ts";

export interface DispatchEmailResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

export async function dispatchEmail(
  sender: IEmailSender,
  to: string,
  content: NotificationContent,
): Promise<DispatchEmailResult> {
  try {
    await sender.send({
      to,
      subject: content.title,
      text: content.body,
      html: content.html,
    });
    return { status: "SENT" };
  } catch (err) {
    return { status: "FAILED", lastError: err instanceof Error ? err.message : String(err) };
  }
}
