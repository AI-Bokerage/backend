export interface IIdempotencyRepo {
  exists(eventId: string): Promise<boolean>;
  insert(eventId: string): Promise<void>;
}
