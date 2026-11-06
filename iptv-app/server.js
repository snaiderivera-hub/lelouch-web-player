const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { Readable } = require('stream');

const root = __dirname;
const port = 8686;

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
  '.m3u8': 'application/vnd.apple.mpegurl',
  '.ts': 'video/mp2t',
  '.mp4': 'video/mp4',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf'
};

function redactSafeUrl(raw) {
  if (!raw) return '';
  return raw
    .replace(/password=([^&]+)/gi, 'password=[REDACTED]')
    .replace(/(\/(?:live|movie|series)\/[^\/]+\/)[^\/]+(\/)/gi, '$1[REDACTED]$2');
}

async function handleProxy(req, res, targetRaw) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, HEAD');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Content-Type, Accept-Ranges');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (!targetRaw) {
    res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'Parámetro target requerido' }));
    return;
  }

  let targetUrl = decodeURIComponent(targetRaw);
  if (targetUrl.includes('%40')) {
    targetUrl = targetUrl.replace(/%40/g, '@');
  }

  let parsed;
  try {
    parsed = new URL(targetUrl);
  } catch (e) {
    res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'URL target inválida' }));
    return;
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'Protocolo no soportado' }));
    return;
  }

  const host = parsed.hostname.toLowerCase();
  // Anti-loopback
  if ((host === 'localhost' || host === '127.0.0.1' || host === '192.168.18.23') && parsed.port == port) {
    res.writeHead(403, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'Llamada recursiva a proxy bloqueada' }));
    return;
  }

  // Normalizaciones de puertos IPTV conocidos
  if (host === 'liontv.es' && (parsed.port === '80' || !parsed.port)) {
    parsed.port = '8080';
    targetUrl = parsed.href;
  }
  if (host === 'redworld.pro' && (parsed.port === '8888' || !parsed.port)) {
    parsed.port = '8880';
    targetUrl = parsed.href;
  }

  const abortController = new AbortController();
  req.on('close', () => {
    abortController.abort();
  });

  const forwardHeaders = {
    'User-Agent': 'IPTVSmartersPlayer / VLC 3.0.18 LibVLC',
    'Accept': '*/*'
  };
  if (req.headers['range']) {
    forwardHeaders['Range'] = req.headers['range'];
  }

  console.log(`[PROXY] -> ${redactSafeUrl(targetUrl)}`);

  try {
    const upstreamRes = await fetch(targetUrl, {
      method: req.method === 'HEAD' ? 'HEAD' : 'GET',
      headers: forwardHeaders,
      signal: abortController.signal,
      redirect: 'follow'
    });

    const responseHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS, HEAD',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Content-Type, Accept-Ranges'
    };

    const contentType = upstreamRes.headers.get('content-type');
    if (contentType) responseHeaders['Content-Type'] = contentType;

    const contentLength = upstreamRes.headers.get('content-length');
    if (contentLength) responseHeaders['Content-Length'] = contentLength;

    const contentRange = upstreamRes.headers.get('content-range');
    if (contentRange) responseHeaders['Content-Range'] = contentRange;

    const acceptRanges = upstreamRes.headers.get('accept-ranges');
    if (acceptRanges) responseHeaders['Accept-Ranges'] = acceptRanges;

    res.writeHead(upstreamRes.status, responseHeaders);

    if (req.method === 'HEAD' || !upstreamRes.body) {
      res.end();
      return;
    }

    const stream = Readable.fromWeb(upstreamRes.body);
    stream.on('error', (err) => {
      if (err.name !== 'AbortError') {
        console.warn(`[PROXY STREAM ERROR] ${err.message}`);
      }
      res.end();
    });
    stream.pipe(res);
  } catch (err) {
    if (err.name === 'AbortError') return;
    console.error(`[PROXY FAIL] ${err.message}`);
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ error: 'Error al contactar proveedor IPTV', details: err.message }));
    }
  }
}

const server = http.createServer((req, res) => {
  const reqUrl = new URL(req.url, `http://localhost:${port}`);
  const pathname = reqUrl.pathname;

  // Enrutar endpoint de proxy IPTV (/api/proxy o /proxy)
  if (pathname === '/api/proxy' || pathname === '/proxy') {
    const target = reqUrl.searchParams.get('target');
    handleProxy(req, res, target);
    return;
  }

  // Enrutar archivos estáticos
  let urlPath = pathname;
  if (urlPath === '/' || urlPath === '') urlPath = '/index.html';

  const filePath = path.join(root, decodeURIComponent(urlPath));

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath).toLowerCase();
    const contentType = mimeTypes[ext] || 'application/octet-stream';
    res.writeHead(200, {
      'Content-Type': contentType,
      'Access-Control-Allow-Origin': '*',
      'Cache-Control': 'no-store, no-cache, must-revalidate'
    });
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('404 - No encontrado: ' + urlPath);
  }
});

server.listen(port, '0.0.0.0', () => {
  console.log(`=======================================================`);
  console.log(`  LELOUCH IPTV LOCAL GATEWAY ACTIVO EN PUERTO ${port}`);
  console.log(`  Acceso Local PC:   http://localhost:${port}`);
  console.log(`  Acceso Toshiba TV: http://192.168.18.23:${port}`);
  console.log(`  Proxy IPTV CORS:   http://192.168.18.23:${port}/api/proxy`);
  console.log(`=======================================================`);
});
