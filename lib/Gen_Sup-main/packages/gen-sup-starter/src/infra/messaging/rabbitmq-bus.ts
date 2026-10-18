import amqp, { type ChannelModel, type Channel } from "amqplib";
import type { EventPublisher, EventEnvelope } from "../../domain/ports/event-publisher.port.ts";
import { logger } from "../../common/logger.ts";

export class RabbitMqBus implements EventPublisher {
  private connection: ChannelModel | undefined;
  private channel: Channel | undefined;
  private connectingPromise: Promise<void> | undefined;

  constructor(private readonly url: string) {}

  async connect(): Promise<void> {
    if (this.channel) return;
    if (this.connectingPromise) return this.connectingPromise;
    this.connectingPromise = this.doConnect();
    try {
      await this.connectingPromise;
    } finally {
      this.connectingPromise = undefined;
    }
  }

  private async doConnect(): Promise<void> {
    const connection = await amqp.connect(this.url, { timeout: 5000 });
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
    if (!this.channel) throw new Error("RabbitMqBus has no open channel — the connection failed or was closed");
    return this.channel;
  }

  async publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void> {
    if (!this.channel) {
      try {
        await this.connect();
      } catch (err) {
        logger.error({ err }, "[rabbitmq-bus] reconnect attempt before publish failed");
        throw err;
      }
    }
    const channel = this.requireChannel();
    await channel.assertExchange(exchange, "topic", { durable: true });
    channel.publish(exchange, routingKey, Buffer.from(JSON.stringify(envelope)), {
      contentType: "application/json",
      persistent: true,
    });
  }

  async close(): Promise<void> {
    try {
      await this.channel?.close();
    } catch (err) {
      logger.warn({ err }, "[rabbitmq-bus] error closing channel");
    }
    try {
      await this.connection?.close();
    } catch (err) {
      logger.warn({ err }, "[rabbitmq-bus] error closing connection");
    } finally {
      this.channel = undefined;
      this.connection = undefined;
    }
  }
}
