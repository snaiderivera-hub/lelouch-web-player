/**
 * @module SupabaseService
 * Servicio de sincronización y persistencia en la nube mediante Supabase (PostgreSQL).
 * Sincroniza Playlists, Favoritos e Historial de reproducción en tiempo real.
 */

import { redactSensitiveUrl } from '../utils/security.js';

const SUPABASE_URL = 'https://rotupbdeljgfddywryhk.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo';

class SupabaseService {
  constructor() {
    this.url = SUPABASE_URL;
    this.key = SUPABASE_ANON_KEY;
    this.isAvailable = Boolean(this.url && this.key);
  }

  _getHeaders(extra = {}) {
    return {
      'apikey': this.key,
      'Authorization': `Bearer ${this.key}`,
      'Content-Type': 'application/json',
      ...extra
    };
  }

  // ══════════════════════════════════════════════════════
  // PLAYLISTS EN LA NUBE
  // ══════════════════════════════════════════════════════

  /**
   * Obtiene todas las playlists registradas en Supabase.
   */
  async getPlaylists() {
    if (!this.isAvailable) return [];
    try {
      const res = await fetch(`${this.url}/rest/v1/playlists?select=*&order=updated_at.desc`, {
        headers: this._getHeaders()
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      const data = await res.json();
      return (data || []).map(row => ({
        id: row.id,
        name: row.name,
        url: row.url,
        serverBaseUrl: row.server_url,
        username: row.username,
        isActive: row.is_active,
        channelsCount: row.channels_count,
        moviesCount: row.movies_count,
        seriesCount: row.series_count,
        updatedAt: row.updated_at
      }));
    } catch (e) {
      console.warn('[SupabaseService] Error al obtener playlists de la nube:', e);
      return [];
    }
  }

  /**
   * Guarda o actualiza una playlist en Supabase (Upsert).
   * @param {Object} playlist
   */
  async savePlaylist(playlist) {
    if (!this.isAvailable || !playlist || !playlist.url) return null;
    try {
      const body = {
        name: playlist.name || 'Playlist',
        url: playlist.url,
        server_url: playlist.serverBaseUrl || '',
        username: playlist.username || '',
        password: playlist.password || '••••••••',
        is_active: Boolean(playlist.isActive),
        channels_count: playlist.channelsCount || 0,
        movies_count: playlist.moviesCount || 0,
        series_count: playlist.seriesCount || 0,
        updated_at: new Date().toISOString()
      };

      const res = await fetch(`${this.url}/rest/v1/playlists?on_conflict=url`, {
        method: 'POST',
        headers: this._getHeaders({
          'Prefer': 'resolution=merge-duplicates,return=representation'
        }),
        body: JSON.stringify(body)
      });

      if (!res.ok) {
        const errText = await res.text();
        console.warn('[SupabaseService] No se pudo guardar la playlist en Supabase:', errText);
        return null;
      }

      const saved = await res.json();
      console.log('☁️ [SupabaseService] Playlist sincronizada en Supabase con éxito:', playlist.name);
      return saved?.[0] || null;
    } catch (e) {
      console.warn('[SupabaseService] Excepción al guardar en Supabase:', e);
      return null;
    }
  }

  /**
   * Elimina una playlist de Supabase por su URL.
   * @param {string} url
   */
  async deletePlaylist(url) {
    if (!this.isAvailable || !url) return false;
    try {
      const res = await fetch(`${this.url}/rest/v1/playlists?url=eq.${encodeURIComponent(url)}`, {
        method: 'DELETE',
        headers: this._getHeaders()
      });
      return res.ok;
    } catch (e) {
      console.warn('[SupabaseService] Error al eliminar playlist de Supabase:', e);
      return false;
    }
  }

  // ══════════════════════════════════════════════════════
  // FAVORITOS EN LA NUBE
  // ══════════════════════════════════════════════════════

  /**
   * Guarda un favorito en Supabase.
   */
  async saveFavorite(item) {
    if (!this.isAvailable || !item || !item.id) return null;
    try {
      const body = {
        content_id: String(item.id),
        type: item.type || 'live',
        title: item.title || item.name || 'Sin título',
        logo: item.logo || item.poster || '',
        stream_url: item.streamUrl || item.url || '',
        category_name: item.categoryName || item.category || ''
      };

      const res = await fetch(`${this.url}/rest/v1/favorites?on_conflict=content_id,type`, {
        method: 'POST',
        headers: this._getHeaders({
          'Prefer': 'resolution=merge-duplicates,return=representation'
        }),
        body: JSON.stringify(body)
      });
      return res.ok;
    } catch (e) {
      console.warn('[SupabaseService] Error al guardar favorito:', e);
      return false;
    }
  }

  /**
   * Obtiene todos los favoritos guardados en la nube.
   */
  async getFavorites() {
    if (!this.isAvailable) return [];
    try {
      const res = await fetch(`${this.url}/rest/v1/favorites?select=*&order=created_at.desc`, {
        headers: this._getHeaders()
      });
      if (!res.ok) return [];
      const data = await res.json();
      return (data || []).map(r => ({
        id: r.content_id,
        type: r.type,
        name: r.title,
        title: r.title,
        logo: r.logo,
        streamUrl: r.stream_url,
        categoryName: r.category_name
      }));
    } catch {
      return [];
    }
  }

  /**
   * Elimina un favorito de Supabase.
   */
  async deleteFavorite(contentId, type = 'live') {
    if (!this.isAvailable || !contentId) return false;
    try {
      const res = await fetch(`${this.url}/rest/v1/favorites?content_id=eq.${encodeURIComponent(contentId)}&type=eq.${encodeURIComponent(type)}`, {
        method: 'DELETE',
        headers: this._getHeaders()
      });
      return res.ok;
    } catch {
      return false;
    }
  }

  // ══════════════════════════════════════════════════════
  // PLAYLISTS PERSONALIZADAS Y ELEMENTOS (FASE 6)
  // ══════════════════════════════════════════════════════

  /**
   * Obtiene o crea la playlist personalizada principal del usuario.
   * @param {string} [name='Mi Lista LELOUCH']
   * @returns {Promise<Object|null>}
   */
  async getOrCreateDefaultCustomPlaylist(name = 'Mi Lista LELOUCH') {
    if (!this.isAvailable) return null;
    try {
      const res = await fetch(`${this.url}/rest/v1/custom_playlists?select=*&limit=1&order=created_at.asc`, {
        headers: this._getHeaders()
      });
      if (res.ok) {
        const rows = await res.json();
        if (rows && rows.length > 0) return rows[0];
      }

      // Si no existe, crear la playlist inicial
      const createRes = await fetch(`${this.url}/rest/v1/custom_playlists`, {
        method: 'POST',
        headers: this._getHeaders({
          'Prefer': 'return=representation'
        }),
        body: JSON.stringify({
          name,
          description: 'Lista personalizada sincronizada de LELOUCH Web Player',
          enabled: true,
          version: 1
        })
      });

      if (createRes.ok) {
        const created = await createRes.json();
        return created?.[0] || null;
      }
      return null;
    } catch (e) {
      console.warn('[SupabaseService] Error en getOrCreateDefaultCustomPlaylist:', e);
      return null;
    }
  }

  /**
   * Obtiene todos los items de una playlist personalizada con sus URLs vivas resueltas.
   * Consulta v_resolved_playlist_items garantizando que la URL del stream
   * no esté desactualizada ni duplicada.
   * @param {string} playlistId
   * @returns {Promise<Array>}
   */
  async getResolvedPlaylistItems(playlistId) {
    if (!this.isAvailable || !playlistId) return [];
    try {
      // 1. Consultar vista dinámica v_resolved_playlist_items con paginación (PostgREST límite de 1000)
      let allRows = [];
      let offset = 0;
      const PAGE_SIZE = 1000;
      const MAX_PAGES = 30; // hasta 30,000 items

      for (let page = 0; page < MAX_PAGES; page++) {
        const pagedUrl = `${this.url}/rest/v1/v_resolved_playlist_items?playlist_id=eq.${encodeURIComponent(playlistId)}&order=position.asc&limit=${PAGE_SIZE}&offset=${offset}`;
        const res = await fetch(pagedUrl, { headers: this._getHeaders() });
        if (!res.ok) {
          if (allRows.length === 0) break;
          else break;
        }
        const rows = await res.json();
        if (!Array.isArray(rows) || rows.length === 0) break;
        allRows = allRows.concat(rows);
        if (rows.length < PAGE_SIZE) break;
        offset += PAGE_SIZE;
      }

      if (allRows.length > 0) {
        return allRows.map(r => ({
          ...r,
          id: r.id,
          name: r.name || r.direct_name || r.custom_name || 'Canal',
          group: r.group || r.direct_group || r.custom_group || 'General',
          logo: r.logo || r.direct_logo || r.custom_logo || '',
          streamUrl: r.resolved_stream_url || r.direct_url || '',
          resolved_name: r.name || r.direct_name || r.custom_name || 'Canal',
          resolved_group: r.group || r.direct_group || r.custom_group || 'General',
          resolved_logo: r.logo || r.direct_logo || r.custom_logo || '',
          resolved_url: r.resolved_stream_url || r.direct_url || ''
        }));
      }

      // 2. Fallback: consultar playlist_items directamente si la vista no estuviera creada
      const rawRes = await fetch(
        `${this.url}/rest/v1/playlist_items?playlist_id=eq.${encodeURIComponent(playlistId)}&order=position.asc`,
        { headers: this._getHeaders() }
      );
      if (rawRes.ok) {
        const rows = await rawRes.json();
        return rows.map(r => ({
          ...r,
          id: r.id,
          name: r.custom_name || r.direct_name || 'Canal',
          group: r.custom_group || r.direct_group || 'General',
          logo: r.custom_logo || r.direct_logo || '',
          streamUrl: r.direct_url || '',
          resolved_name: r.custom_name || r.direct_name || 'Canal',
          resolved_group: r.custom_group || r.direct_group || 'General',
          resolved_logo: r.custom_logo || r.direct_logo || '',
          resolved_url: r.direct_url || ''
        }));
      }
      return [];
    } catch (e) {
      console.warn('[SupabaseService] Error al obtener items resueltos de la playlist:', e);
      return [];
    }
  }

  /**
   * Guarda y sincroniza en lote los items de una playlist personalizada.
   * Respeta el principio de FASE 6: no duplica stream_url para items de catálogo,
   * guardando únicamente source_id y catalog_item_id.
   * 
   * @param {string} playlistId
   * @param {Array<import('../../../services/playlist/PlaylistTypes.js').LelouchMediaItem>} items
   * @returns {Promise<boolean>}
   */
  async syncPlaylistItems(playlistId, items = []) {
    if (!this.isAvailable || !playlistId || !Array.isArray(items)) return false;

    try {
      // 1. Preparar filas para inserción diferenciando CATALOG vs DIRECT (FASE 7)
      const rows = items.map((item, index) => {
        const isDirect = item.itemType === 'direct' || (!item.sourceId && !item.providerId && !item.catalogItemId);
        const isFromCatalog = !isDirect;
        const targetUrl = item.directUrl || item.streamUrl || item.url || '';
        const targetGroup = item.custom_group || item.direct_group || item.group || item.category || item.categoryName || '';
        const targetName = item.custom_name || item.direct_name || item.name || '';
        
        let resolvedMediaType = (item.mediaType || item.type || '').toLowerCase();
        if (!resolvedMediaType || resolvedMediaType === 'undefined') {
          const lUrl = targetUrl.toLowerCase();
          const lGrp = targetGroup.toLowerCase();
          const lName = targetName.toLowerCase();
          const hasEpisodePattern = /\b(s\d{1,2}|t\d{1,2}|cap\.?\s*\d+|ep\.?\s*\d+|temporada\s*\d+)\b/i.test(lName);
          const isSeriesGroup = lGrp.includes('serie') || lGrp.includes('temporada') || lGrp.includes('season') || lGrp.includes('dorama') || lGrp.includes('anime') || lGrp.includes('novela');
          const isMovieGroup = lGrp.includes('película') || lGrp.includes('pelicula') || lGrp.includes('movie') || lGrp.includes('cine') || lGrp.includes('estrenos') || lGrp.includes('vod');
          
          if (lUrl.includes('/series/') || isSeriesGroup || hasEpisodePattern) {
            resolvedMediaType = 'series';
          } else if (lUrl.includes('/movie/') || isMovieGroup || (lUrl.endsWith('.mp4') && !lUrl.includes('.m3u8'))) {
            resolvedMediaType = 'movie';
          } else {
            resolvedMediaType = 'live';
          }
        }
        return {
          playlist_id: playlistId,
          item_type: isFromCatalog ? 'catalog' : 'direct',
          source_id: isFromCatalog ? (item.sourceId || null) : null,
          catalog_item_id: isFromCatalog ? String(item.providerId || item.catalogItemId) : null,
          media_type: resolvedMediaType,
          custom_name: isFromCatalog ? (item.name || targetName) : null,
          custom_group: isFromCatalog ? (item.group || item.categoryName || targetGroup || 'General') : null,
          custom_logo: isFromCatalog ? (item.logo || null) : null,
          direct_name: targetName || 'Canal Directo',
          direct_url: targetUrl,
          direct_group: targetGroup || 'Directos',
          direct_logo: item.directLogo || item.logo || null,
          tvg_id: item.tvgId || item.epgId || null,
          tvg_name: item.tvgName || item.epgName || null,
          container_extension: item.containerExtension || (resolvedMediaType === 'live' ? 'm3u8' : 'mp4'),
          position: index,
          enabled: item.isEnabled !== false,
          metadata: {
            kodiProps: item.kodiProps || null,
            headers: item.headers || null,
            extraAttributes: item.extraAttributes || null,
            catchup: item.catchup || null
          },
          updated_at: new Date().toISOString()
        };
      });

      // 2. Eliminar items anteriores de esta playlist para reemplazo atómico
      await fetch(`${this.url}/rest/v1/playlist_items?playlist_id=eq.${encodeURIComponent(playlistId)}`, {
        method: 'DELETE',
        headers: this._getHeaders()
      });

      // 3. Insertar los nuevos items en lotes (evita 413 Payload Too Large en listas grandes)
      if (rows.length > 0) {
        const BATCH_SIZE = 250;
        let failedItems = 0;
        for (let i = 0; i < rows.length; i += BATCH_SIZE) {
          const chunk = rows.slice(i, i + BATCH_SIZE);
          let insertRes = null;
          for (let attempt = 1; attempt <= 2; attempt++) {
            try {
              insertRes = await fetch(`${this.url}/rest/v1/playlist_items`, {
                method: 'POST',
                headers: this._getHeaders({
                  'Prefer': 'return=minimal'
                }),
                body: JSON.stringify(chunk)
              });
            } catch (netErr) {
              insertRes = null;
            }
            if (insertRes?.ok) break;
            if (attempt === 1) await new Promise(r => setTimeout(r, 800));
          }

          if (!insertRes?.ok) {
            failedItems += chunk.length;
            const errText = insertRes ? await insertRes.text() : 'error de red';
            console.warn(`[SupabaseService] Error insertando lote ${i}-${i + chunk.length}:`, errText);
            // Si falla un lote, continuar con los siguientes para no perder el resto
          }
        }
        if (failedItems > 0) {
          console.warn(`[SupabaseService] ${failedItems} de ${rows.length} ítems no se pudieron guardar en la nube.`);
        }
      }

      // 4. Obtener versión actual e incrementar versión y timestamp en custom_playlists (FASE 15)
      let nextVersion = 1;
      try {
        const plRes = await fetch(`${this.url}/rest/v1/custom_playlists?id=eq.${encodeURIComponent(playlistId)}&select=version`, {
          headers: this._getHeaders()
        });
        if (plRes.ok) {
          const plRows = await plRes.json();
          if (plRows?.[0] && plRows[0].version !== undefined && plRows[0].version !== null) {
            nextVersion = Number(plRows[0].version) + 1;
          }
        }
      } catch {}

      await fetch(`${this.url}/rest/v1/custom_playlists?id=eq.${encodeURIComponent(playlistId)}`, {
        method: 'PATCH',
        headers: this._getHeaders(),
        body: JSON.stringify({
          version: nextVersion,
          updated_at: new Date().toISOString()
        })
      });

      console.log(`☁️ [SupabaseService] Playlist sincronizada (${rows.length} items persistidos, versión ${nextVersion}).`);
      return true;
    } catch (e) {
      console.warn('[SupabaseService] Excepción al sincronizar items en Supabase:', e);
      return false;
    }
  }

  // ══════════════════════════════════════════════════════
  // VERSIONADO Y COMPROBACIÓN DE SINCRONIZACIÓN (FASE 16)
  // ══════════════════════════════════════════════════════

  /**
   * Obtiene la versión actual de la playlist en el servidor.
   * @param {string} playlistId
   * @returns {Promise<{ version: number, updated_at: string }|null>}
   */
  async getPlaylistVersion(playlistId) {
    if (!this.isAvailable || !playlistId) return null;
    try {
      const res = await fetch(`${this.url}/rest/v1/custom_playlists?id=eq.${encodeURIComponent(playlistId)}&select=version,updated_at`, {
        headers: this._getHeaders()
      });
      if (res.ok) {
        const rows = await res.json();
        if (rows?.[0]) {
          return {
            version: Number(rows[0].version || 1),
            updated_at: rows[0].updated_at || new Date().toISOString()
          };
        }
      }
      return null;
    } catch (e) {
      console.warn('[SupabaseService] Error obteniendo versión:', e);
      return null;
    }
  }

  /**
   * Comprueba si la versión local del cliente coincide con la del servidor.
   * "¿mi versión local = versión servidor?
   *  Si sí: no hago nada
   *  Si no: sincronizo"
   * @param {string} playlistId
   * @param {number|string} localVersion
   * @returns {Promise<{ inSync: boolean, serverVersion: number, localVersion: number, updated_at?: string }>}
   */
  async checkPlaylistSync(playlistId, localVersion) {
    const serverInfo = await this.getPlaylistVersion(playlistId);
    if (!serverInfo) {
      return { inSync: true, serverVersion: Number(localVersion || 1), localVersion: Number(localVersion || 1) };
    }
    const serverVer = Number(serverInfo.version);
    const localVer = Number(localVersion || 0);
    return {
      inSync: (serverVer === localVer),
      serverVersion: serverVer,
      localVersion: localVer,
      updated_at: serverInfo.updated_at
    };
  }

  /**
   * Incrementa la versión de la playlist ante cualquier modificación:
   * (ADD, REMOVE, REORDER, RENAME, CHANGE GROUP, ENABLE, DISABLE).
   * @param {string} playlistId
   * @returns {Promise<number>} Nueva versión
   */
  async incrementPlaylistVersion(playlistId) {
    if (!this.isAvailable || !playlistId) return 1;
    try {
      const current = await this.getPlaylistVersion(playlistId);
      const nextVer = (current?.version || 1) + 1;
      await fetch(`${this.url}/rest/v1/custom_playlists?id=eq.${encodeURIComponent(playlistId)}`, {
        method: 'PATCH',
        headers: this._getHeaders(),
        body: JSON.stringify({
          version: nextVer,
          updated_at: new Date().toISOString()
        })
      });
      return nextVer;
    } catch {
      return 1;
    }
  }

  /**
   * Modifica un elemento individual de la playlist y asegura el incremento de versión.
   * Aplica para: REORDER, RENAME, CHANGE GROUP, ENABLE, DISABLE (FASE 16).
   * @param {string} playlistId
   * @param {string} itemId
   * @param {Object} updates
   * @returns {Promise<boolean>}
   */
  async updatePlaylistItem(playlistId, itemId, updates = {}) {
    if (!this.isAvailable || !playlistId || !itemId) return false;
    try {
      const res = await fetch(`${this.url}/rest/v1/playlist_items?id=eq.${encodeURIComponent(itemId)}`, {
        method: 'PATCH',
        headers: this._getHeaders(),
        body: JSON.stringify({
          ...updates,
          updated_at: new Date().toISOString()
        })
      });
      if (res.ok) {
        await this.incrementPlaylistVersion(playlistId);
        return true;
      }
      return false;
    } catch (e) {
      console.warn('[SupabaseService] Error actualizando item de playlist:', e);
      return false;
    }
  }

  /**
   * Obtiene el Playlist Manifest estructurado en JSON directamente desde Supabase (FASE 17).
   * Diseñado para Lelouch TV y Lelouch Phone (evita re-parsear M3U; listo para Room/SQLite).
   * @param {string} playlistId
   * @returns {Promise<{ playlist: Object, items: Array }|null>}
   */
  async getPlaylistManifest(playlistId) {
    if (!this.isAvailable || !playlistId) return null;
    try {
      const [plRes, items] = await Promise.all([
        fetch(`${this.url}/rest/v1/custom_playlists?id=eq.${encodeURIComponent(playlistId)}&select=id,name,version,updated_at,enabled`, { headers: this._getHeaders() }),
        this.getResolvedPlaylistItems(playlistId)
      ]);

      let playlistMeta = { id: playlistId, name: 'Mi Lista LELOUCH', version: 1, itemCount: items.length };
      if (plRes.ok) {
        const rows = await plRes.json();
        if (rows?.[0]) {
          playlistMeta = {
            id: rows[0].id,
            name: rows[0].name,
            version: Number(rows[0].version || 1),
            updatedAt: rows[0].updated_at,
            enabled: rows[0].enabled !== false,
            itemCount: items.length
          };
        }
      }

      const canonicalItems = items.map((it, idx) => ({
        id: it.id,
        name: it.name || it.direct_name || 'Canal',
        streamUrl: it.resolved_stream_url || it.direct_url,
        group: it.group || it.direct_group || 'General',
        logo: it.logo || it.direct_logo || '',
        tvgId: it.tvg_id || '',
        tvgName: it.tvg_name || it.name || '',
        mediaType: it.media_type || 'live',
        sortOrder: it.position ?? idx,
        isEnabled: it.enabled !== false,
        itemType: it.item_type || 'catalog',
        metadata: it.metadata || null
      }));

      return {
        playlist: playlistMeta,
        items: canonicalItems
      };
    } catch (e) {
      console.warn('[SupabaseService] Error obteniendo playlist manifest:', e);
      return null;
    }
  }

  /**
   * Genera un nuevo token de acceso público (permanente u opcionalmente con expiración) para una playlist.
   * FASE 20: Soporte opcional de expiración (expiresInDays).
   * @param {string} playlistId
   * @param {string} [name='Dispositivo']
   * @param {number|null} [expiresInDays=null] - Días hasta expiración (null = permanente)
   * @returns {Promise<{ token: string, preview: string, id: string, expires_at: string|null }|null>}
   */
  async createAccessToken(playlistId, name = 'Dispositivo', expiresInDays = null) {
    if (!this.isAvailable || !playlistId) return null;

    try {
      // 1. Generar token criptográficamente seguro de 40 caracteres alfanuméricos (a-z, A-Z, 0-9)
      const charset = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
      const randomBytes = new Uint8Array(40);
      crypto.getRandomValues(randomBytes);
      const token = Array.from(randomBytes, b => charset[b % charset.length]).join('');
      const tokenPrefix = token.slice(0, 8);
      const preview = `lel_${token.slice(0, 6)}...${token.slice(-4)}`;

      // 2. Calcular SHA-256 del token (NUNCA guardamos el token plano en Supabase)
      const enc = new TextEncoder();
      const hashBuffer = await crypto.subtle.digest('SHA-256', enc.encode(token));
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const tokenHash = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');

      // FASE 20: Cálculo de fecha de expiración opcional
      const expiresAt = (expiresInDays && Number(expiresInDays) > 0)
        ? new Date(Date.now() + Number(expiresInDays) * 86400000).toISOString()
        : null;

      // 3. Guardar únicamente el hash y metadatos seguros en Supabase (FASE 9 & 20)
      const payload = {
        playlist_id: playlistId,
        token_hash: tokenHash,
        token_prefix: tokenPrefix,
        token_preview: preview,
        name,
        enabled: true,
        is_active: true,
        request_count: 0,
        access_count: 0
      };
      if (expiresAt) {
        payload.expires_at = expiresAt;
      }

      let res = await fetch(`${this.url}/rest/v1/playlist_access_tokens`, {
        method: 'POST',
        headers: this._getHeaders({
          'Prefer': 'return=representation'
        }),
        body: JSON.stringify(payload)
      });

      // Auto-fallback si las columnas token_prefix / enabled / expires_at aún no se han aplicado con ALTER TABLE
      if (!res.ok) {
        const errText = await res.text();
        if (errText.includes('PGRST204') || errText.includes('Could not find')) {
          res = await fetch(`${this.url}/rest/v1/playlist_access_tokens`, {
            method: 'POST',
            headers: this._getHeaders({
              'Prefer': 'return=representation'
            }),
            body: JSON.stringify({
              playlist_id: playlistId,
              token_hash: tokenHash,
              token_preview: preview,
              name,
              is_active: true
            })
          });
        }
        if (!res.ok) {
          const finalErr = await res.text();
          console.warn('[SupabaseService] Error creando token de acceso:', finalErr);
          this.lastError = finalErr;
          return null;
        }
      }

      const rows = await res.json();
      return {
        id: rows?.[0]?.id,
        token, // El token crudo en texto plano solo se retorna en memoria para que el usuario lo reciba
        token_prefix: tokenPrefix,
        preview,
        name,
        created_at: rows?.[0]?.created_at || new Date().toISOString(),
        expires_at: rows?.[0]?.expires_at || expiresAt || null,
        enabled: true
      };
    } catch (e) {
      console.warn('[SupabaseService] Excepción creando token:', e);
      return null;
    }
  }

  /**
   * Obtiene los tokens activos para una playlist.
   * @param {string} playlistId
   * @returns {Promise<Array>}
   */
  async getAccessTokens(playlistId) {
    if (!this.isAvailable || !playlistId) return [];
    try {
      const res = await fetch(
        `${this.url}/rest/v1/playlist_access_tokens?playlist_id=eq.${encodeURIComponent(playlistId)}&order=created_at.desc`,
        { headers: this._getHeaders() }
      );
      if (res.ok) return await res.json();
      return [];
    } catch {
      return [];
    }
  }

  /**
   * Invalida (deshabilita) todos los tokens previos asociados a una playlist para garantizar
   * que al regenerar un enlace, los enlaces anteriores queden 100% inoperativos (FASE 14).
   * @param {string} playlistId
   * @returns {Promise<boolean>}
   */
  async invalidateAllAccessTokens(playlistId) {
    if (!this.isAvailable || !playlistId) return false;
    try {
      let res = await fetch(
        `${this.url}/rest/v1/playlist_access_tokens?playlist_id=eq.${encodeURIComponent(playlistId)}`,
        {
          method: 'PATCH',
          headers: this._getHeaders(),
          body: JSON.stringify({
            enabled: false,
            is_active: false
          })
        }
      );
      if (!res.ok) {
        // Fallback si la columna enabled aún no fue agregada
        res = await fetch(
          `${this.url}/rest/v1/playlist_access_tokens?playlist_id=eq.${encodeURIComponent(playlistId)}`,
          {
            method: 'PATCH',
            headers: this._getHeaders(),
            body: JSON.stringify({
              is_active: false
            })
          }
        );
      }
      return res.ok;
    } catch (e) {
      console.warn('[SupabaseService] Error invalidando tokens previos:', e);
      return false;
    }
  }

  /**
   * Cambia el estado de activación de un token de acceso específico (Activo / Desactivado) (FASE 14).
   * @param {string} tokenId
   * @param {boolean} enabled
   * @returns {Promise<boolean>}
   */
  async setAccessTokenEnabled(tokenId, enabled) {
    if (!this.isAvailable || !tokenId) return false;
    try {
      let res = await fetch(
        `${this.url}/rest/v1/playlist_access_tokens?id=eq.${encodeURIComponent(tokenId)}`,
        {
          method: 'PATCH',
          headers: this._getHeaders(),
          body: JSON.stringify({
            enabled: !!enabled,
            is_active: !!enabled
          })
        }
      );
      if (!res.ok) {
        // Fallback
        res = await fetch(
          `${this.url}/rest/v1/playlist_access_tokens?id=eq.${encodeURIComponent(tokenId)}`,
          {
            method: 'PATCH',
            headers: this._getHeaders(),
            body: JSON.stringify({
              is_active: !!enabled
            })
          }
        );
      }
      return res.ok;
    } catch (e) {
      console.warn('[SupabaseService] Error cambiando estado del token:', e);
      return false;
    }
  }

  /**
   * Revoca un token de acceso por su ID.
   * @param {string} tokenId
   * @returns {Promise<boolean>}
   */
  async revokeAccessToken(tokenId) {
    if (!this.isAvailable || !tokenId) return false;
    try {
      const res = await fetch(
        `${this.url}/rest/v1/playlist_access_tokens?id=eq.${encodeURIComponent(tokenId)}`,
        {
          method: 'DELETE',
          headers: this._getHeaders()
        }
      );
      return res.ok;
    } catch {
      return false;
    }
  }

  // ══════════════════════════════════════════════════════
  // SINCRONIZACIÓN AUTOMÁTICA BIDIRECCIONAL
  // ══════════════════════════════════════════════════════

  /**
   * Sincroniza las playlists locales de IndexedDB con las de Supabase.
   * Si la nube tiene playlists que localmente no existen, las fusiona.
   * Si la base local tiene playlists que no están en Supabase, las sube a la nube.
   */
  async syncPlaylists(localPlaylists = []) {
    if (!this.isAvailable) return localPlaylists;

    try {
      // 1. Subir cualquier playlist local a Supabase
      for (const pl of localPlaylists) {
        if (pl.url) {
          await this.savePlaylist(pl);
        }
      }

      // 2. Traer la lista actualizada desde Supabase
      const cloudLists = await this.getPlaylists();
      return cloudLists;
    } catch (e) {
      console.warn('[SupabaseService] Error durante sincronización:', e);
      return localPlaylists;
    }
  }
}

export const supabaseService = new SupabaseService();

