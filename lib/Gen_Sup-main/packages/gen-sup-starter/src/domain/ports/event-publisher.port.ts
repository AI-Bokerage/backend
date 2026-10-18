export interface EventEnvelope {
  event_id?: string;
  event_type: string;
  event_version?: number;
  occurred_at: string;
  tenant_id: string;
  data: Record<string, unknown>;
}

export interface EventPublisher {
  publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void>;
}
