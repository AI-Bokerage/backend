import type { DocumentType } from '@prisma/client';
import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import * as aiDocuments from './ai-documents.service';
import * as bookingService from './booking.service';
import { leadAccessIds } from './crm-auth.middleware';
import * as documentService from './document.service';
import { asyncHandler } from '../../utils/async-handler';

/** Raw-body upload: the file is the request body; type and name come in the query string. */
function incomingFile(req: Request): documentService.IncomingFile {
  return {
    type: req.query.type as DocumentType,
    fileName: req.query.fileName as string,
    mimeType: req.headers['content-type'] ?? 'application/octet-stream',
    data: Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0),
  };
}

export const listDocumentsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const data = await documentService.listDocuments(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
  );
  res.status(StatusCodes.OK).json({ success: true, data });
});

export const setRequiredDocumentsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const data = await documentService.setRequiredDocuments(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body.types,
  );
  res.status(StatusCodes.OK).json({ success: true, data });
});

export const uploadDocumentHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const doc = await documentService.uploadDocument(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    incomingFile(req),
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: doc });
});

export const requestDocumentsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const data = await documentService.requestDocuments(
    user.tenantId,
    user.userId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data });
});

// Flow 4.5 review of what the AI document service read (ai-documents.service.ts).
async function reviewArgs(req: Request) {
  const { tenantId, userId, role } = req.crmUser!;
  return [{ tenantId, userId, role }, req.params.documentId as string, await leadAccessIds(req.crmUser!)] as const;
}

export const getDocumentReviewHandler = asyncHandler(async (req: Request, res: Response) => {
  res.json({ success: true, data: await aiDocuments.getDocumentReview(...(await reviewArgs(req))) });
});

export const decideDocumentHandler = asyncHandler(async (req: Request, res: Response) => {
  const data = await aiDocuments.decideDocument(...(await reviewArgs(req)), req.body.decisions);
  res.json({ success: true, data });
});

export const confirmDocumentMortgageHandler = asyncHandler(async (req: Request, res: Response) => {
  const data = await aiDocuments.confirmDocumentMortgage(...(await reviewArgs(req)), req.body);
  res.json({ success: true, data });
});

export const retryDocumentReadHandler = asyncHandler(async (req: Request, res: Response) => {
  const data = await aiDocuments.retryDocumentRead(...(await reviewArgs(req)));
  res.status(StatusCodes.ACCEPTED).json({ success: true, data });
});

export const applyDocumentReviewHandler = asyncHandler(async (req: Request, res: Response) => {
  const data = await aiDocuments.applyDocumentReview(...(await reviewArgs(req)));
  res.json({ success: true, data });
});

export const downloadDocumentHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const file = await documentService.downloadDocument(
    user.tenantId,
    req.params.documentId as string,
    await leadAccessIds(user),
    user.userId,
  );
  res.setHeader('Content-Type', file.mimeType);
  res.setHeader(
    'Content-Disposition',
    `attachment; filename="${encodeURIComponent(file.fileName)}"`,
  );
  res.status(StatusCodes.OK).send(file.data);
});

// ── Public, no login: the borrower's link and the booking page ──────────────

export const publicDocumentRequestHandler = asyncHandler(async (req: Request, res: Response) => {
  const data = await documentService.getPublicRequest(req.params.token as string);
  res.status(StatusCodes.OK).json({ success: true, data });
});

export const publicDocumentUploadHandler = asyncHandler(async (req: Request, res: Response) => {
  const data = await documentService.publicUpload(req.params.token as string, incomingFile(req));
  res.status(StatusCodes.CREATED).json({ success: true, data });
});

export const publicBookingHandler = asyncHandler(async (req: Request, res: Response) => {
  const data = await bookingService.intakeBooking(req.params.slug as string, req.body);
  res.status(data.duplicate ? StatusCodes.OK : StatusCodes.CREATED).json({ success: true, data });
});
