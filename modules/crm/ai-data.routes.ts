import { Router, type NextFunction, type Request, type Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { createHash, timingSafeEqual } from 'node:crypto';

import * as data from './ai-assistant.service';
import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';
import { asyncHandler } from '../../utils/async-handler';

/**
 * GET /ai-data/v1 — what assistant-svc reads to answer a question (Flow 4.10),
 * as the asking user and never more than they may see. Read-only: GET routes
 * only. Authenticated by the assistant's key (AI_DATA_API_KEY), not a sign-in.
 */
const aiDataRouter = Router();

const digest = (v: string) => createHash('sha256').update(v).digest();

aiDataRouter.use((req: Request, _res: Response, next: NextFunction) => {
  const key = env.ai.dataApiKey;
  const given = req.get('authorization')?.replace(/^Bearer /, '') ?? '';
  if (!key || !timingSafeEqual(digest(given), digest(key))) {
    return next(new ApiError('Invalid key', StatusCodes.UNAUTHORIZED));
  }
  next();
});

const scope = (req: Request) =>
  data.dataScope({ tenantId: req.get('x-brokerage-id'), userId: req.get('x-user-id'), requestId: req.get('x-request-id') });

const YMD = /^\d{4}-\d{2}-\d{2}$/;
const limit = (req: Request) => Math.min(Math.max(Number(req.query.limit) || 25, 1), 500);
const text = (req: Request, name: string) => (typeof req.query[name] === 'string' ? (req.query[name] as string) : '');

const read = (fn: (req: Request, s: data.DataScope) => Promise<unknown>) =>
  asyncHandler(async (req: Request, res: Response) => {
    res.status(StatusCodes.OK).json(await fn(req, await scope(req)));
  });

aiDataRouter.get(
  '/mortgages/renewals',
  read((req, s) => {
    const [from, to] = [text(req, 'from'), text(req, 'to')];
    if (!YMD.test(from) || !YMD.test(to)) throw new ApiError('from and to must be YYYY-MM-DD', StatusCodes.BAD_REQUEST);
    return data.renewals(s, from, to, limit(req));
  }),
);
aiDataRouter.get('/clients/missing-documents', read((req, s) => data.clientsMissingDocuments(s, text(req, 'documentType') || undefined, limit(req))));
aiDataRouter.get('/tasks/overdue', read((req, s) => data.overdueTasks(s, limit(req))));
aiDataRouter.get('/clients/search', read((req, s) => data.searchClients(s, text(req, 'q'), limit(req))));
aiDataRouter.get('/recommendation-candidates', read((req, s) => data.recommendationCandidates(s, text(req, 'type'), limit(req))));
aiDataRouter.get('/clients/:id/file-summary', read((req, s) => data.fileSummary(s, req.params.id as string)));

export { aiDataRouter };
