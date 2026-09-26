/**
 * @module EPGService
 * Servicio de Guía Electrónica de Programas (EPG).
 * Consulta la EPG corta bajo demanda con caché en memoria y deduplicación.
 * Calcula el programa en emisión (NOW) y el siguiente (NEXT) con porcentaje de progreso.
 */

class EPGService {
  constructor() {
    this._cache = new Map(); // channelId -> { data, expiresAt }
    this._inFlight = new Map(); // channelId -> Promise
    this._ttlMs = 5 * 60 * 1000; // 5 minutos de caché
  }

  /**
   * Obtiene la información EPG para un canal.
   * @param {Object} adapter - Instancia de XtreamAdapter
   * @param {string|number} channelId
   * @returns {Promise<{ now: Object|null, next: Object|null, listings: Array }>}
   */
  async getChannelEPG(adapter, channelId) {
    if (!channelId || !adapter) {
      return { now: null, next: null, listings: [] };
    }

    const key = String(channelId);
    const cached = this._cache.get(key);
    if (cached && Date.now() < cached.expiresAt) {
      return this._parseNowNext(cached.data);
    }

    if (this._inFlight.has(key)) {
      return this._inFlight.get(key);
    }

    const promise = (async () => {
      try {
        const rawListings = await adapter.getEPG(channelId, 6);
        this._cache.set(key, { data: rawListings, expiresAt: Date.now() + this._ttlMs });
        return this._parseNowNext(rawListings);
      } catch (err) {
        return { now: null, next: null, listings: [] };
      } finally {
        this._inFlight.delete(key);
      }
    })();

    this._inFlight.set(key, promise);
    return promise;
  }

  /**
   * Procesa la lista de programas para extraer NOW y NEXT.
   * @private
   */
  _parseNowNext(listings) {
    if (!Array.isArray(listings) || listings.length === 0) {
      return { now: null, next: null, listings: [] };
    }

    const nowSec = Math.floor(Date.now() / 1000);
    const parsed = listings.map(entry => {
      let startSec = entry.start_timestamp ? parseInt(entry.start_timestamp, 10) : 0;
      let stopSec = entry.stop_timestamp ? parseInt(entry.stop_timestamp, 10) : 0;

      if (!startSec && entry.start) {
        startSec = Math.floor(new Date(entry.start).getTime() / 1000);
      }
      if (!stopSec && entry.end) {
        stopSec = Math.floor(new Date(entry.end).getTime() / 1000);
      }

      // Decodificar Base64 en título/descripción si viene codificado
      let title = entry.title || 'Sin título';
      let description = entry.description || '';
      try {
        if (entry.title && /^[A-Za-z0-9+/=]+$/.test(entry.title) && entry.title.length % 4 === 0) {
          title = decodeURIComponent(escape(atob(entry.title)));
        }
        if (entry.description && /^[A-Za-z0-9+/=]+$/.test(entry.description) && entry.description.length % 4 === 0) {
          description = decodeURIComponent(escape(atob(entry.description)));
        }
      } catch {}

      return {
        id: entry.id,
        title,
        description,
        startSec,
        stopSec,
        startTimeStr: startSec ? new Date(startSec * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '',
        stopTimeStr: stopSec ? new Date(stopSec * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''
      };
    });

    // Encontrar programa actual
    const current = parsed.find(p => p.startSec <= nowSec && nowSec < p.stopSec) || parsed[0] || null;
    let next = null;

    if (current) {
      next = parsed.find(p => p.startSec >= current.stopSec) || null;
      if (current.stopSec > current.startSec) {
        current.progress = Math.min(100, Math.max(0, Math.round(((nowSec - current.startSec) / (current.stopSec - current.startSec)) * 100)));
      } else {
        current.progress = 0;
      }
    }

    return {
      now: current,
      next,
      listings: parsed
    };
  }

  clear() {
    this._cache.clear();
    this._inFlight.clear();
  }
}

export const epgService = new EPGService();
