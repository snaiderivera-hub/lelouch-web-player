/**
 * @module M3uParserAdapter
 * Adaptador que envuelve librerías externas de M3U (como iptv-m3u-playlist-parser) en TypeScript.
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

import { ExternalM3uPlaylist, ExternalPlaylistItem } from './PlaylistTypes';

const KNOWN_ATTRS = new Set([
  'tvg-id', 'tvg-name', 'tvg-logo', 'tvg-country', 'tvg-language', 'tvg-rec', 'tvg-shift',
  'group-title', 'catchup', 'catchup-days', 'catchup-hours', 'catchup-source',
  'tv-archive', 'tv-archive-duration', 'radio'
]);

export function parseAttributes(attrString: string): Record<string, string> {
  const attrs: Record<string, string> = {};
  if (!attrString) return attrs;

  const regex = /([a-zA-Z0-9_\-]+)=(?:["']([^"']*)["']|([^\s"']+))/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(attrString)) !== null) {
    const key = match[1].toLowerCase();
    const val = match[2] !== undefined ? match[2] : match[3];
    attrs[key] = val;
  }

  return attrs;
}

export class M3uParserAdapter {
  private static _customParser: any = null;

  static setExternalParser(parserInstance: any): void {
    this._customParser = parserInstance;
  }

  static getExternalParser(): any | null {
    if (this._customParser) return this._customParser;
    if (typeof window !== 'undefined' && (window as any).iptvPlaylistParser) {
      return (window as any).iptvPlaylistParser;
    }
    if (typeof globalThis !== 'undefined' && (globalThis as any).iptvPlaylistParser) {
      return (globalThis as any).iptvPlaylistParser;
    }
    return null;
  }

  static parse(content: string, overrideParser: any = null): ExternalM3uPlaylist {
    if (!content || typeof content !== 'string') {
      return {
        header: { attrs: {}, raw: '' },
        items: []
      };
    }

    const parser = overrideParser || this.getExternalParser();

    if (parser && typeof parser.parse === 'function') {
      try {
        const rawResult = parser.parse(content);
        return this._adaptExternalOutput(rawResult);
      } catch (err) {
        console.warn('[M3uParserAdapter] Error en parser externo, usando motor fallback nativo:', err);
      }
    }

    return this._parseInternal(content);
  }

  private static _adaptExternalOutput(raw: any): ExternalM3uPlaylist {
    if (!raw) return { header: { attrs: {} }, items: [] };

    const header = {
      attrs: raw.header?.attrs || {},
      raw: raw.header?.raw || ''
    };

    const items: ExternalPlaylistItem[] = (raw.items || []).map((item: any) => {
      // Extraer y preservar atributos desconocidos
      const rawAttrs = item.attrs || item.attributes || {};
      const extraAttributes: Record<string, string> = {};
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
          title: item.group?.title || null
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

  private static _parseInternal(content: string): ExternalM3uPlaylist {
    const lines = content.split(/\r?\n/);
    const header = { attrs: {}, raw: '' };
    const items: ExternalPlaylistItem[] = [];

    let currentEntry: ExternalPlaylistItem | null = null;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      if (line.startsWith('#EXTM3U')) {
        header.raw = line;
        const attrStr = line.slice(7).trim();
        header.attrs = parseAttributes(attrStr);
        continue;
      }

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
        const extraAttributes: Record<string, string> = {};
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

      if (line.startsWith('#EXTGRP:')) {
        const grp = line.slice(8).trim();
        if (grp) currentEntry.group.title = grp;
        continue;
      }

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

      currentEntry.url = line;
      items.push(currentEntry);
      currentEntry = null;
    }

    return { header, items };
  }

  static extractXtreamInfo(url: string): {
    isXtream: boolean;
    serverBaseUrl?: string;
    username?: string;
    password?: string;
    streamId?: string;
    type?: string;
    extension?: string;
  } {
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
   */
  static isXtreamUrl(url: string): boolean {
    return this.extractXtreamInfo(url).isXtream;
  }

  /**
   * Helper opcional para generar una URL de descarga M3U desde credenciales Xtream.
   */
  static buildXtreamM3uUrl(
    serverBaseUrl: string,
    username: string,
    password: string,
    options: { type?: string; output?: string } = {}
  ): string {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const type = options.type || 'm3u_plus';
    const output = options.output || 'm3u8';
    return `${base}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=${type}&output=${output}`;
  }

  /**
   * Helper opcional para construir URLs de timeshift/catchup estándar de Xtream Codes.
   */
  static buildXtreamCatchupUrl(
    serverBaseUrl: string,
    username: string,
    password: string,
    streamId: string,
    durationMinutes: number = 60,
    startTimestamp: number = Date.now()
  ): string {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const dateObj = new Date(startTimestamp);
    const pad = (n: number) => String(n).padStart(2, '0');
    const startStr = `${dateObj.getFullYear()}-${pad(dateObj.getMonth() + 1)}-${pad(dateObj.getDate())}:${pad(dateObj.getHours())}-${pad(dateObj.getMinutes())}`;
    return `${base}/streaming/timeshift.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&stream=${streamId}&duration=${durationMinutes}&start=${startStr}`;
  }
}

