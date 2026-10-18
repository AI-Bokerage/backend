import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import * as tenantService from './tenant.service';
import { asyncHandler } from '../../utils/async-handler';

function crmUser(req: Request) {
  return req.crmUser!;
}

export const getProfileHandler = asyncHandler(async (req: Request, res: Response) => {
  const profile = await tenantService.getProfile(crmUser(req).tenantId);
  res.status(StatusCodes.OK).json({ success: true, data: profile });
});

export const updateProfileHandler = asyncHandler(async (req: Request, res: Response) => {
  const profile = await tenantService.updateProfile(crmUser(req).tenantId, req.body);
  res.status(StatusCodes.OK).json({ success: true, data: profile });
});

export const getBookingSettingsHandler = asyncHandler(async (req: Request, res: Response) => {
  const settings = await tenantService.getBookingSettings(crmUser(req).tenantId);
  res.status(StatusCodes.OK).json({ success: true, data: settings });
});

export const updateBookingSettingsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const settings = await tenantService.updateBookingSettings(
    user.tenantId,
    req.body,
    user.bearerToken,
  );
  res.status(StatusCodes.OK).json({ success: true, data: settings });
});

export const tenantBookHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const leads = await tenantService.tenantWideBook(user.tenantId, user.bearerToken);
  res.status(StatusCodes.OK).json({ success: true, data: leads });
});

export const startSelfServiceExportHandler = asyncHandler(async (req: Request, res: Response) => {
  const job = await tenantService.startSelfServiceExport(crmUser(req).tenantId);
  res.status(StatusCodes.CREATED).json({ success: true, data: job });
});

export const getSelfServiceExportStatusHandler = asyncHandler(
  async (req: Request, res: Response) => {
    const job = await tenantService.getExportStatus(crmUser(req).tenantId);
    res.status(StatusCodes.OK).json({ success: true, data: job });
  },
);

export const downloadSelfServiceExportHandler = asyncHandler(
  async (req: Request, res: Response) => {
    const filePath = await tenantService.getExportDownloadPath(
      crmUser(req).tenantId,
      req.params.jobId as string,
    );
    res.download(filePath, 'brokerage-export.zip');
  },
);

export const brokerageDashboardHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const data = await tenantService.brokerageDashboard(user.tenantId, user.bearerToken);
  res.status(StatusCodes.OK).json({ success: true, data });
});

export const reassignAllClientsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const result = await tenantService.reassignAllClients(
    user.tenantId,
    user.bearerToken,
    req.params.userId as string,
    req.body.toBrokerUserId,
    user.userId,
  );
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

/** modules/auth's Tenant Admin gate (Flow 0) asks this before People/Teams/Invitations. */
export const internalProfileStatusHandler = asyncHandler(async (req: Request, res: Response) => {
  const complete = await tenantService.isProfileComplete(req.params.tenantId as string);
  res.status(StatusCodes.OK).json({ success: true, data: { complete } });
});
