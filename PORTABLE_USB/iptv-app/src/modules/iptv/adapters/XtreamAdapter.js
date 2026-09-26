/**
 * @module XtreamAdapter
 * Adapter para proveedores compatibles con Xtream Codes / Xtream UI.
 * 
 * Implementa la interfaz conceptual IPTVProviderAdapter:
 *   testConnection()
 *   getAccountInfo()
 *   getCategories()
 *   getLiveChannels()
 *   getMovies()
 *   getSeries()
 *   getSeriesInfo(seriesId)
 *   getEPG(channelId?, limit?)
 *
 * REGLAS:
 * - Un endpoint opcional que falla no cancela la importación completa.
 * - Los campos nulos son tolerados.
 * - Los IDs pueden ser string o numéricos.
 * - El servidor puede devolver HTML en vez de JSON (se detecta).
 * - Las credenciales NUNCA se logean completas.
 */

import { buildApiUrl, buildSafeLogUrl } from '../utils/security.js';
import {
  normalizeAccountInfo,
  normalizeCategories,
  normalizeLiveStreams,
  normalizeVodStreams,
  normalizeSeriesList,
  normalizeSeriesInfo,
} from '../utils/normalize.js';
import { IPTVError } from '../types/iptv.types.js';

const DEFAULT_TIMEOUT_MS = 20000;

function getProxyBase() {
  const port = (typeof window !== 'undefined' && window.IPTV_PROXY_PORT) ? window.IPTV_PROXY_PORT : 7878;
  return `http://localhost:${port}/proxy`;
}

/**
 * Determina si las peticiones deben ir por el proxy local.
 * Se activa cuando el servidor usa HTTP (no HTTPS) para evitar CORS.
 */
function shouldUseProxy(serverBaseUrl) {
  return serverBaseUrl.startsWith('http://');
}

/**
 * Ejecuta una petición a la API Xtream, con manejo de timeout y errores.
 * @param {string} url  URL completa con credenciales
 * @param {string} safeUrl  URL para logs (sin credenciales)
 * @param {AbortSignal} signal
 * @returns {Promise<Object>}
 */
async function fetchJson(url, safeUrl, signal) {
  const startTime = Date.now();
  
  // Usar proxy si es HTTP (para CORS)
  const useProxy = shouldUseProxy(url);
  const finalUrl = useProxy
    ? `${getProxyBase()}?target=${encodeURIComponent(url)}`
    : url;

  let res;
  try {
    res = await fetch(finalUrl, {
      signal,
      headers: { 'Accept': 'application/json, text/plain, */*' },
    });
  } catch (err) {
    if (err.name === 'AbortError') throw createError(IPTVError.TIMEOUT, 'La solicitud excedió el tiempo límite.', safeUrl);
    throw createError(IPTVError.NETWORK_ERROR, err.message, safeUrl);
  }

  const responseTimeMs = Date.now() - startTime;

  if (res.status === 401 || res.status === 403) {
    throw createError(IPTVError.AUTH_FAILED, `Autenticación fallida (HTTP ${res.status}).`, safeUrl);
  }
  if (res.status === 429) {
    throw createError(IPTVError.RATE_LIMITED, 'Demasiadas solicitudes al servidor.', safeUrl);
  }
  if (!res.ok) {
    throw createError(IPTVError.SERVER_UNAVAILABLE, `El servidor respondió HTTP ${res.status}.`, safeUrl);
  }

  const contentType = res.headers.get('content-type') || '';
  const text = await res.text();

  // Detectar si el servidor devolvió HTML en vez de JSON
  if (text.trim().startsWith('<')) {
    throw createError(IPTVError.XTREAM_NOT_SUPPORTED, 'El servidor respondió con HTML en lugar de JSON.', safeUrl);
  }

  let data;
  try {
    data = JSON.parse(text);
  } catch {
    throw createError(IPTVError.INVALID_JSON, 'La respuesta no es JSON válido.', safeUrl);
  }

  // Detectar respuesta de error de la API
  if (data?.user_info === false || (data?.user_info && data.user_info.auth === 0)) {
    throw createError(IPTVError.AUTH_FAILED, 'Las credenciales fueron rechazadas por el servidor.', safeUrl);
  }

  return { data, responseTimeMs, contentType };
}

function createError(code, message, endpoint) {
  const err = new Error(`[${code}] ${message}`);
  err.code = code;
  err.endpoint = endpoint;
  return err;
}

/**
 * XtreamAdapter — clase principal para interactuar con servidores Xtream.
 */
export class XtreamAdapter {
  /**
   * @param {import('../types/iptv.types.js').ParsedIPTVUrl} parsedUrl
   */
  constructor(parsedUrl) {
    this.parsedUrl = parsedUrl;
    this.serverBaseUrl = parsedUrl.serverBaseUrl;
    this.username = parsedUrl.username;
    this.password = parsedUrl._password;
    this.diagnostics = [];
  }

  _buildUrl(action = null) {
    return buildApiUrl(this.serverBaseUrl, this.username, this.password, action);
  }

  _safeUrl(action = null) {
    return buildSafeLogUrl(this.serverBaseUrl, action);
  }

  /**
   * Verifica conectividad básica con el servidor.
   * @param {number} timeoutMs
   * @returns {Promise<{ connected: boolean, responseTimeMs: number, error: string|null }>}
   */
  async testConnection(timeoutMs = DEFAULT_TIMEOUT_MS) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const safeUrl = this._safeUrl(null);

    try {
      const { responseTimeMs } = await fetchJson(this._buildUrl(), safeUrl, controller.signal);
      clearTimeout(timer);
      this._logDiagnostic(safeUrl, 200, 'application/json', responseTimeMs, null, null, true);
      return { connected: true, responseTimeMs, error: null };
    } catch (err) {
      clearTimeout(timer);
      this._logDiagnostic(safeUrl, null, null, null, null, err.message, false);
      return { connected: false, responseTimeMs: null, error: err.message };
    }
  }

  /**
   * Obtiene información de la cuenta y servidor.
   * @returns {Promise<{ account: Object, server: Object }>}
   */
  async getAccountInfo() {
    const controller = new AbortController();
    setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);
    const safeUrl = this._safeUrl(null);

    const { data, responseTimeMs } = await fetchJson(this._buildUrl(), safeUrl, controller.signal);
    this._logDiagnostic(safeUrl, 200, 'application/json', responseTimeMs, null, null, true);
    return normalizeAccountInfo(data, this.serverBaseUrl);
  }

  /**
   * Obtiene categorías de live, VOD y series (con tolerancia de fallos por endpoint).
   * @returns {Promise<{ live: Category[], vod: Category[], series: Category[] }>}
   */
  async getCategories() {
    const [liveRaw, vodRaw, seriesRaw] = await Promise.allSettled([
      this._fetchAction('get_live_categories'),
      this._fetchAction('get_vod_categories'),
      this._fetchAction('get_series_categories'),
    ]);

    return {
      live:   normalizeCategories(liveRaw.status === 'fulfilled' ? liveRaw.value : [], 'live'),
      vod:    normalizeCategories(vodRaw.status === 'fulfilled' ? vodRaw.value : [], 'vod'),
      series: normalizeCategories(seriesRaw.status === 'fulfilled' ? seriesRaw.value : [], 'series'),
    };
  }

  /**
   * Obtiene todos los canales live y los normaliza.
   * @param {Map<string,string>} categoryMap
   * @returns {Promise<LiveChannel[]>}
   */
  async getLiveChannels(categoryMap) {
    const raw = await this._fetchAction('get_live_streams');
    return normalizeLiveStreams(raw, categoryMap, this.serverBaseUrl, this.username, this.password);
  }

  /**
   * Obtiene todas las películas VOD y las normaliza.
   * @param {Map<string,string>} categoryMap
   * @returns {Promise<Movie[]>}
   */
  async getMovies(categoryMap) {
    const raw = await this._fetchAction('get_vod_streams');
    return normalizeVodStreams(raw, categoryMap, this.serverBaseUrl, this.username, this.password);
  }

  /**
   * Obtiene la lista de series y la normaliza.
   * @param {Map<string,string>} categoryMap
   * @returns {Promise<Series[]>}
   */
  async getSeries(categoryMap) {
    const raw = await this._fetchAction('get_series');
    return normalizeSeriesList(raw, categoryMap);
  }

  /**
   * Obtiene la información detallada de una película (VOD bajo demanda).
   * @param {string|number} vodId
   * @returns {Promise<Object>}
   */
  async getMovieInfo(vodId) {
    const action = `get_vod_info&vod_id=${vodId}`;
    const safeUrl = this._safeUrl(action);
    const controller = new AbortController();
    setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

    try {
      const { data, responseTimeMs } = await fetchJson(
        this._buildUrl(action),
        safeUrl,
        controller.signal
      );
      this._logDiagnostic(safeUrl, 200, 'application/json', responseTimeMs, null, null, true);
      return data || {};
    } catch (err) {
      this._logDiagnostic(safeUrl, null, null, null, null, err.message, false);
      throw err;
    }
  }

  /**
   * Obtiene temporadas y episodios de una serie específica.
   * @param {string|number} seriesId
   * @returns {Promise<Object>}
   */
  async getSeriesInfo(seriesId) {
    const controller = new AbortController();
    setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);
    const action = `get_series_info&series_id=${seriesId}`;
    const safeUrl = this._safeUrl(action);

    const { data, responseTimeMs } = await fetchJson(
      buildApiUrl(this.serverBaseUrl, this.username, this.password, action),
      safeUrl,
      controller.signal
    );

    this._logDiagnostic(safeUrl, 200, 'application/json', responseTimeMs, null, null, true);
    const normalizedSeasons = normalizeSeriesInfo(data, String(seriesId), this.serverBaseUrl, this.username, this.password);
    return {
      info: data?.info || {},
      seasons: normalizedSeasons,
      episodes: data?.episodes || {},
      raw: data
    };
  }

  /**
   * Obtiene la EPG corta para un canal (o todos si no se especifica).
   * @param {string|null} channelId
   * @param {number} limit
   * @returns {Promise<EPGEntry[]>}
   */
  async getEPG(channelId = null, limit = 10) {
    const action = channelId
      ? `get_short_epg&stream_id=${channelId}&limit=${limit}`
      : 'get_epg_and_shortepg';
    try {
      const raw = await this._fetchAction(action);
      if (raw?.epg_listings && Array.isArray(raw.epg_listings)) {
        return raw.epg_listings;
      }
      return Array.isArray(raw) ? raw : [];
    } catch {
      return [];  // EPG es opcional
    }
  }

  /** @private */
  async _fetchAction(action) {
    const isHeavy = action.includes('get_vod_streams') || action.includes('get_live_streams') || action.includes('get_series');
    const timeout = isHeavy ? 60000 : DEFAULT_TIMEOUT_MS;
    const controller = new AbortController();
    setTimeout(() => controller.abort(), timeout);
    const safeUrl = this._safeUrl(action);

    const { data, responseTimeMs } = await fetchJson(
      this._buildUrl(action), safeUrl, controller.signal
    );

    const count = Array.isArray(data) ? data.length : null;
    this._logDiagnostic(safeUrl, 200, 'application/json', responseTimeMs, count, null, true);
    return data;
  }

  /** @private */
  _logDiagnostic(endpoint, httpStatus, contentType, responseTimeMs, recordCount, error, success) {
    this.diagnostics.push({ endpoint, httpStatus, contentType, responseTimeMs, recordCount, error, success });
  }

  getDiagnostics() {
    return [...this.diagnostics];
  }
}
