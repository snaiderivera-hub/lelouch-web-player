/**
 * @module PlayerService
 * Servicio centralizado de reproducción de video.
 * Controla instancias de Hls.js, mpegts.js y Video HTML5.
 * Proporciona resiliencia ante errores con reconexión automática (máx 3 intentos),
 * registro de progreso ("Continuar viendo") y gestión estricta de memoria.
 */

import { cacheService } from './CacheService.js';

function getProxyUrl(rawUrl) {
  if (!rawUrl) return '';
  if (rawUrl.includes('/api/proxy') || rawUrl.includes('/proxy?target=')) return rawUrl;
  if (typeof window !== 'undefined' && (window.location.protocol === 'https:' || (!window.location.hostname.includes('localhost') && !window.location.hostname.includes('127.0.0.1')))) {
    return `${window.location.origin}/api/proxy?target=${encodeURIComponent(rawUrl)}`;
  }
  const port = (typeof window !== 'undefined' && window.IPTV_PROXY_PORT) ? window.IPTV_PROXY_PORT : 7878;
  if (rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) {
    return `http://localhost:${port}/proxy?target=${encodeURIComponent(rawUrl)}`;
  }
  return rawUrl;
}

export const PlayerState = {
  IDLE: 'IDLE',
  LOADING: 'LOADING',
  PLAYING: 'PLAYING',
  BUFFERING: 'BUFFERING',
  ERROR: 'ERROR',
  RECONNECTING: 'RECONNECTING'
};

class PlayerService {
  constructor() {
    this._videoEl = null;
    this._hls = null;
    this._mpegts = null;
    this._currentMedia = null;
    this._state = PlayerState.IDLE;
    this._retryCount = 0;
    this._maxRetries = 3;
    this._retryTimer = null;
    this._progressThrottleTimer = null;
    this._listeners = new Set();
    // Modo de emisión: 'ts' (MPEG-TS directo, recomendado para Xtream) | 'auto' | 'hls'
    this.streamFormat = (typeof localStorage !== 'undefined' && localStorage.getItem('iptv_stream_format')) || 'ts';
  }

  /**
   * Cambia el modo de reproducción de stream (ts, auto, hls) y lo persiste.
   * @param {'ts'|'auto'|'hls'} format
   */
  setStreamFormat(format) {
    if (['ts', 'auto', 'hls'].includes(format)) {
      this.streamFormat = format;
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('iptv_stream_format', format);
      }
      if (this._currentMedia && this._currentMedia.type === 'live') {
        this.play(this._currentMedia);
      }
    }
  }

  /**
   * Suscribe un listener a cambios de estado del player.
   * @param {function({ state: string, message: string, media: Object })} fn
   */
  onStateChange(fn) {
    this._listeners.add(fn);
    return () => this._listeners.delete(fn);
  }

  _notify(state, message = '') {
    this._state = state;
    for (const fn of this._listeners) {
      try {
        fn({ state, message, media: this._currentMedia });
      } catch (e) {
        console.error('[PlayerService] Error in state listener:', e);
      }
    }
  }

  /**
   * Asocia el elemento <video> principal.
   * @param {HTMLVideoElement} videoElement
   */
  attachVideoElement(videoElement) {
    if (this._videoEl === videoElement) return;
    this.destroy();
    this._videoEl = videoElement;

    this._videoEl.addEventListener('playing', () => this._notify(PlayerState.PLAYING));
    this._videoEl.addEventListener('waiting', () => this._notify(PlayerState.BUFFERING, 'Cargando...'));
    this._videoEl.addEventListener('timeupdate', () => this._handleTimeUpdate());
    this._videoEl.addEventListener('error', (e) => this._handleNativeError(e));
    this._videoEl.addEventListener('ended', () => {
      if (this._currentMedia?.type === 'live') {
        console.warn('[PlayerService] El stream en vivo finalizó o se cortó el socket remoto. Reconectando...');
        this._notify(PlayerState.RECONNECTING, 'Reconectando señal en vivo...');
        setTimeout(() => {
          if (this._currentMedia?.type === 'live') {
            this.play(this._currentMedia);
          }
        }, 800);
      }
    });
  }

  forward(delta = 10) {
    if (!this._videoEl) return;
    if (this._currentMedia?.type === 'live') {
      if (this._videoEl.buffered.length > 0) {
        this._videoEl.currentTime = this._videoEl.buffered.end(this._videoEl.buffered.length - 1);
      }
    } else {
      this._videoEl.currentTime = Math.min(this._videoEl.duration || Infinity, this._videoEl.currentTime + delta);
    }
  }

  rewind(delta = 10) {
    if (!this._videoEl) return;
    this._videoEl.currentTime = Math.max(0, this._videoEl.currentTime - delta);
  }

  togglePlay() {
    if (!this._videoEl) return;
    if (this._videoEl.paused) this._videoEl.play().catch(() => {});
    else this._videoEl.pause();
  }

  toggleMute() {
    if (!this._videoEl) return;
    this._videoEl.muted = !this._videoEl.muted;
  }

  /**
   * Carga y reproduce un medio (Live, VOD, Episodio).
   * @param {{ id: string, title: string, url: string, type: 'live'|'vod'|'series', poster?: string, startTime?: number }} media
   */
  play(media) {
    if (!this._videoEl) {
      console.warn('[PlayerService] No hay elemento video asociado.');
      return;
    }

    this._clearRetry();
    this._destroyEngines();
    this._currentMedia = media;
    this._retryCount = 0;
    this._triedHlsFallback = false;
    this._triedTsFallback = false;

    this._notify(PlayerState.LOADING, 'Iniciando stream...');

    let rawUrl = media.url;

    // Adaptación según el formato preferido por el usuario
    if (media.type === 'live') {
      if (this.streamFormat === 'ts' && rawUrl.includes('.m3u8')) {
        rawUrl = rawUrl.replace(/\.m3u8$/, '.ts').replace(/output=m3u8/, 'output=ts');
      } else if (this.streamFormat === 'hls' && rawUrl.includes('.ts')) {
        rawUrl = rawUrl.replace(/\.ts$/, '.m3u8').replace(/output=ts/, 'output=m3u8');
      }
      // En modo 'auto': se utiliza el formato nativo del canal (.m3u8 o .ts).
      // Si HLS tiene 502/503 o fragmentos caídos, conmuta en milisegundos a MPEG-TS (.ts).
      // Si MPEG-TS sufre error MSE de códec (como MP3), conmuta en milisegundos a HLS (.m3u8).
    }

    const proxiedUrl = getProxyUrl(rawUrl);

    // 1. Si el usuario eligió MPEG-TS (.ts) o el stream es .ts directo
    if (media.type === 'live' && (this.streamFormat === 'ts' || (this.streamFormat !== 'hls' && rawUrl.includes('.ts')))) {
      if (window.mpegts && window.mpegts.isSupported()) {
        this._playMpegts(proxiedUrl, media);
        return;
      }
    }

    // 2. Si es formato HLS (.m3u8) o modo Auto
    if (window.Hls && window.Hls.isSupported() && (rawUrl.includes('.m3u8') || rawUrl.includes('output=m3u8'))) {
      this._playHls(proxiedUrl, media);
    }
    // 3. Fallback a MPEG-TS o Live
    else if (window.mpegts && window.mpegts.isSupported() && (rawUrl.includes('.ts') || media.type === 'live')) {
      this._playMpegts(proxiedUrl, media);
    }
    // 4. Fallback a HTML5 Video directo (mp4, mkv)
    else {
      this._playHtml5(proxiedUrl, media);
    }
  }

  _playHls(url, media) {
    let remoteOrigin = '';
    try {
      if (media?.url) {
        remoteOrigin = new URL(media.url).origin;
      }
    } catch {}

    const hls = new window.Hls({
      enableWorker: true,
      lowLatencyMode: true,
      backBufferLength: 60,
      fragLoadingMaxRetry: 1,
      manifestLoadingMaxRetry: 1,
      levelLoadingMaxRetry: 1,
      xhrSetup: (xhr, reqUrl) => {
        let targetUrl = reqUrl;

        // Si Hls.js resolvió una URL relativa contra localhost, redirigirla al servidor remoto original
        if (targetUrl.includes('/proxy?target=')) {
          return;
        }
        if (targetUrl.startsWith('/') && !targetUrl.startsWith('//')) {
          if (remoteOrigin) {
            targetUrl = remoteOrigin + targetUrl;
          }
        }

        if (targetUrl.startsWith('http://') || targetUrl.startsWith('https://')) {
          const proxied = getProxyUrl(targetUrl);
          if (proxied !== targetUrl) {
            xhr.open('GET', proxied, true);
          }
        }
      }
    });

    this._hls = hls;
    hls.loadSource(url);
    hls.attachMedia(this._videoEl);

    hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
      if (media.startTime && media.startTime > 0) {
        this._videoEl.currentTime = media.startTime;
      }
      this._videoEl.play().catch(() => {});
    });

    hls.on(window.Hls.Events.ERROR, (event, data) => {
      const statusCode = data.response?.code || data.response?.status;
      const isGatewayOrServerError = statusCode >= 500 && statusCode < 600;

      if (data.fatal || isGatewayOrServerError || data.details === 'fragLoadError') {
        if (media?.type === 'live') {
          if (!this._triedTsFallback) {
            this._triedTsFallback = true;
            console.warn(`[PlayerService] HLS no disponible (${statusCode ? `HTTP ${statusCode}` : data.details}). Conmutando a MPEG-TS (.ts)...`);
            this._fallbackMpegts(url, media);
            return;
          } else {
            console.warn('[PlayerService] Tanto HLS como MPEG-TS están caídos en el servidor remoto.');
            this._notify(PlayerState.ERROR, `Canal caído en el servidor (502 Bad Gateway).`);
            return;
          }
        }

        if (data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
          this._notify(PlayerState.RECONNECTING, 'Recuperando decodificador...');
          hls.recoverMediaError();
          return;
        }

        this._handleErrorWithRetry(statusCode ? `HTTP ${statusCode}` : 'Error de conexión en HLS');
      }
    });
  }

  _fallbackMpegts(url, media) {
    this._destroyEngines();
    let raw = media.url;
    if (raw.includes('.m3u8')) {
      raw = raw.replace(/\.m3u8$/, '.ts').replace(/output=m3u8/, 'output=ts');
    }
    const proxiedTs = getProxyUrl(raw);
    this._notify(PlayerState.LOADING, 'HLS caído. Conmutando a MPEG-TS directo (.ts)...');
    this._playMpegts(proxiedTs, media);
  }

  _fallbackHls(url, media) {
    this._destroyEngines();
    let raw = media.url;
    if (raw.includes('.ts')) {
      raw = raw.replace(/\.ts$/, '.m3u8').replace(/output=ts/, 'output=m3u8');
    }
    const proxiedHls = getProxyUrl(raw);
    this._notify(PlayerState.LOADING, 'Conmutando a HLS (.m3u8)...');
    this._playHls(proxiedHls, media);
  }

  _playMpegts(url, media) {
    try {
      const isLive = media.type === 'live';
      const player = window.mpegts.createPlayer({
        type: 'mse',
        isLive: isLive,
        url: url,
      }, {
        enableWorker: true,
        lazyLoad: false,
        liveBufferLatencyChasing: false,
        liveBufferLatencyMaxLatency: 6.0,
        liveBufferLatencyMinRemain: 1.5,
        autoCleanupSourceBuffer: true,
        autoCleanupMaxBackwardDuration: 30,
        autoCleanupMinBackwardDuration: 15,
        fixAudioTimestampGap: true,
        accurateSeek: false,
        stashInitialSize: 512 * 1024,
      });

      this._mpegts = player;
      player.attachMediaElement(this._videoEl);
      player.load();
      player.play().catch(() => {});

      player.on(window.mpegts.Events.ERROR, (errType, errDetail) => {
        if (media?.type === 'live') {
          if (!this._triedHlsFallback) {
            this._triedHlsFallback = true;
            console.warn(`[PlayerService] MPEG-TS falló (${errType}: ${errDetail}). Alternando a HLS (.m3u8)...`);
            this._fallbackHls(url, media);
            return;
          } else {
            console.warn('[PlayerService] Tanto MPEG-TS como HLS están caídos en el servidor remoto.');
            this._notify(PlayerState.ERROR, `Canal caído en el servidor (${errDetail || '502 Bad Gateway'}).`);
            return;
          }
        }
        this._handleErrorWithRetry(`MPEG-TS ERROR: ${errDetail || errType}`);
      });
    } catch (e) {
      if (!this._triedHlsFallback && media?.type === 'live') {
        this._triedHlsFallback = true;
        this._fallbackHls(url, media);
      } else {
        this._playHtml5(url, media);
      }
    }
  }

  _playHtml5(url, media) {
    this._videoEl.src = url;
    if (media.startTime && media.startTime > 0) {
      this._videoEl.currentTime = media.startTime;
    }
    this._videoEl.play().catch((err) => {
      if (err.name !== 'AbortError') {
        this._handleErrorWithRetry(`HTML5 VIDEO ERROR: ${err.message}`);
      }
    });
  }

  _handleErrorWithRetry(errMsg) {
    // Si el error es 502/Bad Gateway o canal caído, no insistir en reintentos infinitos
    if (errMsg && (errMsg.includes('502') || errMsg.includes('Bad Gateway') || errMsg.includes('caído') || errMsg.includes('fuera de servicio'))) {
      this._notify(PlayerState.ERROR, errMsg || 'Canal caído en el proveedor.');
      return;
    }

    if (this._retryCount < this._maxRetries) {
      this._retryCount++;
      const delay = this._retryCount * 2000;
      this._notify(PlayerState.RECONNECTING, `Reintentando (${this._retryCount}/${this._maxRetries}) en ${delay / 1000}s...`);

      this._retryTimer = setTimeout(() => {
        if (this._currentMedia) {
          this.play(this._currentMedia);
        }
      }, delay);
    } else {
      this._notify(PlayerState.ERROR, errMsg || 'No se pudo reproducir el stream tras varios intentos.');
    }
  }

  _handleNativeError() {
    if (this._videoEl?.error) {
      const code = this._videoEl.error.code;
      if (this._currentMedia?.type === 'live') {
        if (this._mpegts && !this._triedHlsFallback) {
          this._triedHlsFallback = true;
          console.warn('[PlayerService] Error nativo decodificando MPEG-TS. Conmutando a HLS (.m3u8)...');
          this._fallbackHls(this._currentMedia.url, this._currentMedia);
          return;
        } else if (this._hls && !this._triedTsFallback) {
          this._triedTsFallback = true;
          console.warn('[PlayerService] Error nativo en HLS. Conmutando a MPEG-TS (.ts)...');
          this._fallbackMpegts(this._currentMedia.url, this._currentMedia);
          return;
        }
      }

      const msgs = {
        1: 'Reproducción abortada por el usuario.',
        2: 'Error de red al descargar el medio.',
        3: 'Error al decodificar video/audio.',
        4: 'Formato o códec no soportado por el navegador.'
      };
      this._handleErrorWithRetry(msgs[code] || 'Error de reproducción nativo.');
    }
  }

  _handleTimeUpdate() {
    if (!this._videoEl || !this._currentMedia || this._currentMedia.type === 'live') return;

    // Throttle progress updates a IndexedDB cada 6 segundos
    if (!this._progressThrottleTimer) {
      this._progressThrottleTimer = setTimeout(() => {
        this._progressThrottleTimer = null;
        if (this._videoEl && this._currentMedia && this._videoEl.currentTime > 5) {
          cacheService.updateHistoryProgress({
            contentId: this._currentMedia.id,
            type: this._currentMedia.type,
            title: this._currentMedia.title,
            poster: this._currentMedia.poster,
            currentTime: this._videoEl.currentTime,
            duration: this._videoEl.duration || 0,
            streamUrl: this._currentMedia.url
          });
        }
      }, 6000);
    }
  }

  _clearRetry() {
    if (this._retryTimer) {
      clearTimeout(this._retryTimer);
      this._retryTimer = null;
    }
  }

  _destroyEngines() {
    this._clearRetry();
    if (this._hls) {
      try { this._hls.destroy(); } catch {}
      this._hls = null;
    }
    if (this._mpegts) {
      try {
        this._mpegts.pause();
        this._mpegts.unload();
        this._mpegts.detachMediaElement();
        this._mpegts.destroy();
      } catch {}
      this._mpegts = null;
    }
    if (this._videoEl) {
      this._videoEl.pause();
      this._videoEl.removeAttribute('src');
      this._videoEl.load();
    }
  }

  stop() {
    this.destroy();
    this._notify(PlayerState.IDLE, '');
  }

  destroy() {
    this._destroyEngines();
    this._currentMedia = null;
    this._state = PlayerState.IDLE;
    if (this._progressThrottleTimer) {
      clearTimeout(this._progressThrottleTimer);
      this._progressThrottleTimer = null;
    }
  }

  get state() {
    return this._state;
  }

  get currentMedia() {
    return this._currentMedia;
  }
}

export const playerService = new PlayerService();
