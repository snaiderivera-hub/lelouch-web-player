/**
 * @module SupabaseService
 * Servicio de sincronización y persistencia en la nube mediante Supabase (PostgreSQL).
 * Sincroniza Playlists, Favoritos e Historial de reproducción en tiempo real.
 */

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
      // 1. Intentar consultar vista dinámica v_resolved_playlist_items
      const res = await fetch(
        `${this.url}/rest/v1/v_resolved_playlist_items?playlist_id=eq.${encodeURIComponent(playlistId)}&order=position.asc`,
        { headers: this._getHeaders() }
      );
      if (res.ok) {
        const rows = await res.json();
        return rows.map(r => ({
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
        return {
          playlist_id: playlistId,
          item_type: isFromCatalog ? 'catalog' : 'direct',
          source_id: isFromCatalog ? (item.sourceId || null) : null,
          catalog_item_id: isFromCatalog ? String(item.providerId || item.catalogItemId) : null,
          media_type: item.mediaType || item.type || 'live',
          custom_name: isFromCatalog ? item.name : null,
          custom_group: isFromCatalog ? (item.group || item.categoryName || 'General') : null,
          custom_logo: isFromCatalog ? (item.logo || null) : null,
          direct_name: isDirect ? (item.directName || item.name || 'Canal Directo') : null,
          direct_url: isDirect ? (item.directUrl || item.streamUrl || item.url) : null,
          direct_group: isDirect ? (item.directGroup || item.group || item.category || 'Directos') : null,
          direct_logo: isDirect ? (item.directLogo || item.logo || null) : null,
          tvg_id: item.tvgId || item.epgId || null,
          tvg_name: item.tvgName || item.epgName || null,
          container_extension: item.containerExtension || 'm3u8',
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

      // 3. Insertar los nuevos items en lote
      if (rows.length > 0) {
        const insertRes = await fetch(`${this.url}/rest/v1/playlist_items`, {
          method: 'POST',
          headers: this._getHeaders({
            'Prefer': 'return=minimal'
          }),
          body: JSON.stringify(rows)
        });

        if (!insertRes.ok) {
          const errText = await insertRes.text();
          console.warn('[SupabaseService] Error insertando items de la playlist:', errText);
          return false;
        }
      }

      // 4. Actualizar versión y timestamp en custom_playlists
      await fetch(`${this.url}/rest/v1/custom_playlists?id=eq.${encodeURIComponent(playlistId)}`, {
        method: 'PATCH',
        headers: this._getHeaders(),
        body: JSON.stringify({
          updated_at: new Date().toISOString()
        })
      });

      console.log(`☁️ [SupabaseService] Playlist sincronizada (${rows.length} items persistidos).`);
      return true;
    } catch (e) {
      console.warn('[SupabaseService] Excepción al sincronizar items en Supabase:', e);
      return false;
    }
  }

  /**
   * Genera un nuevo token de acceso público permanente para una playlist.
   * @param {string} playlistId
   * @param {string} [name='Dispositivo']
   * @returns {Promise<{ token: string, preview: string, id: string }|null>}
   */
  async createAccessToken(playlistId, name = 'Dispositivo') {
    if (!this.isAvailable || !playlistId) return null;

    try {
      // 1. Generar token criptográficamente seguro de 40 caracteres alfanuméricos (a-z, A-Z, 0-9)
      const charset = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
      const randomBytes = new Uint8Array(40);
      crypto.getRandomValues(randomBytes);
      const token = Array.from(randomBytes, b => charset[b % charset.length]).join('');
      const preview = `lel_${token.slice(0, 6)}...${token.slice(-4)}`;

      // 2. Calcular SHA-256 del token
      const enc = new TextEncoder();
      const hashBuffer = await crypto.subtle.digest('SHA-256', enc.encode(token));
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const tokenHash = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');

      // 3. Guardar el hash en Supabase
      const res = await fetch(`${this.url}/rest/v1/playlist_access_tokens`, {
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

      if (!res.ok) {
        console.warn('[SupabaseService] Error creando token de acceso:', await res.text());
        return null;
      }

      const rows = await res.json();
      return {
        id: rows?.[0]?.id,
        token, // El token crudo solo se retorna una vez para que el usuario lo copie
        preview,
        name
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

