export interface IDistributedLock {
  /** Returns true if `key` was NOT already locked and is now held for `ttlSeconds`; false if already held. */
  acquire(key: string, ttlSeconds: number): Promise<boolean>;
}
