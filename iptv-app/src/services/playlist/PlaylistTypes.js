/**
 * @module PlaylistTypes
 * Definiciones de tipos e interfaces para la capa de gestión de playlists M3U.
 * Modelo interno único LelouchMediaItem y contratos de parseo/generación.
 */

/**
 * @typedef {'live' | 'movie' | 'series' | 'radio' | 'vod' | 'direct'} MediaType
 */

/**
 * Representación interna canónica única para cualquier elemento de medios
 * procedente de Xtream API, M3U externo o enlace manual directo.
 * @typedef {Object} LelouchMediaItem
 * @property {string} id - Identificador único del item
 * @property {'catalog' | 'direct'} [itemType] - Tipo de item (catálogo referenciado o directo manual)
 * @property {string|null} [sourceId] - ID de la cuenta/fuente en public.playlists (si aplica)
 * @property {string|null} [providerId] - ID en el catálogo del proveedor (stream_id)
 * @property {string|null} [catalogItemId] - Alias de providerId
 * @property {'live' | 'movie' | 'series' | 'radio'} mediaType - Tipo de medio normalizado
 * @property {MediaType} [type] - Alias de mediaType
 * @property {string} name - Nombre legible del canal/película/episodio
 * @property {string|null} [originalName] - Nombre original sin alterar
 * @property {string} group - Categoría o grupo ("Deportes", "Acción", etc.)
 * @property {string} [categoryName] - Alias de group
 * @property {string|null} [logo] - URL del logo o poster
 * @property {string|null} [directName] - Nombre original de canal directo
 * @property {string|null} [directUrl] - URL real de canal directo
 * @property {string|null} [directGroup] - Categoría original de canal directo
 * @property {string|null} [directLogo] - Logo original de canal directo
 * @property {string} streamUrl - URL directa o resuelta de streaming
 * @property {string} [originalUrl] - URL original sin alterar
 * @property {string|null} [containerExtension] - Extensión del contenedor ('m3u8', 'ts', 'mp4', 'mkv')
 * @property {number|null} [duration] - Duración en segundos (-1 para live)
 * @property {string|null} [tvgId] - ID de guía EPG (tvg-id)
 * @property {string|null} [epgId] - Alias de tvgId
 * @property {string|null} [tvgName] - Nombre EPG (tvg-name)
 * @property {string|null} [epgName] - Alias de tvgName
 * @property {string|null} [country] - Código o nombre del país (tvg-country)
 * @property {string|null} [language] - Idioma del canal (tvg-language)
 * @property {Object|null} [headers] - Cabeceras HTTP personalizadas ({ userAgent, referrer, cookie })
 * @property {string|null} [httpUserAgent] - Alias de headers.userAgent
 * @property {string|null} [httpReferrer] - Alias de headers.referrer
 * @property {Object.<string, string>|null} [kodiProps] - Propiedades Kodi (#KODIPROP)
 * @property {Object.<string, string>|null} [extraAttributes] - Atributos M3U no reconocidos o adicionales preservados
 * @property {Object|null} [catchup] - Metadatos de catchup/timeshift
 * @property {string|null} [catchup.type] - Tipo de catchup ('default', 'append', 'shift')
 * @property {number|null} [catchup.days] - Días de archivo disponibles
 * @property {number|null} [catchup.hours] - Horas de archivo disponibles
 * @property {string|null} [catchup.source] - Plantilla de URL para timeshift
 * @property {number} [providerOrder] - Orden original en el proveedor o playlist
 * @property {number} [sortOrder] - Alias de providerOrder
 * @property {Object|null} [metadata] - Metadatos crudos del proveedor o parser
 * @property {Object|null} [rawMetadata] - Alias de metadata
 * @property {boolean} [isEnabled] - true = activo/visible, false = oculto
 * @property {number} [addedAt] - Timestamp de adición
 */

/**
 * Entrada cruda extraída del parseo de líneas #EXTINF
 * @typedef {Object} ParsedM3uEntry
 * @property {number} duration - Duración en segundos (-1 para live)
 * @property {string} name - Nombre extraído del título
 * @property {string} url - URL del stream
 * @property {Object.<string, string>} attributes - Atributos clave=valor de #EXTINF
 * @property {string|null} [group] - Valor de group-title o #EXTGRP
 * @property {string|null} [vlcUserAgent] - User-Agent de #EXTVLCOPT
 * @property {string|null} [vlcReferrer] - Referer de #EXTVLCOPT
 * @property {Object.<string, string>} kodiProps - Propiedades de #KODIPROP
 */

/**
 * Cabecera de un archivo M3U parseado (#EXTM3U)
 * @typedef {Object} M3uHeader
 * @property {string|null} [name] - Atributo name="..."
 * @property {string|null} [tvgUrl] - Atributo x-tvg-url o url-tvg
 * @property {Object.<string, string>} rawAttributes - Todos los atributos de la cabecera
 */

/**
 * Opciones para el generador M3U
 * @typedef {Object} GenerateM3uOptions
 * @property {string} [playlistName] - Nombre de la playlist para cabecera #EXTM3U
 * @property {string|null} [epgUrl] - URL XMLTV para x-tvg-url
 * @property {boolean} [includeCatchup] - Si se deben incluir atributos de catch-up (default: true)
 * @property {boolean} [includeVlcOpts] - Si se deben incluir directivas #EXTVLCOPT (default: true)
 * @property {boolean} [includeKodiProps] - Si se deben incluir directivas #KODIPROP (default: true)
 * @property {boolean} [includeExtGrp] - Si se debe añadir línea redundante #EXTGRP (default: false)
 * @property {boolean} [onlyEnabled] - Solo incluir items donde isEnabled !== false (default: true)
 */

/**
 * Estrategia de desduplicación
 * @typedef {'url' | 'catalog_id' | 'name_and_category' | 'epg_id'} DeduplicationStrategy
 */

/**
 * Modelo externo que devuelve librerías de terceros como iptv-m3u-playlist-parser.
 * M3uParserAdapter envuelve esta estructura para aislar a Lelouch.
 * @typedef {Object} ExternalPlaylistItem
 * @property {string} name
 * @property {string} url
 * @property {Object} tvg
 * @property {string|null} [tvg.id]
 * @property {string|null} [tvg.name]
 * @property {string|null} [tvg.logo]
 * @property {string|null} [tvg.country]
 * @property {string|null} [tvg.language]
 * @property {string|null} [tvg.rec]
 * @property {string|null} [tvg.shift]
 * @property {Object} group
 * @property {string|null} [group.title]
 * @property {Object} http
 * @property {string|null} [http.referrer]
 * @property {string|null} [http.userAgent]
 * @property {Object|null} [catchup]
 * @property {string|null} [catchup.type]
 * @property {string|number|null} [catchup.days]
 * @property {string|null} [catchup.source]
 * @property {Object.<string, string>|null} [kodiProps]
 * @property {string|null} [raw]
 * @property {number|null} [duration]
 */

/**
 * @typedef {Object} ExternalM3uPlaylist
 * @property {{ attrs: Record<string, string>, raw?: string }} header
 * @property {ExternalPlaylistItem[]} items
 */

/**
 * FASE 25: Métricas de rendimiento y consumo para listas M3U
 * @typedef {Object} M3uPerformanceMetrics
 * @property {number} sizeBytes - Tamaño en bytes del archivo M3U
 * @property {string} sizeMB - Tamaño formateado en Megabytes (ej. '12.45')
 * @property {number} entryCount - Cantidad de entradas (#EXTINF / items)
 * @property {number} parseTimeMs - Tiempo de parseo en milisegundos
 * @property {string} throughputMBps - Velocidad de procesamiento en MB/s
 * @property {number} entriesPerSecond - Canales procesados por segundo
 * @property {Object} memoryEstimate - Estimación de consumo de memoria
 * @property {number|null} memoryEstimate.jsHeapUsedMB - Heap usado en MB si está disponible
 * @property {number} memoryEstimate.estimatedObjectMemoryMB - Estimación de footprint en V8
 * @property {'OPTIMAL'|'MODERATE'|'LARGE_WARNING'|'CRITICAL_OVERSIZED'} status - Nivel de carga
 * @property {string} recommendation - Recomendación técnica accionable
 * @property {ExternalM3uPlaylist} [playlist] - Playlist parseada opcional
 */

/**
 * @typedef {Object} M3uLimitsConfig
 * @property {number} MAX_RECOMMENDED_SIZE_BYTES
 * @property {number} CRITICAL_SIZE_LIMIT_BYTES
 * @property {number} MAX_RECOMMENDED_ENTRIES
 * @property {number} CRITICAL_ENTRIES_LIMIT
 * @property {number} DEFAULT_FETCH_MAX_BYTES
 */

// Exportación para módulos ES
export const MediaTypeEnum = {
  LIVE: 'live',
  VOD: 'vod',
  SERIES: 'series',
  DIRECT: 'direct',
  RADIO: 'radio',
};


