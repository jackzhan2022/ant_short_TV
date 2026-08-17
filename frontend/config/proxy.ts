/**
 * Local development proxy.
 *
 * Run the Java backend on http://localhost:8080, then start the frontend with:
 * npm run dev
 */
const localApiTarget = process.env.API_PROXY_TARGET || 'http://localhost:8080';

export default {
  dev: {
    '/api/': {
      target: localApiTarget,
      changeOrigin: true,
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
