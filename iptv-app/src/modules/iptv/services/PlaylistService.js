/**
 * @module PlaylistService
 * Gestión de playlists y cuentas guardadas (Xtream Codes, M3U).
 * Permite agregar, activar, editar, eliminar y probar conexiones.
 * Sincroniza estrictamente con Supabase (public.playlists como única fuente de la verdad).
 */

import { cacheService } from './CacheService.js';
import { supabaseService } from './SupabaseService.js';
import { parseIPTVUrl } from '../parsers/UrlParser.js';
import { maskUsername } from '../utils/security.js';

class PlaylistService {
  /**
   * Obtiene la lista de playlists guardadas.
   * Reconcilia estrictamente con Supabase public.playlists (elimina de IndexedDB las que ya no estén en Supabase).
   */
  async getAll() {
    let lists = (await cacheService.getPlaylists()) || [];

    // Purgar cualquier rastro local de la lista personalizada retirada
    const retiredIds = lists
      .filter(l => l.id === 'custom_lelouch_playlist' || String(l.url || '').includes('/api/playlist') || String(l.serverBaseUrl || '').includes('vercel.app'))
      .map(l => l.id);
    for (const rid of retiredIds) {
      await cacheService.deletePlaylist(rid);
    }
    lists = lists.filter(l => !retiredIds.includes(l.id));

    try {
      if (supabaseService.isAvailable) {
        const cloudLists = await supabaseService.getPlaylists();
        if (Array.isArray(cloudLists)) {
          const matchedLocalIds = new Set();
          const reconciled = [];

          for (const cPl of cloudLists) {
            const parsed = parseIPTVUrl(cPl.url);
            const cleanServer = (cPl.serverBaseUrl || parsed.serverBaseUrl || '').replace(/\/+$/, '');
            const cleanUser = (cPl.username && !cPl.username.includes('*')) ? cPl.username : (parsed.username || '');

            const exists = lists.find(l =>
              l.id === cPl.id ||
              l.url === cPl.url ||
              (cleanServer && cleanUser && (l.serverBaseUrl || '').replace(/\/+$/, '') === cleanServer && (l.username || '') === cleanUser)
            );

            if (!exists) {
              const newLocalPl = {
                id: cPl.id || `pl_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
                name: cPl.name,
                url: cPl.url,
                serverBaseUrl: cleanServer || parsed.serverBaseUrl,
                username: cleanUser,
                password: cPl.password || parsed._password || '',
                maskedUsername: cleanUser ? maskUsername(cleanUser) : '',
                sourceType: parsed.sourceType,
                isActive: !!cPl.isActive,
                expiresAt: cPl.expiresAt || null,
                channelsCount: cPl.channelsCount || 0,
                moviesCount: cPl.moviesCount || 0,
                seriesCount: cPl.seriesCount || 0,
                createdAt: Date.now(),
                lastUsedAt: Date.now()
              };
              await cacheService.savePlaylist(newLocalPl);
              matchedLocalIds.add(newLocalPl.id);
              reconciled.push(newLocalPl);
            } else {
              matchedLocalIds.add(exists.id);
              let changed = false;
              if (cPl.url && exists.url !== cPl.url) {
                exists.url = cPl.url;
                changed = true;
              }
              if (cPl.expiresAt && exists.expiresAt !== cPl.expiresAt) {
                exists.expiresAt = cPl.expiresAt;
                changed = true;
              }
              if (cleanUser && exists.username !== cleanUser) {
                exists.username = cleanUser;
                exists.maskedUsername = maskUsername(cleanUser);
                changed = true;
              }
              if (cPl.name && exists.name !== cPl.name) {
                exists.name = cPl.name;
                changed = true;
              }
              if (changed) {
                await cacheService.savePlaylist(exists);
              }
              reconciled.push(exists);
            }
          }

          // Purgar de IndexedDB cualquier lista vieja/fantasma que ya fue borrada en Supabase
          for (const localItem of lists) {
            if (!matchedLocalIds.has(localItem.id)) {
              await cacheService.deletePlaylist(localItem.id);
            }
          }

          lists = reconciled;
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
    await this.getAll();
    const lists = await cacheService.getPlaylists();
    if (!lists || lists.length === 0) return null;

    const savedActiveId = (typeof localStorage !== 'undefined') ? localStorage.getItem('iptv_active_playlist_id') : null;
    let active = null;

    if (savedActiveId && savedActiveId !== 'custom_lelouch_playlist') {
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
   * Agrega o actualiza una playlist en IndexedDB y en la nube Supabase (esperando confirmación).
   * @param {string} rawUrl
   * @param {string} customName
   */
  async addOrUpdate(rawUrl, customName = '') {
    let cleanUrl = (rawUrl || '').trim();
    if (cleanUrl.includes('liontv.es:80/')) {
      cleanUrl = cleanUrl.replace('liontv.es:80/', 'liontv.es:8080/');
    }
    const parsed = parseIPTVUrl(cleanUrl);
    const cleanServer = String(parsed.serverBaseUrl || '').replace(/\/+$/, '');
    const cleanUser = parsed.username || '';
    const cleanPass = parsed._password || '';
    if (cleanServer && cleanUser && cleanPass) {
      cleanUrl = `${cleanServer}/get.php?username=${encodeURIComponent(cleanUser)}&password=${encodeURIComponent(cleanPass)}&type=m3u_plus&output=ts`;
    }

    const fallbackId = `pl_${btoa(unescape(encodeURIComponent(cleanServer + '_' + (cleanUser || 'anon')))).replace(/[^a-zA-Z0-9]/g, '').slice(0, 16)}`;

    const existingLists = (await cacheService.getPlaylists()) || [];

    const existing = existingLists.find(p =>
      p.id === fallbackId ||
      p.url === cleanUrl ||
      p.url === rawUrl ||
      (cleanServer && cleanUser && String(p.serverBaseUrl || '').replace(/\/+$/, '') === cleanServer && (p.username || '') === cleanUser)
    );

    const savedActiveId = (typeof localStorage !== 'undefined') ? localStorage.getItem('iptv_active_playlist_id') : null;
    const isFirst = existingLists.length === 0;
    const shouldBeActive = existing ? (existing.isActive || existing.id === savedActiveId) : (isFirst || !savedActiveId);

    const playlist = {
      id: existing ? existing.id : fallbackId,
      name: customName.trim() || (existing && existing.name) || `${parsed.hostname} (${cleanUser || 'Playlist'})`,
      url: cleanUrl,
      serverBaseUrl: cleanServer,
      username: cleanUser,
      rawUsername: cleanUser,
      password: cleanPass || (existing && existing.password) || '',
      maskedUsername: cleanUser ? maskUsername(cleanUser) : '',
      sourceType: parsed.sourceType,
      isActive: shouldBeActive,
      expiresAt: existing ? existing.expiresAt : null,
      createdAt: existing ? existing.createdAt : Date.now(),
      lastUsedAt: Date.now()
    };

    // 1. Subir a Supabase primero para obtener su ID canónico de nube
    try {
      const savedCloud = await supabaseService.savePlaylist(playlist);
      if (savedCloud && savedCloud.id) {
        if (existing && existing.id !== savedCloud.id) {
          await cacheService.deletePlaylist(existing.id);
        }
        playlist.id = savedCloud.id;
      }
    } catch (err) {
      console.warn('[PlaylistService] Fallo al subir playlist a Supabase:', err);
    }

    if (shouldBeActive && typeof localStorage !== 'undefined') {
      localStorage.setItem('iptv_active_playlist_id', playlist.id);
    }

    // 2. Guardar localmente en IndexedDB
    await cacheService.savePlaylist(playlist);
    if (shouldBeActive) {
      await cacheService.setActivePlaylist(playlist.id);
    }

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
      await supabaseService.savePlaylist({ ...active, isActive: true }).catch(() => {});
    }
    return active;
  }

  /**
   * Elimina una playlist por ID de Supabase y de IndexedDB esperando confirmación real.
   */
  async remove(id) {
    const lists = (await cacheService.getPlaylists()) || [];
    const target = lists.find(p => p.id === id);
    if (target) {
      await supabaseService.deletePlaylist(target);
    } else {
      await supabaseService.deletePlaylist({ id });
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
      url: playlist.url,
      serverBaseUrl: playlist.serverBaseUrl,
      username: playlist.username || playlist.rawUsername || '',
      rawUsername: playlist.username || playlist.rawUsername || '',
      sourceType: playlist.sourceType,
      isActive: isActive,
      expiresAt: playlist.expiresAt || playlist.expires_at || null,
      daysRemaining: playlist.daysRemaining,
      status: playlist.status || 'Active',
      maxConnections: playlist.maxConnections,
      activeConnections: playlist.activeConnections,
      channelsCount: playlist.channelsCount || playlist.channels_count || 0,
      moviesCount: playlist.moviesCount || playlist.movies_count || 0,
      seriesCount: playlist.seriesCount || playlist.series_count || 0,
      createdAt: playlist.createdAt,
      lastUsedAt: playlist.lastUsedAt
    };
  }
}

export const playlistService = new PlaylistService();
