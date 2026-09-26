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
