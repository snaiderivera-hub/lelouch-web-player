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

export type DeduplicationStrategy = 'url' | 'catalog_id' | 'name_and_category' | 'epg_id';

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

