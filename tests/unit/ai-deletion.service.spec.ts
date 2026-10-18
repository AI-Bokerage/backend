jest.mock('../../config/database');
jest.mock('../../modules/crm/ai.client');

import { getPrismaClient } from '../../config/database';
import { deleteClientsAiData, retryAiDataDeletions } from '../../modules/crm/ai-deletion.service';
import { aiCall } from '../../modules/crm/ai.client';

const ALL = ['voice', 'docread', 'notes', 'email', 'assistant', 'search', 'gateway'];
const row = (pending = ALL) => ({ id: 'd1', tenantId: 'tenant-1', clientId: 'lead-1', pending, attempts: 0 });
const mockPrisma = {
  aiDataDeletion: { createMany: jest.fn(), findMany: jest.fn(), delete: jest.fn(), update: jest.fn() },
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('4.11 "Deleted permanently" reaches every AI service', () => {
  it('records the deletion first, then tells all seven services; done when all answer', async () => {
    mockPrisma.aiDataDeletion.findMany.mockResolvedValue([row()]);
    (aiCall as jest.Mock).mockResolvedValue({ deleted: {} });
    await deleteClientsAiData('tenant-1', ['lead-1']);

    expect(mockPrisma.aiDataDeletion.createMany).toHaveBeenCalledWith({
      data: [{ tenantId: 'tenant-1', clientId: 'lead-1', pending: ALL }],
      skipDuplicates: true,
    });
    expect((aiCall as jest.Mock).mock.calls.map((c) => [c[0], c[1], c[2]])).toEqual(
      ALL.map((s) => [s, 'DELETE', '/v1/clients/lead-1/ai-data']),
    );
    expect(mockPrisma.aiDataDeletion.delete).toHaveBeenCalledWith({ where: { id: 'd1' } });
  });

  it('keeps only the services that were down, and the hourly retry finishes them', async () => {
    mockPrisma.aiDataDeletion.findMany.mockResolvedValue([row()]);
    (aiCall as jest.Mock).mockImplementation(async (service: string) => {
      if (service === 'search') throw new Error("can't be reached");
      return { deleted: {} };
    });
    await deleteClientsAiData('tenant-1', ['lead-1']);
    expect(mockPrisma.aiDataDeletion.update).toHaveBeenCalledWith({
      where: { id: 'd1' },
      data: expect.objectContaining({ pending: ['search'], attempts: { increment: 1 } }),
    });
    expect(mockPrisma.aiDataDeletion.delete).not.toHaveBeenCalled();

    (aiCall as jest.Mock).mockReset().mockResolvedValue({ deleted: {} });
    mockPrisma.aiDataDeletion.findMany.mockResolvedValue([row(['search'])]);
    await expect(retryAiDataDeletions()).resolves.toBe(1);
    expect(aiCall).toHaveBeenCalledTimes(1);
    expect(mockPrisma.aiDataDeletion.delete).toHaveBeenCalled();
  });
});
