export interface OutboxEnqueueParams {
  tenantId: string;
  eventType: string;
  exchange: string;
  routingKey: string;
  payload: object;
}

export interface IOutboxWriter {
  enqueue(params: OutboxEnqueueParams): Promise<void>;
}
