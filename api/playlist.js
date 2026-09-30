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

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://rotupbdeljgfddywryhk.supabase.co';
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo';

export const config = {
  api: {
    responseLimit: false,
  },
};

/**
 * Escapa comillas en atributos M3U
 */
function escapeAttr(val) {
  if (!val) return '';
  return String(val).replace(/"/g, "'").trim();
}

export default async function handler(req, res) {
  // Encabezados CORS universales
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  // 1. Extraer token de query o de la URL
  let token = req.query.token;
  if (!token && req.url) {
    const match = req.url.match(/\/api\/playlist\/([a-zA-Z0-9_-]+)/);
    if (match) token = match[1];
  }

  if (!token || typeof token !== 'string' || token.trim().length < 8) {
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    return res.status(400).send('#EXTM3U\n#EXTINF:-1,Token de acceso no proporcionado o invalido\nhttp://localhost/error');
  }

  token = token.trim();

  // 2. Calcular SHA-256 del token para comparar con la base de datos
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');

  const headers = {
    'apikey': SUPABASE_ANON_KEY,
    'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
    'Content-Type': 'application/json'
  };

  try {
    // 3. Buscar el token en la tabla 'playlist_access_tokens'
    const tokenQueryUrl = `${SUPABASE_URL}/rest/v1/playlist_access_tokens?token_hash=eq.${tokenHash}&is_active=eq.true&select=id,playlist_id,is_active,expires_at,access_count`;
    const tokenRes = await fetch(tokenQueryUrl, { headers });

    if (!tokenRes.ok) {
      console.error('[API Playlist] Error consultando token en Supabase:', await tokenRes.text());
      res.setHeader('Content-Type', 'text/plain; charset=utf-8');
      return res.status(500).send('#EXTM3U\n#EXTINF:-1,Error interno validando token\nhttp://localhost/error');
    }

    const tokenRows = await tokenRes.json();
    if (!tokenRows || tokenRows.length === 0) {
      res.setHeader('Content-Type', 'text/plain; charset=utf-8');
      return res.status(404).send('#EXTM3U\n#EXTINF:-1,Enlace M3U no encontrado o revocado\nhttp://localhost/error');
    }

    const tokenRecord = tokenRows[0];

    // Verificar si expiró
    if (tokenRecord.expires_at) {
      const expires = new Date(tokenRecord.expires_at);
      if (expires < new Date()) {
        res.setHeader('Content-Type', 'text/plain; charset=utf-8');
        return res.status(403).send('#EXTM3U\n#EXTINF:-1,Este enlace M3U ha expirado\nhttp://localhost/error');
      }
    }

    // 4. Actualizar métricas de acceso de forma asíncrona (sin bloquear la respuesta)
    fetch(`${SUPABASE_URL}/rest/v1/playlist_access_tokens?id=eq.${tokenRecord.id}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({
        last_used_at: new Date().toISOString(),
        access_count: (tokenRecord.access_count || 0) + 1
      })
    }).catch(err => console.warn('[API Playlist] Error actualizando métricas de token:', err));

    // 5. Consultar los items resueltos de la vista 'v_resolved_playlist_items'
    const itemsUrl = `${SUPABASE_URL}/rest/v1/v_resolved_playlist_items?playlist_id=eq.${tokenRecord.playlist_id}&enabled=eq.true&order=position.asc`;
    const [itemsRes, playlistRes] = await Promise.all([
      fetch(itemsUrl, { headers }),
      fetch(`${SUPABASE_URL}/rest/v1/custom_playlists?id=eq.${tokenRecord.playlist_id}&select=name`, { headers })
    ]);

    if (!itemsRes.ok) {
      console.error('[API Playlist] Error obteniendo items:', await itemsRes.text());
      res.setHeader('Content-Type', 'text/plain; charset=utf-8');
      return res.status(500).send('#EXTM3U\n#EXTINF:-1,Error cargando items de la lista\nhttp://localhost/error');
    }

    const items = await itemsRes.json();
    let playlistName = 'Mi Lista LELOUCH';
    if (playlistRes.ok) {
      const plData = await playlistRes.json();
      if (plData?.[0]?.name) playlistName = plData[0].name;
    }

    // 6. Ensamblar manifiesto #EXTM3U estándar de alto rendimiento
    const lines = [`#EXTM3U name="${escapeAttr(playlistName)}"\n`];

    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      const streamUrl = it.resolved_stream_url || it.direct_url;
      if (!streamUrl) continue;

      const duration = -1;
      let extinf = `#EXTINF:${duration}`;

      const name = it.name || it.direct_name || it.custom_name || 'Canal';
      const logo = it.logo || it.direct_logo || it.custom_logo || '';
      const group = it.group || it.direct_group || it.custom_group || 'General';

      if (it.tvg_id) extinf += ` tvg-id="${escapeAttr(it.tvg_id)}"`;
      if (name) extinf += ` tvg-name="${escapeAttr(name)}"`;
      if (logo) extinf += ` tvg-logo="${escapeAttr(logo)}"`;
      extinf += ` group-title="${escapeAttr(group)}"`;

      if (it.media_type === 'radio') extinf += ' radio="true"';

      lines.push(`${extinf},${name}`);

      // Headers IPTV estándar (#EXTVLCOPT) si se definieron en metadatos
      if (it.metadata?.headers?.['User-Agent']) {
        lines.push(`#EXTVLCOPT:http-user-agent=${it.metadata.headers['User-Agent']}`);
      }
      if (it.metadata?.headers?.['Referer']) {
        lines.push(`#EXTVLCOPT:http-referrer=${it.metadata.headers['Referer']}`);
      }

      lines.push(streamUrl);
    }

    const m3uContent = lines.join('\n');
    const safeFilename = playlistName.replace(/[^a-zA-Z0-9_-]/g, '_');

    // 7. Enviar respuesta con headers IPTV y CDN
    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl; charset=utf-8');
    res.setHeader('Content-Disposition', `inline; filename="${safeFilename}.m3u"`);
    res.setHeader('Cache-Control', 'public, max-age=60, s-maxage=300');
    return res.status(200).send(m3uContent);

  } catch (error) {
    console.error('[API Playlist] Excepción en handler:', error);
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    return res.status(500).send('#EXTM3U\n#EXTINF:-1,Error interno del servidor\nhttp://localhost/error');
  }
}
