import amqp, { type ChannelModel, type Channel } from "amqplib";
import type { IEventPublisher, EventEnvelope } from "../../domain/ports/event-publisher.port.ts";
import { logger } from "../../common/logger.ts";

export interface ConsumeOptions {
  prefetch?: number;
}

export class RabbitMqBus implements IEventPublisher {
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

  async publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void> {
    if (!this.channel) {
      // ponytail: lazy one-shot reconnect so a transient broker restart self-heals on the
      // outbox dispatcher's next poll tick instead of failing forever; real error propagates on failure.
      try {
        await this.connect();
      } catch (err) {
        logger.error({ err }, "[rabbitmq-bus] reconnect attempt before publish failed");
      }
    }
    this.requireChannel().publish(exchange, routingKey, Buffer.from(JSON.stringify(envelope)), { contentType: "application/json", persistent: true });
  }

  async consume(queue: string, handler: (envelope: EventEnvelope) => Promise<void>, options: ConsumeOptions = {}): Promise<void> {
    const channel = this.requireChannel();
    if (options.prefetch) await channel.prefetch(options.prefetch);

    await channel.consume(queue, (msg) => {
      if (!msg) return;
      void (async () => {
        try {
          const envelope = JSON.parse(msg.content.toString("utf8")) as EventEnvelope;
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
