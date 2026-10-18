
import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { createHmac, timingSafeEqual } from 'node:crypto';

import { onDocreadEvent } from './ai-documents.service';
import { env } from '../../config/env';
import { logger } from '../../config/logger';
import { ApiError } from '../../utils/api-error';
import { asyncHandler } from '../../utils/async-handler';

/**
 * Signed job callbacks from the AI services (ai service/docs/api/webhooks.md):
 * x-ai-signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, "<t>.<raw body>")>.
 * Delivery is at least once, so every handler is idempotent (a repeat changes nothing).
 */
const TOLERANCE_S = 300;

export function verifyAiSignature(secret: string, header: string, raw: Buffer, nowMs = Date.now()) {
  const parts = Object.fromEntries(
    header.split(',').map((p) => p.split('=', 2) as [string, string]),
  );
  const t = Number(parts.t);
  if (!parts.v1 || !Number.isInteger(t) || Math.abs(nowMs / 1000 - t) > TOLERANCE_S) return false;
  const expected = createHmac('sha256', secret).update(`${t}.`).update(raw).digest();
  const given = Buffer.from(parts.v1, 'hex');
  return given.length === expected.length && timingSafeEqual(given, expected);
}

export interface AiCallback {
  id: string;
  event: string;
  data: { id: string; brokerageId: string; status: string; errorCode: string | null; [k: string]: unknown };
}

const HANDLERS: Record<string, { secret: () => string; handle: (cb: AiCallback) => Promise<void> }> = {
  docread: { secret: () => env.ai.docread.webhookSecret, handle: onDocreadEvent },
};

/** POST /crm/webhooks/ai/:service — public, authenticated by the signature only. */
export const aiCallbackHandler = asyncHandler(async (req: Request, res: Response) => {
  const handler = HANDLERS[req.params.service as string];
  const secret = handler?.secret();
  if (!handler || !secret) throw new ApiError('Not found', StatusCodes.NOT_FOUND);
  const ok =
    req.rawBody !== undefined &&
    verifyAiSignature(secret, req.get('x-ai-signature') ?? '', req.rawBody);
  if (!ok) throw new ApiError('Invalid signature', StatusCodes.UNAUTHORIZED);

  const callback = req.body as AiCallback;
  logger.info({ service: req.params.service, event: callback.event, delivery: callback.id }, 'ai.callback');
  await handler.handle(callback);
  res.status(StatusCodes.NO_CONTENT).end();
});
