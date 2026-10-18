import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { exportActivityCsv, listActivity } from './activity-log';
import {
  createBrokerage,
  getBrokerage,
  listBrokerages,
  markBrokerageOwnerAccepted,
  reactivateBrokerage,
  startBrokerageCancellation,
  suspendBrokerage,
  recordAuthEvent,
} from './brokerage.service';
import { getDownloadPath, getLatestExportForTenant } from './export.service';
import { allowExtraToday, getUsageStatus } from './usage.service';
import { asyncHandler } from '../../utils/async-handler';

export const createBrokerageHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await createBrokerage(req.body);
  res.status(StatusCodes.CREATED).json({ success: true, data: result });
});

export const listBrokeragesHandler = asyncHandler(async (req: Request, res: Response) => {
  const { page, size } = req.query as unknown as { page: number; size: number };
  const result = await listBrokerages(page, size);
  res.status(StatusCodes.OK).json({ success: true, data: result.items, total: result.total });
});

export const getBrokerageHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await getBrokerage(req.params.id as string);
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const suspendBrokerageHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await suspendBrokerage(req.params.id as string, req.body.reason);
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const reactivateBrokerageHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await reactivateBrokerage(req.params.id as string, req.body.note);
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const startCancellationHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await startBrokerageCancellation(req.params.id as string);
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const getExportStatusHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await getLatestExportForTenant(req.params.id as string);
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const downloadExportHandler = asyncHandler(async (req: Request, res: Response) => {
  const filePath = await getDownloadPath(req.params.jobId as string);
  res.download(filePath, 'brokerage-export.zip');
});

export const ownerAcceptedWebhookHandler = asyncHandler(async (req: Request, res: Response) => {
  await markBrokerageOwnerAccepted(req.body.tenantId);
  res.status(StatusCodes.NO_CONTENT).send();
});

/** modules/auth → platform: sign-in/security events for the Super Admin's activity log (Flow 1). */
export const authEventWebhookHandler = asyncHandler(async (req: Request, res: Response) => {
  await recordAuthEvent(req.body);
  res.status(StatusCodes.NO_CONTENT).send();
});

export const getUsageStatusHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await getUsageStatus(req.params.id as string);
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const allowExtraTodayHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await allowExtraToday(req.params.id as string);
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const getMeHandler = asyncHandler(async (req: Request, res: Response) => {
  res.status(StatusCodes.OK).json({ success: true, data: req.platformUser });
});

interface ActivityLogQuery {
  action?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

function parseActivityFilter(req: Request) {
  const { action, from, to, page, size } = req.query as unknown as ActivityLogQuery;
  return {
    action,
    from: from ? new Date(from) : undefined,
    to: to ? new Date(to) : undefined,
    page,
    size,
  };
}

export const listActivityHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await listActivity(parseActivityFilter(req));
  res.status(StatusCodes.OK).json({ success: true, data: result.items, total: result.total });
});

export const exportActivityHandler = asyncHandler(async (req: Request, res: Response) => {
  const csv = await exportActivityCsv(parseActivityFilter(req), req.platformUser!.userId);
  res
    .status(StatusCodes.OK)
    .set('Content-Type', 'text/csv')
    .set('Content-Disposition', 'attachment; filename="activity-log.csv"')
    .send(csv);
});
