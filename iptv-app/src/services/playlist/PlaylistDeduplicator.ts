/**
 * @module PlaylistDeduplicator
 * Módulo de deduplicación y fusión inteligente de elementos de playlist (TypeScript).
 * 
 * FASE 23 — Deduplicación Multi-Factor:
 * Considera: provider/source, tvg-id, stream URL normalizada, nombre y grupo.
 */

import { LelouchMediaItem, DeduplicationStrategy, ItemComparisonResult } from './PlaylistTypes';

export class PlaylistDeduplicator {
  /**
   * Normaliza una URL de stream eliminando credenciales efímeras y query params variables.
   */
  static normalizeStreamUrl(rawUrl: string): string {
    if (!rawUrl || typeof rawUrl !== 'string') return '';
    let urlStr = rawUrl.trim();

    if (urlStr.includes('/api/proxy') || urlStr.includes('/proxy?target=')) {
      try {
        const match = urlStr.match(/[?&]target=([^&]+)/);
        if (match && match[1]) {
          urlStr = decodeURIComponent(match[1]);
        }
      } catch {}
    }

    try {
      const parsed = new URL(urlStr);
      let host = parsed.hostname.toLowerCase();
      let port = parsed.port;
      if ((parsed.protocol === 'http:' && port === '80') || (parsed.protocol === 'https:' && port === '443')) {
        port = '';
      }
      const hostPort = port ? `${host}:${port}` : host;

      let pathname = (parsed.pathname || '').replace(/\/+$/, '');
      pathname = pathname.replace(
        /^(\/(?:live|movie|series))\/(?:[^/]+)\/(?:[^/]+)\/(.+)$/i,
        '$1/$2'
      );

      const ephemeralKeys = new Set([
        'token', 'auth', 'sid', 'session', 'session_id', 'expires', 'h',
        'wmsauthsign', 'username', 'password', 'user', 'pass', 'pwd'
      ]);

      const searchParams = new URLSearchParams(parsed.search);
      const remainingParams: string[] = [];
      searchParams.forEach((val, key) => {
        if (!ephemeralKeys.has(key.toLowerCase())) {
          remainingParams.push(`${encodeURIComponent(key)}=${encodeURIComponent(val)}`);
        }
      });
      remainingParams.sort();
      const cleanSearch = remainingParams.length > 0 ? `?${remainingParams.join('&')}` : '';

      return `${hostPort}${pathname}${cleanSearch}`.toLowerCase();
    } catch {
      return urlStr
        .replace(/([?&](?:username|user|password|pass|token)=)[^&]+/gi, '')
        .replace(/\/+/g, '/')
        .toLowerCase()
        .trim();
    }
  }

  static normalizeString(str: string): string {
    if (!str) return '';
    return str
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  }

  /**
   * Compara dos elementos de playlist evaluando los 5 factores:
   * 1. provider / source
   * 2. tvg-id
   * 3. stream URL normalizada
   * 4. nombre
   * 5. grupo
   */
  static compareItems(a: LelouchMediaItem, b: LelouchMediaItem): ItemComparisonResult {
    if (!a || !b) {
      return {
        isDuplicate: false,
        sameSource: false,
        sameNormalizedUrl: false,
        sameTvgId: false,
        sameName: false,
        sameGroup: false,
        confidence: 'distinct',
        reason: 'Uno de los elementos es nulo'
      };
    }

    const sourceA = a.sourceId || a.providerId || null;
    const sourceB = b.sourceId || b.providerId || null;
    const sameSource = Boolean(sourceA && sourceB && sourceA === sourceB);
    const differentSources = Boolean(sourceA && sourceB && sourceA !== sourceB);

    const normUrlA = this.normalizeStreamUrl(a.streamUrl || a.directUrl || '');
    const normUrlB = this.normalizeStreamUrl(b.streamUrl || b.directUrl || '');
    const sameNormalizedUrl = Boolean(normUrlA && normUrlB && normUrlA === normUrlB);

    const tvgIdA = (a.tvgId || a.epgId || '').trim().toLowerCase();
    const tvgIdB = (b.tvgId || b.epgId || '').trim().toLowerCase();
    const sameTvgId = Boolean(tvgIdA && tvgIdB && tvgIdA === tvgIdB);

    const nameA = this.normalizeString(a.name || '');
    const nameB = this.normalizeString(b.name || '');
    const sameName = Boolean(nameA && nameB && nameA === nameB);

    const groupA = this.normalizeString(a.group || a.categoryName || '');
    const groupB = this.normalizeString(b.group || b.categoryName || '');
    const sameGroup = Boolean(groupA && groupB && groupA === groupB);

    // REGLA 1: Identidad técnica absoluta de stream URL
    if (sameNormalizedUrl) {
      return {
        isDuplicate: true,
        sameSource,
        sameNormalizedUrl: true,
        sameTvgId,
        sameName,
        sameGroup,
        confidence: 'exact_stream',
        reason: 'Misma URL técnica de stream normalizada'
      };
    }

    // REGLA 2: Misma fuente + Mismo ID de catálogo
    const catIdA = a.catalogItemId || a.providerOrder;
    const catIdB = b.catalogItemId || b.providerOrder;
    if (sameSource && catIdA && catIdB && String(catIdA) === String(catIdB)) {
      return {
        isDuplicate: true,
        sameSource: true,
        sameNormalizedUrl,
        sameTvgId,
        sameName,
        sameGroup,
        confidence: 'same_provider_item',
        reason: 'Mismo canal dentro del mismo proveedor'
      };
    }

    // REGLA 3 (FASE 23 CRUCIAL): Distintas fuentes con el mismo nombre
    // Fuente A ─ Canal X vs Fuente B ─ Canal X
    // NO decidir automáticamente que son idénticos únicamente por nombre
    if (differentSources) {
      if (sameName && !sameNormalizedUrl) {
        return {
          isDuplicate: false, // Se preservan ambos canales
          sameSource: false,
          sameNormalizedUrl: false,
          sameTvgId,
          sameName: true,
          sameGroup,
          confidence: 'cross_source_distinct_kept',
          reason: 'Canales homónimos en fuentes diferentes (preservados independientemente)'
        };
      }
    }

    // REGLA 4: Mismo nombre dentro de la misma fuente y mismo grupo
    if (sameSource && sameName && sameGroup) {
      return {
        isDuplicate: true,
        sameSource: true,
        sameNormalizedUrl,
        sameTvgId,
        sameName: true,
        sameGroup: true,
        confidence: 'same_provider_item',
        reason: 'Mismo nombre y categoría dentro del mismo proveedor'
      };
    }

    // REGLA 5: Sin fuentes declaradas, nombres iguales con URLs distintas
    if (sameName && !sameNormalizedUrl) {
      return {
        isDuplicate: false,
        sameSource,
        sameNormalizedUrl: false,
        sameTvgId,
        sameName: true,
        sameGroup,
        confidence: 'distinct',
        reason: 'Nombres iguales con URLs de stream distintas (preservados)'
      };
    }

    return {
      isDuplicate: false,
      sameSource,
      sameNormalizedUrl,
      sameTvgId,
      sameName,
      sameGroup,
      confidence: 'distinct',
      reason: 'Canales totalmente distintos'
    };
  }

  static deduplicate(
    items: LelouchMediaItem[],
    strategy: DeduplicationStrategy = 'lelouch_multi_factor',
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

  deduplicate(
    items: LelouchMediaItem[],
    strategy: DeduplicationStrategy = 'lelouch_multi_factor',
    mergeMetadata: boolean = true
  ): LelouchMediaItem[] {
    return PlaylistDeduplicator.deduplicate(items, strategy, mergeMetadata);
  }

  static findDuplicates(
    items: LelouchMediaItem[],
    strategy: DeduplicationStrategy = 'lelouch_multi_factor'
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
    const normUrl = this.normalizeStreamUrl(item.streamUrl || item.directUrl || '');
    const cleanName = this.normalizeString(item.name || '');
    const cleanCat = this.normalizeString(item.group || item.categoryName || '');
    const source = item.sourceId || item.providerId || null;
    const catId = item.catalogItemId || '';
    const tvgId = (item.tvgId || item.epgId || '').trim().toLowerCase();

    switch (strategy) {
      case 'lelouch_multi_factor':
      default:
        if (normUrl) {
          if (source) return `lmf_${source}_${normUrl}`;
          return `lmf_url_${normUrl}`;
        }
        if (source && catId) return `lmf_cat_${source}_${catId}`;
        if (source) return `lmf_snc_${source}_${cleanCat}_${cleanName}`;
        return `lmf_idx_${index}_${cleanName}`;

      case 'strict_provider':
        const s = source || 'direct';
        if (normUrl) return `sp_${s}_${normUrl}`;
        return `sp_${s}_${catId || cleanName}`;

      case 'normalized_stream_url':
      case 'url':
        return `url_${normUrl || cleanName}`;

      case 'catalog_id':
        if (source && catId) return `cat_${source}_${catId}`;
        return `url_${normUrl || cleanName}`;

      case 'name_and_category':
        return `nc_${cleanCat}_${cleanName}`;

      case 'epg_id':
      case 'cross_source_epg':
        if (tvgId) return `epg_${tvgId}`;
        return `url_${normUrl || cleanName}`;
    }
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

    const srcUrl = source.streamUrl || source.directUrl;
    const targetUrl = target.streamUrl || target.directUrl;
    if (srcUrl && targetUrl && srcUrl !== targetUrl) {
      if (!target.metadata || typeof target.metadata !== 'object') {
        target.metadata = typeof target.metadata === 'string' ? { raw: target.metadata } : {};
      }
      if (!Array.isArray(target.metadata.backupStreams)) {
        target.metadata.backupStreams = [];
      }
      if (!target.metadata.backupStreams.includes(srcUrl)) {
        target.metadata.backupStreams.push(srcUrl);
      }
    }
  }
}
