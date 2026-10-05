/**
 * Vercel Serverless Function: Endpoint dinámico de entrega de Playlists M3U.
 * 
 * Ruta pública: https://lelouch-web-player.vercel.app/api/playlist/<TOKEN>
 * 
 * Características:
 * 1. Acceso autenticado mediante token público de alta entropía (hash SHA-256 en BD).
 * 2. Consulta la vista 'v_resolved_playlist_items' en Supabase para resolver URLs
 *    en tiempo real (evitando duplicar credenciales y sincronizando cambios automáticamente).
 * 3. Entrega formato estándar #EXTM3U compatible con TiviMate, OTT Navigator, VLC,
 *    IPTV Smarters, Smart TVs y reproductores Android/iOS.
 * 4. Cache-Control optimizado (60s local, 300s edge CDN) para máxima velocidad sin saturar.
 */

import crypto from 'node:crypto';
import zlib from 'node:zlib';

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://rotupbdeljgfddywryhk.supabase.co';
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo';

/**
 * FASE 22 — RLS y Resolución Server-Side:
 * El TV Box no tiene sesión de usuario en Supabase (el token es su credencial).
 * En el backend Serverless de Vercel, usamos SUPABASE_SERVICE_ROLE_KEY si está disponible
 * para resolver la consulta saltando RLS de forma segura en el servidor,
 * o SUPABASE_ANON_KEY como fallback.
 */
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const SERVER_AUTH_KEY = SUPABASE_SERVICE_ROLE_KEY || SUPABASE_ANON_KEY;

export const config = {
  maxDuration: 60,
  api: {
    responseLimit: false,
  },
};

/**
 * FASE 19 — Redacción de credenciales en Vercel logs y mensajes de error
 */
function redactSensitiveUrl(input) {
  if (!input) return '';
  let str = typeof input === 'string' ? input : String(input);
  str = str.replace(/([?&](?:username|user|usr)=)[^& \n\r\t"']+/gi, '$1***');
  str = str.replace(/([?&](?:password|pass|pwd)=)[^& \n\r\t"']+/gi, '$1***');
  str = str.replace(/([?&](?:token|auth|secret)=)[^& \n\r\t"']+/gi, '$1***');
  str = str.replace(/(\/(?:live|movie|series)\/)[^/ \n\r\t"']+\/[^/ \n\r\t"']+(\/[^ \n\r\t"']*)/gi, '$1***/***$2');
  str = str.replace(/(get\.php\?[^ \n\r\t"']+)/gi, (match) => {
    return match
      .replace(/([?&](?:username|user|usr)=)[^&]+/gi, '$1***')
      .replace(/([?&](?:password|pass|pwd)=)[^&]+/gi, '$1***');
  });
  return str;
}

/**
 * FASE 21 — NO hacer proxy de los videos por Vercel.
 * TV solicita stream directamente al PROVEEDOR.
 * Garantiza que la URL entregada en la playlist (#EXTM3U o JSON manifest) sea SIEMPRE la
 * URL directa del proveedor. Si la URL contenía un envoltorio de proxy (/api/proxy?target=...),
 * se desenvuelve para que el reproductor (TV/móvil) conecte directamente al servidor IPTV.
 * Lelouch publica la playlist, no retransmite el vídeo.
 */
function unwrapProxyUrl(url) {
  if (!url || typeof url !== 'string') return '';
  const trimmed = url.trim();
  if (trimmed.includes('/api/proxy') || trimmed.includes('/proxy?target=')) {
    try {
      const match = trimmed.match(/[?&]target=([^&]+)/);
      if (match && match[1]) {
        return decodeURIComponent(match[1]);
      }
    } catch {
      // Ignorar fallo de decodificación y usar original
    }
  }
  return trimmed;
}

class PlaylistGenerator {
  static convertToParserPlaylist(items, options = {}) {
    const {
      playlistName = 'Mi Lista LELOUCH',
      epgUrl = null
    } = options;

    const validItems = Array.isArray(items) 
      ? items.filter(x => x.isEnabled !== false) 
      : [];

    const headerAttrs = {};
    if (playlistName) headerAttrs['name'] = playlistName;
    if (epgUrl) headerAttrs['x-tvg-url'] = epgUrl;

    return {
      header: {
        attrs: headerAttrs,
        raw: `#EXTM3U name="${playlistName}"`
      },
      items: validItems.map((item, idx) => ({
        name: item.name || 'Canal',
        url: unwrapProxyUrl(item.streamUrl || item.directUrl || item.url || ''),
        tvg: {
          id: item.tvgId || item.epgId || '',
          name: item.tvgName || item.epgName || item.name || '',
          logo: item.logo || item.directLogo || '',
          country: item.country || '',
          language: item.language || '',
          rec: item.extraAttributes?.['tvg-rec'] || '',
          shift: item.extraAttributes?.['tvg-shift'] || ''
        },
        group: {
          title: item.group || item.categoryName || item.category || 'General'
        },
        http: {
          referrer: item.headers?.referrer || item.headers?.Referer || '',
          'user-agent': item.headers?.userAgent || item.headers?.['User-Agent'] || ''
        },
        catchup: item.catchup || null,
        timeshift: item.catchup?.days ? String(item.catchup.days) : '',
        kodiProps: item.kodiProps || {},
        extraAttributes: item.extraAttributes || {},
        mediaType: item.mediaType || item.media_type || (item.url?.includes('/series/') ? 'series' : (item.url?.includes('/movie/') ? 'movie' : 'live')),
        line: idx + 1,
        raw: ''
      }))
    };
  }

  static generateM3U(input, options = {}) {
    let playlist = input;
    if (Array.isArray(input)) {
      playlist = this.convertToParserPlaylist(input, options);
    }

    if (!playlist || typeof playlist !== 'object') return '#EXTM3U\n';

    const {
      playlistName = playlist.header?.attrs?.name || 'Mi Lista LELOUCH',
      epgUrl = playlist.header?.attrs?.['x-tvg-url'] || playlist.header?.attrs?.['url-tvg'] || null,
      includeCatchup = true,
      includeVlcOpts = true,
      includeKodiProps = true,
      includeExtGrp = false
    } = options;

    const items = Array.isArray(playlist.items) ? playlist.items : [];
    const blocks = [];

    let header = '#EXTM3U';
    if (playlistName) header += ` name="${this._escapeAttr(playlistName)}"`;
    if (epgUrl) header += ` x-tvg-url="${this._escapeAttr(epgUrl)}"`;
    blocks.push(header);

    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      const streamUrl = it.url;
      if (!streamUrl) continue;

      const duration = -1;
      let extinf = `#EXTINF:${duration}`;

      const tvgId = it.tvg?.id;
      if (tvgId) extinf += ` tvg-id="${this._escapeAttr(tvgId)}"`;

      const tvgName = it.tvg?.name || it.name;
      if (tvgName) extinf += ` tvg-name="${this._escapeAttr(tvgName)}"`;

      const logo = it.tvg?.logo;
      if (logo) extinf += ` tvg-logo="${this._escapeAttr(logo)}"`;

      const country = it.tvg?.country;
      if (country) extinf += ` tvg-country="${this._escapeAttr(country)}"`;

      const language = it.tvg?.language;
      if (language) extinf += ` tvg-language="${this._escapeAttr(language)}"`;

      const group = it.group?.title || 'General';
      extinf += ` group-title="${this._escapeAttr(group)}"`;

      const itemMediaType = it.mediaType || (streamUrl.includes('/series/') ? 'series' : (streamUrl.includes('/movie/') ? 'movie' : 'live'));
      extinf += ` tvg-type="${this._escapeAttr(itemMediaType)}" media-type="${this._escapeAttr(itemMediaType)}"`;

      if (includeCatchup && it.catchup) {
        const catchupType = it.catchup.type || 'default';
        const catchupDays = it.catchup.days || 7;
        extinf += ` tv-archive="1" tv-archive-duration="${catchupDays}" catchup="${catchupType}"`;
        if (it.catchup.hours) extinf += ` catchup-hours="${it.catchup.hours}"`;
        if (it.catchup.source) extinf += ` catchup-source="${this._escapeAttr(it.catchup.source)}"`;
      }

      if (it.extraAttributes && typeof it.extraAttributes === 'object') {
        for (const [k, v] of Object.entries(it.extraAttributes)) {
          if (v !== undefined && v !== null && !extinf.includes(` ${k}=`)) {
            extinf += ` ${k}="${this._escapeAttr(v)}"`;
          }
        }
      }

      const itemLines = [`${extinf},${it.name || 'Canal'}`];

      if (includeExtGrp && group) {
        itemLines.push(`#EXTGRP:${group}`);
      }

      if (includeKodiProps && it.kodiProps && typeof it.kodiProps === 'object') {
        for (const [k, v] of Object.entries(it.kodiProps)) {
          itemLines.push(`#KODIPROP:${k}=${v}`);
        }
      }

      if (includeVlcOpts) {
        const ua = it.http?.['user-agent'];
        const ref = it.http?.referrer;
        if (ua) itemLines.push(`#EXTVLCOPT:http-user-agent=${ua}`);
        if (ref) itemLines.push(`#EXTVLCOPT:http-referrer=${ref}`);
      }

      itemLines.push(streamUrl);
      blocks.push(itemLines.join('\n'));
    }

    return blocks.join('\n\n') + '\n';
  }

  static _escapeAttr(val) {
    if (!val) return '';
    return String(val).replace(/"/g, "'").replace(/[\r\n]+/g, ' ').trim();
  }
}

export default async function handler(req, res) {
  // Encabezados CORS universales
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  // FASE 20 — Anti-indexación obligatoria: La URL M3U es un secreto compartido
  res.setHeader('X-Robots-Tag', 'noindex, nofollow, noarchive, nosnippet');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  // FASE 27 — Rechazar métodos no permitidos (solo GET y HEAD)
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.setHeader('Allow', 'GET, HEAD, OPTIONS');
    return res.status(405).json({ error: 'Método no permitido', status: 405 });
  }

  // 1. Extraer token y formato de la petición (FASE 17)
  let token = req.query.token;
  let isVersionCheck = req.query.format === 'version' || req.query.check === 'version';
  let isJsonManifest = req.query.format === 'json' || req.query.format === 'manifest' || (req.headers['accept'] && req.headers['accept'].includes('application/json'));

  if (req.url) {
    if (req.url.includes('/api/playlist/version/')) {
      const match = req.url.match(/\/api\/playlist\/version\/([a-zA-Z0-9_-]+)/);
      if (match) {
        token = match[1];
        isVersionCheck = true;
      }
    } else if (req.url.includes('/api/playlist/manifest/')) {
      const match = req.url.match(/\/api\/playlist\/manifest\/([a-zA-Z0-9_-]+)/);
      if (match) {
        token = match[1];
        isJsonManifest = true;
      }
    } else if (!token) {
      const match = req.url.match(/\/api\/playlist\/([a-zA-Z0-9_-]+)/);
      if (match && !['version', 'manifest', 'json'].includes(match[1])) {
        token = match[1];
      }
    }
  }

  // Helper unificado para enviar respuestas de error compatibles con GET y HEAD
  const sendError = (status, message) => {
    if (isJsonManifest || isVersionCheck) {
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      const body = JSON.stringify({ error: message, status });
      res.setHeader('Content-Length', String(Buffer.byteLength(body, 'utf8')));
      if (req.method === 'HEAD') {
        return res.status(status).end();
      }
      return res.status(status).send(body);
    }
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    const body = `#EXTM3U\n#EXTINF:-1,${message}\nhttp://localhost/error`;
    res.setHeader('Content-Length', String(Buffer.byteLength(body, 'utf8')));
    if (req.method === 'HEAD') {
      return res.status(status).end();
    }
    return res.status(status).send(body);
  };

  // Validar formato del token (debe ser una cadena alfanumérica segura)
  const tokenRegex = /^[a-zA-Z0-9_-]{16,128}$/;
  if (!token || typeof token !== 'string' || !tokenRegex.test(token.trim())) {
    return sendError(400, 'Formato de token invalido');
  }

  token = token.trim();

  // 2. Calcular SHA-256 del token para comparar con la base de datos (FASE 9 & 10)
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');

  const headers = {
    'apikey': SERVER_AUTH_KEY,
    'Authorization': `Bearer ${SERVER_AUTH_KEY}`,
    'Content-Type': 'application/json'
  };

  try {
    // 3. Buscar el token por HASH SHA-256 en la tabla 'playlist_access_tokens' (FASE 9)
    const tokenQueryUrl = `${SUPABASE_URL}/rest/v1/playlist_access_tokens?token_hash=eq.${tokenHash}&select=*`;
    const tokenRes = await fetch(tokenQueryUrl, { headers });

    if (!tokenRes.ok) {
      console.error('[API Playlist] Error consultando token en Supabase:', redactSensitiveUrl(await tokenRes.text()));
      return sendError(500, 'Error validando token en la base de datos');
    }

    const tokenRows = await tokenRes.json();
    if (!tokenRows || tokenRows.length === 0) {
      // FASE 27: Token inexistente -> 404
      return sendError(404, 'Enlace M3U no encontrado o revocado');
    }

    const tokenRecord = tokenRows[0];

    // FASE 27: Verificar si el token está deshabilitado -> 410 Gone (o 404)
    if (tokenRecord.enabled === false || tokenRecord.is_active === false) {
      return sendError(410, 'Este enlace M3U ha sido desactivado');
    }

    // FASE 27: Verificar si expiró -> 410 Gone
    if (tokenRecord.expires_at) {
      const expires = new Date(tokenRecord.expires_at);
      if (expires < new Date()) {
        return sendError(410, 'Este enlace M3U ha expirado');
      }
    }

    // 4. Actualizar métricas de acceso de forma asíncrona (FASE 9: last_accessed_at, request_count)
    const currentCount = Number(tokenRecord.request_count ?? tokenRecord.access_count ?? 0);
    fetch(`${SUPABASE_URL}/rest/v1/playlist_access_tokens?id=eq.${tokenRecord.id}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({
        last_accessed_at: new Date().toISOString(),
        request_count: currentCount + 1,
        access_count: currentCount + 1
      })
    }).catch(err => console.warn('[API Playlist] Error actualizando métricas de token:', redactSensitiveUrl(err?.message || String(err))));

    // 5. Consultar metadatos y versión de la playlist
    const playlistRes = await fetch(`${SUPABASE_URL}/rest/v1/custom_playlists?id=eq.${tokenRecord.playlist_id}&select=name,enabled,description,version,updated_at`, { headers });

    let playlistName = 'Mi Lista LELOUCH';
    let playlistVersion = 1;
    let playlistUpdatedAt = new Date().toISOString();
    if (playlistRes.ok) {
      const plData = await playlistRes.json();
      if (plData?.[0]) {
        if (plData[0].enabled === false) {
          // Playlist deshabilitada por el usuario -> 410 Gone
          return sendError(410, 'Esta playlist esta desactivada por el usuario');
        }
        if (plData[0].name) playlistName = plData[0].name;
        if (plData[0].version !== undefined && plData[0].version !== null) {
          playlistVersion = Number(plData[0].version);
        }
        if (plData[0].updated_at) playlistUpdatedAt = plData[0].updated_at;
      }
    }

    const clientEtag = req.headers['if-none-match'];
    const currentEtag = `"v${playlistVersion}"`;

    // FASE 17 - CASO A: Consulta ultraligera de versión para Lelouch TV / Phone
    // Lelouch TV -> playlist/version -> ¿cambió?
    if (isVersionCheck) {
      const localVer = req.query.local_version || req.headers['x-local-version'];
      const inSync = (localVer !== undefined && localVer !== null && Number(localVer) === playlistVersion);

      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.setHeader('Cache-Control', 'no-store, max-age=0');
      res.setHeader('X-Playlist-Version', String(playlistVersion));
      res.setHeader('ETag', currentEtag);

      if (clientEtag === currentEtag || inSync) {
        return res.status(304).end(); // Si sí: no hago nada
      }

      if (req.method === 'HEAD') {
        return res.status(200).end();
      }

      return res.status(200).json({
        playlistId: tokenRecord.playlist_id,
        name: playlistName,
        version: playlistVersion,
        updatedAt: playlistUpdatedAt,
        inSync: false // Si no: sincronizo
      });
    }

    // 6. Consultar los items resueltos de la vista 'v_resolved_playlist_items' con paginación robusta
    // Supabase PostgREST tiene un tope estricto de 1000 filas por petición (max-rows = 1000).
    // Paginamos secuencialmente para evitar agotar el pool de conexiones y timeouts.
    let allItems = [];
    const PAGE_SIZE = 1000;
    const MAX_PAGES = Number(process.env.MAX_PLAYLIST_PAGES) || 60; // Soporta hasta 60,000 elementos
    let offset = 0;
    let reachedEnd = false;

    for (let page = 0; page < MAX_PAGES; page++) {
      const pageUrl = `${SUPABASE_URL}/rest/v1/v_resolved_playlist_items?playlist_id=eq.${tokenRecord.playlist_id}&enabled=eq.true&order=position.asc&select=id,name,direct_name,group,direct_group,logo,direct_logo,resolved_stream_url,direct_url,media_type,tvg_id,tvg_name,position,container_extension&limit=${PAGE_SIZE}&offset=${offset}`;

      let pageRes;
      try {
        pageRes = await fetch(pageUrl, { headers });
      } catch (fetchErr) {
        console.error(`[API Playlist] Error de red al consultar página ${page} (offset ${offset}):`, redactSensitiveUrl(fetchErr?.message || String(fetchErr)));
        return sendError(502, `Error de conexión con la base de datos en página ${page} (offset ${offset})`);
      }

      if (!pageRes.ok) {
        const errText = await pageRes.text();
        console.error(`[API Playlist] Error Supabase HTTP ${pageRes.status} en página ${page} (offset ${offset}):`, redactSensitiveUrl(errText));
        return sendError(502, `Error al cargar página ${page} (offset ${offset}) desde la base de datos`);
      }

      let pageData;
      try {
        pageData = await pageRes.json();
      } catch (jsonErr) {
        console.error(`[API Playlist] Error parseando respuesta JSON en página ${page} (offset ${offset}):`, jsonErr);
        return sendError(500, `Respuesta malformada en página ${page} (offset ${offset})`);
      }

      if (!Array.isArray(pageData) || pageData.length === 0) {
        reachedEnd = true;
        break; // Fin del catálogo alcanzado
      }

      allItems = allItems.concat(pageData);

      // Si el lote vino con menos de PAGE_SIZE, es la última página
      if (pageData.length < PAGE_SIZE) {
        reachedEnd = true;
        break;
      }

      offset += PAGE_SIZE;
    }

    // FASE 31 / P0 #1: Si el catálogo supera MAX_PAGES y no alcanzó el fin natural,
    // NUNCA devolver una playlist mutilada/parcial como HTTP 200.
    // Devolver error explícito 413 (Payload Too Large) indicando el límite exacto.
    if (!reachedEnd) {
      console.error(`[API Playlist] Playlist excede el límite máximo de ${MAX_PAGES * PAGE_SIZE} elementos`);
      return sendError(413, `La playlist supera el límite máximo de ${MAX_PAGES * PAGE_SIZE} elementos permitido por el servidor`);
    }

    const items = allItems;

    // Mapear a LelouchMediaItem[] canónico con URLs directas al proveedor (FASE 21 & 27)
    // FASE 27: custom_name y custom_group tienen precedencia sobre los valores por defecto del proveedor
    const mediaItems = items.map((it, idx) => {
      const streamUrl = unwrapProxyUrl(it.resolved_stream_url || it.direct_url);
      const itemName = it.custom_name || it.name || it.direct_name || 'Canal';
      const itemGroup = it.custom_group || it.group || it.direct_group || 'General';
      let mediaType = (it.media_type || '').toLowerCase();
      if (!mediaType || mediaType === 'live') {
        const lUrl = streamUrl.toLowerCase();
        const lName = itemName.toLowerCase();
        const lGrp = itemGroup.toLowerCase();
        const hasEpisodePattern = /\b(s\d{1,2}|t\d{1,2}|cap\.?\s*\d+|ep\.?\s*\d+|temporada\s*\d+)\b/i.test(lName);
        const isSeriesGroup = lGrp.includes('serie') || lGrp.includes('temporada') || lGrp.includes('season') || lGrp.includes('dorama') || lGrp.includes('anime') || lGrp.includes('novela');
        const isMovieGroup = lGrp.includes('película') || lGrp.includes('pelicula') || lGrp.includes('movie') || lGrp.includes('cine') || lGrp.includes('estrenos') || lGrp.includes('vod');
        if (lUrl.includes('/series/') || isSeriesGroup || hasEpisodePattern) {
          mediaType = 'series';
        } else if (lUrl.includes('/movie/') || isMovieGroup || (lUrl.endsWith('.mp4') && !lUrl.includes('.m3u8'))) {
          mediaType = 'movie';
        } else {
          mediaType = mediaType || 'live';
        }
      }
      return {
        id: it.id,
        name: itemName,
        streamUrl,
        group: itemGroup,
        logo: it.custom_logo || it.logo || it.direct_logo || '',
        tvgId: it.tvg_id || '',
        tvgName: it.tvg_name || it.custom_name || it.name || '',
        mediaType,
        sortOrder: it.position ?? idx,
        isEnabled: it.enabled !== false,
        itemType: it.item_type || 'catalog',
        headers: it.metadata?.headers || null,
        kodiProps: it.metadata?.kodiProps || null,
        catchup: it.metadata?.catchup || null,
        extraAttributes: it.metadata?.extraAttributes || null
      };
    });

    // FASE 17 - CASO B: Entrega nativa de Playlist Manifest JSON (Lelouch TV / Phone / Web)
    // Lelouch TV -> playlist manifest JSON -> Room -> TV UI (sin re-parsear M3U)
    if (isJsonManifest) {
      const jsonBody = JSON.stringify({
        playlist: {
          id: tokenRecord.playlist_id,
          name: playlistName,
          version: playlistVersion,
          updatedAt: playlistUpdatedAt,
          itemCount: mediaItems.length
        },
        items: mediaItems
      });

      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.setHeader('Content-Length', String(Buffer.byteLength(jsonBody, 'utf8')));
      res.setHeader('Cache-Control', 'no-store, max-age=0');
      res.setHeader('X-Playlist-Version', String(playlistVersion));
      res.setHeader('ETag', currentEtag);

      if (clientEtag === currentEtag) {
        return res.status(304).end(); // Si sí: no hago nada
      }

      if (req.method === 'HEAD') {
        return res.status(200).end();
      }

      const acceptEncoding = (req.headers['accept-encoding'] || '').toLowerCase();
      if (acceptEncoding.includes('gzip')) {
        const compressed = zlib.gzipSync(Buffer.from(jsonBody, 'utf8'));
        res.setHeader('Content-Encoding', 'gzip');
        res.setHeader('Content-Length', String(compressed.length));
        return res.status(200).send(compressed);
      }

      return res.status(200).send(jsonBody);
    }

    // FASE 17 & 27 - CASO C: Entrega clásica de #EXTM3U para reproductores externos (TiviMate, VLC, OTT Navigator)
    // y soporte para peticiones HEAD
    const parserPlaylist = PlaylistGenerator.convertToParserPlaylist(mediaItems, { playlistName });
    const m3uContent = PlaylistGenerator.generateM3U(parserPlaylist);

    const safeFilename = playlistName.replace(/[^a-zA-Z0-9_-]/g, '_');
    const contentLength = Buffer.byteLength(m3uContent, 'utf8');

    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store, max-age=0');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Playlist-Version', String(playlistVersion));
    res.setHeader('ETag', currentEtag);
    res.setHeader('Content-Disposition', `inline; filename="${safeFilename}.m3u"`);

    if (req.method === 'HEAD') {
      res.setHeader('Content-Length', String(contentLength));
      return res.status(200).end();
    }

    const acceptEncoding = (req.headers['accept-encoding'] || '').toLowerCase();
    if (acceptEncoding.includes('gzip')) {
      const compressed = zlib.gzipSync(Buffer.from(m3uContent, 'utf8'));
      res.setHeader('Content-Encoding', 'gzip');
      res.setHeader('Content-Length', String(compressed.length));
      return res.status(200).send(compressed);
    }

    res.setHeader('Content-Length', String(contentLength));
    return res.status(200).send(m3uContent);

  } catch (error) {
    console.error('[API Playlist] Excepción en handler:', redactSensitiveUrl(error?.message || String(error)));
    return sendError(500, 'Error interno del servidor');
  }
}
