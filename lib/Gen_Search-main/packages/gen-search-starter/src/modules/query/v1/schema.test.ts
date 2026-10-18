import { describe, it, expect } from "vitest";
import { makeSearchQuerySchema, type SearchQueryLimits } from "./schema.ts";

const limits: SearchQueryLimits = {
  minQueryLength: 1,
  maxQueryLength: 500,
  defaultResults: 20,
  maxResults: 100,
  maxOffset: 10_000,
};

describe("makeSearchQuerySchema offset", () => {
  it("defaults offset to 0 when omitted", () => {
    const schema = makeSearchQuerySchema(limits);
    const parsed = schema.parse({ q: "acme" });
    expect(parsed.offset).toBe(0);
  });

  it("coerces a string offset to a number", () => {
    const schema = makeSearchQuerySchema(limits);
    const parsed = schema.parse({ q: "acme", offset: "50" });
    expect(parsed.offset).toBe(50);
  });

  it("rejects a negative offset", () => {
    const schema = makeSearchQuerySchema(limits);
    expect(() => schema.parse({ q: "acme", offset: -1 })).toThrow();
  });

  it("rejects an offset beyond maxOffset", () => {
    const schema = makeSearchQuerySchema(limits);
    expect(() => schema.parse({ q: "acme", offset: 10_001 })).toThrow();
  });
});
