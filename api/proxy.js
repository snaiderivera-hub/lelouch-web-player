/**
 * Vercel Serverless Function: CORS & Mixed Content Proxy for IPTV streams and APIs.
 * Solves:
 * 1. Mixed Content: Allows HTTPS Vercel frontend to query HTTP IPTV servers.
 * 2. CORS: Injects Access-Control-Allow-Origin: * for all responses.
 */

export const config = {
  api: {
    responseLimit: '50mb',
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

    // Bloquear acceso a loopback local
    const host = parsed.hostname.toLowerCase();
    if (['localhost', '127.0.0.1', '::1', '0.0.0.0'].includes(host)) {
      return res.status(403).json({ error: 'Acceso a loopback no permitido' });
    }

    const response = await fetch(targetUrl, {
      method: req.method,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Accept': '*/*',
        ...(req.headers['range'] ? { 'Range': req.headers['range'] } : {}),
      },
    });

    // Reenviar encabezados relevantes
    const contentType = response.headers.get('content-type') || 'application/octet-stream';
    res.setHeader('Content-Type', contentType);

    const contentLength = response.headers.get('content-length');
    if (contentLength) {
      res.setHeader('Content-Length', contentLength);
    }

    const contentRange = response.headers.get('content-range');
    if (contentRange) {
      res.setHeader('Content-Range', contentRange);
    }

    res.status(response.status);

    const arrayBuffer = await response.arrayBuffer();
    return res.send(Buffer.from(arrayBuffer));
  } catch (err) {
    console.error('[Proxy Error]:', err.message);
    return res.status(502).json({ error: 'Error al consultar servidor IPTV', detail: err.message });
  }
}
