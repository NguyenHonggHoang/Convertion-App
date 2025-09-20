const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  const target = process.env.BACKEND_URL || 'http://localhost:8080';

  app.use(
    ['/api', '/v3/api-docs', '/swagger-ui', '/actuator'],
    createProxyMiddleware({
      target,
      changeOrigin: true,
      logLevel: 'warn',
      ws: false,
      // Only proxy API routes so /favicon.ico and static assets are served locally
      onError(err, req, res) {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            error: 'proxy_error',
            message: 'Backend is unreachable',
            detail: err.code || String(err),
          })
        );
      },
    })
  );
};