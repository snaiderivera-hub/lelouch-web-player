/**
 * @module PlaylistNormalizer
 * Normalizador de items hacia el modelo único canónico LelouchMediaItem (TypeScript).
 * 
 * Unifica cualquier origen:
 *   M3U    ──────┐
 *                │
 *   Xtream ──────┼───► LelouchMediaItem (Estructura Común Única)
 *                │
 *   Direct ──────┘
 * 
 * El Creador M3U y la UI de LELOUCH operan exclusivamente con LelouchMediaItem
 * sin necesidad de conocer la fuente original del canal.
 */

import { LelouchMediaItem, ExternalPlaylistItem, ParsedM3uEntry } from './PlaylistTypes';
import { M3uParserAdapter } from './M3uParserAdapter';

export class PlaylistNormalizer {
  /**
   * Clasifica el tipo de medio a uno de los 4 tipos canónicos: 'live' | 'movie' | 'series' | 'radio'.
   */
  static detectMediaType(
    url: string,
    attributes: Record<string, string> = {},
    name: string = ''
  ): 'live' | 'movie' | 'series' | 'radio' {
    if (!url) return 'live';

    if (attributes['radio'] === 'true' || attributes['radio'] === '1' || name.toLowerCase().includes('radio')) {
      return 'radio';
    }

    const lowerUrl = url.toLowerCase();

    if (lowerUrl.includes('/movie/')) return 'movie';
    if (lowerUrl.includes('/series/')) return 'series';
    if (lowerUrl.includes('/live/')) return 'live';

    if (lowerUrl.includes('.mp4') || lowerUrl.includes('.mkv') || lowerUrl.includes('.avi')) {
      if (/[sS]\d{1,2}[eE]\d{1,2}|temporada|season/i.test(name)) {
        return 'series';
      }
      return 'movie';
    }

    if (lowerUrl.includes('.m3u8') || lowerUrl.includes('.ts') || lowerUrl.includes('output=ts') || lowerUrl.includes('output=m3u8')) {
      return 'live';
    }

    return 'live';
  }

  /**
   * Extrae la extensión del stream (m3u8, ts, mp4, etc.)
   */
  static extractExtension(url: string, defaultExt: string = 'm3u8'): string {
    if (!url) return defaultExt;
    try {
      const pathname = new URL(url).pathname;
      const lastDot = pathname.lastIndexOf('.');
      if (lastDot !== -1 && lastDot < pathname.length - 1) {
        const ext = pathname.slice(lastDot + 1).toLowerCase();
        if (['m3u8', 'ts', 'mp4', 'mkv', 'avi', 'mp3', 'aac', 'flv'].includes(ext)) {
          return ext;
        }
      }
    } catch {}
    return defaultExt;
  }

  /**
   * 1. NORMALIZACIÓN DESDE M3U
   * Convierte una entrada del modelo externo M3U a LelouchMediaItem.
   */
  static fromExternalItem(
    entry: ExternalPlaylistItem,
    index: number = 0,
    sourceId: string | null = null
  ): LelouchMediaItem {
    const url = (entry.url || '').trim();
    const name = (entry.name || 'Canal sin nombre').trim();
    const group = entry.group?.title || 'General';
    const tvg = entry.tvg || {};

    const xtream = M3uParserAdapter.extractXtreamInfo(url);
    const mediaType = this.detectMediaType(url, { radio: tvg.rec === 'radio' ? 'true' : '' }, name);
    const ext = this.extractExtension(url, mediaType === 'movie' ? 'mp4' : 'm3u8');

    let catchup: LelouchMediaItem['catchup'] = null;
    if (entry.catchup) {
      catchup = {
        type: entry.catchup.type || 'default',
        days: typeof entry.catchup.days === 'number'
          ? entry.catchup.days
          : (entry.catchup.days ? parseInt(String(entry.catchup.days), 10) : null),
        hours: typeof entry.catchup.hours === 'number'
          ? entry.catchup.hours
          : (entry.catchup.hours ? parseInt(String(entry.catchup.hours), 10) : null),
        source: entry.catchup.source || null
      };
    }

    const providerId = xtream.isXtream ? (xtream.streamId || null) : (tvg.id || null);
    const id = xtream.isXtream && xtream.streamId && sourceId
      ? `item_${sourceId}_${xtream.streamId}`
      : `item_m3u_${index}_${Math.abs(this._hashCode(url))}`;

    const headers = (entry.http?.userAgent || entry.http?.referrer || entry.http?.cookie) ? {
      userAgent: entry.http?.userAgent || null,
      referrer: entry.http?.referrer || null,
      cookie: entry.http?.cookie || null
    } : null;

    return {
      id,
      sourceId: sourceId || null,
      providerId,
      catalogItemId: providerId,

      mediaType,
      type: mediaType,

      name,
      originalName: name,

      group,
      categoryName: group,
      logo: tvg.logo || null,

      streamUrl: url,
      originalUrl: url,
      containerExtension: ext,
      duration: typeof entry.duration === 'number' ? entry.duration : -1,

      tvgId: tvg.id || null,
      epgId: tvg.id || null,
      tvgName: tvg.name || name,
      epgName: tvg.name || name,

      language: tvg.language || null,
      country: tvg.country || null,

      headers,
      httpUserAgent: headers?.userAgent || null,
      httpReferrer: headers?.referrer || null,

      kodiProps: entry.kodiProps && Object.keys(entry.kodiProps).length > 0 ? entry.kodiProps : null,
      extraAttributes: entry.extraAttributes || null,

      catchup,

      providerOrder: index,
      sortOrder: index,

      metadata: entry.raw || entry,
      rawMetadata: entry.raw || entry,

      isEnabled: true,
      addedAt: Date.now()
    };
  }

  /**
   * Alias de compatibilidad con ParsedM3uEntry.
   */
  static fromM3uEntry(
    entry: ParsedM3uEntry | ExternalPlaylistItem,
    index: number = 0,
    sourceId: string | null = null
  ): LelouchMediaItem {
    if ('tvg' in entry && typeof entry.tvg === 'object') {
      return this.fromExternalItem(entry as ExternalPlaylistItem, index, sourceId);
    }

    const legacy = entry as ParsedM3uEntry;
    const attrs = legacy.attributes || {};
    const extraAttrs: Record<string, string> = {};
    const knownSet = new Set([
      'tvg-id', 'tvg-name', 'tvg-logo', 'tvg-country', 'tvg-language', 'tvg-rec', 'tvg-shift',
      'group-title', 'catchup', 'catchup-days', 'catchup-hours', 'catchup-source',
      'tv-archive', 'tv-archive-duration', 'radio'
    ]);
    for (const [k, v] of Object.entries(attrs)) {
      if (!knownSet.has(k.toLowerCase()) && v !== undefined && v !== null) {
        extraAttrs[k] = String(v);
      }
    }

    return this.fromExternalItem({
      name: legacy.name,
      url: legacy.url,
      duration: legacy.duration,
      tvg: {
        id: attrs['tvg-id'] || null,
        name: attrs['tvg-name'] || null,
        logo: attrs['tvg-logo'] || null,
        country: attrs['tvg-country'] || null,
        language: attrs['tvg-language'] || null,
      },
      group: {
        title: legacy.group || attrs['group-title'] || 'General'
      },
      http: {
        referrer: legacy.vlcReferrer || null,
        userAgent: legacy.vlcUserAgent || null,
        cookie: (legacy as any).vlcCookie || null
      },
      catchup: (attrs['catchup'] || attrs['tv-archive'] === '1' || attrs['catchup-days'] || attrs['catchup-hours']) ? {
        type: attrs['catchup'] || 'default',
        days: attrs['catchup-days'] ? parseInt(String(attrs['catchup-days']), 10) : (attrs['tv-archive-duration'] ? parseInt(String(attrs['tv-archive-duration']), 10) : null),
        hours: attrs['catchup-hours'] ? parseInt(String(attrs['catchup-hours']), 10) : null,
        source: attrs['catchup-source'] || null
      } : null,
      kodiProps: legacy.kodiProps || {},
      extraAttributes: Object.keys(extraAttrs).length > 0 ? extraAttrs : null,
      raw: ''
    }, index, sourceId);
  }

  /**
   * 2. NORMALIZACIÓN DESDE XTREAM CODES API
   * Convierte canales en vivo, películas VOD o episodios a LelouchMediaItem.
   */
  static fromXtream(
    rawStream: any,
    type: 'live' | 'vod' | 'series',
    sourceId: string,
    index: number = 0
  ): LelouchMediaItem {
    const streamId = String(rawStream.id || rawStream.stream_id || '');
    const name = String(rawStream.name || rawStream.title || 'Contenido Xtream').trim();
    const group = String(rawStream.categoryName || rawStream.category || 'General').trim();
    const streamUrl = String(rawStream.streamUrl || '').trim();

    const mediaType: 'live' | 'movie' | 'series' = type === 'vod' ? 'movie' : type;
    const ext = rawStream.containerExtension || rawStream.container_extension || (mediaType === 'movie' ? 'mp4' : 'm3u8');
    const logo = rawStream.logo || rawStream.stream_icon || rawStream.poster || rawStream.cover || null;
    const tvgId = rawStream.epgId || rawStream.epg_channel_id || null;

    let catchup: LelouchMediaItem['catchup'] = null;
    if (rawStream.tvArchive || rawStream.tv_archive === 1 || rawStream.tv_archive === '1') {
      catchup = {
        type: 'default',
        days: rawStream.tvArchiveDuration || (rawStream.tv_archive_duration ? parseInt(String(rawStream.tv_archive_duration), 10) : 7),
        source: null
      };
    }

    let parsedAddedAt = Date.now();
    if (rawStream.addedAt) {
      parsedAddedAt = typeof rawStream.addedAt === 'number' ? rawStream.addedAt : new Date(rawStream.addedAt).getTime();
    } else if (rawStream.added) {
      const ts = parseInt(String(rawStream.added), 10);
      if (!isNaN(ts)) parsedAddedAt = ts * 1000;
    }

    return {
      id: `item_${sourceId}_${streamId}`,
      itemType: 'catalog',
      sourceId,
      providerId: streamId,
      catalogItemId: streamId,

      mediaType,
      type: mediaType,

      name,
      originalName: rawStream.name || rawStream.title || null,

      group,
      categoryName: group,
      logo,

      streamUrl,
      originalUrl: streamUrl,
      containerExtension: ext,
      duration: typeof rawStream.duration === 'number' ? rawStream.duration : -1,

      tvgId,
      epgId: tvgId,
      tvgName: name,
      epgName: name,

      language: rawStream.language || null,
      country: rawStream.country || null,

      headers: null,
      httpUserAgent: null,
      httpReferrer: null,

      kodiProps: null,
      extraAttributes: null,

      catchup,

      providerOrder: index,
      sortOrder: index,

      metadata: rawStream.rawMetadata || rawStream,
      rawMetadata: rawStream.rawMetadata || rawStream,

      isEnabled: true,
      addedAt: parsedAddedAt
    };
  }

  /**
   * 3. NORMALIZACIÓN DESDE ENLACE DIRECTO / MANUAL
   * Convierte canales manuales o enlaces directos a LelouchMediaItem.
   */
  static fromDirectInput(
    input: { name: string; url: string; category?: string; logo?: string },
    index: number = 0
  ): LelouchMediaItem {
    const url = (input.url || '').trim();
    const name = (input.name || 'Canal Directo').trim();
    const group = (input.category || 'Directos').trim();
    const mediaType = this.detectMediaType(url, {}, name);
    const ext = this.extractExtension(url, mediaType === 'movie' ? 'mp4' : 'm3u8');

    return {
      id: `item_dir_${Date.now()}_${index}`,
      itemType: 'direct',
      sourceId: null,
      providerId: null,
      catalogItemId: null,

      mediaType,
      type: mediaType,

      name,
      originalName: name,

      group,
      categoryName: group,
      logo: input.logo || null,

      directName: name,
      directUrl: url,
      directGroup: group,
      directLogo: input.logo || null,

      streamUrl: url,
      originalUrl: url,
      containerExtension: ext,

      tvgId: null,
      epgId: null,
      tvgName: name,
      epgName: name,

      language: null,
      country: null,

      headers: null,
      httpUserAgent: null,
      httpReferrer: null,

      kodiProps: null,

      catchup: null,

      providerOrder: index,
      sortOrder: index,

      metadata: null,
      rawMetadata: null,

      isEnabled: true,
      addedAt: Date.now()
    };
  }

  private static _hashCode(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = ((hash << 5) - hash) + str.charCodeAt(i);
      hash |= 0;
    }
    return hash;
  }
}
