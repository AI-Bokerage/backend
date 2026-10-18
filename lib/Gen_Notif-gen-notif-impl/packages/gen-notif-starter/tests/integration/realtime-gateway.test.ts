import { describe, it, expect, beforeAll, afterAll } from "vitest";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { Server as SocketIoServer } from "socket.io";
import { io as ioClient, type Socket as ClientSocket } from "socket.io-client";
import { GenericContainer, type StartedTestContainer } from "testcontainers";
import { SignJWT, generateKeyPair } from "jose";
import { SocketIoRedisGateway } from "../../src/infra/realtime/socket-io-redis-gateway.ts";
import type { IJwtVerifier } from "../../src/domain/ports/jwt-verifier.port.ts";

describe("SocketIoRedisGateway end-to-end", () => {
  let redisContainer: StartedTestContainer;
  let httpServer: http.Server;
  let port: number;
  let gateway: SocketIoRedisGateway;

  beforeAll(async () => {
    redisContainer = await new GenericContainer("redis:7").withExposedPorts(6379).start();
    const redisUrl = `redis://${redisContainer.getHost()}:${redisContainer.getMappedPort(6379)}`;

    const fakeVerifier: IJwtVerifier = {
      async verify(token: string) {
        const [, payloadB64] = token.split(".");
        return JSON.parse(Buffer.from(payloadB64!, "base64url").toString());
      },
    };

    gateway = new SocketIoRedisGateway(redisUrl, fakeVerifier);
    httpServer = http.createServer();
    const io = new SocketIoServer(httpServer);
    gateway.attach(io);
    await new Promise<void>((resolve) => httpServer.listen(0, resolve));
    port = (httpServer.address() as AddressInfo).port;
  }, 60_000);

  afterAll(async () => {
    httpServer.close();
    await redisContainer.stop();
  });

  it("delivers a publishInApp() call to the connected client in that tenant/user's room", async () => {
    const fakeToken = `header.${Buffer.from(JSON.stringify({ sub: "user-1", tenantId: "t1", roles: [] })).toString("base64url")}.sig`;
    const client: ClientSocket = ioClient(`http://localhost:${port}`, { auth: { token: fakeToken } });
    await new Promise<void>((resolve, reject) => {
      client.on("connect", resolve);
      client.on("connect_error", reject);
    });

    const received = new Promise((resolve) => client.once("notification", resolve));
    await gateway.publishInApp("t1", "user-1", {
      notifId: "n1", type: "doc.uploaded", title: "New doc", body: "A file was uploaded", createdAt: new Date().toISOString(),
    });

    const payload = await received;
    expect(payload).toMatchObject({ notifId: "n1", type: "doc.uploaded" });
    client.close();
  });
});
