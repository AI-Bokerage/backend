export function userRoom(tenantId: string, userId: string): string {
  return `tenant:${tenantId}:user:${userId}`;
}

export function buildNotifChannel(tenantId: string, userId: string): string {
  return `tenant:${tenantId}:notif:${userId}`;
}

export const NOTIF_CHANNEL_PATTERN = "tenant:*:notif:*";

export function parseNotifChannel(channel: string): { tenantId: string; userId: string } | null {
  const parts = channel.split(":");
  if (parts.length < 4 || parts[0] !== "tenant" || parts[2] !== "notif") return null;
  return { tenantId: parts[1]!, userId: parts[3]! };
}
