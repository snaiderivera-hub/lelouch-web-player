/**
 * @module SearchService
 * Motor de búsqueda global ultra-rápido en memoria para Live TV, Películas y Series.
 * Implementa índice precomputado generado una sola vez al cargar el catálogo:
 * { id, type, title, normalizedTitle, category, normalizedCategory, image, year, item }
 * 
 * Soporta límites específicos (Live: 3, Movies: 8, Series: 4) para el Search Overlay XALB
 * y normalización de tildes (NFD) precomputada sin overhead durante las pulsaciones.
 */

export function normalizeSearchText(str) {
  if (!str) return '';
  return String(str)
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim();
}

class SearchService {
  constructor() {
    this._liveIndex = [];
    this._moviesIndex = [];
    this._seriesIndex = [];
    this._isIndexed = false;
    this._lastSearchTimeMs = 0;
  }

  /**
   * Construye el índice de búsqueda precomputado en memoria con los catálogos.
   * Calcula 'normalizedTitle' y 'normalizedCategory' exactamente UNA VEZ por elemento.
   * @param {{ live: Array, movies: Array, series: Array }} catalog
   */
  buildIndex({ live = [], movies = [], series = [] }) {
    const t0 = performance.now();

    this._liveIndex = new Array(live.length);
    for (let i = 0; i < live.length; i++) {
      const item = live[i];
      const title = String(item.name || item.title || '');
      const cat = String(item.category_name || item.categoryName || '');
      this._liveIndex[i] = {
        id: String(item.id || item.stream_id || i),
        type: 'live',
        title,
        normalizedTitle: normalizeSearchText(title),
        category: cat,
        normalizedCategory: normalizeSearchText(cat),
        image: item.stream_icon || item.logo || '',
        year: '',
        item
      };
    }

    this._moviesIndex = new Array(movies.length);
    for (let i = 0; i < movies.length; i++) {
      const item = movies[i];
      const title = String(item.name || item.title || '');
      const cat = String(item.category_name || item.categoryName || '');
      this._moviesIndex[i] = {
        id: String(item.id || item.stream_id || i),
        type: 'movie',
        title,
        normalizedTitle: normalizeSearchText(title),
        category: cat,
        normalizedCategory: normalizeSearchText(cat),
        image: item.stream_icon || item.poster || item.cover || '',
        year: item.year ? String(item.year) : '',
        item
      };
    }

    this._seriesIndex = new Array(series.length);
    for (let i = 0; i < series.length; i++) {
      const item = series[i];
      const title = String(item.name || item.title || '');
      const cat = String(item.category_name || item.categoryName || '');
      this._seriesIndex[i] = {
        id: String(item.id || item.series_id || i),
        type: 'series',
        title,
        normalizedTitle: normalizeSearchText(title),
        category: cat,
        normalizedCategory: normalizeSearchText(cat),
        image: item.cover || item.poster || item.stream_icon || '',
        year: item.year ? String(item.year) : '',
        item
      };
    }

    this._isIndexed = true;
    const elapsed = Math.round(performance.now() - t0);
    console.log(`[SearchService] Índice precomputado listo: ${this.totalItems.toLocaleString()} elementos en ${elapsed}ms.`);
  }

  /**
   * Realiza una búsqueda ultra-rápida sobre los índices precomputados.
   * @param {string} query
   * @param {number|{live: number, movies: number, series: number}} limits
   * @returns {{ live: Array, movies: Array, series: Array, totalMatches: number, searchTimeMs: number }}
   */
  search(query, limits = { live: 3, movies: 8, series: 4 }, filterFn = null) {
    const t0 = performance.now();
    const q = normalizeSearchText(query);

    if (!q || q.length < 2) {
      this._lastSearchTimeMs = 0;
      return { live: [], movies: [], series: [], totalMatches: 0, searchTimeMs: 0 };
    }

    const maxLive = typeof limits === 'number' ? limits : (limits.live ?? 3);
    const maxMovies = typeof limits === 'number' ? limits : (limits.movies ?? 8);
    const maxSeries = typeof limits === 'number' ? limits : (limits.series ?? 4);

    const searchBucket = (bucket, maxLimit) => {
      if (maxLimit <= 0) return [];
      const exactPrefix = [];
      const titleContains = [];
      const catContains = [];

      const len = bucket.length;
      for (let i = 0; i < len; i++) {
        const entry = bucket[i];
        if (filterFn && !filterFn(entry)) continue;
        const nTitle = entry.normalizedTitle;

        if (nTitle.startsWith(q)) {
          exactPrefix.push(entry);
          if (exactPrefix.length >= maxLimit) break;
        } else if (nTitle.includes(q)) {
          titleContains.push(entry);
        } else if (entry.normalizedCategory.includes(q) || (entry.year && entry.year === q)) {
          catContains.push(entry);
        }

        // Si ya recolectamos suficientes candidatos prioritarios, abortar anticipadamente
        if (exactPrefix.length + titleContains.length >= maxLimit * 2) {
          break;
        }
      }

      const merged = exactPrefix
        .concat(titleContains)
        .concat(catContains)
        .slice(0, maxLimit);

      return merged;
    };

    const liveMatches = searchBucket(this._liveIndex, maxLive);
    const moviesMatches = searchBucket(this._moviesIndex, maxMovies);
    const seriesMatches = searchBucket(this._seriesIndex, maxSeries);

    const totalMatches = liveMatches.length + moviesMatches.length + seriesMatches.length;
    const elapsed = Math.round((performance.now() - t0) * 100) / 100;
    this._lastSearchTimeMs = elapsed;

    return {
      live: liveMatches,
      movies: moviesMatches,
      series: seriesMatches,
      totalMatches,
      searchTimeMs: elapsed
    };
  }

  get totalItems() {
    return this._liveIndex.length + this._moviesIndex.length + this._seriesIndex.length;
  }

  get isReady() {
    return this._isIndexed;
  }

  get lastSearchTimeMs() {
    return this._lastSearchTimeMs;
  }
}

export const searchService = new SearchService();
