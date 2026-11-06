/**
 * @module PlaylistDeduplicator
 * Módulo de deduplicación y fusión inteligente de elementos de playlist.
 * 
 * FASE 23 — Deduplicación Multi-Factor:
 * Cuando se añaden elementos de varias fuentes:
 *   Fuente A ─ Canal X
 *   Fuente B ─ Canal X
 *   Fuente C ─ Canal Y
 * 
 * NO decidimos automáticamente que A y B son idénticos únicamente por nombre.
 * La deduplicación considera 5 factores deterministas:
 * 1. provider / source
 * 2. tvg-id
 * 3. stream URL normalizada (sin tokens/credenciales efímeras)
 * 4. nombre
 * 5. grupo
 */

export class PlaylistDeduplicator {
  /**
   * Normaliza una URL de stream eliminando credenciales efímeras, session tokens y query params variables
   * para obtener la huella digital técnica del stream real en el servidor.
   * 
   * Ejemplos:
   * http://servidor.tv:8080/live/user1/pass1/12345.ts?token=abc  ->  servidor.tv:8080/live/12345.ts
   * http://servidor.tv:8080/live/user2/pass2/12345.ts            ->  servidor.tv:8080/live/12345.ts (Mismo stream)
   * 
   * @param {string} rawUrl
   * @returns {string} Huella técnica del stream
   */
  static normalizeStreamUrl(rawUrl) {
    if (!rawUrl || typeof rawUrl !== 'string') return '';
    let urlStr = rawUrl.trim();

    // 1. Si vino con wrapper de proxy, desenvolverlo
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
      // Normalizar puertos estándar
      if ((parsed.protocol === 'http:' && port === '80') || (parsed.protocol === 'https:' && port === '443')) {
        port = '';
      }
      const hostPort = port ? `${host}:${port}` : host;

      let pathname = (parsed.pathname || '').replace(/\/+$/, '');

      // Enmascarar credenciales de Xtream en el path: /live/user/pass/id.ext -> /live/id.ext
      pathname = pathname.replace(
        /^(\/(?:live|movie|series))\/(?:[^/]+)\/(?:[^/]+)\/(.+)$/i,
        '$1/$2'
      );

      // Filtrar query params efímeros de sesión
      const ephemeralKeys = new Set([
        'token', 'auth', 'sid', 'session', 'session_id', 'expires', 'h',
        'wmsauthsign', 'username', 'password', 'user', 'pass', 'pwd'
      ]);

      const searchParams = new URLSearchParams(parsed.search);
      const remainingParams = [];
      searchParams.forEach((val, key) => {
        if (!ephemeralKeys.has(key.toLowerCase())) {
          remainingParams.push(`${encodeURIComponent(key)}=${encodeURIComponent(val)}`);
        }
      });
      remainingParams.sort();
      const cleanSearch = remainingParams.length > 0 ? `?${remainingParams.join('&')}` : '';

      return `${hostPort}${pathname}${cleanSearch}`.toLowerCase();
    } catch {
      // Fallback si no es URL estándar
      return urlStr
        .replace(/([?&](?:username|user|password|pass|token)=)[^&]+/gi, '')
        .replace(/\/+/g, '/')
        .toLowerCase()
        .trim();
    }
  }

  /**
   * Normaliza cadenas para comparación de texto (sin diacríticos, en minúsculas y sin símbolos).
   * @param {string} str
   * @returns {string}
   */
  static normalizeString(str) {
    if (!str) return '';
    return str
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '') // Quita tildes / diacríticos
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');      // Solo alfanuméricos
  }

  /**
   * Compara dos elementos de playlist evaluando los 5 factores:
   * 1. provider / source
   * 2. tvg-id
   * 3. stream URL normalizada
   * 4. nombre
   * 5. grupo
   * 
   * @param {import('./PlaylistTypes.js').LelouchMediaItem} a
   * @param {import('./PlaylistTypes.js').LelouchMediaItem} b
   * @returns {import('./PlaylistTypes.js').ItemComparisonResult}
   */
  static compareItems(a, b) {
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

    const normUrlA = this.normalizeStreamUrl(a.streamUrl || a.directUrl || a.url || '');
    const normUrlB = this.normalizeStreamUrl(b.streamUrl || b.directUrl || b.url || '');
    const sameNormalizedUrl = Boolean(normUrlA && normUrlB && normUrlA === normUrlB);

    const tvgIdA = (a.tvgId || a.epgId || '').trim().toLowerCase();
    const tvgIdB = (b.tvgId || b.epgId || '').trim().toLowerCase();
    const sameTvgId = Boolean(tvgIdA && tvgIdB && tvgIdA === tvgIdB);

    const nameA = this.normalizeString(a.name || '');
    const nameB = this.normalizeString(b.name || '');
    const sameName = Boolean(nameA && nameB && nameA === nameB);

    const groupA = this.normalizeString(a.group || a.categoryName || a.category || '');
    const groupB = this.normalizeString(b.group || b.categoryName || b.category || '');
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
        reason: 'Mismo canal dentro del mismo proveedor (mismo catalog_item_id)'
      };
    }

    // REGLA 3 (FASE 23 CRUCIAL): Distintas fuentes con el mismo nombre
    // Fuente A ─ Canal X vs Fuente B ─ Canal X
    // NO decidir automáticamente que son idénticos únicamente por nombre
    if (differentSources) {
      if (sameName && !sameNormalizedUrl) {
        return {
          isDuplicate: false, // NO duplicado -> Se preservan ambos canales
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

    // REGLA 5: Sin fuentes declaradas, solo coincidencia de nombre pero URLs distintas
    if (sameName && !sameNormalizedUrl) {
      return {
        isDuplicate: false, // Se preservan para evitar borrar canales distintos con nombres iguales
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

  /**
   * Desduplica una lista de LelouchMediaItem según la política configurada.
   * Política por defecto: 'lelouch_multi_factor' (FASE 23).
   * 
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {import('./PlaylistTypes.js').DeduplicationStrategy} [strategy='lelouch_multi_factor']
   * @param {boolean} [mergeMetadata=true] - Si true, fusiona logos, tvg-id y conserva streams secundarios
   * @returns {import('./PlaylistTypes.js').LelouchMediaItem[]}
   */
  static deduplicate(items, strategy = 'lelouch_multi_factor', mergeMetadata = true) {
    if (!Array.isArray(items) || items.length <= 1) return items || [];

    const seenMap = new Map();
    const result = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const key = this._generateKey(item, strategy, i);

      if (!seenMap.has(key)) {
        const cloned = { ...item };
        seenMap.set(key, cloned);
        result.push(cloned);
      } else if (mergeMetadata) {
        const existing = seenMap.get(key);
        this._mergeItemMetadata(existing, item);
      }
    }

    return result;
  }

  /**
   * Método de instancia para conveniencia.
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {import('./PlaylistTypes.js').DeduplicationStrategy} [strategy='lelouch_multi_factor']
   * @param {boolean} [mergeMetadata=true]
   * @returns {import('./PlaylistTypes.js').LelouchMediaItem[]}
   */
  deduplicate(items, strategy = 'lelouch_multi_factor', mergeMetadata = true) {
    return PlaylistDeduplicator.deduplicate(items, strategy, mergeMetadata);
  }

  /**
   * Encuentra los duplicados en una lista sin modificarlos.
   * @param {import('./PlaylistTypes.js').LelouchMediaItem[]} items
   * @param {import('./PlaylistTypes.js').DeduplicationStrategy} [strategy='lelouch_multi_factor']
   * @returns {{ key: string, count: number, items: import('./PlaylistTypes.js').LelouchMediaItem[] }[]}
   */
  static findDuplicates(items, strategy = 'lelouch_multi_factor') {
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
   * Genera una clave determinista según los factores solicitados.
   * @private
   */
  static _generateKey(item, strategy, index) {
    const normUrl = this.normalizeStreamUrl(item.streamUrl || item.directUrl || item.url || '');
    const cleanName = this.normalizeString(item.name || '');
    const cleanCat = this.normalizeString(item.group || item.categoryName || item.category || '');
    const source = item.sourceId || item.providerId || null;
    const catId = item.catalogItemId || '';
    const tvgId = (item.tvgId || item.epgId || '').trim().toLowerCase();

    switch (strategy) {
      case 'lelouch_multi_factor':
      default:
        // FASE 23: Clave compuesta multi-factor
        // 1. Si hay stream URL normalizada válida, es la huella de duplicación absoluta
        if (normUrl) {
          // Si el source está disponible, lo prefijamos para aislar fuentes independientes
          if (source) {
            return `lmf_${source}_${normUrl}`;
          }
          return `lmf_url_${normUrl}`;
        }
        // 2. Si es de catálogo con sourceId + catalogItemId
        if (source && catId) {
          return `lmf_cat_${source}_${catId}`;
        }
        // 3. Fallback: Fuente + Nombre + Categoría
        if (source) {
          return `lmf_snc_${source}_${cleanCat}_${cleanName}`;
        }
        // 4. Si no hay source ni URL (entrada anómala), usar índice
        return `lmf_idx_${index}_${cleanName}`;

      case 'strict_provider':
        // Aislamiento total de proveedores
        const s = source || 'direct';
        if (normUrl) return `sp_${s}_${normUrl}`;
        return `sp_${s}_${catId || cleanName}`;

      case 'normalized_stream_url':
      case 'url':
        return `url_${normUrl || cleanName}`;

      case 'catalog_id':
        if (source && catId) {
          return `cat_${source}_${catId}`;
        }
        return `url_${normUrl || cleanName}`;

      case 'name_and_category':
        // Solo para compatibilidad explícita si el usuario la selecciona manualmente
        return `nc_${cleanCat}_${cleanName}`;

      case 'epg_id':
      case 'cross_source_epg':
        if (tvgId) {
          return `epg_${tvgId}`;
        }
        return `url_${normUrl || cleanName}`;
    }
  }

  /**
   * Fusiona metadatos ausentes desde el elemento duplicado hacia la entrada preservada.
   * Preserva además URLs alternativas en metadata.backupStreams para redundancia.
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

    // FASE 23: Preservar stream URL alternativa si difiere de la principal
    const srcUrl = source.streamUrl || source.directUrl || source.url;
    const targetUrl = target.streamUrl || target.directUrl || target.url;
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
