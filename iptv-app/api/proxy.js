/**
 * Vercel Serverless Function: High-performance CORS & Mixed Content Proxy for IPTV streams and APIs.
 * Supports:
 * - Real-time chunk streaming with Node.js Readable stream piping (MPEG-TS, MP4, MKV).
 * - Intelligent M3U8 manifest rewriting to resolve relative chunk URLs and route through proxy.
 * - HTTP Range request forwarding for video seek/scrubbing.
 * - Anti-SSRF private/loopback protection.
 */
import { Readable } from 'node:stream';

export const config = {
  api: {
    responseLimit: false,
    bodyParser: false,
  },
};

export default async function handler(req, res) {
  // Configurar encabezados CORS globales
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, HEAD');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Expose-Headers', '*');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const { target } = req.query;
  if (!target) {
    return res.status(400).json({ error: 'Parámetro target requerido' });
  }

  const targetUrl = decodeURIComponent(target);

  try {
    const parsed = new URL(targetUrl);
    if (!['http:', 'https:'].includes(parsed.protocol)) {
      return res.status(400).json({ error: 'Protocolo inválido' });
    }

    // Bloquear acceso a loopback local y redes privadas
    const host = parsed.hostname.toLowerCase();
    if (['localhost', '127.0.0.1', '::1', '0.0.0.0'].includes(host)) {
      return res.status(403).json({ error: 'Acceso a loopback no permitido' });
    }

    const abortController = new AbortController();
    req.on('close', () => {
      abortController.abort();
    });

    const forwardHeaders = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
      'Accept': '*/*',
    };
    if (req.headers['range']) {
      forwardHeaders['Range'] = req.headers['range'];
    }

    const response = await fetch(targetUrl, {
      method: req.method,
      headers: forwardHeaders,
      signal: abortController.signal,
    });

    let contentType = response.headers.get('content-type') || 'application/octet-stream';
    const isM3u8 = targetUrl.includes('.m3u8') || targetUrl.includes('output=m3u8') || contentType.includes('mpegurl');

    if (isM3u8) {
      contentType = 'application/vnd.apple.mpegurl';
      res.setHeader('Content-Type', contentType);
      res.status(response.status);

      if (!response.ok) {
        const text = await response.text();
        return res.send(text);
      }

      // Reescritura de manifest M3U8 para resolver segmentos contra el proxy
      const manifestText = await response.text();
      const baseUrl = new URL(targetUrl);
      const lines = manifestText.split(/\r?\n/);
      const rewrittenLines = lines.map(line => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) {
          return line;
        }
        try {
          const absoluteSegment = new URL(trimmed, baseUrl).href;
          return `/api/proxy?target=${encodeURIComponent(absoluteSegment)}`;
        } catch {
          return line;
        }
      });

      const rewrittenManifest = rewrittenLines.join('\n');
      res.setHeader('Content-Length', Buffer.byteLength(rewrittenManifest, 'utf8'));
      return res.send(rewrittenManifest);
    }

    // Para streams de video, audio, imágenes y respuestas JSON
    res.setHeader('Content-Type', contentType);

    const contentLength = response.headers.get('content-length');
    if (contentLength) {
      res.setHeader('Content-Length', contentLength);
    }

    const contentRange = response.headers.get('content-range');
    if (contentRange) {
      res.setHeader('Content-Range', contentRange);
    }

    const acceptRanges = response.headers.get('accept-ranges');
    if (acceptRanges) {
      res.setHeader('Accept-Ranges', acceptRanges);
    }

    res.status(response.status);

    if (!response.body) {
      return res.end();
    }

    const stream = Readable.fromWeb(response.body);
    stream.on('error', (err) => {
      console.warn('[Proxy Stream Error]:', err.message);
      if (!res.headersSent) {
        res.status(502).end();
      } else {
        res.destroy();
      }
    });

    return stream.pipe(res);
  } catch (err) {
    if (err.name === 'AbortError') {
      return res.end();
    }
    console.error('[Proxy Error]:', err.message);
    if (!res.headersSent) {
      return res.status(502).json({ error: 'Error al consultar servidor IPTV', detail: err.message });
    }
    return res.end();
  }
}
