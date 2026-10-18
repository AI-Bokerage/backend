import type { Server as SocketIoServer } from "socket.io";

export interface InAppPayload {
  notifId: string;
  type: string;
  title: string;
  body: string;
  entityRefType?: string;
  entityRefId?: string;
  createdAt: string;
}

export interface IRealtimeGateway {
  attach(io: SocketIoServer): void;
  publishInApp(tenantId: string, userId: string, payload: InAppPayload): Promise<void>;
}
