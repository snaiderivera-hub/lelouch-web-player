/**
 * @module PlaylistGenerator
 * Generador de archivos y manifiestos M3U / M3U8 Plus y JSON Manifests (TypeScript).
 */

import { LelouchMediaItem, GenerateM3uOptions } from './PlaylistTypes';

export class PlaylistGenerator {
  static generateM3U(items: LelouchMediaItem[], options: GenerateM3uOptions = {}): string {
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

    let validItems = items;
    if (onlyEnabled) {
      validItems = validItems.filter(x => x.isEnabled !== false);
    }
    validItems = [...validItems].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));

    let header = '#EXTM3U';
    if (playlistName) {
      header += ` name="${this._escapeAttr(playlistName)}"`;
    }
    if (epgUrl) {
      header += ` x-tvg-url="${this._escapeAttr(epgUrl)}"`;
    }
    header += '\n\n';

    const outputLines: string[] = [header];

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

      const group = item.group || item.categoryName || 'General';
      extinf += ` group-title="${this._escapeAttr(group)}"`;

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

      if (item.extraAttributes && typeof item.extraAttributes === 'object') {
        for (const [k, v] of Object.entries(item.extraAttributes)) {
          if (v !== undefined && v !== null && !extinf.includes(` ${k}=`)) {
            extinf += ` ${k}="${this._escapeAttr(v)}"`;
          }
        }
      }

      extinf += `,${item.name || 'Canal'}\n`;
      outputLines.push(extinf);

      if (includeExtGrp && group) {
        outputLines.push(`#EXTGRP:${group}\n`);
      }

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

      if (includeKodiProps && item.kodiProps) {
        for (const [k, v] of Object.entries(item.kodiProps)) {
          outputLines.push(`#KODIPROP:${k}=${v}\n`);
        }
      }

      outputLines.push(`${item.streamUrl.trim()}\n\n`);
    }

    return outputLines.join('');
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
