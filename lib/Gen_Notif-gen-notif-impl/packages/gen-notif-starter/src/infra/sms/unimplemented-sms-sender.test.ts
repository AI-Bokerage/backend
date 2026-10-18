import { describe, it, expect } from "vitest";
import { UnimplementedSmsSender } from "./unimplemented-sms-sender.ts";
import { SmsNotConfiguredError } from "../../common/errors.ts";

describe("UnimplementedSmsSender", () => {
  it("throws SmsNotConfiguredError rather than silently succeeding", async () => {
    const sender = new UnimplementedSmsSender();
    await expect(sender.send({ to: "+15551234567", body: "hi" })).rejects.toThrow(SmsNotConfiguredError);
  });
});
