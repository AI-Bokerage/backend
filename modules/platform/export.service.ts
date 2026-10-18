import archiver from 'archiver';
import { createWriteStream } from 'node:fs';
import { mkdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';

import type { BrokerageExportJobDto, TenantRecord } from './platform.types';
import { getPrismaClient } from '../../config/database';
import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';
import { deleteClientsAiData } from '../crm/ai-deletion.service';
import { removeStoredFiles, storedFilePath } from '../crm/document-storage';

const EXPORT_DIR = path.join(process.cwd(), 'storage', 'brokerage-exports');

function toDto(row: {
  id: string;
  tenantId: string;
  status: string;
  readyAt: Date | null;
  expiresAt: Date | null;
}): BrokerageExportJobDto {
  return {
    id: row.id,
    tenantId: row.tenantId,
    status: row.status as BrokerageExportJobDto['status'],
    readyAt: row.readyAt?.toISOString() ?? null,
    expiresAt: row.expiresAt?.toISOString() ?? null,
  };
}

interface ExportPackage {
  json: Record<string, unknown>;
  sheets: Record<string, Record<string, unknown>[]>;
  documents: { name: string; storagePath: string }[];
}

/**
 * Flow 1 "Their data is packaged: spreadsheets plus a zip of documents" — one
 * CSV per record type and every stored document file. SIN/bank details stay
 * masked (hasSin/hasBankDetails), never exported in plain text. The team
 * roster lives in modules/auth's own database and isn't included.
 */
async function crmDataPackage(tenantId: string): Promise<Omit<ExportPackage, 'json'>> {
  const db = getPrismaClient();
  const byLead = { lead: { tenantId } };
  const [
    leads,
    mortgages,
    tasks,
    appointments,
    contacts,
    applicants,
    debts,
    properties,
    fundsSources,
    followUps,
    documents,
  ] = await Promise.all([
    db.lead.findMany({ where: { tenantId } }),
    db.mortgage.findMany({ where: byLead }),
    db.task.findMany({ where: { tenantId } }),
    db.appointment.findMany({ where: { tenantId } }),
    db.contact.findMany({ where: { tenantId } }),
    db.applicant.findMany({ where: byLead }),
    db.debt.findMany({ where: byLead }),
    db.propertyRecord.findMany({ where: byLead }),
    db.fundsSource.findMany({ where: byLead }),
    db.followUp.findMany({ where: byLead }),
    db.leadDocument.findMany({ where: { tenantId } }),
  ]);
  return {
    sheets: {
      leads: leads.map(({ sin, bankDetails, ...rest }) => ({
        ...rest,
        hasSin: sin !== null,
        hasBankDetails: bankDetails !== null,
      })),
      mortgages,
      tasks,
      appointments,
      contacts,
      applicants,
      debts,
      properties,
      fundsSources,
      followUps,
      documents: documents.map((d) => ({ ...d, storagePath: undefined })),
    },
    documents: documents.map((d) => ({
      name: `documents/${d.leadId}/${d.id}-${d.fileName}`,
      storagePath: d.storagePath,
    })),
  };
}

async function runExportJob(
  tenantId: string,
  buildPackage: () => Promise<ExportPackage>,
): Promise<BrokerageExportJobDto> {
  await mkdir(EXPORT_DIR, { recursive: true });

  const job = await getPrismaClient().brokerageExportJob.create({
    data: { tenantId, status: 'EXPORTING' },
  });

  const filePath = path.join(EXPORT_DIR, `${job.id}.zip`);

  try {
    await writeZip(filePath, await buildPackage());
    const now = new Date();
    const readyAt = now;
    const expiresAt = new Date(
      now.getTime() + env.platform.exportDownloadDays * 24 * 60 * 60 * 1000,
    );

    const updated = await getPrismaClient().brokerageExportJob.update({
      where: { id: job.id },
      data: { status: 'READY', filePath, readyAt, expiresAt },
    });
    return toDto(updated);
  } catch {
    const failed = await getPrismaClient().brokerageExportJob.update({
      where: { id: job.id },
      data: { status: 'FAILED' },
    });
    return toDto(failed);
  }
}

export async function startExport(tenant: TenantRecord): Promise<BrokerageExportJobDto> {
  return runExportJob(tenant.id, async () => ({
    json: { exportedAt: new Date().toISOString(), brokerage: tenant },
    ...(await crmDataPackage(tenant.id)),
  }));
}

/**
 * Tenant Admin's self-service "Export all our data" (Flow 2) — the same CRM
 * package as the Super-Admin-triggered cancellation export above.
 */
export async function startSelfServiceExport(tenantId: string): Promise<BrokerageExportJobDto> {
  return runExportJob(tenantId, async () => ({
    json: { exportedAt: new Date().toISOString(), tenantId },
    ...(await crmDataPackage(tenantId)),
  }));
}

/** Spreadsheet-safe CSV: quotes where needed, and a leading ' on cells a spreadsheet would run as a formula. */
export function toCsv(rows: Record<string, unknown>[]): string {
  const columns = [
    ...new Set(rows.flatMap((r) => Object.keys(r).filter((k) => r[k] !== undefined))),
  ];
  const cell = (value: unknown) => {
    if (value === null || value === undefined) return '';
    let text =
      value instanceof Date
        ? value.toISOString()
        : typeof value === 'object'
          ? JSON.stringify(value)
          : String(value);
    if (/^[=+\-@]/.test(text)) text = `'${text}`;
    return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  return [columns.join(','), ...rows.map((r) => columns.map((c) => cell(r[c])).join(','))].join(
    '\n',
  );
}

function writeZip(filePath: string, pkg: ExportPackage): Promise<void> {
  return new Promise((resolve, reject) => {
    const output = createWriteStream(filePath);
    const archive = archiver('zip', { zlib: { level: 9 } });
    output.on('close', resolve);
    archive.on('error', reject);
    archive.pipe(output);
    archive.append(JSON.stringify(pkg.json, null, 2), { name: 'brokerage.json' });
    for (const [name, rows] of Object.entries(pkg.sheets)) {
      archive.append(toCsv(rows), { name: `${name}.csv` });
    }
    for (const doc of pkg.documents) {
      archive.file(storedFilePath(doc.storagePath), { name: doc.name });
    }
    void archive.finalize();
  });
}

export async function getLatestExportForTenant(
  tenantId: string,
): Promise<BrokerageExportJobDto | null> {
  const row = await getPrismaClient().brokerageExportJob.findFirst({
    where: { tenantId },
    orderBy: { requestedAt: 'desc' },
  });
  return row ? toDto(row) : null;
}

export async function getDownloadPath(jobId: string): Promise<string> {
  const row = await getPrismaClient().brokerageExportJob.findUniqueOrThrow({
    where: { id: jobId },
  });
  if (row.status !== 'READY' || !row.filePath) {
    throw new ApiError('Export is not ready for download', 409);
  }
  if (row.expiresAt && row.expiresAt < new Date()) {
    throw new ApiError('Download window has expired', 410);
  }
  await stat(row.filePath); // throws if the file was already permanently deleted
  return row.filePath;
}

/**
 * Permanently deletes export files past the retention period. No internal
 * scheduler here (matches the rest of the Gen_MS family's own convention —
 * host-triggered sweeps) — see server.ts for where this gets called on an
 * interval.
 */
export async function purgeExpiredExports(): Promise<number> {
  const cutoff = new Date(Date.now() - env.platform.exportRetentionDays * 24 * 60 * 60 * 1000);
  const expired = await getPrismaClient().brokerageExportJob.findMany({
    where: { status: 'READY', readyAt: { lt: cutoff } },
  });

  for (const job of expired) {
    if (job.filePath) {
      await rm(job.filePath, { force: true });
    }
    await getPrismaClient().brokerageExportJob.update({
      where: { id: job.id },
      data: { status: 'DELETED', deletedAt: new Date() },
    });
  }
  return expired.length;
}

/**
 * Flow 1 "permanently deleted after the retention period": every CRM row and
 * stored document belonging to the brokerage. The activity log is kept — it
 * is the platform's own audit trail, not the brokerage's data.
 */
export async function purgeTenantCrmData(tenantId: string): Promise<void> {
  const db = getPrismaClient();
  const files = [
    ...(await db.leadDocument.findMany({ where: { tenantId }, select: { storagePath: true } })),
  ];
  const leads = await db.lead.findMany({ where: { tenantId }, select: { id: true } });
  // Lead's own children (intake, mortgages, documents, drafts, links) cascade with it.
  await db.$transaction([
    db.task.deleteMany({ where: { tenantId } }),
    db.appointment.deleteMany({ where: { tenantId } }),
    db.lead.deleteMany({ where: { tenantId } }),
    db.contact.deleteMany({ where: { tenantId } }),
    db.notification.deleteMany({ where: { tenantId } }),
    db.googleConnection.deleteMany({ where: { tenantId } }),
    db.crmUserSettings.deleteMany({ where: { tenantId } }),
    db.tenantProfile.deleteMany({ where: { tenantId } }),
    db.bookingPageSettings.deleteMany({ where: { tenantId } }),
  ]);
  await removeStoredFiles(files.map((f) => f.storagePath));
  // Their copies in the AI services go too (recorded first, retried hourly if a service is down).
  await deleteClientsAiData(tenantId, leads.map((l) => l.id));
}
