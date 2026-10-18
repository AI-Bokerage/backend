import { describe, it, expect } from "vitest";
import { userRoom, buildNotifChannel, parseNotifChannel } from "./rooms.ts";

describe("realtime room/channel naming", () => {
  it("round-trips tenantId/userId through buildNotifChannel -> parseNotifChannel", () => {
    const tenantId = "11111111-1111-1111-1111-111111111111";
    const userId = "22222222-2222-2222-2222-222222222222";
    const channel = buildNotifChannel(tenantId, userId);
    expect(channel).toBe(`tenant:${tenantId}:notif:${userId}`);
    expect(parseNotifChannel(channel)).toEqual({ tenantId, userId });
  });

  it("userRoom uses the :user: segment, distinct from the :notif: channel", () => {
    expect(userRoom("t1", "u1")).toBe("tenant:t1:user:u1");
  });

  it("parseNotifChannel rejects a malformed channel", () => {
    expect(parseNotifChannel("garbage")).toBeNull();
    expect(parseNotifChannel("tenant:t1:user:u1")).toBeNull(); // wrong segment (user, not notif)
  });
});
