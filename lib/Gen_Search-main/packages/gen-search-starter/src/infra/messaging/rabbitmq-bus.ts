import amqp, { type ChannelModel, type Channel } from "amqplib";
import type { IEventConsumer, IndexerEventEnvelope, ConsumeOptions } from "../../domain/ports/event-bus.port.ts";
import { logger } from "../../common/logger.ts";

// Consumer-only, no publish() — this library never produces events (see
// docs/source-audit-notes.md). Unlike Gen_SLA's RabbitMqBus, there's no
// outbox/publish path to keep symmetric with, so it isn't ported here.
export class RabbitMqBus implements IEventConsumer {
  private connection: ChannelModel | undefined;
  private channel: Channel | undefined;

  constructor(private readonly url: string) {}

  async connect(): Promise<void> {
    if (this.channel) return;
    const connection = await amqp.connect(this.url);
    connection.on("error", (err) => logger.error({ err }, "[rabbitmq-bus] connection error"));
    connection.on("close", () => {
      logger.warn("[rabbitmq-bus] connection closed");
      this.connection = undefined;
      this.channel = undefined;
    });

    const channel = await connection.createChannel();
    channel.on("error", (err) => logger.error({ err }, "[rabbitmq-bus] channel error"));
    channel.on("close", () => {
      logger.warn("[rabbitmq-bus] channel closed");
      this.channel = undefined;
    });

    this.connection = connection;
    this.channel = channel;
  }

  private requireChannel(): Channel {
    if (!this.channel) throw new Error("RabbitMqBus.connect() must be called before use");
    return this.channel;
  }

  /** Idempotent — safe to call even when the source service already declared this exchange. */
  async assertExchange(name: string, type: "topic" | "direct" | "fanout" = "topic"): Promise<void> {
    await this.requireChannel().assertExchange(name, type, { durable: true });
  }

  /** `deadLetterExchange` routes rejected/nacked messages there — omit for a terminal queue (e.g. the DLQ itself). */
  async assertQueue(name: string, deadLetterExchange?: string): Promise<void> {
    await this.requireChannel().assertQueue(name, {
      durable: true,
      ...(deadLetterExchange && { arguments: { "x-dead-letter-exchange": deadLetterExchange } }),
    });
  }

  async bindQueue(queue: string, exchange: string, routingKey: string): Promise<void> {
    await this.requireChannel().bindQueue(queue, exchange, routingKey);
  }

  async consume(queue: string, handler: (envelope: IndexerEventEnvelope) => Promise<void>, options: ConsumeOptions = {}): Promise<void> {
    const channel = this.requireChannel();
    if (options.prefetch) await channel.prefetch(options.prefetch);

    await channel.consume(queue, (msg) => {
      if (!msg) return;
      void (async () => {
        try {
          const envelope = JSON.parse(msg.content.toString("utf8")) as IndexerEventEnvelope;
          await handler(envelope);
          channel.ack(msg);
        } catch (err) {
          logger.error({ err, queue }, "[rabbitmq-bus] handler failed — nacking to DLX");
          channel.nack(msg, false, false);
        }
      })();
    });
  }

  async close(): Promise<void> {
    await this.channel?.close();
    await this.connection?.close();
    this.channel = undefined;
    this.connection = undefined;
  }
}
