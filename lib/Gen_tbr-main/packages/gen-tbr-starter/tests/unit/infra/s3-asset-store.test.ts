import { describe, it, expect, vi } from "vitest";

const sendMock = vi.fn();
vi.mock("@aws-sdk/client-s3", () => ({
  S3Client: vi.fn(() => ({ send: sendMock })),
  PutObjectCommand: vi.fn((input) => ({ input })),
}));

import { S3AssetStore } from "../../../src/infra/storage/s3-asset-store.ts";
import { AssetStoreError } from "../../../src/common/errors.ts";

describe("S3AssetStore", () => {
  it("put() writes the object and returns a constructed URL", async () => {
    sendMock.mockResolvedValueOnce({});
    const store = new S3AssetStore({
      bucket: "gen-tbr-assets",
      region: "us-east-1",
      accessKeyId: "id",
      secretAccessKey: "secret",
    });
    const result = await store.put({
      assetId: "asset-1",
      tenantId: "tenant-1",
      contentType: "image/png",
      bytes: Buffer.from("fake-bytes"),
    });
    expect(result.assetId).toBe("asset-1");
    expect(result.url).toContain("tenant-1/assets/asset-1");
  });

  it("put() wraps a send failure in AssetStoreError", async () => {
    sendMock.mockRejectedValueOnce(new Error("network down"));
    const store = new S3AssetStore({
      bucket: "gen-tbr-assets",
      region: "us-east-1",
      accessKeyId: "id",
      secretAccessKey: "secret",
    });
    await expect(
      store.put({ assetId: "asset-2", tenantId: "tenant-1", contentType: "image/png", bytes: Buffer.from("x") }),
    ).rejects.toThrow(AssetStoreError);
  });
});
