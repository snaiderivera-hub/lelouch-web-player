/**
 * @module PlaylistGenerator
 * Generador de archivos y manifiestos M3U / M3U8 Plus y JSON Manifests (TypeScript).
 */

import { LelouchMediaItem, GenerateM3uOptions, ExternalM3uPlaylist } from './PlaylistTypes';

/**
 * FASE 21 — Garantiza que las URLs de stream sean directas al proveedor (NO proxy por Vercel).
 * Si la URL contiene un wrapper /api/proxy?target=..., lo desenvuelve a la URL directa original.
 */
export function unwrapProxyUrl(rawUrl: string): string {
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
   */
  static convertToParserPlaylist(items: LelouchMediaItem[], options: GenerateM3uOptions = {}): ExternalM3uPlaylist {
    const {
      playlistName = 'Mi Lista LELOUCH',
      epgUrl = null
    } = options;

    const validItems = Array.isArray(items) 
      ? items.filter(x => x.isEnabled !== false) 
      : [];

    const headerAttrs: Record<string, string> = {};
    if (playlistName) headerAttrs['name'] = playlistName;
    if (epgUrl) headerAttrs['x-tvg-url'] = epgUrl;

    return {
      header: {
        attrs: headerAttrs,
        raw: `#EXTM3U name="${playlistName}"`
      },
      items: validItems.map((item, idx) => ({
        name: item.name || 'Canal',
        url: unwrapProxyUrl(item.streamUrl || item.directUrl || ''),
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
          title: item.group || item.categoryName || 'General'
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

  static generateM3U(input: ExternalM3uPlaylist | LelouchMediaItem[], options: GenerateM3uOptions = {}): string {
    let playlist: ExternalM3uPlaylist;
    if (Array.isArray(input)) {
      playlist = this.convertToParserPlaylist(input, options);
    } else {
      playlist = input;
    }

    if (!playlist || typeof playlist !== 'object') return '#EXTM3U\n';

    // 1. Delegar en librería externa si tiene generateM3U (ej. iptv-m3u-playlist-parser >= 0.5.0)
    if (typeof globalThis !== 'undefined' && (globalThis as any).iptvPlaylistParser?.generateM3U) {
      try {
        return (globalThis as any).iptvPlaylistParser.generateM3U(playlist, options);
      } catch (e) {
        console.warn('[PlaylistGenerator] Error en generateM3U externo:', e);
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
    const blocks: string[] = [];

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

      if (includeCatchup && it.catchup) {
        const catchupType = it.catchup.type || 'default';
        const catchupDays = it.catchup.days || 7;
        extinf += ` tv-archive="1" tv-archive-duration="${catchupDays}" catchup="${catchupType}"`;
        if (it.catchup.hours) extinf += ` catchup-hours="${it.catchup.hours}"`;
        if (it.catchup.source) extinf += ` catchup-source="${this._escapeAttr(it.catchup.source)}"`;
      }

      const itemLines: string[] = [`${extinf},${it.name || 'Canal'}`];

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

  static generateManifestJson(items: LelouchMediaItem[], meta: Record<string, any> = {}): string {
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

  private static _escapeAttr(val: any): string {
    if (!val) return '';
    return String(val).replace(/"/g, "'").replace(/[\r\n]+/g, ' ').trim();
  }
}
