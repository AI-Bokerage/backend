// src/infra/auth-client/auth-client.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpAuthClient } from "./auth-client.ts";
import { SignupEmailAlreadyRegisteredError, GenAuthRegistrationError } from "../../common/errors.ts";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      status,
      json: async () => body,
      text: async () => JSON.stringify(body),
    }),
  );
}

describe("AuthClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("register returns userId on 201", async () => {
    mockFetchOnce(201, { userId: "u1" });
    const client = new HttpAuthClient("http://gen-auth");
    expect(await client.register("a@example.com", "hunter22")).toEqual({ userId: "u1" });
  });

  it("register throws SignupEmailAlreadyRegisteredError on 409", async () => {
    mockFetchOnce(409, { error: "email_already_exists" });
    const client = new HttpAuthClient("http://gen-auth");
    await expect(client.register("a@example.com", "hunter22")).rejects.toThrow(SignupEmailAlreadyRegisteredError);
  });

  it("register throws GenAuthRegistrationError on any other non-201 status", async () => {
    mockFetchOnce(500, { error: "internal" });
    const client = new HttpAuthClient("http://gen-auth");
    await expect(client.register("a@example.com", "hunter22")).rejects.toThrow(GenAuthRegistrationError);
  });
});
