import { randomUUID } from 'node:crypto';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';

import { env } from '../../config/env';

/**
 * Flow 4.5 "Stored securely in Canada". shortcut: local disk under
 * DOCUMENT_STORAGE_DIR; swap these three functions for a Canadian-region
 * object store at deploy time — callers only ever see the opaque key.
 */
function root() {
  return path.resolve(process.cwd(), env.crm.documentStorageDir);
}

export async function storeFile(tenantId: string, data: Buffer): Promise<string> {
  const key = path.join(tenantId, randomUUID());
  const full = path.join(root(), key);
  await mkdir(path.dirname(full), { recursive: true });
  await writeFile(full, data);
  return key;
}

export function storedFilePath(key: string) {
  return path.join(root(), key);
}

export async function readStoredFile(key: string): Promise<Buffer> {
  return readFile(storedFilePath(key));
}

export async function removeStoredFiles(keys: string[]) {
  await Promise.all(keys.map((k) => rm(storedFilePath(k), { force: true })));
}
