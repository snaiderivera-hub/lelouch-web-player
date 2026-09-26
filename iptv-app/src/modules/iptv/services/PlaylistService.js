/**
 * @module PlaylistService
 * Gestión de playlists y cuentas guardadas (Xtream Codes, M3U).
 * Permite agregar, activar, editar, eliminar y probar conexiones.
 * Oculta contraseñas en representaciones públicas para la UI.
 */

import { cacheService } from './CacheService.js';
import { supabaseService } from './SupabaseService.js';
import { parseIPTVUrl } from '../parsers/UrlParser.js';
import { maskUsername } from '../utils/security.js';

class PlaylistService {
  /**
   * Obtiene la lista de playlists guardadas con credenciales enmascaradas.
   * Sincroniza bidireccionalmente con Supabase y garantiza que haya una activa marcada.
   */
  async getAll() {
    let lists = await cacheService.getPlaylists();

    try {
      if (supabaseService.isAvailable) {
        const cloudLists = await supabaseService.getPlaylists();
        if (cloudLists && cloudLists.length > 0) {
          for (const cPl of cloudLists) {
            const exists = lists.find(l => l.url === cPl.url || l.id === cPl.id);
            if (!exists) {
              const parsed = parseIPTVUrl(cPl.url);
              const newLocalPl = {
                id: cPl.id || `pl_${Date.now()}`,
                name: cPl.name,
                url: cPl.url,
                serverBaseUrl: cPl.serverBaseUrl || parsed.serverBaseUrl,
                username: cPl.username || parsed.username || '',
                maskedUsername: cPl.username ? maskUsername(cPl.username) : '',
                sourceType: parsed.sourceType,
                isActive: !!cPl.isActive,
                createdAt: Date.now(),
                lastUsedAt: Date.now()
              };
              await cacheService.savePlaylist(newLocalPl);
              lists.push(newLocalPl);
            }
          }
        }
      }
    } catch (e) {
      console.warn('[PlaylistService] Error en sincronización con Supabase:', e);
    }

    const savedActiveId = (typeof localStorage !== 'undefined') ? localStorage.getItem('iptv_active_playlist_id') : null;
    let activeItem = null;

    if (savedActiveId) {
      activeItem = lists.find(l => l.id === savedActiveId);
    }
    if (!activeItem) {
      activeItem = lists.find(l => l.isActive);
    }
    if (!activeItem && lists.length > 0) {
      activeItem = lists[0];
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('iptv_active_playlist_id', activeItem.id);
      }
      await cacheService.setActivePlaylist(activeItem.id);
    }

    const currentActiveId = activeItem?.id || savedActiveId;

    return lists.map(p => {
      const sanitized = this._sanitize(p);
      sanitized.isActive = (p.id === currentActiveId);
      return sanitized;
    });
  }

  /**
   * Obtiene la playlist activa (con URL real para uso del servicio).
   */
  async getActive() {
    const lists = await cacheService.getPlaylists();
    if (!lists || lists.length === 0) return null;

    const savedActiveId = (typeof localStorage !== 'undefined') ? localStorage.getItem('iptv_active_playlist_id') : null;
    let active = null;

    if (savedActiveId) {
      active = lists.find(p => p.id === savedActiveId);
    }
    if (!active) {
      active = lists.find(p => p.isActive);
    }
    if (!active) {
      active = lists[0];
    }

    if (active) {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('iptv_active_playlist_id', active.id);
      }
      if (!active.isActive) {
        await cacheService.setActivePlaylist(active.id);
        active.isActive = true;
      }
    }

    return active;
  }

  /**
   * Agrega o actualiza una playlist en IndexedDB y la nube Supabase.
   * @param {string} rawUrl
   * @param {string} customName
   */
  async addOrUpdate(rawUrl, customName = '') {
    const parsed = parseIPTVUrl(rawUrl);
    const id = `pl_${btoa(unescape(encodeURIComponent(parsed.serverBaseUrl + '_' + (parsed.username || 'anon')))).replace(/[^a-zA-Z0-9]/g, '').slice(0, 16)}`;

    const existingLists = await cacheService.getPlaylists();
    
    // Buscar si ya existe una lista con la misma URL exacta O el mismo servidor+usuario
    const existing = existingLists.find(p => 
      p.id === id || 
      p.url === rawUrl || 
      (p.serverBaseUrl === parsed.serverBaseUrl && p.username === (parsed.username || ''))
    );

    if (existing && !customName) {
      // Si ya existe y no estamos solo actualizando el nombre, bloquear la duplicación.
      throw new Error(`Esta playlist ya está registrada como "${existing.name}". No se permiten listas repetidas.`);
    }

    const finalId = existing ? existing.id : id;
    const savedActiveId = (typeof localStorage !== 'undefined') ? localStorage.getItem('iptv_active_playlist_id') : null;
    const isFirst = existingLists.length === 0;

    // Preservar si ya era activa o si es la primera
    const shouldBeActive = existing ? (existing.isActive || existing.id === savedActiveId) : (isFirst || !savedActiveId);

    const playlist = {
      id: finalId,
      name: customName.trim() || (existing && existing.name) || `${parsed.hostname} (${parsed.username ? maskUsername(parsed.username) : 'Playlist'})`,
      url: rawUrl,
      serverBaseUrl: parsed.serverBaseUrl,
      username: parsed.username || '',
      maskedUsername: parsed.username ? maskUsername(parsed.username) : '',
      sourceType: parsed.sourceType,
      isActive: shouldBeActive,
      createdAt: existing ? existing.createdAt : Date.now(),
      lastUsedAt: Date.now()
    };

    if (shouldBeActive && typeof localStorage !== 'undefined') {
      localStorage.setItem('iptv_active_playlist_id', id);
    }

    // 1. Guardar localmente en IndexedDB
    await cacheService.savePlaylist(playlist);
    if (shouldBeActive) {
      await cacheService.setActivePlaylist(id);
    }

    // 2. Guardar en la nube Supabase
    supabaseService.savePlaylist(playlist).catch(err => {
      console.warn('[PlaylistService] Fallo al subir playlist a Supabase:', err);
    });

    return this._sanitize(playlist);
  }

  /**
   * Activa una playlist por ID y actualiza estado en nube.
   */
  async activate(id) {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('iptv_active_playlist_id', id);
    }
    await cacheService.setActivePlaylist(id);
    const active = await this.getActive();
    if (active) {
      supabaseService.savePlaylist({ ...active, isActive: true }).catch(() => {});
    }
    return active;
  }

  /**
   * Elimina una playlist por ID de IndexedDB y Supabase.
   */
  async remove(id) {
    const lists = await cacheService.getPlaylists();
    const target = lists.find(p => p.id === id);
    if (target && target.url) {
      supabaseService.deletePlaylist(target.url).catch(() => {});
    }
    const currentActiveId = (typeof localStorage !== 'undefined') ? localStorage.getItem('iptv_active_playlist_id') : null;
    if (currentActiveId === id && typeof localStorage !== 'undefined') {
      localStorage.removeItem('iptv_active_playlist_id');
    }
    return cacheService.deletePlaylist(id);
  }

  /**
   * Sanitiza la playlist para la UI (ocultando passwords).
   */
  _sanitize(playlist) {
    if (!playlist) return null;
    const savedActiveId = (typeof localStorage !== 'undefined') ? localStorage.getItem('iptv_active_playlist_id') : null;
    const isActive = savedActiveId ? (playlist.id === savedActiveId) : !!playlist.isActive;
    return {
      id: playlist.id,
      name: playlist.name,
      serverBaseUrl: playlist.serverBaseUrl,
      username: playlist.maskedUsername || maskUsername(playlist.username || ''),
      sourceType: playlist.sourceType,
      isActive: isActive,
      createdAt: playlist.createdAt,
      lastUsedAt: playlist.lastUsedAt
    };
  }
}

export const playlistService = new PlaylistService();
