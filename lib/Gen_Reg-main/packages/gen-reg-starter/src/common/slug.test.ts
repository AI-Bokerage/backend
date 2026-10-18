import { describe, it, expect } from "vitest";
import { toSlug, isValidSlug, RESERVED_SUBDOMAINS } from "./slug.ts";

describe("slug", () => {
  it("normalizes mixed-case, spaces, and punctuation into a valid slug", () => {
    expect(toSlug("  Acme Corp! ")).toBe("acme-corp");
  });

  it("collapses repeated separators", () => {
    expect(toSlug("a---b__c")).toBe("a-b-c");
  });

  it("rejects a slug shorter than the minimum length", () => {
    expect(isValidSlug("ab")).toBe(false);
  });

  it("rejects a slug starting with a hyphen", () => {
    expect(isValidSlug("-abc")).toBe(false);
  });

  it("accepts a well-formed slug", () => {
    expect(isValidSlug("acme-corp")).toBe(true);
  });

  it("flags reserved subdomains", () => {
    expect(RESERVED_SUBDOMAINS.has("www")).toBe(true);
    expect(RESERVED_SUBDOMAINS.has("acme-corp")).toBe(false);
  });
});
