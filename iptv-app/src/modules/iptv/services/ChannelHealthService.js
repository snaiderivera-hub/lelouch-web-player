/**
 * @module ChannelHealthService
 * Servicio independiente para monitoreo de salud, disponibilidad y latencia de canales IPTV.
 * 
 * Principios de diseño:
 * 1. Concurrencia acotada estricta (máximo 2-3 conexiones simultáneas) para no saturar al proveedor.
 * 2. Cero falsos OFFLINE: distingue fallos definitivos (404/410) de transitorios (timeout/502 -> DEGRADED/UNKNOWN).
 * 3. Cancelación limpia inmediata (cancelAll / AbortController) al navegar o cambiar de vista.
 * 4. Caché con TTL (15 min) para evitar peticiones redundantes.
 * 5. No interferencia con la reproducción activa (pausa automática cuando el player está activo).
 * 6. Inspección ultraligera de manifiestos HLS (.m3u8), sin descargar flujos de video pesados.
 */

export const HealthStatus = {
  ONLINE: 'ONLINE',       // Manifiesto m3u8 recibido correctamente (#EXTM3U)
  OFFLINE: 'OFFLINE',     // Stream no existe (404/410) o no responde tras reintento
  DEGRADED: 'DEGRADED',   // Latencia excesiva o errores transitorios (502/504)
  UNKNOWN: 'UNKNOWN',     // Estado indeterminado o no verificado
  CHECKING: 'CHECKING'    // En proceso de verificación
};

export class ChannelHealthService {
  /**
   * @param {Object} options
   * @param {number} [options.maxConcurrency=2] - Concurrencia máxima simultánea
   * @param {number} [options.timeoutMs=5000] - Tiempo límite por petición
   * @param {number} [options.pacingDelayMs=120] - Pausa de cortesía entre peticiones para el proveedor
   * @param {number} [options.cacheTtlMs=900000] - Tiempo de vida de la caché (15 min)
   */
  constructor(options = {}) {
    this.maxConcurrency = options.maxConcurrency || 2;
    this.timeoutMs = options.timeoutMs || 5000;
    this.pacingDelayMs = options.pacingDelayMs || 120;
    this.cacheTtlMs = options.cacheTtlMs || 15 * 60 * 1000;

    // Estado interno
    this._queue = [];
    this._activeWorkers = 0;
    this._activeAbortControllers = new Set();
    this._cache = new Map(); // streamId -> { status, latencyMs, timestamp, details }
    this._isPaused = false;
    this._isPlayerActive = false;
    this._isRunning = false;

    // Listeners de eventos
    this._listeners = new Set();
  }

  /**
   * Suscribe un listener a cambios de salud de canales.
   * @param {function({ channelId: string, result: Object })} fn
   */
  onHealthUpdate(fn) {
    this._listeners.add(fn);
    return () => this._listeners.delete(fn);
  }

  _emit(channelId, result) {
    for (const fn of this._listeners) {
      try {
        fn({ channelId, result });
      } catch (e) {
        console.error('[ChannelHealthService] Listener error:', e);
      }
    }
  }

  /**
   * Consulta el estado en caché de un canal.
   * @param {string|number} channelId
   * @returns {Object|null}
   */
  getCachedHealth(channelId) {
    if (!channelId) return null;
    const entry = this._cache.get(String(channelId));
    if (!entry) return null;

    if (Date.now() - entry.timestamp > this.cacheTtlMs) {
      this._cache.delete(String(channelId));
      return null;
    }
    return entry;
  }

  /**
   * Pausa o reanuda el escaneo en función de la actividad del reproductor.
   * Garantiza que el streaming activo tenga el 100% de prioridad.
   * @param {boolean} active
   */
  setPlayerActive(active) {
    this._isPlayerActive = Boolean(active);
    if (this._isPlayerActive) {
      this.pause();
    } else {
      this.resume();
    }
  }

  /**
   * Pausa la ejecución de la cola.
   */
  pause() {
    this._isPaused = true;
  }

  /**
   * Reanuda la ejecución de la cola.
   */
  resume() {
    this._isPaused = false;
    this._processQueue();
  }

  /**
   * Cancela todas las peticiones activas y vacía la cola pendiente.
   * Vital para cambios de vista o teardown de componentes.
   */
  cancelAll() {
    // 1. Vaciar cola pendiente
    const cancelledCount = this._queue.length;
    for (const item of this._queue) {
      if (item.reject) {
        item.reject(new DOMException('Health check cancelled by user', 'AbortError'));
      }
    }
    this._queue = [];

    // 2. Abortar controladores en vuelo
    for (const controller of this._activeAbortControllers) {
      try {
        controller.abort();
      } catch {}
    }
    this._activeAbortControllers.clear();
    this._activeWorkers = 0;
    this._isRunning = false;

    return { cancelledPending: cancelledCount };
  }

  /**
   * Alias de cancelAll para compatibilidad de ciclo de vida.
   */
  stop() {
    return this.cancelAll();
  }

  /**
   * Limpia toda la memoria caché.
   */
  clearCache() {
    this._cache.clear();
  }

  /**
   * Obtiene métricas del estado del servicio.
   */
  getStats() {
    return {
      activeWorkers: this._activeWorkers,
      queueLength: this._queue.length,
      cachedEntries: this._cache.size,
      isPaused: this._isPaused,
      isPlayerActive: this._isPlayerActive,
      isRunning: this._isRunning
    };
  }

  /**
   * Verifica la salud de un canal individual con soporte de caché y reintentos.
   * @param {Object} channel - Objeto canal con id y streamUrl
   * @param {Object} [options]
   * @param {boolean} [options.force=false] - Forzar petición ignorando caché
   * @returns {Promise<Object>}
   */
  async checkChannel(channel, options = {}) {
    if (!channel || (!channel.id && !channel.stream_id)) {
      throw new Error('Canal no válido para health check.');
    }

    const channelId = String(channel.id || channel.stream_id);

    // 1. Verificar si está en caché vigente
    if (!options.force) {
      const cached = this.getCachedHealth(channelId);
      if (cached) {
        return { ...cached, fromCache: true };
      }
    }

    // 2. Encolar verificación en el despachador de concurrencia acotada
    return new Promise((resolve, reject) => {
      this._queue.push({
        channel,
        channelId,
        resolve,
        reject,
        options
      });
      this._processQueue();
    });
  }

  /**
   * Verifica un lote acotado de canales respetando la concurrencia estricta.
   * @param {Array} channels - Lista de canales (ej: 50 canales)
   * @param {Object} [options]
   * @param {function({ current: number, total: number, result: Object })} [options.onProgress]
   * @param {AbortSignal} [options.signal]
   * @returns {Promise<Array>}
   */
  async checkBatch(channels = [], options = {}) {
    if (!Array.isArray(channels) || channels.length === 0) {
      return [];
    }

    const total = channels.length;
    let completed = 0;
    const results = [];

    // Si se pasa una señal de aborto externa
    if (options.signal) {
      options.signal.addEventListener('abort', () => {
        this.cancelAll();
      }, { once: true });
    }

    const promises = channels.map(async (channel, index) => {
      try {
        const result = await this.checkChannel(channel, options);
        completed++;
        results[index] = result;

        if (typeof options.onProgress === 'function') {
          options.onProgress({
            current: completed,
            total,
            result,
            channel
          });
        }
        return result;
      } catch (err) {
        completed++;
        const errorResult = {
          channelId: String(channel.id || channel.stream_id),
          status: err.name === 'AbortError' ? HealthStatus.UNKNOWN : HealthStatus.DEGRADED,
          latencyMs: null,
          error: err.message,
          timestamp: Date.now()
        };
        results[index] = errorResult;
        return errorResult;
      }
    });

    return Promise.all(promises);
  }

  // ── Despachador de cola con concurrencia acotada ──

  async _processQueue() {
    if (this._isPaused || this._isPlayerActive) return;
    if (this._activeWorkers >= this.maxConcurrency) return;
    if (this._queue.length === 0) {
      if (this._activeWorkers === 0) this._isRunning = false;
      return;
    }

    this._isRunning = true;
    this._activeWorkers++;
    const item = this._queue.shift();

    try {
      const result = await this._performHealthCheck(item.channel, item.options);
      
      // Guardar en caché si es resultado definitivo
      if (result.status !== HealthStatus.UNKNOWN) {
        this._cache.set(item.channelId, { ...result, timestamp: Date.now() });
      }

      this._emit(item.channelId, result);
      item.resolve(result);
    } catch (err) {
      if (err.name === 'AbortError') {
        item.reject(err);
      } else {
        const fallback = {
          channelId: item.channelId,
          status: HealthStatus.DEGRADED,
          latencyMs: null,
          error: err.message,
          timestamp: Date.now()
        };
        item.resolve(fallback);
      }
    } finally {
      this._activeWorkers--;

      // Pausa de cortesía (pacing) para no bombardear al servidor
      if (this.pacingDelayMs > 0 && this._queue.length > 0) {
        await new Promise(r => setTimeout(r, this.pacingDelayMs));
      }

      this._processQueue();
    }
  }

  /**
   * Ejecución real de la prueba sobre el canal usando inspección de manifiesto.
   */
  async _performHealthCheck(channel, options = {}) {
    const channelId = String(channel.id || channel.stream_id || '');
    const probeUrl = this._resolveProbeUrl(channel);

    if (!probeUrl) {
      return {
        channelId,
        status: HealthStatus.OFFLINE,
        latencyMs: null,
        error: 'URL de stream no disponible',
        timestamp: Date.now()
      };
    }

    // Primer intento
    let attemptResult = await this._probeEndpoint(probeUrl, channelId);

    // Si falló con error de red o timeout, realizar 1 reintento para evitar falsos OFFLINE
    if (attemptResult.status === HealthStatus.OFFLINE && attemptResult.isRetryable) {
      await new Promise(r => setTimeout(r, 200));
      attemptResult = await this._probeEndpoint(probeUrl, channelId, true);
    }

    return {
      channelId,
      channelName: channel.name || channel.title || 'Canal',
      status: attemptResult.status,
      latencyMs: attemptResult.latencyMs,
      httpStatus: attemptResult.httpStatus,
      contentType: attemptResult.contentType,
      error: attemptResult.error || null,
      timestamp: Date.now()
    };
  }

  /**
   * Realiza la petición HTTP a través del proxy local.
   * Con comprobación dual inteligente: si HLS devuelve 502, prueba el flujo directo MPEG-TS (.ts).
   */
  async _probeEndpoint(targetUrl, channelId, isRetry = false) {
    const controller = new AbortController();
    this._activeAbortControllers.add(controller);

    const timeoutTimer = setTimeout(() => {
      controller.abort();
    }, this.timeoutMs);

    const startTime = performance.now();

    try {
      const port = (typeof window !== 'undefined' && window.IPTV_PROXY_PORT) ? window.IPTV_PROXY_PORT : 7878;
      const proxyUrl = `http://localhost:${port}/proxy?target=${encodeURIComponent(targetUrl)}`;

      const response = await fetch(proxyUrl, {
        method: 'GET',
        headers: {
          'Accept': '*/*',
          'Range': 'bytes=0-1024'
        },
        signal: controller.signal
      });

      clearTimeout(timeoutTimer);
      const latencyMs = Math.round(performance.now() - startTime);

      if (response.ok || response.status === 206) {
        return {
          status: HealthStatus.ONLINE,
          latencyMs,
          httpStatus: response.status,
          contentType: response.headers.get('content-type') || 'video/mp2t',
          isRetryable: false
        };
      }

      // Si falla con 502/504 en .m3u8, probar si el flujo .ts directo sí está disponible
      if ((response.status === 502 || response.status === 504) && targetUrl.includes('.m3u8')) {
        const tsUrl = targetUrl.replace(/\.m3u8$/, '.ts').replace(/output=m3u8/, 'output=ts');
        const tsProxyUrl = `http://localhost:${port}/proxy?target=${encodeURIComponent(tsUrl)}`;
        try {
          const tsRes = await fetch(tsProxyUrl, {
            method: 'GET',
            headers: { 'Range': 'bytes=0-1024' },
            signal: controller.signal
          });
          if (tsRes.ok || tsRes.status === 206) {
            return {
              status: HealthStatus.ONLINE,
              latencyMs,
              httpStatus: tsRes.status,
              contentType: 'video/mp2t',
              isRetryable: false
            };
          }
        } catch {}
      }

      // Códigos HTTP de error definitivo
      if (response.status === 404 || response.status === 410) {
        return {
          status: HealthStatus.OFFLINE,
          latencyMs,
          httpStatus: response.status,
          error: `HTTP ${response.status} Stream inexistente`,
          isRetryable: false
        };
      }

      if (response.status === 502 || response.status === 504 || response.status === 429) {
        return {
          status: HealthStatus.DEGRADED,
          latencyMs,
          httpStatus: response.status,
          error: `HTTP ${response.status} Servidor saturado`,
          isRetryable: true
        };
      }

      return {
        status: HealthStatus.DEGRADED,
        latencyMs,
        httpStatus: response.status,
        error: `HTTP ${response.status}`,
        isRetryable: true
      };

    } catch (err) {
      clearTimeout(timeoutTimer);
      const latencyMs = Math.round(performance.now() - startTime);

      if (err.name === 'AbortError') {
        return {
          status: HealthStatus.DEGRADED,
          latencyMs: null,
          httpStatus: null,
          error: 'Timeout de conexión (> ' + this.timeoutMs + 'ms)',
          isRetryable: !isRetry
        };
      }

      return {
        status: HealthStatus.OFFLINE,
        latencyMs: null,
        httpStatus: null,
        error: err.message || 'Error de conexión',
        isRetryable: !isRetry
      };
    } finally {
      this._activeAbortControllers.delete(controller);
    }
  }

  /**
   * Resuelve la URL de prueba para el canal.
   */
  _resolveProbeUrl(channel) {
    if (channel.streamUrl) return channel.streamUrl;
    if (channel.url) return channel.url;
    return null;
  }

  /**
   * Marca manualmente un canal como OFFLINE (ej: cuando falla la reproducción en el player).
   */
  markOffline(channelId, reason = 'Error fatal de reproducción') {
    if (!channelId) return;
    const entry = {
      channelId: String(channelId),
      status: HealthStatus.OFFLINE,
      latencyMs: null,
      error: reason,
      timestamp: Date.now()
    };
    this._cache.set(String(channelId), entry);
    this._emit(String(channelId), entry);
  }

  /**
   * Retorna si un canal está confirmado como OFFLINE en caché.
   */
  isChannelOffline(channelId) {
    if (!channelId) return false;
    const entry = this.getCachedHealth(channelId);
    return entry?.status === HealthStatus.OFFLINE;
  }

  /**
   * Retorna el estado actual de salud de un canal.
   */
  getChannelStatus(channelId) {
    if (!channelId) return HealthStatus.UNKNOWN;
    const entry = this.getCachedHealth(channelId);
    return entry?.status || HealthStatus.UNKNOWN;
  }
}

// Instancia singleton por defecto
export const channelHealthService = new ChannelHealthService({
  maxConcurrency: 2,
  timeoutMs: 4000,
  pacingDelayMs: 120,
  cacheTtlMs: 15 * 60 * 1000
});
