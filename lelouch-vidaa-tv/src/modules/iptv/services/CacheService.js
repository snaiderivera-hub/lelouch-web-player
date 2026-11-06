/**
 * @module CacheService
 * Servicio de almacenamiento persistente usando IndexedDB con fallback robusto en memoria RAM y LocalStorage.
 * Diseñado para catálogos masivos (>100,000 registros), estado de sesión,
 * favoritos, historial de reproducción ("Continuar viendo") y EPG.
 * Tolera bloqueos de almacenamiento de navegadores como Microsoft Edge (Tracking Prevention) o Brave.
 */

const DB_NAME = 'NexusIPTV_DB';
const DB_VERSION = 1;

class CacheService {
  constructor() {
    this._db = null;
    this._initPromise = null;
    this._memStore = new Map();
    this._isMemoryMode = false;
  }

  async _getDB() {
    if (this._isMemoryMode) return null;
    if (this._db) return this._db;
    if (this._initPromise) return this._initPromise;

    this._initPromise = new Promise((resolve) => {
      try {
        if (typeof window === 'undefined' || !window.indexedDB) {
          console.warn('[CacheService] IndexedDB no disponible, activando modo memoria');
          this._isMemoryMode = true;
          return resolve(null);
        }

        const request = indexedDB.open(DB_NAME, DB_VERSION);

        request.onupgradeneeded = (event) => {
          try {
            const db = event.target.result;
            if (!db.objectStoreNames.contains('catalogs')) {
              db.createObjectStore('catalogs', { keyPath: 'key' });
            }
            if (!db.objectStoreNames.contains('playlists')) {
              const playlistStore = db.createObjectStore('playlists', { keyPath: 'id' });
              playlistStore.createIndex('isActive', 'isActive', { unique: false });
            }
            if (!db.objectStoreNames.contains('favorites')) {
              const favStore = db.createObjectStore('favorites', { keyPath: 'id' });
              favStore.createIndex('type', 'type', { unique: false });
            }
            if (!db.objectStoreNames.contains('history')) {
              const histStore = db.createObjectStore('history', { keyPath: 'contentId' });
              histStore.createIndex('updatedAt', 'updatedAt', { unique: false });
            }
          } catch (e) {
            console.warn('[CacheService] Error creando object stores en IndexedDB:', e);
          }
        };

        request.onsuccess = (event) => {
          this._db = event.target.result;
          resolve(this._db);
        };

        request.onerror = (event) => {
          console.warn('[CacheService] Acceso a IndexedDB bloqueado (ej. Edge Tracking Prevention). Operando en modo RAM / Fallback seguro.');
          this._isMemoryMode = true;
          resolve(null);
        };
      } catch (err) {
        console.warn('[CacheService] Excepción al abrir IndexedDB:', err);
        this._isMemoryMode = true;
        resolve(null);
      }
    });

    return this._initPromise;
  }

  // ════════════ CACHÉ DE CATÁLOGOS ════════════
  async set(key, data, ttlMs = 30 * 60 * 1000) {
    const item = {
      key,
      data,
      expiresAt: Date.now() + ttlMs,
      updatedAt: Date.now()
    };

    // Almacenar siempre en memoria rápida
    this._memStore.set(key, item);

    try {
      const db = await this._getDB();
      if (!db) return true;

      return new Promise((resolve) => {
        const tx = db.transaction('catalogs', 'readwrite');
        const store = tx.objectStore('catalogs');
        const req = store.put(item);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch (e) {
      return true;
    }
  }

  async get(key) {
    // 1. Verificar memoria rápida
    const memItem = this._memStore.get(key);
    if (memItem) {
      if (memItem.expiresAt && Date.now() > memItem.expiresAt) {
        this._memStore.delete(key);
      } else {
        return memItem.data;
      }
    }

    try {
      const db = await this._getDB();
      if (!db) return null;

      return new Promise((resolve) => {
        const tx = db.transaction('catalogs', 'readonly');
        const store = tx.objectStore('catalogs');
        const req = store.get(key);
        req.onsuccess = () => {
          const item = req.result;
          if (!item) return resolve(null);
          if (item.expiresAt && Date.now() > item.expiresAt) {
            this.delete(key).catch(() => {});
            return resolve(null);
          }
          this._memStore.set(key, item);
          resolve(item.data);
        };
        req.onerror = () => resolve(null);
      });
    } catch {
      return null;
    }
  }

  async delete(key) {
    this._memStore.delete(key);
    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('catalogs', 'readwrite');
        const store = tx.objectStore('catalogs');
        const req = store.delete(key);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return true;
    }
  }

  async clearCatalogCache() {
    this._memStore.clear();
    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('catalogs', 'readwrite');
        const store = tx.objectStore('catalogs');
        const req = store.clear();
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return true;
    }
  }

  // ════════════ FAVORITOS ════════════
  async getFavorites(type = null) {
    try {
      const db = await this._getDB();
      if (!db) {
        const all = Array.from(this._memStore.values()).filter(v => v._isFav);
        return type ? all.filter(f => f.type === type) : all;
      }
      return new Promise((resolve) => {
        const tx = db.transaction('favorites', 'readonly');
        const store = tx.objectStore('favorites');
        const req = store.getAll();
        req.onsuccess = () => {
          const all = req.result || [];
          resolve(type ? all.filter(f => f.type === type) : all);
        };
        req.onerror = () => resolve([]);
      });
    } catch {
      return [];
    }
  }

  async saveFavorite(fav) {
    if (!fav.id) return false;
    const item = { ...fav, _isFav: true, savedAt: Date.now() };
    this._memStore.set(`fav_${fav.id}`, item);

    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('favorites', 'readwrite');
        const store = tx.objectStore('favorites');
        const req = store.put(item);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return true;
    }
  }

  async deleteFavorite(id) {
    this._memStore.delete(`fav_${id}`);
    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('favorites', 'readwrite');
        const store = tx.objectStore('favorites');
        const req = store.delete(id);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return true;
    }
  }

  async isFavorite(id) {
    if (!id) return false;
    if (this._memStore.has(`fav_${id}`)) return true;
    try {
      const db = await this._getDB();
      if (!db) return false;
      return new Promise((resolve) => {
        const tx = db.transaction('favorites', 'readonly');
        const store = tx.objectStore('favorites');
        const req = store.get(id);
        req.onsuccess = () => resolve(!!req.result);
        req.onerror = () => resolve(false);
      });
    } catch {
      return false;
    }
  }

  async toggleFavorite(fav) {
    const isFav = await this.isFavorite(fav.id);
    if (isFav) {
      await this.deleteFavorite(fav.id);
      return false;
    } else {
      await this.saveFavorite(fav);
      return true;
    }
  }

  // ════════════ HISTORIAL ("CONTINUAR VIENDO") ════════════
  async getHistory(limit = 10) {
    try {
      const db = await this._getDB();
      if (!db) {
        const all = Array.from(this._memStore.values())
          .filter(v => v._isHist)
          .sort((a, b) => b.updatedAt - a.updatedAt)
          .slice(0, limit);
        return all;
      }
      return new Promise((resolve) => {
        const tx = db.transaction('history', 'readonly');
        const store = tx.objectStore('history');
        const req = store.getAll();
        req.onsuccess = () => {
          const all = (req.result || [])
            .sort((a, b) => b.updatedAt - a.updatedAt)
            .slice(0, limit);
          resolve(all);
        };
        req.onerror = () => resolve([]);
      });
    } catch {
      return [];
    }
  }

  async addHistory(entry) {
    if (!entry.contentId) return false;
    const record = {
      ...entry,
      _isHist: true,
      contentId: String(entry.contentId),
      updatedAt: Date.now()
    };
    this._memStore.set(`hist_${record.contentId}`, record);

    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('history', 'readwrite');
        const store = tx.objectStore('history');
        const req = store.put(record);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return true;
    }
  }

  async updateHistoryProgress(entry) {
    if (!entry.contentId) return;
    const record = {
      contentId: String(entry.contentId),
      _isHist: true,
      type: entry.type || 'vod',
      title: entry.title || '',
      poster: entry.poster || '',
      currentTime: Math.floor(entry.currentTime || 0),
      duration: Math.floor(entry.duration || 0),
      progressPercent: entry.duration > 0 ? Math.min(100, Math.round((entry.currentTime / entry.duration) * 100)) : 0,
      streamUrl: entry.streamUrl || '',
      updatedAt: Date.now()
    };
    this._memStore.set(`hist_${record.contentId}`, record);

    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('history', 'readwrite');
        const store = tx.objectStore('history');
        const req = store.put(record);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return false;
    }
  }

  // ════════════ PLAYLISTS GUARDADAS ════════════
  async getPlaylists() {
    let memList = this._memStore.get('playlists_all');
    if (!memList) {
      try {
        if (typeof localStorage !== 'undefined') {
          const saved = localStorage.getItem('iptv_playlists_backup');
          if (saved) memList = JSON.parse(saved);
        }
      } catch {}
    }

    try {
      const db = await this._getDB();
      if (!db) return memList || [];

      return new Promise((resolve) => {
        const tx = db.transaction('playlists', 'readonly');
        const store = tx.objectStore('playlists');
        const req = store.getAll();
        req.onsuccess = () => {
          const res = req.result || [];
          if (res.length > 0) {
            this._memStore.set('playlists_all', res);
          }
          resolve(res.length > 0 ? res : (memList || []));
        };
        req.onerror = () => resolve(memList || []);
      });
    } catch {
      return memList || [];
    }
  }

  async savePlaylist(playlist) {
    // Almacenar en memoria y localStorage como salvaguarda
    const current = await this.getPlaylists();
    const idx = current.findIndex(p => p.id === playlist.id || p.url === playlist.url);
    if (idx >= 0) current[idx] = playlist;
    else current.push(playlist);

    this._memStore.set('playlists_all', current);
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('iptv_playlists_backup', JSON.stringify(current));
      }
    } catch {}

    try {
      const db = await this._getDB();
      if (!db) return playlist;
      return new Promise((resolve, reject) => {
        const tx = db.transaction('playlists', 'readwrite');
        const store = tx.objectStore('playlists');
        const req = store.put(playlist);
        req.onsuccess = () => resolve(playlist);
        req.onerror = () => resolve(playlist);
      });
    } catch {
      return playlist;
    }
  }

  async deletePlaylist(id) {
    const current = (await this.getPlaylists()).filter(p => p.id !== id);
    this._memStore.set('playlists_all', current);
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('iptv_playlists_backup', JSON.stringify(current));
      }
    } catch {}

    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('playlists', 'readwrite');
        const store = tx.objectStore('playlists');
        const req = store.delete(id);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return true;
    }
  }

  async setActivePlaylist(id) {
    const playlists = await this.getPlaylists();
    for (const pl of playlists) {
      pl.isActive = (pl.id === id);
    }
    this._memStore.set('playlists_all', playlists);
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('iptv_playlists_backup', JSON.stringify(playlists));
        localStorage.setItem('iptv_active_playlist_id', id);
      }
    } catch {}

    try {
      const db = await this._getDB();
      if (!db) return true;
      return new Promise((resolve) => {
        const tx = db.transaction('playlists', 'readwrite');
        const store = tx.objectStore('playlists');
        for (const pl of playlists) {
          store.put(pl);
        }
        tx.oncomplete = () => resolve(true);
        tx.onerror = () => resolve(true);
      });
    } catch {
      return true;
    }
  }
}

export const cacheService = new CacheService();
