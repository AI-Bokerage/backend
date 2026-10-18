import { describe, it, expect, vi } from "vitest";

vi.mock("node:dns/promises", () => ({
  resolveTxt: vi.fn(async (hostname: string) => {
    if (hostname === "_gen-tbr-verify.example.com") return [["gen-tbr-verify=abc123"]];
    if (hostname === "_gen-tbr-verify.nodata.example.com") {
      throw Object.assign(new Error("ENODATA"), { code: "ENODATA" });
    }
    throw Object.assign(new Error("ENOTFOUND"), { code: "ENOTFOUND" });
  }),
  resolveCname: vi.fn(async (hostname: string) => {
    if (hostname === "_gen-tbr.example.com") return ["verify.gen-tbr.example.net"];
    if (hostname === "_gen-tbr.nodata.example.com") {
      throw Object.assign(new Error("ENODATA"), { code: "ENODATA" });
    }
    throw Object.assign(new Error("ENOTFOUND"), { code: "ENOTFOUND" });
  }),
}));

import { NodeDnsVerifier } from "../../../src/infra/dns/node-dns-verifier.ts";

describe("NodeDnsVerifier", () => {
  it("resolveTxt returns records on a match", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveTxt("_gen-tbr-verify.example.com");
    expect(records).toEqual([["gen-tbr-verify=abc123"]]);
  });

  it("resolveTxt returns [] on ENOTFOUND instead of throwing", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveTxt("_gen-tbr-verify.nowhere.invalid");
    expect(records).toEqual([]);
  });

  it("resolveCname returns [] on ENOTFOUND instead of throwing", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveCname("_gen-tbr.nowhere.invalid");
    expect(records).toEqual([]);
  });

  it("resolveTxt returns [] on ENODATA instead of throwing", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveTxt("_gen-tbr-verify.nodata.example.com");
    expect(records).toEqual([]);
  });

  it("resolveCname returns [] on ENODATA instead of throwing", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveCname("_gen-tbr.nodata.example.com");
    expect(records).toEqual([]);
  });
});
