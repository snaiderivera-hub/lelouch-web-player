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

export class PlaylistGenerator {
  /**
   * Genera el contenido de texto completo en formato #EXTM3U a partir de LelouchMediaItem[].
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {import('./PlaylistTypes.js').GenerateM3uOptions} [options]
   * @returns {string}
   */
  static generateM3U(items, options = {}) {
    const {
      playlistName = 'Mi Lista LELOUCH',
      epgUrl = null,
      includeCatchup = true,
      includeVlcOpts = true,
      includeKodiProps = true,
      includeExtGrp = false,
      onlyEnabled = true
    } = options;

    if (!Array.isArray(items)) return '#EXTM3U\n';

    // Filtrar elementos activos y ordenar por sortOrder
    let validItems = items;
    if (onlyEnabled) {
      validItems = validItems.filter(x => x.isEnabled !== false);
    }
    validItems = [...validItems].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));

    // 1. Construir cabecera #EXTM3U
    let header = '#EXTM3U';
    if (playlistName) {
      header += ` name="${this._escapeAttr(playlistName)}"`;
    }
    if (epgUrl) {
      header += ` x-tvg-url="${this._escapeAttr(epgUrl)}"`;
    }
    header += '\n\n';

    const outputLines = [header];

    // 2. Iterar items generando #EXTINF y directivas
    for (let i = 0; i < validItems.length; i++) {
      const item = validItems[i];
      if (!item.streamUrl) continue;

      const duration = (typeof item.duration === 'number' && !isNaN(item.duration)) ? item.duration : -1;
      let extinf = `#EXTINF:${duration}`;

      const tvgId = item.tvgId || item.epgId;
      if (tvgId) {
        extinf += ` tvg-id="${this._escapeAttr(tvgId)}"`;
      }
      const tvgName = item.tvgName || item.epgName || item.name;
      if (tvgName) {
        extinf += ` tvg-name="${this._escapeAttr(tvgName)}"`;
      }
      if (item.logo) {
        extinf += ` tvg-logo="${this._escapeAttr(item.logo)}"`;
      }
      if (item.country) {
        extinf += ` tvg-country="${this._escapeAttr(item.country)}"`;
      }
      if (item.language) {
        extinf += ` tvg-language="${this._escapeAttr(item.language)}"`;
      }
      if (item.mediaType === 'radio' || item.type === 'radio') {
        extinf += ' radio="true"';
      }

      // Categoría / Grupo
      const group = item.group || item.categoryName || 'General';
      extinf += ` group-title="${this._escapeAttr(group)}"`;

      // Catch-up / Timeshift
      if (includeCatchup && item.catchup) {
        const catchupType = item.catchup.type || 'default';
        const catchupDays = item.catchup.days || 7;
        extinf += ` tv-archive="1" tv-archive-duration="${catchupDays}" catchup="${catchupType}"`;
        if (item.catchup.hours) {
          extinf += ` catchup-hours="${item.catchup.hours}"`;
        }
        if (item.catchup.source) {
          extinf += ` catchup-source="${this._escapeAttr(item.catchup.source)}"`;
        }
      }

      // Preservar atributos desconocidos
      if (item.extraAttributes && typeof item.extraAttributes === 'object') {
        for (const [k, v] of Object.entries(item.extraAttributes)) {
          if (v !== undefined && v !== null && !extinf.includes(` ${k}=`)) {
            extinf += ` ${k}="${this._escapeAttr(v)}"`;
          }
        }
      }

      // Nombre del canal tras la coma
      extinf += `,${item.name || 'Canal'}\n`;
      outputLines.push(extinf);

      // Línea redundante #EXTGRP (opcional para reproductores antiguos)
      if (includeExtGrp && group) {
        outputLines.push(`#EXTGRP:${group}\n`);
      }

      // Directivas #EXTVLCOPT
      if (includeVlcOpts) {
        const ua = item.headers?.userAgent || item.httpUserAgent;
        const ref = item.headers?.referrer || item.httpReferrer;
        const cookie = item.headers?.cookie;
        if (ua) {
          outputLines.push(`#EXTVLCOPT:http-user-agent=${ua}\n`);
        }
        if (ref) {
          outputLines.push(`#EXTVLCOPT:http-referrer=${ref}\n`);
        }
        if (cookie) {
          outputLines.push(`#EXTVLCOPT:http-cookie=${cookie}\n`);
        }
      }

      // Directivas #KODIPROP
      if (includeKodiProps && item.kodiProps) {
        for (const [k, v] of Object.entries(item.kodiProps)) {
          outputLines.push(`#KODIPROP:${k}=${v}\n`);
        }
      }

      // URL del stream
      outputLines.push(`${item.streamUrl.trim()}\n\n`);
    }

    return outputLines.join('');
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
        streamUrl: item.streamUrl,
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
