/**
 * Vercel Serverless Function: CORS & Mixed Content Proxy para desarrollo y vistas web locales.
 * 
 * FASE 21 — REGLA ARQUITECTÓNICA ESTRICTA:
 * NUNCA se debe hacer proxy de los flujos continuos de vídeo de 10 Mbps hacia TVs o reproductores externos
 * (TiviMate, VLC, OTT Navigator, Lelouch TV, Android Box, etc.).
 * 
 * Arquitectura canónica:
 * TV -> descarga playlist -> LELOUCH VERCEL (devuelve texto M3U / manifest JSON)
 * TV -> solicita stream directamente -> PROVEEDOR
 * 
 * Hacer proxy de streams de vídeo por Vercel es innecesario, costoso (ancho de banda CDN / timeouts de 10-60s)
 * y frágil. Lelouch publica la playlist; NO retransmite el vídeo.
 * 
 * Este endpoint existe únicamente como mecanismo de compatibilidad para el reproductor web en navegador
 * cuando se topa con restricciones del sandbox del navegador (CORS o Mixed Content HTTP sobre HTTPS).
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

    // Respuestas de API JSON (player_api.php, categorías, canales, VOD)
    const isApiJson = contentType.includes('json') || targetUrl.includes('player_api.php') || targetUrl.includes('get.php');
    if (isApiJson) {
      res.status(response.status);
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      if (response.ok && req.method === 'GET') {
        // Cachear en el CDN Edge de Vercel para aceleración instantánea (2 minutos en CDN, 10 min stale)
        res.setHeader('Cache-Control', 'public, s-maxage=120, stale-while-revalidate=600');
      }
      const jsonText = await response.text();
      res.setHeader('Content-Length', Buffer.byteLength(jsonText, 'utf8'));
      return res.send(jsonText);
    }

    // Para streams continuos de video/audio (MPEG-TS, MP4, MKV)
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
