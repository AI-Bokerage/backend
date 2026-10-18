import type { PrismaClient, Prisma } from "../../../__generated__/prisma/index.js";
import type { IOutboxWriter, OutboxEnqueueParams } from "../../domain/ports/outbox-writer.port.ts";

export class PrismaOutboxWriter implements IOutboxWriter {
  constructor(private readonly prisma: PrismaClient) {}

  async enqueue(params: OutboxEnqueueParams): Promise<void> {
    await this.prisma.slaOutboxEvent.create({
      data: {
        tenantId: params.tenantId,
        eventType: params.eventType,
        exchange: params.exchange,
        routingKey: params.routingKey,
        payload: params.payload as Prisma.InputJsonValue,
        status: "PENDING",
        retryCount: 0,
      },
    });
  }
}
