/**
 * @module PlaylistTypes
 * Definiciones de tipos e interfaces TypeScript para la capa de gestión de playlists M3U.
 */

export type MediaType = 'live' | 'movie' | 'series' | 'radio' | 'vod' | 'direct';

export interface LelouchMediaItem {
  id: string;
  itemType?: 'catalog' | 'direct';
  sourceId?: string | null;
  providerId?: string | null;
  catalogItemId?: string | null;

  mediaType: 'live' | 'movie' | 'series' | 'radio';
  type?: MediaType;

  name: string;
  originalName?: string | null;

  group: string;
  categoryName?: string;
  logo?: string | null;

  // Campos específicos para cuando itemType === 'direct'
  directName?: string | null;
  directUrl?: string | null;
  directGroup?: string | null;
  directLogo?: string | null;

  streamUrl: string;
  originalUrl?: string;
  containerExtension?: string | null;
  duration?: number | null;

  tvgId?: string | null;
  epgId?: string | null;
  tvgName?: string | null;
  epgName?: string | null;

  language?: string | null;
  country?: string | null;

  headers?: {
    userAgent?: string | null;
    referrer?: string | null;
    cookie?: string | null;
    [key: string]: any;
  } | null;
  httpUserAgent?: string | null;
  httpReferrer?: string | null;

  kodiProps?: Record<string, string> | null;
  extraAttributes?: Record<string, string> | null;

  catchup?: {
    type?: string | null;
    days?: number | null;
    hours?: number | null;
    source?: string | null;
  } | null;

  providerOrder?: number;
  sortOrder?: number;

  metadata?: any;
  rawMetadata?: any;

  isEnabled?: boolean;
  addedAt?: number;
}

export interface ParsedM3uEntry {
  duration: number;
  name: string;
  url: string;
  attributes: Record<string, string>;
  group?: string | null;
  vlcUserAgent?: string | null;
  vlcReferrer?: string | null;
  kodiProps: Record<string, string>;
}

export interface M3uHeader {
  name?: string | null;
  tvgUrl?: string | null;
  rawAttributes: Record<string, string>;
}

export interface GenerateM3uOptions {
  playlistName?: string;
  epgUrl?: string | null;
  includeCatchup?: boolean;
  includeVlcOpts?: boolean;
  includeKodiProps?: boolean;
  includeExtGrp?: boolean;
  onlyEnabled?: boolean;
}

/**
 * Estrategias de deduplicación de Lelouch (FASE 23).
 * 'lelouch_multi_factor' (por defecto): considera provider, tvg-id, stream URL normalizada, nombre y grupo.
 * Evita decidir automáticamente que dos canales son idénticos solo por llamarse igual si provienen de distintas fuentes.
 */
export type DeduplicationStrategy = 
  | 'lelouch_multi_factor' 
  | 'strict_provider' 
  | 'normalized_stream_url' 
  | 'url' 
  | 'catalog_id' 
  | 'name_and_category' 
  | 'epg_id' 
  | 'cross_source_epg';

export interface ItemComparisonResult {
  isDuplicate: boolean;
  sameSource: boolean;
  sameNormalizedUrl: boolean;
  sameTvgId: boolean;
  sameName: boolean;
  sameGroup: boolean;
  confidence: 'exact_stream' | 'same_provider_item' | 'identical_fingerprint' | 'cross_source_distinct_kept' | 'distinct';
  reason: string;
}

/**
 * Modelo externo que devuelve librerías de terceros como iptv-m3u-playlist-parser.
 * M3uParserAdapter envuelve esta estructura para que Lelouch no dependa de ella.
 */
export interface ExternalPlaylistItem {
  name: string;
  url: string;
  tvg: {
    id?: string | null;
    name?: string | null;
    logo?: string | null;
    country?: string | null;
    language?: string | null;
    rec?: string | null;
    shift?: string | null;
  };
  group: {
    title?: string | null;
  };
  http: {
    referrer?: string | null;
    userAgent?: string | null;
    cookie?: string | null;
    [key: string]: string | null | undefined;
  };
  catchup?: {
    type?: string | null;
    days?: string | number | null;
    hours?: string | number | null;
    source?: string | null;
  } | null;
  kodiProps?: Record<string, string> | null;
  extraAttributes?: Record<string, string> | null;
  raw?: string;
  duration?: number;
}

export interface ExternalM3uPlaylist {
  header: {
    attrs: Record<string, string>;
    raw?: string;
  };
  items: ExternalPlaylistItem[];
}

/**
 * FASE 25: Métricas de rendimiento y consumo para listas M3U
 */
export interface M3uPerformanceMetrics {
  sizeBytes: number;
  sizeMB: string;
  entryCount: number;
  parseTimeMs: number;
  throughputMBps: string;
  entriesPerSecond: number;
  memoryEstimate: {
    jsHeapUsedMB: number | null;
    estimatedObjectMemoryMB: number;
  };
  status: 'OPTIMAL' | 'MODERATE' | 'LARGE_WARNING' | 'CRITICAL_OVERSIZED';
  recommendation: string;
  playlist?: ExternalM3uPlaylist;
}

export interface M3uLimitsConfig {
  MAX_RECOMMENDED_SIZE_BYTES: number;
  CRITICAL_SIZE_LIMIT_BYTES: number;
  MAX_RECOMMENDED_ENTRIES: number;
  CRITICAL_ENTRIES_LIMIT: number;
  DEFAULT_FETCH_MAX_BYTES: number;
}

