/**
 * @module PlaylistGenerator
 * Generador de archivos y manifiestos M3U / M3U8 Plus y JSON Manifests.
 * 
 * Capaz de serializar colecciones de LelouchMediaItem generando:
 * 1. Formato estándar M3U Plus con cabeceras #EXTM3U, directivas #EXTINF enriquecidas,
 *    atributos tvg (id, name, logo, country, language), catchup/timeshift,
 *    directivas #EXTGRP, #EXTVLCOPT y #KODIPROP.
 * 2. Formato JSON Manifest compacto para sincronización ultra rápida con LELOUCH Android TV y Mobile
 *    sin obligar a las aplicaciones nativas a reparsear texto M3U.
 */

/**
 * FASE 21 — Garantiza que las URLs de stream sean directas al proveedor (NO proxy por Vercel).
 * Si la URL contiene un wrapper /api/proxy?target=..., lo desenvuelve a la URL directa original.
 * @param {string} rawUrl
 * @returns {string} URL directa del proveedor
 */
export function unwrapProxyUrl(rawUrl) {
  if (!rawUrl || typeof rawUrl !== 'string') return '';
  const trimmed = rawUrl.trim();
  if (trimmed.includes('/api/proxy') || trimmed.includes('/proxy?target=')) {
    try {
      const match = trimmed.match(/[?&]target=([^&]+)/);
      if (match && match[1]) {
        return decodeURIComponent(match[1]);
      }
    } catch {
      // Ignorar fallo de decodificación
    }
  }
  return trimmed;
}

export class PlaylistGenerator {
  /**
   * Convierte una colección de LelouchMediaItem[] al modelo de playlist estándar del parser (iptv-m3u-playlist-parser).
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {Object} [options]
   * @returns {import('./PlaylistTypes.js').ExternalM3uPlaylist}
   */
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
        line: idx + 1,
        raw: ''
      }))
    };
  }

  /**
   * Genera el contenido de texto completo en formato #EXTM3U a partir de
   * un ExternalM3uPlaylist (modelo del parser) o LelouchMediaItem[].
   * 
   * Si una librería externa (como iptv-m3u-playlist-parser) con soporte generateM3U()
   * está configurada, la invoca directamente; en caso contrario, serializa el modelo
   * preservando el 100% de atributos y directivas.
   * 
   * @param {import('./PlaylistTypes.js').ExternalM3uPlaylist | import('./PlaylistTypes.js').LelouchMediaItem[]} input
   * @param {import('./PlaylistTypes.js').GenerateM3uOptions} [options]
   * @returns {string}
   */
  static generateM3U(input, options = {}) {
    // Si la entrada es un array de LelouchMediaItem[], convertir primero a parser playlist
    let playlist = input;
    if (Array.isArray(input)) {
      playlist = this.convertToParserPlaylist(input, options);
    }

    if (!playlist || typeof playlist !== 'object') return '#EXTM3U\n';

    // 1. Delegar en librería externa si tiene generateM3U (ej. iptv-m3u-playlist-parser >= 0.5.0)
    if (typeof globalThis !== 'undefined' && globalThis.iptvPlaylistParser?.generateM3U) {
      try {
        return globalThis.iptvPlaylistParser.generateM3U(playlist, options);
      } catch (e) {
        console.warn('[PlaylistGenerator] Error en generateM3U externo, usando generador canónico:', e);
      }
    }

    const {
      playlistName = playlist.header?.attrs?.name || 'Mi Lista LELOUCH',
      epgUrl = playlist.header?.attrs?.['x-tvg-url'] || playlist.header?.attrs?.['url-tvg'] || null,
      includeCatchup = true,
      includeVlcOpts = true,
      includeKodiProps = true,
      includeExtGrp = false
    } = options;

    const items = Array.isArray(playlist.items) ? playlist.items : [];

    // Construcción limpia y estándar del manifiesto
    const blocks = [];

    // Cabecera #EXTM3U
    let header = '#EXTM3U';
    if (playlistName) header += ` name="${this._escapeAttr(playlistName)}"`;
    if (epgUrl) header += ` x-tvg-url="${this._escapeAttr(epgUrl)}"`;
    blocks.push(header);

    // Iterar items del modelo del parser
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

      // Catch-up / Timeshift
      if (includeCatchup && it.catchup) {
        const catchupType = it.catchup.type || 'default';
        const catchupDays = it.catchup.days || 7;
        extinf += ` tv-archive="1" tv-archive-duration="${catchupDays}" catchup="${catchupType}"`;
        if (it.catchup.hours) extinf += ` catchup-hours="${it.catchup.hours}"`;
        if (it.catchup.source) extinf += ` catchup-source="${this._escapeAttr(it.catchup.source)}"`;
      }

      // Preservar atributos desconocidos
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

      // Directivas #KODIPROP
      if (includeKodiProps && it.kodiProps && typeof it.kodiProps === 'object') {
        for (const [k, v] of Object.entries(it.kodiProps)) {
          itemLines.push(`#KODIPROP:${k}=${v}`);
        }
      }

      // Directivas #EXTVLCOPT
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

  /**
   * Genera el manifiesto en formato JSON estructurado para clientes LELOUCH nativos.
   * Evita el gasto de CPU de parsear texto M3U en Android TV o Mobile.
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {Object} [meta]
   * @returns {string} JSON formateado
   */
  static generateManifestJson(items, meta = {}) {
    const validItems = Array.isArray(items) ? items.filter(x => x.isEnabled !== false) : [];
    
    const manifest = {
      name: meta.name || 'Mi Lista LELOUCH',
      version: meta.version || 1,
      totalCount: validItems.length,
      updatedAt: meta.updatedAt || new Date().toISOString(),
      items: validItems.map(item => ({
        id: item.id,
        sourceId: item.sourceId || null,
        providerId: item.providerId || item.catalogItemId || null,
        name: item.name,
        originalName: item.originalName || null,
        mediaType: item.mediaType || item.type,
        group: item.group || item.categoryName,
        logo: item.logo || null,
        streamUrl: unwrapProxyUrl(item.streamUrl || item.directUrl || item.url || ''),
        tvgId: item.tvgId || item.epgId || null,
        tvgName: item.tvgName || item.epgName || null,
        containerExtension: item.containerExtension || null,
        providerOrder: item.providerOrder ?? item.sortOrder ?? 0
      }))
    };

    return JSON.stringify(manifest, null, 2);
  }

  /**
   * Escapa comillas dobles y saltos de línea para atributos M3U.
   * @private
   */
  static _escapeAttr(val) {
    if (!val) return '';
    return String(val).replace(/"/g, "'").replace(/[\r\n]+/g, ' ').trim();
  }
}
