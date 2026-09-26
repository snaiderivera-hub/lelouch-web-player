/**
 * @module CacheService
 * Servicio de almacenamiento persistente usando IndexedDB.
 * Diseñado para catálogos masivos (>100,000 registros), estado de sesión,
 * favoritos, historial de reproducción ("Continuar viendo") y EPG.
 */

const DB_NAME = 'NexusIPTV_DB';
const DB_VERSION = 1;

class CacheService {
  constructor() {
    this._db = null;
    this._initPromise = null;
  }

  async _getDB() {
    if (this._db) return this._db;
    if (this._initPromise) return this._initPromise;

    this._initPromise = new Promise((resolve, reject) => {
      if (typeof window === 'undefined' || !window.indexedDB) {
        return reject(new Error('IndexedDB no está disponible en este entorno.'));
      }

      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // Store para caché general (catálogos grandes, metadatos, EPG)
        if (!db.objectStoreNames.contains('catalogs')) {
          db.createObjectStore('catalogs', { keyPath: 'key' });
        }

        // Store para playlists guardadas
        if (!db.objectStoreNames.contains('playlists')) {
          const playlistStore = db.createObjectStore('playlists', { keyPath: 'id' });
          playlistStore.createIndex('isActive', 'isActive', { unique: false });
        }

        // Store para favoritos (Live, Películas, Series)
        if (!db.objectStoreNames.contains('favorites')) {
          const favStore = db.createObjectStore('favorites', { keyPath: 'id' });
          favStore.createIndex('type', 'type', { unique: false });
        }

        // Store para continuar viendo / historial
        if (!db.objectStoreNames.contains('history')) {
          const histStore = db.createObjectStore('history', { keyPath: 'contentId' });
          histStore.createIndex('updatedAt', 'updatedAt', { unique: false });
        }
      };

      request.onsuccess = (event) => {
        this._db = event.target.result;
        resolve(this._db);
      };

      request.onerror = (event) => {
        reject(event.target.error || new Error('Error al abrir IndexedDB.'));
      };
    });

    return this._initPromise;
  }

  // ════════════ CACHÉ DE CATÁLOGOS ════════════
  /**
   * Guarda un objeto o array en caché con tiempo de expiración.
   * @param {string} key
   * @param {any} data
   * @param {number} ttlMs
   */
  async set(key, data, ttlMs = 30 * 60 * 1000) {
    try {
      const db = await this._getDB();
      return new Promise((resolve, reject) => {
        const tx = db.transaction('catalogs', 'readwrite');
        const store = tx.objectStore('catalogs');
        const item = {
          key,
          data,
          expiresAt: Date.now() + ttlMs,
          updatedAt: Date.now()
        };
        const req = store.put(item);
        req.onsuccess = () => resolve(true);
        req.onerror = () => reject(req.error);
      });
    } catch (e) {
      console.warn('[CacheService] Error guardando caché:', e);
      return false;
    }
  }

  /**
   * Obtiene un elemento de caché si no ha expirado.
   * @param {string} key
   * @returns {Promise<any|null>}
   */
  async get(key) {
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('catalogs', 'readonly');
        const store = tx.objectStore('catalogs');
        const req = store.get(key);
        req.onsuccess = () => {
          const item = req.result;
          if (!item) return resolve(null);
          if (item.expiresAt && Date.now() > item.expiresAt) {
            // Expirado, borrar en segundo plano
            this.delete(key).catch(() => {});
            return resolve(null);
          }
          resolve(item.data);
        };
        req.onerror = () => resolve(null);
      });
    } catch {
      return null;
    }
  }

  async delete(key) {
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('catalogs', 'readwrite');
        const store = tx.objectStore('catalogs');
        const req = store.delete(key);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return false;
    }
  }

  async clearCatalogCache() {
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('catalogs', 'readwrite');
        const store = tx.objectStore('catalogs');
        const req = store.clear();
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return false;
    }
  }

  // ════════════ FAVORITOS ════════════
  async getFavorites(type = null) {
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('favorites', 'readonly');
        const store = tx.objectStore('favorites');
        const req = store.getAll();
        req.onsuccess = () => {
          const all = req.result || [];
          if (!type) return resolve(all);
          resolve(all.filter(f => f.type === type));
        };
        req.onerror = () => resolve([]);
      });
    } catch {
      return [];
    }
  }

  async isFavorite(id) {
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('favorites', 'readonly');
        const store = tx.objectStore('favorites');
        const req = store.get(String(id));
        req.onsuccess = () => resolve(!!req.result);
        req.onerror = () => resolve(false);
      });
    } catch {
      return false;
    }
  }

  async toggleFavorite(item) {
    try {
      const db = await this._getDB();
      const id = String(item.id);
      const isFav = await this.isFavorite(id);

      return new Promise((resolve, reject) => {
        const tx = db.transaction('favorites', 'readwrite');
        const store = tx.objectStore('favorites');
        if (isFav) {
          const req = store.delete(id);
          req.onsuccess = () => resolve(false); // ya no es favorito
          req.onerror = () => reject(req.error);
        } else {
          const favRecord = {
            id,
            type: item.type || 'live',
            name: item.name || item.title || '',
            logo: item.logo || item.poster || '',
            categoryName: item.categoryName || '',
            streamUrl: item.streamUrl || '',
            rating: item.rating || null,
            addedAt: Date.now()
          };
          const req = store.put(favRecord);
          req.onsuccess = () => resolve(true); // agregado a favoritos
          req.onerror = () => reject(req.error);
        }
      });
    } catch (e) {
      console.warn('[CacheService] Error en toggleFavorite:', e);
      return false;
    }
  }

  // ════════════ HISTORIAL / CONTINUAR VIENDO ════════════
  async getHistory(limit = 20) {
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('history', 'readonly');
        const store = tx.objectStore('history');
        const req = store.getAll();
        req.onsuccess = () => {
          const items = (req.result || []).sort((a, b) => b.updatedAt - a.updatedAt);
          resolve(items.slice(0, limit));
        };
        req.onerror = () => resolve([]);
      });
    } catch {
      return [];
    }
  }

  async updateHistoryProgress(entry) {
    if (!entry.contentId) return;
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('history', 'readwrite');
        const store = tx.objectStore('history');
        const record = {
          contentId: String(entry.contentId),
          type: entry.type || 'vod',
          title: entry.title || '',
          poster: entry.poster || '',
          currentTime: Math.floor(entry.currentTime || 0),
          duration: Math.floor(entry.duration || 0),
          progressPercent: entry.duration > 0 ? Math.min(100, Math.round((entry.currentTime / entry.duration) * 100)) : 0,
          streamUrl: entry.streamUrl || '',
          updatedAt: Date.now()
        };
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
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('playlists', 'readonly');
        const store = tx.objectStore('playlists');
        const req = store.getAll();
        req.onsuccess = () => resolve(req.result || []);
        req.onerror = () => resolve([]);
      });
    } catch {
      return [];
    }
  }

  async savePlaylist(playlist) {
    try {
      const db = await this._getDB();
      return new Promise((resolve, reject) => {
        const tx = db.transaction('playlists', 'readwrite');
        const store = tx.objectStore('playlists');
        const req = store.put(playlist);
        req.onsuccess = () => resolve(playlist);
        req.onerror = () => reject(req.error);
      });
    } catch (e) {
      throw e;
    }
  }

  async deletePlaylist(id) {
    try {
      const db = await this._getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('playlists', 'readwrite');
        const store = tx.objectStore('playlists');
        const req = store.delete(id);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
      });
    } catch {
      return false;
    }
  }

  async setActivePlaylist(id) {
    try {
      const playlists = await this.getPlaylists();
      const db = await this._getDB();
      return new Promise((resolve, reject) => {
        const tx = db.transaction('playlists', 'readwrite');
        const store = tx.objectStore('playlists');
        for (const pl of playlists) {
          pl.isActive = (pl.id === id);
          store.put(pl);
        }
        tx.oncomplete = () => resolve(true);
        tx.onerror = () => reject(tx.error);
      });
    } catch (e) {
      console.warn('[CacheService] Error setting active playlist:', e);
      return false;
    }
  }
}

export const cacheService = new CacheService();
