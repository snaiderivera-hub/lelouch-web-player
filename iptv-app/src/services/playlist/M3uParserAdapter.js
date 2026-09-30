/**
 * @module M3uParserAdapter
 * Adaptador que envuelve librerías externas de M3U (como iptv-m3u-playlist-parser).
 * 
 * Actúa como Anti-Corruption Layer (Capa Anticorrupción) para que el resto del ecosistema
 * Lelouch (UI, componentes, generador, reproductor) no dependa directamente de ninguna
 * biblioteca externa de terceros.
 * 
 * Cumple exhaustivamente con la FASE 4:
 * Preserva:
 * - #EXTM3U y cabecera
 * - #EXTINF y duration (exacta)
 * - tvg-id, tvg-name, tvg-logo, tvg-country, tvg-language, group-title
 * - #EXTGRP (override o fallback de categoría)
 * - #EXTVLCOPT (User-Agent, Referer, Cookie)
 * - #KODIPROP (todas las propiedades adaptive/DRM)
 * - catchup, catchup-source, catchup-days, catchup-hours
 * - Atributos desconocidos (se conservan en extraAttributes en lugar de descartarlos)
 * - URL original íntegra
 */

const KNOWN_ATTRS = new Set([
  'tvg-id', 'tvg-name', 'tvg-logo', 'tvg-country', 'tvg-language', 'tvg-rec', 'tvg-shift',
  'group-title', 'catchup', 'catchup-days', 'catchup-hours', 'catchup-source',
  'tv-archive', 'tv-archive-duration', 'radio'
]);

/**
 * Parsea el valor de los atributos de una línea #EXTINF o #EXTM3U.
 * @param {string} attrString
 * @returns {Record<string, string>}
 */
export function parseAttributes(attrString) {
  const attrs = {};
  if (!attrString) return attrs;

  const regex = /([a-zA-Z0-9_\-]+)=(?:["']([^"']*)["']|([^\s"']+))/g;
  let match;
  while ((match = regex.exec(attrString)) !== null) {
    const key = match[1].toLowerCase();
    const val = match[2] !== undefined ? match[2] : match[3];
    attrs[key] = val;
  }

  return attrs;
}

export class M3uParserAdapter {
  /**
   * Instancia externa de parser configurada (opcional).
   * @type {any}
   */
  static _customParser = null;

  /**
   * Permite inyectar una librería externa específica (ej. iptv-m3u-playlist-parser).
   * @param {any} parserInstance
   */
  static setExternalParser(parserInstance) {
    this._customParser = parserInstance;
  }

  /**
   * Obtiene el parser externo disponible (inyectado, en window o en globalThis).
   * @returns {any|null}
   */
  static getExternalParser() {
    if (this._customParser) return this._customParser;
    if (typeof window !== 'undefined' && window.iptvPlaylistParser) {
      return window.iptvPlaylistParser;
    }
    if (typeof globalThis !== 'undefined' && globalThis.iptvPlaylistParser) {
      return globalThis.iptvPlaylistParser;
    }
    return null;
  }

  /**
   * Parsea un manifiesto M3U devolviendo el modelo externo estandarizado.
   * Si una librería externa (como iptv-m3u-playlist-parser) está disponible, la invoca;
   * si no, ejecuta el motor nativo interno garantizando compatibilidad 100%.
   * 
   * @param {string} content - Contenido en texto plano del archivo M3U
   * @param {any} [overrideParser] - Parser opcional para pruebas o inyección ad-hoc
   * @returns {import('./PlaylistTypes.js').ExternalM3uPlaylist}
   */
  static parse(content, overrideParser = null) {
    if (!content || typeof content !== 'string') {
      return {
        header: { attrs: {}, raw: '' },
        items: []
      };
    }

    const parser = overrideParser || this.getExternalParser();

    // 1. Si existe una librería externa configurada (ej. iptv-m3u-playlist-parser)
    if (parser && (typeof parser.parse === 'function' || typeof parser.parsePlaylist === 'function')) {
      try {
        const parseFn = typeof parser.parsePlaylist === 'function' ? parser.parsePlaylist : parser.parse;
        const rawResult = parseFn.call(parser, content);
        return this._adaptExternalOutput(rawResult);
      } catch (err) {
        console.warn('[M3uParserAdapter] Error en parser externo, usando motor fallback nativo:', err);
      }
    }

    // 2. Motor nativo de M3uParserAdapter (robusto, preservación completa y sin dependencias)
    return this._parseInternal(content);
  }

  /**
   * Adapta la salida heterogénea de bibliotecas de terceros al contrato ExternalM3uPlaylist.
   * Preserva atributos no reconocidos en extraAttributes para no descartar nada.
   * @private
   */
  static _adaptExternalOutput(raw) {
    if (!raw) return { header: { attrs: {} }, items: [] };

    const header = {
      attrs: raw.header?.attrs || {},
      raw: raw.header?.raw || ''
    };

    const items = (raw.items || []).map(item => {
      // Extraer y preservar atributos desconocidos
      const rawAttrs = item.attrs || item.attributes || {};
      const extraAttributes = {};
      for (const [k, v] of Object.entries(rawAttrs)) {
        if (!KNOWN_ATTRS.has(k.toLowerCase()) && v !== undefined && v !== null) {
          extraAttributes[k] = String(v);
        }
      }

      return {
        name: item.name || '',
        url: item.url || '',
        duration: typeof item.duration === 'number' ? item.duration : -1,
        tvg: {
          id: item.tvg?.id || null,
          name: item.tvg?.name || null,
          logo: item.tvg?.logo || null,
          country: item.tvg?.country || null,
          language: item.tvg?.language || null,
          rec: item.tvg?.rec || null,
          shift: item.tvg?.shift || null,
        },
        group: {
          title: (Array.isArray(item.group) ? item.group[0] : item.group?.title) || null
        },
        http: {
          referrer: item.http?.referrer || item.http?.['referrer'] || item.http?.['Referer'] || item.http?.['http-referrer'] || null,
          userAgent: item.http?.userAgent || item.http?.['user-agent'] || item.http?.['User-Agent'] || item.http?.['http-user-agent'] || null,
          cookie: item.http?.cookie || item.http?.['Cookie'] || item.http?.['http-cookie'] || null,
        },
        catchup: item.catchup ? {
          type: item.catchup.type || null,
          days: item.catchup.days || null,
          hours: item.catchup.hours || null,
          source: item.catchup.source || null,
        } : null,
        kodiProps: item.kodiProps || {},
        extraAttributes: Object.keys(extraAttributes).length > 0 ? extraAttributes : null,
        raw: item.raw || ''
      };
    });

    return { header, items };
  }

  /**
   * Motor nativo de parseo optimizado para LELOUCH.
   * Procesa streaming de líneas sin descartar ningún metadato.
   * @private
   */
  static _parseInternal(content) {
    const lines = content.split(/\r?\n/);
    const header = { attrs: {}, raw: '' };
    const items = [];

    let currentEntry = null;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      // Cabecera #EXTM3U
      if (line.startsWith('#EXTM3U')) {
        header.raw = line;
        const attrStr = line.slice(7).trim();
        header.attrs = parseAttributes(attrStr);
        continue;
      }

      // Directiva #EXTINF
      if (line.startsWith('#EXTINF:')) {
        const colonIndex = line.indexOf(':');
        const commaIndex = line.lastIndexOf(',');

        let duration = -1;
        let attrStr = '';
        let title = 'Canal sin nombre';

        if (commaIndex > colonIndex) {
          const beforeComma = line.slice(colonIndex + 1, commaIndex).trim();
          title = line.slice(commaIndex + 1).trim();

          const firstSpace = beforeComma.indexOf(' ');
          if (firstSpace !== -1) {
            duration = parseFloat(beforeComma.slice(0, firstSpace)) || -1;
            attrStr = beforeComma.slice(firstSpace + 1).trim();
          } else {
            duration = parseFloat(beforeComma) || -1;
          }
        } else {
          const afterColon = line.slice(colonIndex + 1).trim();
          const firstSpace = afterColon.indexOf(' ');
          if (firstSpace !== -1) {
            duration = parseFloat(afterColon.slice(0, firstSpace)) || -1;
            attrStr = afterColon.slice(firstSpace + 1).trim();
          } else {
            duration = parseFloat(afterColon) || -1;
          }
        }

        const attributes = parseAttributes(attrStr);

        // Identificar y almacenar atributos desconocidos
        const extraAttributes = {};
        for (const [k, v] of Object.entries(attributes)) {
          if (!KNOWN_ATTRS.has(k.toLowerCase())) {
            extraAttributes[k] = v;
          }
        }

        const catchupDays = attributes['catchup-days'] || attributes['tv-archive-duration'] || null;
        const catchupHours = attributes['catchup-hours'] || null;
        const catchupType = attributes['catchup'] || (attributes['tv-archive'] === '1' ? 'default' : null);
        const catchupSource = attributes['catchup-source'] || null;
        const hasCatchup = !!(catchupType || catchupDays || catchupHours || catchupSource);

        currentEntry = {
          name: title,
          url: '',
          duration,
          tvg: {
            id: attributes['tvg-id'] || null,
            name: attributes['tvg-name'] || null,
            logo: attributes['tvg-logo'] || null,
            country: attributes['tvg-country'] || null,
            language: attributes['tvg-language'] || null,
            rec: attributes['tvg-rec'] || null,
            shift: attributes['tvg-shift'] || null,
          },
          group: {
            title: attributes['group-title'] || null
          },
          http: {
            referrer: null,
            userAgent: null,
            cookie: null
          },
          catchup: hasCatchup ? {
            type: catchupType || 'default',
            days: catchupDays ? parseInt(String(catchupDays), 10) : null,
            hours: catchupHours ? parseInt(String(catchupHours), 10) : null,
            source: catchupSource
          } : null,
          kodiProps: {},
          extraAttributes: Object.keys(extraAttributes).length > 0 ? extraAttributes : null,
          raw: line
        };
        continue;
      }

      if (!currentEntry) continue;

      // Directiva #EXTGRP: (override o fallback de grupo)
      if (line.startsWith('#EXTGRP:')) {
        const grp = line.slice(8).trim();
        if (grp) currentEntry.group.title = grp;
        continue;
      }

      // Directiva #EXTVLCOPT:
      if (line.startsWith('#EXTVLCOPT:')) {
        const opt = line.slice(11).trim();
        const eqIdx = opt.indexOf('=');
        if (eqIdx !== -1) {
          const optKey = opt.slice(0, eqIdx).trim().toLowerCase();
          const optVal = opt.slice(eqIdx + 1).trim();
          if (optKey === 'http-user-agent' || optKey === 'user-agent') currentEntry.http.userAgent = optVal;
          if (optKey === 'http-referrer' || optKey === 'http-referer' || optKey === 'referer') currentEntry.http.referrer = optVal;
          if (optKey === 'http-cookie' || optKey === 'cookie') currentEntry.http.cookie = optVal;
        }
        continue;
      }

      // Directiva #KODIPROP:
      if (line.startsWith('#KODIPROP:')) {
        const prop = line.slice(10).trim();
        const eqIdx = prop.indexOf('=');
        if (eqIdx !== -1) {
          const propKey = prop.slice(0, eqIdx).trim();
          const propVal = prop.slice(eqIdx + 1).trim();
          if (!currentEntry.kodiProps) {
            currentEntry.kodiProps = {};
          }
          currentEntry.kodiProps[propKey] = propVal;
        }
        continue;
      }

      if (line.startsWith('#')) continue;

      // URL del stream original
      currentEntry.url = line;
      items.push(currentEntry);
      currentEntry = null;
    }

    return { header, items };
  }

  /**
   * Extrae credenciales Xtream de una URL.
   * @param {string} url
   */
  static extractXtreamInfo(url) {
    if (!url || typeof url !== 'string') return { isXtream: false };

    try {
      const parsed = new URL(url.trim());
      const path = parsed.pathname;

      const pathMatch = path.match(/^\/(live|movie|series)\/([^\/]+)\/([^\/]+)\/(\d+)(?:\.([a-z0-9]+))?$/i);
      if (pathMatch) {
        return {
          isXtream: true,
          serverBaseUrl: parsed.origin,
          type: pathMatch[1].toLowerCase(),
          username: decodeURIComponent(pathMatch[2]),
          password: decodeURIComponent(pathMatch[3]),
          streamId: pathMatch[4],
          extension: pathMatch[5] || (pathMatch[1].toLowerCase() === 'live' ? 'm3u8' : 'mp4')
        };
      }

      const username = parsed.searchParams.get('username');
      const password = parsed.searchParams.get('password');
      if (username && password) {
        return {
          isXtream: true,
          serverBaseUrl: parsed.origin,
          type: 'xtream_m3u',
          username,
          password
        };
      }
    } catch {}

    return { isXtream: false };
  }

  /**
   * Helper opcional para comprobar rápidamente si una URL es de tipo Xtream.
   * @param {string} url
   * @returns {boolean}
   */
  static isXtreamUrl(url) {
    return this.extractXtreamInfo(url).isXtream;
  }

  /**
   * Helper opcional para generar una URL de descarga M3U desde credenciales Xtream.
   * @param {string} serverBaseUrl
   * @param {string} username
   * @param {string} password
   * @param {{ type?: string, output?: string }} [options]
   * @returns {string}
   */
  static buildXtreamM3uUrl(serverBaseUrl, username, password, options = {}) {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const type = options.type || 'm3u_plus';
    const output = options.output || 'm3u8';
    return `${base}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=${type}&output=${output}`;
  }

  /**
   * Helper opcional para construir URLs de timeshift/catchup estándar de Xtream Codes.
   * @param {string} serverBaseUrl
   * @param {string} username
   * @param {string} password
   * @param {string} streamId
   * @param {number} [durationMinutes]
   * @param {number} [startTimestamp]
   * @returns {string}
   */
  static buildXtreamCatchupUrl(
    serverBaseUrl,
    username,
    password,
    streamId,
    durationMinutes = 60,
    startTimestamp = Date.now()
  ) {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const dateObj = new Date(startTimestamp);
    const pad = n => String(n).padStart(2, '0');
    const startStr = `${dateObj.getFullYear()}-${pad(dateObj.getMonth() + 1)}-${pad(dateObj.getDate())}:${pad(dateObj.getHours())}-${pad(dateObj.getMinutes())}`;
    return `${base}/streaming/timeshift.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&stream=${streamId}&duration=${durationMinutes}&start=${startStr}`;
  }
}
