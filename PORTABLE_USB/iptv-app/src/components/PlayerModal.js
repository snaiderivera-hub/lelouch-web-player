/**
 * @module PlayerModal
 * Reproductor de video web multiformato con Hls.js + mpegts.js (para flujos .ts IPTV).
 * Soporta streaming en vivo, VOD (Películas) y Series.
 * Incluye badges de resolución, atajos de teclado, selector de calidad, PiP y botón para abrir en VLC.
 */

import { playerService } from '../modules/iptv/services/PlayerService.js';

function getProxyUrl(rawUrl) {
  if (!rawUrl) return '';
  const port = (typeof window !== 'undefined' && window.IPTV_PROXY_PORT) ? window.IPTV_PROXY_PORT : 7878;
  if (rawUrl.includes(`localhost:${port}/proxy`)) return rawUrl;
  if (rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) {
    return `http://localhost:${port}/proxy?target=${encodeURIComponent(rawUrl)}`;
  }
  return rawUrl;
}

export class PlayerModal {
  constructor() {
    this._modalEl = null;
    this._videoEl = null;
    this._hls = null;
    this._mpegts = null;
    this._currentStream = null;
    this._keyboardHandler = null;
    this.init();
  }

  init() {
    if (!document.getElementById('player-modal')) {
      const modal = document.createElement('div');
      modal.id = 'player-modal';
      modal.className = 'modal-overlay hidden';
      modal.innerHTML = `
        <div class="player-modal-content">
          <div class="player-header">
            <div class="player-title-box">
              <span class="player-badge" id="player-quality-badge">AUTO</span>
              <h3 id="player-stream-title" class="player-title">Reproduciendo...</h3>
            </div>
            <button id="player-close-btn" class="modal-close-btn" title="Cerrar (Esc)">&times;</button>
          </div>

          <div class="player-video-container">
            <video id="player-video" controls autoplay playsinline class="video-element"></video>
            <div id="player-loading-spinner" class="player-spinner hidden">
              <div class="spinner"></div>
              <span class="buffering-text">BUFFERING</span>
            </div>
          </div>

          <div id="player-vod-audio-warning" class="hidden" style="background: rgba(245, 158, 11, 0.1); border-top: 1px solid rgba(245, 158, 11, 0.3); color: #fbbf24; font-size: 0.8rem; padding: 0.4rem; text-align: center; width: 100%;">
            ⚠️ ¿Sin sonido? Tu navegador web bloquea formatos de audio de cine. Usa el botón <b>▶️ Abrir en VLC</b> abajo para escucharlo perfectamente.
          </div>

          <div class="player-footer">
            <div class="player-controls-extra">
              <label for="player-level-select">Calidad:</label>
              <select id="player-level-select" class="player-select">
                <option value="-1">Automática</option>
              </select>
              <button id="player-rewind-btn" class="btn btn-secondary btn-sm" title="Retroceder 10s (←)">⏪ -10s</button>
              <button id="player-forward-btn" class="btn btn-secondary btn-sm" title="Adelantar 10s (→)">⏩ +10s</button>
              <button id="player-pip-btn" class="btn btn-secondary btn-sm">📺 PiP</button>
              <button id="player-vlc-btn" class="btn btn-primary btn-sm">▶️ Abrir en VLC</button>
            </div>
            <div class="player-shortcuts-hint">
              <span><b>Espacio</b>: Play/Pausa</span> | 
              <span><b>← / →</b>: ±10s</span> | 
              <span><b>F</b>: Pantalla Completa</span> | 
              <span><b>M</b>: Silenciar</span> | 
              <span><b>Esc</b>: Cerrar</span>
            </div>
          </div>
        </div>
      `;
      document.body.appendChild(modal);
    }

    this._modalEl = document.getElementById('player-modal');
    this._videoEl = document.getElementById('player-video');

    document.getElementById('player-close-btn').addEventListener('click', () => this.close());
    document.getElementById('player-rewind-btn')?.addEventListener('click', () => {
      this._videoEl.currentTime = Math.max(0, this._videoEl.currentTime - 10);
    });
    document.getElementById('player-forward-btn')?.addEventListener('click', () => {
      if (this._currentStream?.type === 'live') {
        if (this._videoEl.buffered.length > 0) {
          this._videoEl.currentTime = this._videoEl.buffered.end(this._videoEl.buffered.length - 1);
        }
      } else {
        this._videoEl.currentTime = Math.min(this._videoEl.duration || Infinity, this._videoEl.currentTime + 10);
      }
    });
    document.getElementById('player-pip-btn').addEventListener('click', () => this.togglePiP());
    document.getElementById('player-vlc-btn').addEventListener('click', () => this.openInVLC());

    this._modalEl.addEventListener('click', (e) => {
      if (e.target === this._modalEl) this.close();
    });

    this._videoEl.addEventListener('ended', () => {
      if (this._currentStream?.type === 'live') {
        console.warn('[PlayerModal] El stream en vivo finalizó o se cortó el socket remoto. Reconectando...');
        const spinner = document.getElementById('player-loading-spinner');
        const badgeEl = document.getElementById('player-quality-badge');
        if (spinner) spinner.classList.remove('hidden');
        if (badgeEl) {
          badgeEl.textContent = 'RECONECTANDO';
          badgeEl.className = 'player-badge badge-loading';
        }
        setTimeout(() => {
          if (this._currentStream && !this._modalEl.classList.contains('hidden')) {
            this.open(this._currentStream);
          }
        }, 1000);
      }
    });
  }

  /**
   * Abre el reproductor y carga la fuente de video usando el motor adecuado (Hls.js, mpegts.js o HTML5)
   * @param {Object} streamData - { title, url, type }
   */
  open(streamData) {
    this._currentStream = streamData;
    const titleEl = document.getElementById('player-stream-title');
    const badgeEl = document.getElementById('player-quality-badge');
    const levelSelect = document.getElementById('player-level-select');
    const spinner = document.getElementById('player-loading-spinner');
    const audioWarning = document.getElementById('player-vod-audio-warning');

    titleEl.textContent = streamData.title || 'Stream IPTV';
    badgeEl.textContent = 'CARGANDO';
    badgeEl.className = 'player-badge badge-loading';
    spinner.classList.remove('hidden');

    if (audioWarning) {
      audioWarning.classList.toggle('hidden', streamData.type === 'live');
    }

    // Detener de inmediato cualquier stream de Live TV para evitar cruce de audio o video
    try {
      playerService.stop();
    } catch {}
    const integratedVid = document.getElementById('integrated-video');
    if (integratedVid) {
      try {
        integratedVid.pause();
        integratedVid.removeAttribute('src');
        integratedVid.load();
      } catch {}
    }
    const plPH = document.getElementById('integrated-player-placeholder');
    if (plPH) plPH.classList.remove('hidden');
    const integratedSpinner = document.getElementById('integrated-player-spinner');
    if (integratedSpinner) integratedSpinner.classList.add('hidden');

    this._modalEl.classList.remove('hidden');

    this.cleanupEngine();

    if (!streamData._isFallback) {
      this._triedHlsFallback = false;
      this._triedTsFallback = false;
    }

    let rawStreamUrl = streamData.url;
    const streamFormat = (typeof localStorage !== 'undefined' && localStorage.getItem('iptv_stream_format')) || 'ts';

    if (streamData.type === 'live') {
      if (streamFormat === 'ts' && rawStreamUrl.includes('.m3u8')) {
        rawStreamUrl = rawStreamUrl.replace(/\.m3u8$/, '.ts').replace(/output=m3u8/, 'output=ts');
      } else if (streamFormat === 'hls' && rawStreamUrl.includes('.ts')) {
        rawStreamUrl = rawStreamUrl.replace(/\.ts$/, '.m3u8').replace(/output=ts/, 'output=m3u8');
      } else if (streamFormat === 'auto') {
        if (rawStreamUrl.includes('/live/') && rawStreamUrl.includes('.m3u8')) {
          rawStreamUrl = rawStreamUrl.replace(/\.m3u8$/, '.ts').replace(/output=m3u8/, 'output=ts');
        }
      }
    }

    const proxiedStreamUrl = getProxyUrl(rawStreamUrl);

    // 1. Si el usuario eligió MPEG-TS o el stream es .ts
    if (streamData.type === 'live' && (streamFormat === 'ts' || rawStreamUrl.includes('.ts'))) {
      if (window.mpegts && window.mpegts.isSupported()) {
        this.fallbackMpegts(proxiedStreamUrl, badgeEl, spinner);
        return;
      }
    }

    // 2. Si es M3U8 -> Usar Hls.js
    if (window.Hls && window.Hls.isSupported() && rawStreamUrl.includes('.m3u8')) {
      const hls = new window.Hls({
        enableWorker: true,
        lowLatencyMode: true,
        fragLoadingMaxRetry: 1,
        manifestLoadingMaxRetry: 1,
        levelLoadingMaxRetry: 1,
        xhrSetup: (xhr, url) => {
          let resolved = url;
          if (resolved.startsWith('/') && !resolved.startsWith('//')) {
            const origin = (streamData.url && streamData.url.startsWith('http')) 
              ? new URL(streamData.url).origin 
              : 'http://liontv.es:80';
            resolved = `${origin}${resolved}`;
          }
          const port = (typeof window !== 'undefined' && window.IPTV_PROXY_PORT) ? window.IPTV_PROXY_PORT : 7878;
          if (resolved.startsWith('http://') && !resolved.includes(`localhost:${port}/proxy`)) {
            const proxied = `http://localhost:${port}/proxy?target=${encodeURIComponent(resolved)}`;
            xhr.open('GET', proxied, true);
          }
        }
      });

      this._hls = hls;
      hls.loadSource(proxiedStreamUrl);
      hls.attachMedia(this._videoEl);

      hls.on(window.Hls.Events.MANIFEST_PARSED, (event, data) => {
        spinner.classList.add('hidden');
        this._videoEl.play().catch(() => {});

        levelSelect.innerHTML = '<option value="-1">Automática</option>';
        data.levels.forEach((level, index) => {
          const opt = document.createElement('option');
          opt.value = index;
          opt.textContent = `${level.height}p (${Math.round(level.bitrate / 1000)} kbps)`;
          levelSelect.appendChild(opt);
        });

        badgeEl.textContent = 'HLS';
        badgeEl.className = 'player-badge badge-hls';
      });

      hls.on(window.Hls.Events.LEVEL_SWITCHED, (event, data) => {
        const level = hls.levels[data.level];
        if (level && level.height) {
          badgeEl.textContent = `${level.height}P`;
          badgeEl.className = level.height >= 1080 ? 'player-badge badge-fhd' : 'player-badge badge-hd';
        }
      });

      hls.on(window.Hls.Events.ERROR, (event, data) => {
        const statusCode = data.response?.code || data.response?.status;
        if (data.fatal || (statusCode >= 500 && statusCode < 600) || data.details === 'fragLoadError') {
          if (!this._triedTsFallback) {
            this._triedTsFallback = true;
            this.fallbackMpegts(proxiedStreamUrl, badgeEl, spinner);
          } else {
            spinner.classList.add('hidden');
            badgeEl.textContent = 'CANAL CAÍDO';
            badgeEl.className = 'player-badge badge-error';
          }
        }
      });

      levelSelect.onchange = () => {
        hls.currentLevel = parseInt(levelSelect.value, 10);
      };
    } 
    // 2. Si es .ts o Canal Live Xtream -> Usar mpegts.js (HTML5 MPEG-TS Demuxer)
    else if (window.mpegts && window.mpegts.isSupported() && (rawStreamUrl.includes('.ts') || streamData.type === 'live')) {
      this.fallbackMpegts(proxiedStreamUrl, badgeEl, spinner);
    } 
    // 3. VOD (MP4 / MKV) -> HTML5 Nativo (Sin proxy, directo para permitir Range Requests y buffering fluido)
    else {
      this._videoEl.src = rawStreamUrl;
      this._videoEl.play().then(() => {
        spinner.classList.add('hidden');
        badgeEl.textContent = rawStreamUrl.endsWith('.mkv') ? 'MKV' : 'MP4';
        badgeEl.className = 'player-badge badge-vod';
      }).catch(() => {
        spinner.classList.add('hidden');
        badgeEl.textContent = 'REPRODUCIENDO';
      });
    }

    // Registrar atajos de teclado
    this._keyboardHandler = (e) => {
      if (this._modalEl.classList.contains('hidden')) return;

      if (e.key === 'Escape') {
        this.close();
      } else if (e.key === ' ' || e.key === 'k') {
        e.preventDefault();
        if (this._videoEl.paused) this._videoEl.play();
        else this._videoEl.pause();
      } else if (e.key === 'f' || e.key === 'F') {
        e.preventDefault();
        if (document.fullscreenElement) document.exitFullscreen();
        else this._videoEl.requestFullscreen().catch(() => {});
      } else if (e.key === 'm' || e.key === 'M') {
        e.preventDefault();
        this._videoEl.muted = !this._videoEl.muted;
      } else if (e.key === 'ArrowRight' || e.key === 'l' || e.key === 'L') {
        e.preventDefault();
        if (this._currentStream?.type === 'live') {
          if (this._videoEl.buffered.length > 0) {
            this._videoEl.currentTime = this._videoEl.buffered.end(this._videoEl.buffered.length - 1);
          }
        } else {
          this._videoEl.currentTime = Math.min(this._videoEl.duration || Infinity, this._videoEl.currentTime + 10);
        }
      } else if (e.key === 'ArrowLeft' || e.key === 'j' || e.key === 'J') {
        e.preventDefault();
        this._videoEl.currentTime = Math.max(0, this._videoEl.currentTime - 10);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        this._videoEl.volume = Math.min(1, this._videoEl.volume + 0.1);
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        this._videoEl.volume = Math.max(0, this._videoEl.volume - 0.1);
      }
    };

    window.addEventListener('keydown', this._keyboardHandler);
  }

  fallbackMpegts(streamUrl, badgeEl, spinner) {
    try {
      if (window.mpegts && window.mpegts.isSupported()) {
        const player = window.mpegts.createPlayer({
          type: 'mse',
          isLive: true,
          url: streamUrl,
        }, {
          enableWorker: true,
          lazyLoad: false,
          liveBufferLatencyChasing: true,
          liveBufferLatencyMaxLatency: 5.0,
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
        player.play().then(() => {
          spinner.classList.add('hidden');
          badgeEl.textContent = 'MPEG-TS';
          badgeEl.className = 'player-badge badge-hd';
        }).catch(() => {
          spinner.classList.add('hidden');
          badgeEl.textContent = 'LIVE TS';
        });

        player.on(window.mpegts.Events.ERROR, () => {
          if (!this._triedHlsFallback && this._currentStream?.type === 'live') {
            this._triedHlsFallback = true;
            console.warn('[PlayerModal] Error en MPEG-TS. Conmutando a HLS...');
            let hlsUrl = this._currentStream.url;
            if (hlsUrl.includes('.ts')) hlsUrl = hlsUrl.replace(/\.ts$/, '.m3u8').replace(/output=ts/, 'output=m3u8');
            this.open({ ...this._currentStream, url: hlsUrl, _isFallback: true });
            return;
          }
          spinner.classList.add('hidden');
          badgeEl.textContent = 'CANAL CAÍDO';
          badgeEl.className = 'player-badge badge-error';
        });
      } else {
        this._videoEl.src = streamUrl;
        this._videoEl.play().catch(() => {});
        spinner.classList.add('hidden');
      }
    } catch {
      spinner.classList.add('hidden');
    }
  }

  async openInVLC(customUrl = null, customTitle = null) {
    const url = customUrl || this._currentStream?.url;
    const title = customTitle || this._currentStream?.title || 'Stream IPTV';
    if (!url) return;

    const btn = document.getElementById('player-vlc-btn');
    const originalText = btn ? btn.innerHTML : '▶️ Abrir en VLC';
    if (btn) btn.innerHTML = '⏳ Abriendo en VLC...';

    try {
      const port = (typeof window !== 'undefined' && window.location.port) ? window.location.port : 8686;
      const res = await fetch(`http://localhost:${port}/api/open-vlc?url=${encodeURIComponent(url)}`);
      const data = await res.json();

      if (data.installed && data.success) {
        if (this._videoEl) this._videoEl.pause();
        if (btn) {
          btn.innerHTML = '✅ VLC Iniciado';
          btn.style.background = '#10b981';
        }
        setTimeout(() => {
          if (btn) {
            btn.innerHTML = originalText;
            btn.style.background = '';
          }
        }, 3500);
        return;
      } else if (!data.installed) {
        if (btn) btn.innerHTML = originalText;
        this.showVlcNotInstalledModal(url, title);
        return;
      }
    } catch {
      // Si la llamada falla, mostrar modal con aviso y descarga
    }

    if (btn) btn.innerHTML = originalText;
    this.showVlcNotInstalledModal(url, title);
  }

  showVlcNotInstalledModal(streamUrl, title) {
    let modal = document.getElementById('vlc-not-installed-modal');
    if (!modal) {
      modal = document.createElement('div');
      modal.id = 'vlc-not-installed-modal';
      modal.className = 'modal-overlay';
      document.body.appendChild(modal);
    }

    modal.innerHTML = `
      <div class="player-modal-content" style="max-width: 460px; padding: 1.75rem; text-align: center; border-radius: 14px; background: #0f172a; border: 1px solid #334155; color: #f8fafc; box-shadow: 0 20px 40px rgba(0,0,0,0.6);">
        <div style="font-size: 3.2rem; margin-bottom: 0.5rem; line-height: 1;">⚠️</div>
        <h3 style="margin-bottom: 0.75rem; font-size: 1.25rem; font-weight: 700; color: #f8fafc;">VLC Media Player no está instalado</h3>
        <p style="color: #94a3b8; font-size: 0.9rem; line-height: 1.5; margin-bottom: 1.5rem;">
          Para reproducir transmisiones directamente en la aplicación externa, necesitas tener instalado <strong>VLC Media Player</strong> en tu computadora. Es gratuito, sin publicidad y reproduce cualquier formato de video.
        </p>
        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          <a href="https://www.videolan.org/vlc/" target="_blank" rel="noopener noreferrer" class="btn btn-primary" style="padding: 0.75rem 1rem; text-decoration: none; display: block; font-weight: 600; font-size: 0.95rem; border-radius: 8px;">
            ⬇️ Descargar VLC Oficial (Gratis)
          </a>
          <button id="vlc-download-m3u" class="btn btn-secondary" style="padding: 0.7rem 1rem; font-size: 0.9rem; border-radius: 8px;">
            📄 Descargar lista .M3U (Para otro reproductor)
          </button>
          <button id="vlc-modal-close" class="btn" style="padding: 0.5rem; background: transparent; border: none; color: #64748b; font-size: 0.85rem; cursor: pointer;">
            Cerrar
          </button>
        </div>
      </div>
    `;

    modal.classList.remove('hidden');

    document.getElementById('vlc-modal-close')?.addEventListener('click', () => {
      modal.classList.add('hidden');
    });

    document.getElementById('vlc-download-m3u')?.addEventListener('click', () => {
      const m3uContent = `#EXTM3U\n#EXTINF:-1,${title || 'Stream'}\n${streamUrl}`;
      const blob = new Blob([m3uContent], { type: 'audio/x-mpegurl' });
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = `stream_${Date.now()}.m3u`;
      a.click();
      URL.revokeObjectURL(blobUrl);
      modal.classList.add('hidden');
    });

    modal.onclick = (e) => {
      if (e.target === modal) modal.classList.add('hidden');
    };
  }

  togglePiP() {
    if (document.pictureInPictureElement) {
      document.exitPictureInPicture().catch(() => {});
    } else if (this._videoEl && document.pictureInPictureEnabled) {
      this._videoEl.requestPictureInPicture().catch(() => {});
    }
  }

  cleanupEngine() {
    if (this._hls) {
      this._hls.destroy();
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

  close() {
    this.cleanupEngine();
    if (this._keyboardHandler) {
      window.removeEventListener('keydown', this._keyboardHandler);
      this._keyboardHandler = null;
    }
    const spinner = document.getElementById('player-loading-spinner');
    if (spinner) spinner.classList.add('hidden');

    this._modalEl.classList.add('hidden');
  }
}

export const playerModal = new PlayerModal();
