import { S3Client, PutObjectCommand } from "@aws-sdk/client-s3";
import type { IAssetStore, AssetRef, PutAssetResult } from "../../domain/ports/asset-store.port.ts";
import { AssetStoreError } from "../../common/errors.ts";

export interface S3AssetStoreConfig {
  bucket: string;
  region: string;
  endpoint?: string;
  accessKeyId: string;
  secretAccessKey: string;
  publicBaseUrl?: string;
}

export class S3AssetStore implements IAssetStore {
  private readonly client: S3Client;

  constructor(private readonly config: S3AssetStoreConfig) {
    this.client = new S3Client({
      region: config.region,
      endpoint: config.endpoint,
      credentials: { accessKeyId: config.accessKeyId, secretAccessKey: config.secretAccessKey },
    });
  }

  private keyFor(tenantId: string, assetId: string): string {
    return `tenants/${tenantId}/assets/${assetId}`;
  }

  private urlFor(key: string): string {
    if (this.config.publicBaseUrl) return `${this.config.publicBaseUrl}/${key}`;
    return `https://${this.config.bucket}.s3.${this.config.region}.amazonaws.com/${key}`;
  }

  async put(ref: AssetRef): Promise<PutAssetResult> {
    const key = this.keyFor(ref.tenantId, ref.assetId);
    try {
      await this.client.send(
        new PutObjectCommand({
          Bucket: this.config.bucket,
          Key: key,
          Body: ref.bytes,
          ContentType: ref.contentType,
        }),
      );
    } catch (err) {
      throw new AssetStoreError(err instanceof Error ? err.message : "S3 put failed");
    }
    return { assetId: ref.assetId, url: this.urlFor(key) };
  }

  async get(tenantId: string, assetId: string): Promise<{ url: string } | null> {
    return { url: this.urlFor(this.keyFor(tenantId, assetId)) };
  }
}
