import type { ClientRequest, IncomingMessage } from 'node:http';

/**
 * Local development proxy.
 *
 * Run the Java backend on http://localhost:8080, then start the frontend with:
 * npm run dev
 */
const localApiTarget = process.env.API_PROXY_TARGET || 'http://localhost:8080';
const mediaCdnOrigin = 'https://antvcdn.aixmax.cn';
const mediaProxyPrefix = '/__dev-media';

export default {
  dev: {
    '/api/': {
      target: localApiTarget,
      changeOrigin: true,
      onProxyRes(response: IncomingMessage) {
        const location = response.headers.location;
        if (!location) return;
        try {
          const url = new URL(location);
          if (url.origin === mediaCdnOrigin && url.pathname.startsWith('/materials/')) {
            response.headers.location = `${mediaProxyPrefix}${url.pathname}${url.search}`;
          }
        } catch {
          // Leave relative or unrelated backend redirects unchanged.
        }
      },
    },
    '/__dev-media/materials/': {
      target: mediaCdnOrigin,
      changeOrigin: true,
      pathRewrite: { '^/__dev-media': '' },
      logLevel: 'silent' as const,
      onProxyReq(request: ClientRequest) {
        request.setHeader('Referer', 'https://antv.aixmax.cn/');
        for (const header of ['cookie', 'authorization', 'x-tenant-id', 'x-csrf-token', 'x-xsrf-token']) {
          request.removeHeader(header);
        }
      },
    },
  },
  test: {
    '/api/': {
      target: 'https://pro-api.ant-design-demo.workers.dev',
      changeOrigin: true,
    },
  },
  pre: {
    '/api/': {
      target: 'your pre url',
      changeOrigin: true,
    },
  },
};
