export interface IndexerEventEnvelope {
  event_type: string;
  occurred_at: string;
  tenant_id: string;
  data: Record<string, unknown>;
}

export interface ConsumeOptions {
  prefetch?: number;
}

// Consumer-only — unlike Gen_SLA's IEventPublisher, this library never
// publishes events (search-svc's own scope explicitly says it's "purely a
// consumer of events, never a producer," see docs/source-audit-notes.md).
export interface IEventConsumer {
  connect(): Promise<void>;
  assertExchange(name: string, type?: "topic" | "direct" | "fanout"): Promise<void>;
  assertQueue(name: string, deadLetterExchange?: string): Promise<void>;
  bindQueue(queue: string, exchange: string, routingKey: string): Promise<void>;
  consume(queue: string, handler: (envelope: IndexerEventEnvelope) => Promise<void>, options?: ConsumeOptions): Promise<void>;
  close(): Promise<void>;
}
