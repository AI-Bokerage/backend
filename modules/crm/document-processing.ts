import type { DocumentType } from '@prisma/client';
import net from 'node:net';

import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

/**
 * Flow 4.5 "Virus check clean?" — every file is scanned by clamd (the
 * docker-compose `clamav` service) before it is stored, using clamd's INSTREAM
 * command: chunks prefixed with their 4-byte length, then a zero length.
 * Fail closed: if the scanner can't answer, the upload is refused (503), never
 * stored unscanned. Reading the file is the AI document service's job: see
 * ai-documents.service.ts.
 */
const CHUNK = 64 * 1024;
const SCAN_TIMEOUT_MS = 30_000;

export function scanForViruses(data: Buffer): Promise<{ clean: boolean; virus?: string }> {
  const { host, port } = env.crm.clamav;
  return new Promise((resolve, reject) => {
    const unavailable = () =>
      reject(new ApiError("The virus scanner isn't available right now — try the upload again in a minute", 503));
    const socket = net.connect({ host, port });
    let reply = '';
    socket.setTimeout(SCAN_TIMEOUT_MS, () => socket.destroy(new Error('clamd timeout')));
    socket.on('error', unavailable);
    socket.on('data', (d) => (reply += d.toString('utf8')));
    socket.on('close', () => {
      // "stream: OK\0" or "stream: Eicar-Test-Signature FOUND\0"; anything else is an error.
      const answer = reply.replace(/\0/g, '').trim();
      if (answer.endsWith('OK')) return resolve({ clean: true });
      const found = /^stream: (.+) FOUND$/.exec(answer);
      if (found) return resolve({ clean: false, virus: found[1] });
      unavailable();
    });
    socket.on('connect', () => {
      socket.write('zINSTREAM\0');
      for (let i = 0; i < data.length; i += CHUNK) {
        const chunk = data.subarray(i, i + CHUNK);
        const size = Buffer.alloc(4);
        size.writeUInt32BE(chunk.length);
        socket.write(size);
        socket.write(chunk);
      }
      socket.end(Buffer.alloc(4)); // zero length: end of stream
    });
  });
}

/** "Missing-documents list" — ticked types with nothing received yet. */
export function missingDocuments(required: DocumentType[], received: { type: DocumentType }[]) {
  const have = new Set(received.map((d) => d.type));
  return required.filter((t) => !have.has(t));
}
