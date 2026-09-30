/**
 * @module PlaylistDeduplicator
 * Módulo de desduplicación y fusión inteligente de elementos de playlist.
 * 
 * Permite desduplicar por:
 * 1. URL exacta normalizada.
 * 2. ID de catálogo + Fuente (catalogItemId + sourceId).
 * 3. Nombre canónico y Categoría (eliminando acentos y mayúsculas).
 * 4. EPG ID (tvg-id).
 * 
 * Incluye fusión inteligente: si una entrada duplicada contiene mejor metadata
 * (logo, epgId, catchup), la fusiona en la entrada principal sin perder información.
 */

export class PlaylistDeduplicator {
  /**
   * Desduplica una lista de LelouchMediaItem según la estrategia indicada.
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {import('./PlaylistTypes.js').DeduplicationStrategy} [strategy]
   * @param {boolean} [mergeMetadata] - Si true, fusiona logos y epgIds ausentes del duplicado
   * @returns {import('./PlaylistTypes.js').LelouchMediaItem[]}
   */
  static deduplicate(items, strategy = 'url', mergeMetadata = true) {
    if (!Array.isArray(items) || items.length <= 1) return items || [];

    const seenMap = new Map();
    const result = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const key = this._generateKey(item, strategy, i);

      if (!seenMap.has(key)) {
        // Clonar para no mutar el original
        const cloned = { ...item };
        seenMap.set(key, cloned);
        result.push(cloned);
      } else if (mergeMetadata) {
        // Fusionar metadatos complementarios en la entrada existente
        const existing = seenMap.get(key);
        this._mergeItemMetadata(existing, item);
      }
    }

    return result;
  }

  /**
   * Encuentra los duplicados en una lista sin modificarlos.
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {import('./PlaylistTypes.js').DeduplicationStrategy} [strategy]
   * @returns {{ key: string, count: number, items: import('./PlaylistTypes.js').LelouchMediaItem[] }[]}
   */
  static findDuplicates(items, strategy = 'url') {
    if (!Array.isArray(items)) return [];

    const grouped = new Map();
    items.forEach((item, idx) => {
      const key = this._generateKey(item, strategy, idx);
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key).push(item);
    });

    const duplicates = [];
    grouped.forEach((list, key) => {
      if (list.length > 1) {
        duplicates.push({ key, count: list.length, items: list });
      }
    });

    return duplicates;
  }

  /**
   * Genera una clave de comparación según la estrategia elegida.
   * @private
   */
  static _generateKey(item, strategy, index) {
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

  /**
   * Normaliza una URL removiendo barras finales y ordenando query params si es posible.
   * @private
   */
  static _normalizeUrl(url) {
    if (!url) return '';
    try {
      const parsed = new URL(url.trim());
      // Quitar barra final del pathname
      const path = parsed.pathname.replace(/\/+$/, '');
      return `${parsed.protocol}//${parsed.host}${path}${parsed.search}`;
    } catch {
      return url.trim().toLowerCase().replace(/\/+$/, '');
    }
  }

  /**
   * Normaliza cadenas para comparación de texto (sin tildes, minúsculas).
   * @private
   */
  static _normalizeString(str) {
    if (!str) return '';
    return str
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '') // Quita diacríticos/acentos
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');      // Solo alfanuméricos
  }

  /**
   * Fusiona metadatos ausentes desde el duplicado hacia la entrada preservada.
   * @private
   */
  static _mergeItemMetadata(target, source) {
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
