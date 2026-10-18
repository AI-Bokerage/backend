export interface IWebhookDedupStore {
  /** Returns true the first time `key` is seen within `ttlSeconds`; false on any repeat within that window. */
  tryAcquire(key: string, ttlSeconds: number): Promise<boolean>;
  /** Releases `key` so a subsequent tryAcquire for it succeeds again — used to let a failed webhook be retried. */
  release(key: string): Promise<void>;
}
