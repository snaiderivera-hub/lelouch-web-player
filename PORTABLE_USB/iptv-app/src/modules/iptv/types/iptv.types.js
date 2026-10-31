/**
 * @module iptv.types
 * Modelos de datos normalizados para la aplicación IPTV Data Architect.
 * Todos los datos crudos del proveedor se normalizan a estos modelos antes de
 * ser usados por la UI o los servicios.
 */

/** @enum {string} */
export const SourceType = {
  XTREAM_M3U:  'XTREAM_M3U',
  XTREAM_API:  'XTREAM_API',
  M3U_REMOTE:  'M3U_REMOTE',
  M3U_LOCAL:   'M3U_LOCAL',
  XMLTV:       'XMLTV',
  JSON_API:    'JSON_API',
  UNKNOWN:     'UNKNOWN',
};

/** @enum {string} */
export const IPTVError = {
  AUTH_FAILED:          'AUTH_FAILED',
  NETWORK_ERROR:        'NETWORK_ERROR',
  TIMEOUT:              'TIMEOUT',
  INVALID_M3U:          'INVALID_M3U',
  INVALID_JSON:         'INVALID_JSON',
  XTREAM_NOT_SUPPORTED: 'XTREAM_NOT_SUPPORTED',
  ACCOUNT_EXPIRED:      'ACCOUNT_EXPIRED',
  SERVER_UNAVAILABLE:   'SERVER_UNAVAILABLE',
  RATE_LIMITED:         'RATE_LIMITED',
  PARTIAL_IMPORT:       'PARTIAL_IMPORT',
  CORS_BLOCKED:         'CORS_BLOCKED',
};

/**
 * Información de la cuenta del proveedor.
 * @typedef {Object} AccountInfo
 * @property {string} username
 * @property {string} status
 * @property {number} maxConnections
 * @property {number} activeConnections
 * @property {Date|null} expiresAt
 * @property {Date|null} createdAt
 * @property {number} daysRemaining
 * @property {boolean} isExpired
 */

/**
 * Información del servidor.
 * @typedef {Object} ServerInfo
 * @property {string} url
 * @property {string} protocol
 * @property {string} port
 * @property {string} timezone
 * @property {string} timestampNow
 */

/**
 * Categoría genérica (live o vod).
 * @typedef {Object} Category
 * @property {string} id
 * @property {string} name
 * @property {string} type  'live' | 'vod' | 'series'
 * @property {number} itemCount
 */

/**
 * Canal de televisión en vivo.
 * @typedef {Object} LiveChannel
 * @property {string} id
 * @property {string} categoryId
 * @property {string} categoryName
 * @property {string} name
 * @property {string} logo
 * @property {string} streamUrl
 * @property {string} streamType  'live'
 * @property {string} epgId
 * @property {number|null} rating
 * @property {Date|null} addedAt
 * @property {Object} rawMetadata
 */

/**
 * Película (VOD).
 * @typedef {Object} Movie
 * @property {string} id
 * @property {string} categoryId
 * @property {string} categoryName
 * @property {string} name
 * @property {string} logo
 * @property {string} poster
 * @property {string} streamUrl
 * @property {string} containerExtension  'mkv' | 'mp4' | etc.
 * @property {string} streamType  'vod'
 * @property {number|null} rating
 * @property {string} description
 * @property {number|null} year
 * @property {string} genre
 * @property {number|null} duration
 * @property {Date|null} addedAt
 * @property {Object} rawMetadata
 */

/**
 * Serie.
 * @typedef {Object} Series
 * @property {string} id
 * @property {string} categoryId
 * @property {string} categoryName
 * @property {string} name
 * @property {string} logo
 * @property {string} poster
 * @property {string} description
 * @property {number|null} rating
 * @property {string} genre
 * @property {number|null} year
 * @property {Season[]} seasons
 * @property {Object} rawMetadata
 */

/**
 * Temporada de una serie.
 * @typedef {Object} Season
 * @property {string} id
 * @property {number} seasonNumber
 * @property {string} name
 * @property {string} poster
 * @property {Episode[]} episodes
 */

/**
 * Episodio de una temporada.
 * @typedef {Object} Episode
 * @property {string} id
 * @property {string} seriesId
 * @property {number} seasonNumber
 * @property {number} episodeNumber
 * @property {string} title
 * @property {string} poster
 * @property {string} streamUrl
 * @property {string} containerExtension
 * @property {number|null} duration
 * @property {string} description
 * @property {Date|null} airDate
 * @property {Object} rawMetadata
 */

/**
 * Entrada de EPG (guía de programación).
 * @typedef {Object} EPGEntry
 * @property {string} channelId
 * @property {string} epgId
 * @property {string} title
 * @property {string} description
 * @property {Date} startTime
 * @property {Date} endTime
 * @property {string} category
 */

/**
 * Resultado de la detección de una URL.
 * @typedef {Object} ParsedIPTVUrl
 * @property {string} rawUrl
 * @property {string} sourceType  SourceType
 * @property {string} protocol
 * @property {string} hostname
 * @property {string} port
 * @property {string} serverBaseUrl
 * @property {string} username
 * @property {string} maskedPassword
 * @property {string} pathname
 * @property {Object} params  todos los query params adicionales
 */

/**
 * Estado de diagnóstico de un endpoint.
 * @typedef {Object} DiagnosticEntry
 * @property {string} endpoint
 * @property {number|null} httpStatus
 * @property {string|null} contentType
 * @property {number|null} responseTimeMs
 * @property {number|null} recordCount
 * @property {string|null} error
 * @property {boolean} success
 */
