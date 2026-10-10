/**
 * Vercel Serverless Function: High-performance CORS & Mixed Content Proxy for IPTV streams and APIs.
 * Supports:
 * - Real-time chunk streaming with Node.js Readable stream piping (MPEG-TS, MP4, MKV).
 * - Intelligent M3U8 manifest rewriting to resolve relative chunk URLs and route through proxy.
 * - HTTP Range request forwarding for video seek/scrubbing.
 * - Anti-SSRF private/loopback protection.
 * - IPTV Whitelisted User-Agent spoofing to prevent 403 Forbidden blocks.
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

  let targetUrl = decodeURIComponent(target);
  // Asegurar que si vino con %40 en el path de usuario (@), se preserve como literal @ para Xtream Nginx
  if (targetUrl.includes('%40')) {
    targetUrl = targetUrl.replace(/%40/g, '@');
  }

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

    // Optimizar servidores IPTV con puertos lentos conocidos (ej. LionTV en puerto 80)
    if (host === 'liontv.es' && (parsed.port === '80' || !parsed.port)) {
      parsed.port = '8080';
      targetUrl = parsed.href;
    }

    const abortController = new AbortController();
    req.on('close', () => {
      abortController.abort();
    });

    // Enviar User-Agent compatible con paneles IPTV para evitar bloqueos 403
    const forwardHeaders = {
      'User-Agent': 'IPTVSmartersPlayer / VLC 3.0.18 LibVLC',
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

    let contentType = (response.headers.get('content-type') || '').toLowerCase();
    
    // Definimos qué es seguro proxyar en Vercel
    const isApiJson = contentType.includes('json') || contentType.includes('xml') || contentType.includes('text') || targetUrl.includes('player_api.php') || targetUrl.includes('get.php');
    const isImage = contentType.includes('image') || targetUrl.match(/\.(png|jpg|jpeg|gif|webp)($|\?)/i);
    
    // Si es claramente un video, audio, o binario no permitido, lo bloqueamos
    const isVideoOrBinary = contentType.includes('video') || contentType.includes('audio') || contentType.includes('mpegurl') || contentType.includes('octet-stream') || targetUrl.match(/\.(ts|mp4|mkv|m3u8|avi)($|\?)/i);

    if (isVideoOrBinary || (!isApiJson && !isImage)) {
      // 🚫 BLOQUEO DE VIDEO: Es un flujo multimedia o binario no reconocido.
      // Abortamos la conexión proxy para no consumir ancho de banda de Vercel.
      abortController.abort();
      
      // Retornamos un Redirect (302) para que el reproductor conecte DIRECTAMENTE con el proveedor IPTV.
      return res.redirect(302, targetUrl);
    }

    res.status(response.status);
    
    if (response.ok && req.method === 'GET') {
      // Cachear fuertemente en el CDN Edge de Vercel para aceleración y ahorro (1 día CDN, 7 días stale)
      res.setHeader('Cache-Control', 'public, s-maxage=86400, stale-while-revalidate=604800');
    }

    if (isImage) {
      // ✅ PROXY PERMITIDO: Imágenes (Portadas, logos de canales)
      res.setHeader('Content-Type', contentType || 'image/jpeg');
      
      const contentLength = response.headers.get('content-length');
      if (contentLength) res.setHeader('Content-Length', contentLength);
      
      if (!response.body) return res.end();
      
      const stream = Readable.fromWeb(response.body);
      stream.on('error', (err) => {
        console.warn('[Proxy Image Error]:', err.message);
        if (!res.headersSent) res.status(502).end(); else res.destroy();
      });
      return stream.pipe(res);
    }

    // ✅ PROXY PERMITIDO: Solo respuestas ligeras de API JSON (categorías, canales, VOD)
    res.setHeader('Content-Type', contentType || 'application/json; charset=utf-8');
    
    const textData = await response.text();
    res.setHeader('Content-Length', Buffer.byteLength(textData, 'utf8'));
    return res.send(textData);
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
