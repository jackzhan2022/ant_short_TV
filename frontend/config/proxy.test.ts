import { createServer, get, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import express from '@umijs/bundler-utils/compiled/express';
import { createProxy } from '@umijs/bundler-utils/dist/proxy';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import proxy from './proxy';

const mediaPath = '/materials/1/44/images/202610/131/task-215-1/derived/display.png';
const signature = `?sign=${'a'.repeat(64)}&t=68e51d80`;
const headers = {
  Cookie: 'ANT_SHORT_SESSION=fixture-session',
  Authorization: 'Bearer fixture-token',
  'X-Tenant-Id': '1',
  'X-CSRF-Token': 'fixture-csrf',
  Referer: 'http://localhost:8000/',
};

async function listen(server: Server) {
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

const read = (url: string, requestHeaders: Record<string, string> = {}) =>
  new Promise<{ status: number; headers: import('node:http').IncomingHttpHeaders; body: string }>((resolve, reject) => {
    get(url, { headers: requestHeaders }, (response) => {
      const chunks: Buffer[] = [];
      response.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
      response.on('end', () => resolve({
        status: response.statusCode ?? 0,
        headers: response.headers,
        body: Buffer.concat(chunks).toString(),
      }));
    }).on('error', reject);
  });

describe('local development media proxy', () => {
  let servers: Server[];
  let baseUrl: string;
  let receivedHeaders: import('node:http').IncomingHttpHeaders;
  let receivedPath: string | undefined;

  beforeEach(async () => {
    servers = [];
    const api = createServer((request, response) => {
      if (request.headers.cookie !== headers.Cookie) {
        response.writeHead(401).end('unauthorized');
        return;
      }
      response.writeHead(302, {
        Location: request.url === '/api/foreign'
          ? 'https://other.example.test/image.png'
          : `https://antvcdn.aixmax.cn${mediaPath}${signature}`,
      }).end();
    });
    servers.push(api);
    const apiTarget = await listen(api);
    const cdn = createServer((request, response) => {
      receivedHeaders = request.headers;
      receivedPath = request.url;
      const allowed = request.headers.referer === 'https://antv.aixmax.cn/' &&
        request.url === `${mediaPath}${signature}`;
      response.writeHead(allowed ? 200 : 403, { 'Content-Type': 'image/png' }).end('fixture-image');
    });
    servers.push(cdn);
    const cdnTarget = await listen(cdn);
    const mediaProxy = proxy.dev['/__dev-media/materials/' as keyof typeof proxy.dev];
    const app = express();
    createProxy({
      '/api/': { ...proxy.dev['/api/'], target: apiTarget, logLevel: 'silent' },
      ...(mediaProxy ? { '/__dev-media/materials/': { ...mediaProxy, target: cdnTarget } } : {}),
    }, app);
    app.use((_request, response) => response.status(404).end());
    const local = createServer(app);
    servers.push(local);
    baseUrl = await listen(local);
  });

  afterEach(async () => {
    await Promise.all(servers.map((server) => new Promise<void>((resolve) => server.close(() => resolve()))));
  });

  it('loads an authorized signed CDN image through the local origin without forwarding app credentials', async () => {
    const redirect = await read(`${baseUrl}/api/projects/44/ai-image-results/131/thumbnail`, headers);
    expect(redirect.status).toBe(302);
    expect(redirect.headers.location).toBe(`/__dev-media${mediaPath}${signature}`);

    const image = await read(`${baseUrl}${redirect.headers.location}`, headers);
    expect(image.status).toBe(200);
    expect(image.headers['content-type']).toBe('image/png');
    expect(receivedPath).toBe(`${mediaPath}${signature}`);
    expect(receivedHeaders.referer).toBe('https://antv.aixmax.cn/');
    expect(receivedHeaders.cookie).toBeUndefined();
    expect(receivedHeaders.authorization).toBeUndefined();
    expect(receivedHeaders['x-tenant-id']).toBeUndefined();
    expect(receivedHeaders['x-csrf-token']).toBeUndefined();
  });

  it('preserves backend authentication checks and unrelated redirects', async () => {
    expect((await read(`${baseUrl}/api/projects/44/ai-image-results/131/thumbnail`)).status).toBe(401);
    expect((await read(`${baseUrl}/api/foreign`, headers)).headers.location)
      .toBe('https://other.example.test/image.png');
  });

  it('does not configure this media proxy for production-like environments', () => {
    expect(Object.keys(proxy.test)).not.toContain('/__dev-media/materials/');
    expect(Object.keys(proxy.pre)).not.toContain('/__dev-media/materials/');
  });
});
