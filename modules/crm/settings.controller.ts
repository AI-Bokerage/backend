import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import * as settingsService from './settings.service';
import { asyncHandler } from '../../utils/async-handler';

export const getSettingsHandler = asyncHandler(async (req: Request, res: Response) => {
  const settings = await settingsService.getSettings(req.crmUser!.userId);
  res.status(StatusCodes.OK).json({ success: true, data: settings });
});

export const updateSettingsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const settings = await settingsService.updateSettings(user.tenantId, user.userId, req.body);
  res.status(StatusCodes.OK).json({ success: true, data: settings });
});
