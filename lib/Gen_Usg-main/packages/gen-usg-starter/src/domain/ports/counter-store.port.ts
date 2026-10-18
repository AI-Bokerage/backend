export interface SetBatchEntry {
  key: string;
  value: string;
  ttlSec: number;
}

export interface ICounterStore {
  incrBy(key: string, delta: number): Promise<number>;
  get(key: string): Promise<number | null>;
  mget(keys: string[]): Promise<(number | null)[]>;
  setNX(key: string, value: string, ttlSec: number): Promise<boolean>;
  del(key: string): Promise<void>;
  zadd(key: string, score: number, member: string): Promise<void>;
  zrem(key: string, member: string): Promise<void>;
  zsumScores(key: string): Promise<number>;
  setBatch(entries: SetBatchEntry[]): Promise<void>;
}
