/**
 * @module IPTVService
 * Servicio central que orquesta la importación y provisión de datos IPTV.
 * 
 * Gestiona:
 * - Caché persistente en IndexedDB (a través de CacheService)
 * - Caché en memoria para acceso ultrarrápido
 * - Lazy loading de detalles de Películas y Series (bajo demanda)
 * - Indexación automática para SearchService
 * - Estadísticas reales (Live, Movies, Series, Sports) sin hardcoding
 * - Recuperación y tolerancia a fallos parciales
 */

import { parseIPTVUrl } from '../parsers/UrlParser.js';
import { XtreamAdapter } from '../adapters/XtreamAdapter.js';
import { SourceType, IPTVError } from '../types/iptv.types.js';
import { cacheService } from './CacheService.js';
import { searchService } from './SearchService.js';
import { epgService } from './EPGService.js';

// Duraciones de caché por recurso (en milisegundos)
const CACHE_TTL = {
  account:    15 * 60 * 1000,       // 15 minutos
  categories: 60 * 60 * 1000,       // 1 hora
  catalog:    4 * 60 * 60 * 1000,   // 4 horas
  details:    24 * 60 * 60 * 1000,  // 24 horas para metadata VOD/Series
};

export class IPTVService {
  constructor() {
    this._memCache = new Map();
    this._adapter = null;
    this._parsedUrl = null;
    this._onProgress = null;
    this._movieInfoCache = new Map();
    this._seriesInfoCache = new Map();
    this.state = {
      connected: false,
      sourceType: null,
      account: null,
      server: null,
      categories: { live: [], vod: [], series: [] },
      live: [],
      movies: [],
      series: [],
      sportsCount: 0,
      errors: [],
      lastUpdated: null
    };
  }

  onProgress(cb) {
    this._onProgress = cb;
  }

  _progress(step, percent, detail = '') {
    if (this._onProgress) this._onProgress({ step, percent, detail });
  }

  /**
   * Conecta con el servidor usando una URL cruda.
   * @param {string} rawUrl
   */
  async connect(rawUrl) {
    this._progress('Analizando URL...', 5);
    this._parsedUrl = parseIPTVUrl(rawUrl);
    this.state.sourceType = this._parsedUrl.sourceType;

    this._adapter = new XtreamAdapter(this._parsedUrl);
    this._progress('Probando conexión con el servidor...', 10);

    const test = await this._adapter.testConnection(15000);
    if (!test.connected) {
      this.state.connected = false;
      throw Object.assign(
        new Error(test.error || 'No se pudo conectar al servidor.'),
        { code: IPTVError.SERVER_UNAVAILABLE }
      );
    }

    this.state.connected = true;
    this._progress('Conexión establecida', 15, `${test.responseTimeMs}ms`);
  }

  /**
   * Intenta restaurar el catálogo desde la caché persistente (IndexedDB)
   * para arranque instantáneo sin descargar megabytes por la red.
   */
  async tryRestoreFromCache() {
    if (!this._parsedUrl) return false;
    const prefix = `cat_${btoa(unescape(encodeURIComponent(this._parsedUrl.serverBaseUrl))).slice(0, 12)}_`;

    try {
      const [accountData, categories, live, movies, series] = await Promise.all([
        cacheService.get(`${prefix}account`),
        cacheService.get(`${prefix}categories`),
        cacheService.get(`${prefix}live`),
        cacheService.get(`${prefix}movies`),
        cacheService.get(`${prefix}series`),
      ]);

      if (accountData && categories && (live || movies || series)) {
        this.state.account = accountData.account;
        this.state.server = accountData.server;
        this.state.categories = categories;
        this.state.live = live || [];
        this.state.movies = movies || [];
        this.state.series = series || [];
        this.state.sportsCount = this._calculateSportsCount(this.state.live);
        this.state.lastUpdated = accountData.updatedAt || Date.now();

        // Indexar en el buscador
        searchService.buildIndex({
          live: this.state.live,
          movies: this.state.movies,
          series: this.state.series
        });

        return true;
      }
    } catch (e) {
      console.warn('[IPTVService] No se pudo restaurar caché persistente:', e);
    }
    return false;
  }

  /**
   * Importa todo el catálogo desde la API y lo persiste en IndexedDB.
   */
  async importAll() {
    if (!this._adapter) throw new Error('Llama a connect() primero.');

    this.state.errors = [];
    const prefix = `cat_${btoa(unescape(encodeURIComponent(this._parsedUrl.serverBaseUrl))).slice(0, 12)}_`;

    // 1. Cuenta
    this._progress('Obteniendo información de cuenta...', 20);
    try {
      const { account, server } = await this._adapter.getAccountInfo();
      this.state.account = account;
      this.state.server = server;
      await cacheService.set(`${prefix}account`, { account, server, updatedAt: Date.now() }, CACHE_TTL.account);
      this._progress('✓ Cuenta', 25, `${account.username} — vence: ${account.expiresAt?.toLocaleDateString() ?? 'N/A'}`);
    } catch (err) {
      this.state.errors.push({ context: 'account', message: err.message });
      this._progress('⚠ Error en cuenta', 25, err.message);
    }

    // 2. Categorías
    this._progress('Obteniendo categorías...', 30);
    let categoryMap = new Map();
    try {
      const cats = await this._adapter.getCategories();
      this.state.categories = cats;

      for (const c of [...cats.live, ...cats.vod, ...cats.series]) {
        categoryMap.set(c.id, c.name);
      }

      await cacheService.set(`${prefix}categories`, cats, CACHE_TTL.categories);
      this._progress(
        `✓ ${cats.live.length} categ. LIVE, ${cats.vod.length} VOD, ${cats.series.length} Series`,
        35
      );
    } catch (err) {
      this.state.errors.push({ context: 'categories', message: err.message });
      this._progress('⚠ Error en categorías', 35, err.message);
    }

    // 3. Canales Live
    this._progress('Descargando canales en vivo...', 40);
    try {
      const channels = await this._adapter.getLiveChannels(categoryMap);
      this.state.live = channels;
      this.state.sportsCount = this._calculateSportsCount(channels);
      await cacheService.set(`${prefix}live`, channels, CACHE_TTL.catalog);
      this._progress(`✓ ${channels.length.toLocaleString()} canales LIVE (${this.state.sportsCount} Deportes)`, 55);
    } catch (err) {
      this.state.errors.push({ context: 'live', message: err.message });
      this._progress('⚠ Error en canales LIVE', 55, err.message);
    }

    // 4. Películas VOD
    this._progress('Descargando catálogo VOD...', 60);
    try {
      const movies = await this._adapter.getMovies(categoryMap);
      this.state.movies = movies;
      await cacheService.set(`${prefix}movies`, movies, CACHE_TTL.catalog);
      this._progress(`✓ ${movies.length.toLocaleString()} películas VOD`, 80);
    } catch (err) {
      this.state.errors.push({ context: 'movies', message: err.message });
      this._progress('⚠ Error en VOD', 80, err.message);
    }

    // 5. Series
    this._progress('Descargando series...', 82);
    try {
      const series = await this._adapter.getSeries(categoryMap);
      this.state.series = series;
      await cacheService.set(`${prefix}series`, series, CACHE_TTL.catalog);
      this._progress(`✓ ${series.length.toLocaleString()} series`, 95);
    } catch (err) {
      this.state.series = [];
      this._progress('ℹ Series no disponibles', 95, err.message);
    }

    // Construir índice de búsqueda global
    this._progress('Construyendo índice de búsqueda...', 98);
    searchService.buildIndex({
      live: this.state.live,
      movies: this.state.movies,
      series: this.state.series
    });

    this.state.lastUpdated = Date.now();
    const hasErrors = this.state.errors.length > 0;
    this._progress(
      hasErrors ? '⚠ Importación parcial completada' : '✓ Importación completada',
      100,
      hasErrors ? `${this.state.errors.length} error(es)` : ''
    );

    return this.state;
  }

  /**
   * Obtiene la metadata enriquecida de una película bajo demanda (get_vod_info).
   * @param {string|number} movieId
   */
  async getMovieInfo(movieId) {
    const key = String(movieId);
    if (this._movieInfoCache.has(key)) {
      return this._movieInfoCache.get(key);
    }

    if (!this._adapter) return null;
    try {
      const info = await this._adapter.getMovieInfo(movieId);
      this._movieInfoCache.set(key, info);
      return info;
    } catch (err) {
      console.warn(`[IPTVService] No se pudo obtener get_vod_info(${movieId}):`, err.message);
      return null;
    }
  }

  /**
   * Obtiene temporadas y episodios de una serie bajo demanda (get_series_info).
   * @param {string|number} seriesId
   */
  async getSeriesInfo(seriesId) {
    const key = String(seriesId);
    if (this._seriesInfoCache.has(key)) {
      return this._seriesInfoCache.get(key);
    }

    if (!this._adapter) return null;
    try {
      const info = await this._adapter.getSeriesInfo(seriesId);
      this._seriesInfoCache.set(key, info);
      return info;
    } catch (err) {
      console.warn(`[IPTVService] No se pudo obtener get_series_info(${seriesId}):`, err.message);
      throw err;
    }
  }

  /**
   * Obtiene EPG corta para un canal.
   * @param {string|number} channelId
   */
  async getEPG(channelId) {
    if (!this._adapter) return { now: null, next: null, listings: [] };
    return epgService.getChannelEPG(this._adapter, channelId);
  }

  /**
   * Invalida el caché y vuelve a importar todo.
   */
  async refresh() {
    if (this._parsedUrl) {
      const prefix = `cat_${btoa(unescape(encodeURIComponent(this._parsedUrl.serverBaseUrl))).slice(0, 12)}_`;
      await Promise.all([
        cacheService.delete(`${prefix}account`),
        cacheService.delete(`${prefix}categories`),
        cacheService.delete(`${prefix}live`),
        cacheService.delete(`${prefix}movies`),
        cacheService.delete(`${prefix}series`),
      ]);
    }
    this._memCache.clear();
    this._movieInfoCache.clear();
    this._seriesInfoCache.clear();
    epgService.clear();
    return this.importAll();
  }

  getDiagnostics() {
    return this._adapter?.getDiagnostics() ?? [];
  }

  /**
   * Calcula la cantidad de canales deportivos basándose en nombres de categorías.
   * @private
   */
  _calculateSportsCount(channels) {
    if (!Array.isArray(channels)) return 0;
    const sportsKeywords = ['sport', 'deport', 'futbol', 'football', 'espn', 'fox sport', 'laliga', 'nba', 'nfl', 'mlb', 'ufc', 'box'];
    return channels.filter(ch => {
      const cat = (ch.categoryName || '').toLowerCase();
      const name = (ch.name || '').toLowerCase();
      return sportsKeywords.some(kw => cat.includes(kw) || name.includes(kw));
    }).length;
  }
}

export const iptvService = new IPTVService();
