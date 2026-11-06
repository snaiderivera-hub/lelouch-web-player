/**
 * @module normalize
 * Funciones de normalización de datos crudos del proveedor a modelos internos.
 * Tolera campos null, IDs string o numéricos, valores ausentes.
 */

import { buildLiveStreamUrl, buildVodStreamUrl, buildSeriesStreamUrl } from './security.js';

/**
 * Convierte un Unix timestamp (string o número) a Date.
 * @param {string|number|null} ts
 * @returns {Date|null}
 */
function tsToDate(ts) {
  if (!ts || ts === '0' || ts === 0) return null;
  try {
    const num = typeof ts === 'string' ? parseInt(ts, 10) : ts;
    if (isNaN(num) || num <= 0) return null;
    return new Date(num * 1000);
  } catch {
    return null;
  }
}

/**
 * Convierte un valor de rating a número o null.
 * @param {string|number|null} r
 * @returns {number|null}
 */
function toRating(r) {
  if (r === null || r === undefined || r === '' || r === '0') return null;
  const n = parseFloat(r);
  return isNaN(n) ? null : Math.round(n * 10) / 10;
}

/**
 * Normaliza la respuesta cruda de user_info + server_info al modelo AccountInfo/ServerInfo.
 * @param {Object} raw  Respuesta completa de player_api.php (sin action)
 * @param {string} serverBaseUrl
 * @returns {{ account: Object, server: Object }}
 */
export function normalizeAccountInfo(raw, serverBaseUrl) {
  const ui = raw?.user_info || {};
  const si = raw?.server_info || {};

  const expDate = tsToDate(ui.exp_date);
  const createdAt = tsToDate(ui.created_at);
  const now = new Date();
  const daysRemaining = expDate
    ? Math.max(0, Math.ceil((expDate - now) / (1000 * 60 * 60 * 24)))
    : 0;

  const account = {
    username:         String(ui.username || ''),
    status:           String(ui.status || 'Unknown'),
    isActive:         ui.status === 'Active',
    maxConnections:   parseInt(ui.max_connections) || 0,
    activeConnections:parseInt(ui.active_cons) || 0,
    expiresAt:        expDate,
    createdAt,
    daysRemaining,
    isExpired:        expDate ? expDate < now : false,
    isTrial:          ui.is_trial === '1' || ui.is_trial === true,
  };

  const port = si.port ? String(si.port) : (si.https_port ? String(si.https_port) : '80');
  const server = {
    url:        serverBaseUrl,
    protocol:   String(si.server_protocol || 'http').toUpperCase(),
    port,
    timezone:   String(si.timezone || ''),
    timestampNow: si.timestamp_now ? new Date(si.timestamp_now * 1000).toISOString() : null,
  };

  return { account, server };
}

/**
 * Normaliza un array de categorías crudas de la API Xtream.
 * @param {Array} rawCategories
 * @param {'live'|'vod'|'series'} type
 * @returns {import('../types/iptv.types.js').Category[]}
 */
export function normalizeCategories(rawCategories, type) {
  if (!Array.isArray(rawCategories)) return [];
  return rawCategories.map(c => ({
    id:        String(c.category_id ?? c.id ?? ''),
    name:      String(c.category_name ?? c.name ?? 'Sin nombre'),
    type,
    itemCount: 0,  // se rellena después al procesar streams
  }));
}

/**
 * Normaliza un array de streams live crudos.
 * @param {Array} rawStreams
 * @param {Map<string, string>} categoryMap  id → name
 * @param {string} serverBaseUrl
 * @param {string} username
 * @param {string} password
 * @returns {import('../types/iptv.types.js').LiveChannel[]}
 */
export function normalizeLiveStreams(rawStreams, categoryMap, serverBaseUrl, username, password) {
  if (!Array.isArray(rawStreams)) return [];
  return rawStreams.map(s => {
    const catId = String(s.category_id ?? '');
    const ext = s.container_extension || 'm3u8';
    return {
      id:           String(s.stream_id ?? s.id ?? ''),
      categoryId:   catId,
      categoryName: categoryMap.get(catId) || 'Sin categoría',
      name:         String(s.name ?? ''),
      logo:         String(s.stream_icon ?? ''),
      streamUrl:    buildLiveStreamUrl(serverBaseUrl, username, password, s.stream_id ?? s.id, ext),
      streamType:   'live',
      epgId:        String(s.epg_channel_id ?? ''),
      rating:       toRating(s.rating),
      addedAt:      tsToDate(s.added),
      rawMetadata:  s,
    };
  });
}

/**
 * Normaliza un array de streams VOD crudos.
 * @param {Array} rawStreams
 * @param {Map<string, string>} categoryMap  id → name
 * @param {string} serverBaseUrl
 * @param {string} username
 * @param {string} password
 * @returns {import('../types/iptv.types.js').Movie[]}
 */
export function normalizeVodStreams(rawStreams, categoryMap, serverBaseUrl, username, password) {
  if (!Array.isArray(rawStreams)) return [];
  return rawStreams.map(s => {
    const catId = String(s.category_id ?? '');
    const ext = s.container_extension || 'mp4';
    return {
      id:                 String(s.stream_id ?? s.id ?? ''),
      categoryId:         catId,
      categoryName:       categoryMap.get(catId) || 'Sin categoría',
      name:               String(s.name ?? ''),
      logo:               String(s.stream_icon ?? ''),
      poster:             String(s.stream_icon ?? ''),
      streamUrl:          buildVodStreamUrl(serverBaseUrl, username, password, s.stream_id ?? s.id, ext),
      containerExtension: ext,
      streamType:         'vod',
      rating:             toRating(s.rating),
      description:        String(s.plot ?? ''),
      year:               s.year ? parseInt(s.year) : null,
      genre:              String(s.genre ?? ''),
      duration:           s.duration_secs ? parseInt(s.duration_secs) : null,
      addedAt:            tsToDate(s.added),
      rawMetadata:        s,
    };
  });
}

/**
 * Normaliza un array de series crudas.
 * @param {Array} rawSeries
 * @param {Map<string, string>} categoryMap
 * @returns {import('../types/iptv.types.js').Series[]}
 */
export function normalizeSeriesList(rawSeries, categoryMap) {
  if (!Array.isArray(rawSeries)) return [];
  return rawSeries.map(s => {
    const catId = String(s.category_id ?? '');
    return {
      id:           String(s.series_id ?? s.id ?? ''),
      categoryId:   catId,
      categoryName: categoryMap.get(catId) || 'Sin categoría',
      name:         String(s.name ?? ''),
      logo:         String(s.cover ?? s.stream_icon ?? ''),
      poster:       String(s.cover ?? ''),
      description:  String(s.plot ?? ''),
      rating:       toRating(s.rating),
      genre:        String(s.genre ?? ''),
      year:         s.year ? parseInt(s.year) : null,
      seasons:      [],  // se rellena con get_series_info
      rawMetadata:  s,
    };
  });
}

/**
 * Normaliza la respuesta de get_series_info (temporadas y episodios).
 * @param {Object} raw  Respuesta de get_series_info
 * @param {string} seriesId
 * @param {string} serverBaseUrl
 * @param {string} username
 * @param {string} password
 * @returns {import('../types/iptv.types.js').Season[]}
 */
export function normalizeSeriesInfo(raw, seriesId, serverBaseUrl, username, password) {
  const seasonsRaw = raw?.episodes || raw?.seasons || {};
  const seasons = [];

  for (const [seasonKey, episodes] of Object.entries(seasonsRaw)) {
    const seasonNumber = parseInt(seasonKey) || 0;
    const normalizedEpisodes = Array.isArray(episodes)
      ? episodes.map(ep => {
          const ext = ep.container_extension || 'mp4';
          return {
            id:              String(ep.id ?? ''),
            seriesId,
            seasonNumber,
            episodeNumber:   parseInt(ep.episode_num) || 0,
            title:           String(ep.title ?? `Episodio ${ep.episode_num ?? ''}`),
            poster:          String(ep.info?.movie_image ?? ''),
            streamUrl:       buildSeriesStreamUrl(serverBaseUrl, username, password, ep.id, ext),
            containerExtension: ext,
            duration:        ep.info?.duration_secs ? parseInt(ep.info.duration_secs) : null,
            description:     String(ep.info?.plot ?? ''),
            airDate:         ep.info?.releasedate ? new Date(ep.info.releasedate) : null,
            rawMetadata:     ep,
          };
        })
      : [];

    seasons.push({
      id:            `${seriesId}-s${seasonNumber}`,
      seasonNumber,
      name:          `Temporada ${seasonNumber}`,
      poster:        '',
      episodes:      normalizedEpisodes,
    });
  }

  return seasons.sort((a, b) => a.seasonNumber - b.seasonNumber);
}
