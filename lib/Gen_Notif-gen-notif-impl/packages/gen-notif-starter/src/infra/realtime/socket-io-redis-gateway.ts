import type { Server as SocketIoServer } from "socket.io";
import { createAdapter } from "@socket.io/redis-adapter";
// ponytail: named import, not default — under this project's NodeNext
// moduleResolution, ioredis's default-export type merge resolves as a
// namespace-only type (TS2709/TS2351) even though the default import works
// fine at runtime. The named `{ Redis }` export is the same class and
// type-checks cleanly; verified equivalent at runtime too.
import { Redis } from "ioredis";
import type { IRealtimeGateway, InAppPayload } from "../../domain/ports/realtime-gateway.port.ts";
import type { IJwtVerifier } from "../../domain/ports/jwt-verifier.port.ts";
import { userRoom, buildNotifChannel, parseNotifChannel, NOTIF_CHANNEL_PATTERN } from "./rooms.ts";
import { logger } from "../../common/logger.ts";

export class SocketIoRedisGateway implements IRealtimeGateway {
  private readonly redisUrl: string;
  private readonly jwtVerifier: IJwtVerifier;
  private publishClient: Redis | undefined;

  constructor(redisUrl: string, jwtVerifier: IJwtVerifier) {
    this.redisUrl = redisUrl;
    this.jwtVerifier = jwtVerifier;
  }

  private getPublishClient(): Redis {
    if (!this.publishClient) this.publishClient = new Redis(this.redisUrl);
    return this.publishClient;
  }

  attach(io: SocketIoServer): void {
    // Socket.IO's Redis adapter needs its own dedicated pub/sub connection
    // pair, separate from any client issuing normal commands — a
    // subscriber-mode Redis connection can't issue normal commands.
    const pub = new Redis(this.redisUrl);
    const sub = pub.duplicate();
    io.adapter(createAdapter(pub, sub));

    io.use(async (socket, next) => {
      try {
        const auth = socket.handshake.auth as Record<string, string>;
        const header = socket.handshake.headers["authorization"];
        const token = auth?.token ?? (typeof header === "string" ? header.replace(/^Bearer\s+/i, "").trim() : undefined);
        if (!token) {
          next(new Error("GEN_NOTIF_UNAUTHORIZED: no token"));
          return;
        }
        const claims = await this.jwtVerifier.verify(token);
        socket.data.userId = claims.sub;
        socket.data.tenantId = claims.tenantId;
        socket.data.roles = claims.roles;
        next();
      } catch {
        next(new Error("GEN_NOTIF_UNAUTHORIZED: invalid token"));
      }
    });

    io.on("connection", (socket) => {
      const { tenantId, userId } = socket.data as { tenantId: string; userId: string };
      socket.join(userRoom(tenantId, userId));
    });

    // The publish-to-socket bridge: a dedicated psubscribe connection
    // (again, separate from the adapter's own pub/sub pair and from the
    // client publishInApp() uses).
    const bridgeSub = pub.duplicate();
    bridgeSub.psubscribe(NOTIF_CHANNEL_PATTERN, (err) => {
      if (err) logger.error({ err }, "[gen-notif] failed to psubscribe to notification channel pattern");
    });
    bridgeSub.on("pmessage", (_pattern, channel, message) => {
      const parsed = parseNotifChannel(channel);
      if (!parsed) {
        logger.warn({ channel }, "[gen-notif] received message on unparseable channel — skipping");
        return;
      }
      let payload: unknown;
      try {
        payload = JSON.parse(message);
      } catch {
        logger.warn({ channel }, "[gen-notif] received non-JSON message — skipping");
        return;
      }
      io.to(userRoom(parsed.tenantId, parsed.userId)).emit("notification", payload);
    });
  }

  async publishInApp(tenantId: string, userId: string, payload: InAppPayload): Promise<void> {
    await this.getPublishClient().publish(buildNotifChannel(tenantId, userId), JSON.stringify(payload));
  }
}
