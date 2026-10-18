jest.mock('../../config/database');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/crm/document-storage');
jest.mock('../../modules/crm/google/gmail.service');
jest.mock('../../modules/crm/google/google-connection.service');
jest.mock('../../modules/crm/system-mailer');
jest.mock('../../modules/crm/ai-documents.service');
// The real clamd protocol is tested in document-processing.spec.ts against a fake clamd.
jest.mock('../../modules/crm/document-processing', () => ({
  ...jest.requireActual('../../modules/crm/document-processing'),
  scanForViruses: jest.fn(async (data: Buffer) =>
    data.includes('EICAR-STANDARD-ANTIVIRUS-TEST-FILE') ? { clean: false, virus: 'Eicar-Test-Signature' } : { clean: true },
  ),
}));

import { getPrismaClient } from '../../config/database';
import { sendForReading } from '../../modules/crm/ai-documents.service';
import { storeFile } from '../../modules/crm/document-storage';
import * as docs from '../../modules/crm/document.service';
import { sendEmail } from '../../modules/crm/google/gmail.service';
import { getStatus } from '../../modules/crm/google/google-connection.service';
import { sendSystemEmail } from '../../modules/crm/system-mailer';

const TENANT = 'tenant-1';
const EICAR = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*';

const mockPrisma = {
  lead: { findUnique: jest.fn(), update: jest.fn() },
  leadDocument: { create: jest.fn(), findMany: jest.fn() },
  documentRequestLink: { create: jest.fn(), findUnique: jest.fn() },
  notification: { create: jest.fn() },
};

function lead(overrides: Record<string, unknown> = {}) {
  return {
    id: 'lead-1',
    tenantId: TENANT,
    brokerUserId: 'me',
    name: 'Pat Client',
    email: 'pat@example.com',
    requiredDocuments: ['T4', 'NOA'],
    ...overrides,
  };
}

const pdf = (data = 'pdf bytes') => ({
  type: 'T4' as const,
  fileName: 't4.pdf',
  mimeType: 'application/pdf',
  data: Buffer.from(data),
});

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.lead.findUnique.mockResolvedValue(lead());
  mockPrisma.leadDocument.findMany.mockResolvedValue([]);
  (storeFile as jest.Mock).mockResolvedValue('tenant-1/key');
  mockPrisma.leadDocument.create.mockImplementation(({ data }) => ({ id: 'doc-1', ...data }));
});

describe('"What happens to every file that arrives"', () => {
  it('rejects an infected file: never stored, broker notified', async () => {
    await expect(docs.uploadDocument(TENANT, 'lead-1', ['me'], pdf(EICAR))).rejects.toMatchObject({
      statusCode: 422,
    });
    expect(storeFile).not.toHaveBeenCalled();
    expect(mockPrisma.notification.create).toHaveBeenCalledWith({
      data: expect.objectContaining({ userId: 'me', type: 'document.rejected', body: 't4.pdf — Eicar-Test-Signature' }),
    });
  });

  it('stores a clean file as "reading" and hands it to the AI document service', async () => {
    (sendForReading as jest.Mock).mockImplementation(async (doc) => ({ ...doc, aiDocumentId: 'ai-1' }));
    const doc = await docs.uploadDocument(TENANT, 'lead-1', ['me'], pdf());
    expect(mockPrisma.leadDocument.create).toHaveBeenCalledWith({
      data: expect.objectContaining({ readStatus: 'PENDING', uploadedBy: 'BROKER', storagePath: 'tenant-1/key' }),
    });
    expect(sendForReading).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'doc-1' }),
      expect.objectContaining({ fileName: 't4.pdf' }),
      expect.objectContaining({ id: 'lead-1' }),
      'BROKER',
    );
    expect(doc).toMatchObject({ aiDocumentId: 'ai-1' });
    expect(doc).not.toHaveProperty('storagePath');
  });
});

describe('listDocuments — missing-documents list', () => {
  it('missing = ticked minus received', async () => {
    mockPrisma.leadDocument.findMany.mockResolvedValue([{ type: 'T4' }]);
    const result = await docs.listDocuments(TENANT, 'lead-1', ['me']);
    expect(result.missing).toEqual(['NOA']);
  });
});

describe('requestDocuments — Flow 4.5', () => {
  it("needs the person's email", async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(lead({ email: null }));
    await expect(docs.requestDocuments(TENANT, 'me', 'lead-1', ['me'], {})).rejects.toMatchObject({
      statusCode: 400,
    });
  });

  it('needs something ticked and still missing', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(lead({ requiredDocuments: [] }));
    await expect(docs.requestDocuments(TENANT, 'me', 'lead-1', ['me'], {})).rejects.toMatchObject({
      statusCode: 400,
    });
  });

  it('"Connect it, or send from the system address instead" when email is not connected', async () => {
    (getStatus as jest.Mock).mockResolvedValue({ connected: false });
    await expect(docs.requestDocuments(TENANT, 'me', 'lead-1', ['me'], {})).rejects.toMatchObject({
      statusCode: 409,
    });
    expect(mockPrisma.documentRequestLink.create).not.toHaveBeenCalled();

    const result = await docs.requestDocuments(TENANT, 'me', 'lead-1', ['me'], {
      useSystemAddress: true,
    });
    expect(result.sentVia).toBe('SYSTEM_ADDRESS');
    expect(sendSystemEmail).toHaveBeenCalled();
  });

  it("emails a 7-day, one-person link from the broker's own inbox, storing only a hash", async () => {
    (getStatus as jest.Mock).mockResolvedValue({ connected: true });
    const result = await docs.requestDocuments(TENANT, 'me', 'lead-1', ['me'], {});

    const token = result.url.split('/').pop()!;
    const stored = mockPrisma.documentRequestLink.create.mock.calls[0][0].data;
    expect(stored.tokenHash).not.toBe(token);
    expect(stored.expiresAt.getTime() - Date.now()).toBeGreaterThan(6.9 * 24 * 3600 * 1000);
    expect(sendEmail).toHaveBeenCalledWith(
      TENANT,
      'me',
      expect.objectContaining({ to: 'pat@example.com', leadId: 'lead-1' }),
    );
    expect(result.missing).toEqual(['T4', 'NOA']);
  });
});

describe('borrower link', () => {
  it('"Expired? → generate a new link" — 410', async () => {
    mockPrisma.documentRequestLink.findUnique.mockResolvedValue({
      expiresAt: new Date(Date.now() - 1000),
      lead: lead(),
    });
    await expect(docs.getPublicRequest('a'.repeat(64))).rejects.toMatchObject({ statusCode: 410 });
  });

  it('shows only a first name and the outstanding list', async () => {
    mockPrisma.documentRequestLink.findUnique.mockResolvedValue({
      expiresAt: new Date(Date.now() + 1000),
      lead: lead(),
    });
    const result = await docs.getPublicRequest('a'.repeat(64));
    expect(result).toEqual({
      firstName: 'Pat',
      missing: ['T4', 'NOA'],
      expiresAt: expect.any(Date),
    });
  });
});
