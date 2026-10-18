import { describe, it, expect, afterEach } from "vitest";
import { registerTemplate, renderTemplate, listRegisteredEventTypes, __resetRegistryForTests } from "./registry.ts";

describe("template registry", () => {
  afterEach(() => __resetRegistryForTests());

  it("renders a registered template with the given data", () => {
    registerTemplate("doc.uploaded", (data) => ({
      title: "New document",
      body: `${data["fileName"]} was uploaded`,
    }));
    const content = renderTemplate("doc.uploaded", { fileName: "invoice.pdf" });
    expect(content.title).toBe("New document");
    expect(content.body).toBe("invoice.pdf was uploaded");
  });

  it("falls back to a generic builder for an unregistered eventType", () => {
    const content = renderTemplate("totally.unknown.event", { message: "custom fallback text" });
    expect(content.title).toBe("New notification");
    expect(content.body).toBe("custom fallback text");
  });

  it("listRegisteredEventTypes reflects only what's been registered", () => {
    expect(listRegisteredEventTypes()).toEqual([]);
    registerTemplate("doc.uploaded", () => ({ title: "t", body: "b" }));
    expect(listRegisteredEventTypes()).toEqual(["doc.uploaded"]);
  });
});
