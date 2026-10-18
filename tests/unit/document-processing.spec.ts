import net from 'node:net';

import { env } from '../../config/env';
import { scanForViruses } from '../../modules/crm/document-processing';

const EICAR = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*';

/** A fake clamd: reads one zINSTREAM (length-prefixed chunks, then a zero length) and answers like clamd. */
function fakeClamd(answer?: string) {
  const received: Buffer[] = [];
  const server = net.createServer((socket) => {
    let buf = Buffer.alloc(0);
    socket.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      const command = 'zINSTREAM\0';
      if (buf.length < command.length) return;
      let at = command.length;
      const chunks: Buffer[] = [];
      while (buf.length >= at + 4) {
        const size = buf.readUInt32BE(at);
        if (size === 0) {
          const file = Buffer.concat(chunks);
          received.push(file);
          socket.end(answer ?? (file.includes(EICAR) ? 'stream: Eicar-Test-Signature FOUND\0' : 'stream: OK\0'));
          return;
        }
        if (buf.length < at + 4 + size) return;
        chunks.push(buf.subarray(at + 4, at + 4 + size));
        at += 4 + size;
      }
    });
  });
  return new Promise<{ server: net.Server; received: Buffer[] }>((resolve) =>
    server.listen(0, '127.0.0.1', () => {
      (env.crm as { clamav: { host: string; port: number } }).clamav = {
        host: '127.0.0.1',
        port: (server.address() as net.AddressInfo).port,
      };
      resolve({ server, received });
    }),
  );
}

describe('scanForViruses — Flow 4.5 "Virus check clean?" (clamd INSTREAM)', () => {
  let server: net.Server | undefined;
  afterEach(() => server?.close());

  it('streams the whole file (in chunks) and reports clean', async () => {
    const fake = await fakeClamd();
    server = fake.server;
    const big = Buffer.alloc(200_000, 7); // more than one 64 KB chunk
    await expect(scanForViruses(big)).resolves.toEqual({ clean: true });
    expect(fake.received[0]!.equals(big)).toBe(true);
  });

  it('reports an infected file with the virus name', async () => {
    server = (await fakeClamd()).server;
    await expect(scanForViruses(Buffer.from(`%PDF ${EICAR}`))).resolves.toEqual({
      clean: false,
      virus: 'Eicar-Test-Signature',
    });
  });

  it('fails closed: a scanner error or no scanner at all refuses the upload (503)', async () => {
    server = (await fakeClamd('INSTREAM size limit exceeded. ERROR\0')).server;
    await expect(scanForViruses(Buffer.from('x'))).rejects.toMatchObject({ statusCode: 503 });

    server.close();
    server = undefined;
    (env.crm as { clamav: { host: string; port: number } }).clamav = { host: '127.0.0.1', port: 1 };
    await expect(scanForViruses(Buffer.from('x'))).rejects.toMatchObject({ statusCode: 503 });
  });
});
