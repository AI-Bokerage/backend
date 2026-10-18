export interface EventEnvelope {
  event_id?: string;
  event_type: string;
  event_version?: number;
  occurred_at: string;
  producer?: { service: string; version: string };
  tenant_id: string;
  data: Record<string, unknown>;
}

export interface IEventPublisher {
  publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void>;
}
