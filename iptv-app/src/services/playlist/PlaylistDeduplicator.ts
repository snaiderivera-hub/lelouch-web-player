/**
 * @module PlaylistDeduplicator
 * Módulo de desduplicación y fusión inteligente de elementos de playlist (TypeScript).
 */

import { LelouchMediaItem, DeduplicationStrategy } from './PlaylistTypes';

export class PlaylistDeduplicator {
  static deduplicate(
    items: LelouchMediaItem[],
    strategy: DeduplicationStrategy = 'url',
    mergeMetadata: boolean = true
  ): LelouchMediaItem[] {
    if (!Array.isArray(items) || items.length <= 1) return items || [];

    const seenMap = new Map<string, LelouchMediaItem>();
    const result: LelouchMediaItem[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const key = this._generateKey(item, strategy, i);

      if (!seenMap.has(key)) {
        const cloned = { ...item };
        seenMap.set(key, cloned);
        result.push(cloned);
      } else if (mergeMetadata) {
        const existing = seenMap.get(key)!;
        this._mergeItemMetadata(existing, item);
      }
    }

    return result;
  }

  static findDuplicates(
    items: LelouchMediaItem[],
    strategy: DeduplicationStrategy = 'url'
  ): { key: string; count: number; items: LelouchMediaItem[] }[] {
    if (!Array.isArray(items)) return [];

    const grouped = new Map<string, LelouchMediaItem[]>();
    items.forEach((item, idx) => {
      const key = this._generateKey(item, strategy, idx);
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key)!.push(item);
    });

    const duplicates: { key: string; count: number; items: LelouchMediaItem[] }[] = [];
    grouped.forEach((list, key) => {
      if (list.length > 1) {
        duplicates.push({ key, count: list.length, items: list });
      }
    });

    return duplicates;
  }

  private static _generateKey(
    item: LelouchMediaItem,
    strategy: DeduplicationStrategy,
    index: number
  ): string {
    switch (strategy) {
      case 'catalog_id':
        const pId = item.providerId || item.catalogItemId;
        if (item.sourceId && pId) {
          return `cat_${item.sourceId}_${pId}`;
        }
        return `url_${this._normalizeUrl(item.streamUrl)}`;

      case 'name_and_category':
        const cleanName = this._normalizeString(item.name);
        const cleanCat = this._normalizeString(item.group || item.categoryName || '');
        return `nc_${cleanCat}_${cleanName}`;

      case 'epg_id':
        const tId = item.tvgId || item.epgId;
        if (tId && tId.trim()) {
          return `epg_${tId.trim().toLowerCase()}`;
        }
        return `url_${this._normalizeUrl(item.streamUrl)}`;

      case 'url':
      default:
        return `url_${this._normalizeUrl(item.streamUrl)}`;
    }
  }

  private static _normalizeUrl(url: string): string {
    if (!url) return '';
    try {
      const parsed = new URL(url.trim());
      const path = parsed.pathname.replace(/\/+$/, '');
      return `${parsed.protocol}//${parsed.host}${path}${parsed.search}`;
    } catch {
      return url.trim().toLowerCase().replace(/\/+$/, '');
    }
  }

  private static _normalizeString(str: string): string {
    if (!str) return '';
    return str
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  }

  private static _mergeItemMetadata(target: LelouchMediaItem, source: LelouchMediaItem): void {
    if (!target.logo && source.logo) target.logo = source.logo;
    const sTvgId = source.tvgId || source.epgId;
    if (!(target.tvgId || target.epgId) && sTvgId) {
      target.tvgId = sTvgId;
      target.epgId = sTvgId;
    }
    const sTvgName = source.tvgName || source.epgName;
    if (!(target.tvgName || target.epgName) && sTvgName) {
      target.tvgName = sTvgName;
      target.epgName = sTvgName;
    }
    if (!target.country && source.country) target.country = source.country;
    if (!target.language && source.language) target.language = source.language;
    if (!target.catchup && source.catchup) target.catchup = source.catchup;
    if (!target.headers && source.headers) target.headers = source.headers;
    if (!target.httpUserAgent && source.httpUserAgent) target.httpUserAgent = source.httpUserAgent;
    if (!target.httpReferrer && source.httpReferrer) target.httpReferrer = source.httpReferrer;
  }
}
