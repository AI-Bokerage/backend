export interface AssetRef {
  assetId: string;
  tenantId: string;
  contentType: string;
  bytes: Buffer;
}

export interface PutAssetResult {
  assetId: string;
  url: string;
}

export interface IAssetStore {
  put(ref: AssetRef): Promise<PutAssetResult>;
  get(tenantId: string, assetId: string): Promise<{ url: string } | null>;
}
