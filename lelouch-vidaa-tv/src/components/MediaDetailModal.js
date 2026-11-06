/**
 * @module MediaDetailModal
 * Modal enriquecido para visualizar detalles de VOD (Películas) y Series (Temporadas y Episodios).
 * Soporta: Favoritos (toggle ⭐), PiP (Picture-in-Picture), VLC.
 */

import { playerModal } from './PlayerModal.js';
import { cacheService } from '../modules/iptv/services/CacheService.js';

function escHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function toast(msg, type = 'info', dur = 3000) {
  const el = document.getElementById('toast');
  if (!el) return;
  el.textContent = msg;
  el.className = `toast-notification toast-${type} show`;
  setTimeout(() => el.classList.remove('show'), dur);
}

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

export class MediaDetailModal {
  constructor() {
    this._modalEl = null;
    this.init();
  }

  init() {
    if (!document.getElementById('media-detail-modal')) {
      const modal = document.createElement('div');
      modal.id = 'media-detail-modal';
      modal.className = 'modal-overlay hidden';
      modal.innerHTML = `
        <div class="media-detail-content glass-card">
          <button id="media-detail-close" class="modal-close-btn">&times;</button>
          <div id="media-detail-body">
            <!-- Dinámico -->
          </div>
        </div>
      `;
      document.body.appendChild(modal);
    }

    this._modalEl = document.getElementById('media-detail-modal');
    document.getElementById('media-detail-close').addEventListener('click', () => this.close());
    this._modalEl.addEventListener('click', (e) => {
      if (e.target === this._modalEl) this.close();
    });
  }

  /**
   * Muestra la vista detallada de una película (VOD)
   */
  async openVod(vodData, vodInfo) {
    const body = document.getElementById('media-detail-body');
    const info = vodInfo?.info || {};

    const rawBackdrop = info.backdrop_path && info.backdrop_path.length > 0 ? info.backdrop_path[0] : (vodData.poster || vodData.logo || '');
    const backdrop = (rawBackdrop && rawBackdrop.startsWith('http://') && typeof window !== 'undefined' && window.location.protocol === 'https:')
      ? getProxyUrl(rawBackdrop)
      : rawBackdrop;

    const rating = info.rating || vodData.rating || 'N/A';
    const duration = info.duration || 'N/A';
    const genre = info.genre || vodData.categoryName || 'Variado';
    const director = info.director || 'Desconocido';
    const cast = info.cast || 'N/A';
    const plot = info.plot || vodData.description || 'Sin descripción disponible para este título.';
    const title = vodData.name || vodData.title || 'Película';
    const rawPoster = vodData.poster || vodData.logo || '';
    const posterUrl = (rawPoster && rawPoster.startsWith('http://') && typeof window !== 'undefined' && window.location.protocol === 'https:')
      ? getProxyUrl(rawPoster)
      : rawPoster;
    const itemId = String(vodData.id || vodData.stream_id || '');

    const isFav = itemId ? await cacheService.isFavorite(itemId) : false;

    body.innerHTML = `
      <div class="vod-detail-wrapper" style="background-image: linear-gradient(to bottom, rgba(15,23,42,0.6), rgba(15,23,42,0.95)), url('${backdrop}');">
        <div class="vod-detail-header">
          <img src="${posterUrl}" alt="${escHtml(title)}" class="vod-poster-large" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22100%22 height=%22150%22 fill=%22%23334155%22><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%2394a3b8%22 font-size=%2228%22>🎬</text></svg>'" />
          <div class="vod-info-main">
            <h2 class="vod-title-large">${escHtml(title)}</h2>
            <div class="vod-meta-pills">
              <span class="meta-pill pill-rating">⭐ ${rating}</span>
              <span class="meta-pill pill-duration">⏱️ ${duration}</span>
              <span class="meta-pill pill-genre">🏷️ ${genre}</span>
            </div>
            <p class="vod-plot">${plot}</p>
            <div class="vod-credits">
              <div><strong>Director:</strong> ${director}</div>
              <div><strong>Elenco:</strong> ${cast}</div>
            </div>
            <div class="vod-actions" style="display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center;">
              <button id="btn-play-vod" class="btn btn-primary btn-lg">▶️ Reproducir</button>
              <button id="btn-pip-vod" class="btn btn-secondary btn-lg" title="Picture in Picture">📺 PiP</button>
              <button id="btn-fav-vod" class="btn btn-secondary btn-lg ${isFav ? 'fav-active' : ''}" data-fav="${isFav ? '1' : '0'}">
                ${isFav ? '★ En favoritos' : '☆ Favorito'}
              </button>
              <button id="btn-copy-vod-url" class="btn btn-secondary btn-lg" title="Copiar enlace de streaming directo">🔗 Copiar Enlace</button>
              <button id="btn-add-vod-custom-m3u" class="btn btn-secondary btn-lg" title="Añadir a mi lista M3U personalizada">➕ A Mi Lista M3U</button>
              <button id="btn-vlc-vod" class="btn btn-secondary btn-lg">▶️ Abrir en VLC</button>
            </div>
          </div>
        </div>
      </div>
    `;

    document.getElementById('btn-play-vod').addEventListener('click', () => {
      this.close();
      playerModal.open({ title, url: vodData.streamUrl, type: 'vod' });
    });

    document.getElementById('btn-copy-vod-url')?.addEventListener('click', () => {
      if (window.copyStreamUrl) {
        window.copyStreamUrl(vodData.streamUrl, title);
      } else {
        navigator.clipboard?.writeText(vodData.streamUrl);
        toast(`📋 Enlace copiado: ${title}`, 'success');
      }
    });

    document.getElementById('btn-add-vod-custom-m3u')?.addEventListener('click', () => {
      if (window.addCustomM3UItem) {
        window.addCustomM3UItem({
          id: itemId,
          name: title,
          category: vodData.categoryName || genre || 'Películas',
          logo: posterUrl,
          url: vodData.streamUrl
        });
      }
    });

    document.getElementById('btn-pip-vod')?.addEventListener('click', () => {
      this.close();
      playerModal.open({ title, url: vodData.streamUrl, type: 'vod' });
      // Request PiP after a short delay to allow video to start
      setTimeout(() => playerModal.togglePiP(), 1200);
    });

    document.getElementById('btn-fav-vod')?.addEventListener('click', async () => {
      if (!itemId) return;
      const added = await cacheService.toggleFavorite({
        id: itemId,
        type: 'movie',
        name: title,
        logo: posterUrl,
        categoryName: vodData.categoryName || genre,
        streamUrl: vodData.streamUrl,
        rating
      });
      const btn = document.getElementById('btn-fav-vod');
      if (btn) {
        btn.textContent = added ? '★ En favoritos' : '☆ Favorito';
        btn.classList.toggle('fav-active', added);
        btn.dataset.fav = added ? '1' : '0';
      }
      toast(added ? `⭐ "${title}" agregada a favoritos` : `"${title}" eliminada de favoritos`, 'info');
    });

    document.getElementById('btn-vlc-vod')?.addEventListener('click', () => {
      playerModal.openInVLC(vodData.streamUrl, title);
    });

    this._modalEl.classList.remove('hidden');
  }

  /**
   * Muestra la vista detallada de una Serie con Selector de Temporadas y Episodios
   */
  async openSeries(seriesData, seriesInfo, streamUrlBuilder) {
    const body = document.getElementById('media-detail-body');
    const info = seriesInfo?.info || {};
    const seasons = seriesInfo?.episodes || {};
    const title = seriesData.name || seriesData.title || 'Serie';
    const rawCover = seriesData.poster || seriesData.cover || seriesData.logo || '';
    const coverUrl = (rawCover && rawCover.startsWith('http://') && typeof window !== 'undefined' && window.location.protocol === 'https:')
      ? getProxyUrl(rawCover)
      : rawCover;
    const itemId = String(seriesData.id || seriesData.series_id || '');

    const seasonKeys = Object.keys(seasons);
    const firstSeasonKey = seasonKeys[0] || '1';

    const isFav = itemId ? await cacheService.isFavorite(itemId) : false;

    body.innerHTML = `
      <div class="series-detail-wrapper">
        <div class="series-header-box">
          <img src="${coverUrl}" alt="${escHtml(title)}" class="series-cover-large" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22100%22 height=%22150%22 fill=%22%23334155%22><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%2394a3b8%22 font-size=%2228%22>📺</text></svg>'" />
          <div class="series-meta-main">
            <h2 class="series-title-large">${escHtml(title)}</h2>
            <div class="vod-meta-pills">
              <span class="meta-pill pill-rating">⭐ ${info.rating || seriesData.rating || 'N/A'}</span>
              <span class="meta-pill pill-genre">🏷️ ${info.genre || seriesData.categoryName || 'Serie'}</span>
              <span class="meta-pill pill-seasons">📂 ${seasonKeys.length} Temporadas</span>
            </div>
            <p class="vod-plot">${info.plot || seriesData.description || 'Sin sinopsis disponible.'}</p>
            <div style="display:flex;gap:0.75rem;flex-wrap:wrap;margin-top:0.75rem;">
              <button id="btn-fav-series" class="btn btn-secondary ${isFav ? 'fav-active' : ''}" data-fav="${isFav ? '1' : '0'}">
                ${isFav ? '★ En favoritos' : '☆ Favorito'}
              </button>
            </div>
          </div>
        </div>

        <div class="series-seasons-nav">
          ${seasonKeys.map((s, idx) => `
            <button class="season-tab ${idx === 0 ? 'active' : ''}" data-season="${s}">Temporada ${s}</button>
          `).join('')}
        </div>

        <div id="episodes-container" class="episodes-grid">
          <!-- Renderizado dinámicamente -->
        </div>
      </div>
    `;

    // Fav button for series
    document.getElementById('btn-fav-series')?.addEventListener('click', async () => {
      if (!itemId) return;
      const added = await cacheService.toggleFavorite({
        id: itemId,
        type: 'series',
        name: title,
        logo: coverUrl,
        categoryName: seriesData.categoryName || info.genre || 'Serie',
        streamUrl: '',
        rating: info.rating || seriesData.rating || null
      });
      const btn = document.getElementById('btn-fav-series');
      if (btn) {
        btn.textContent = added ? '★ En favoritos' : '☆ Favorito';
        btn.classList.toggle('fav-active', added);
        btn.dataset.fav = added ? '1' : '0';
      }
      toast(added ? `⭐ "${title}" agregada a favoritos` : `"${title}" eliminada de favoritos`, 'info');
    });

    const renderEpisodes = (seasonNum) => {
      const episodesList = seasons[seasonNum] || [];
      const epContainer = document.getElementById('episodes-container');

      if (!episodesList.length) {
        epContainer.innerHTML = `<p class="empty-text">No hay episodios disponibles para esta temporada.</p>`;
        return;
      }

      epContainer.innerHTML = episodesList.map(ep => {
        const epTitle = ep.title ? `${ep.episodeNum || ep.episode_num}. ${ep.title}` : `Episodio ${ep.episodeNum || ep.episode_num}`;
        const epImg = ep.info?.movie_image || ep.cover || seriesData.cover;
        const epStreamUrl = streamUrlBuilder(ep.id || ep.stream_id, ep.containerExtension || ep.container_extension || 'mp4');

        return `
          <div class="episode-card glass-card">
            <div class="episode-thumb-box">
              <img src="${epImg}" alt="${epTitle}" class="episode-thumb" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22160%22 height=%2290%22 fill=%22%23334155%22><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%2394a3b8%22 font-size=%2224%22>▶️</text></svg>'" />
              <button class="episode-play-overlay" data-url="${epStreamUrl}" data-title="${seriesData.title} - ${epTitle}">▶️</button>
              <button class="episode-pip-btn" data-url="${epStreamUrl}" data-title="${seriesData.title} - ${epTitle}" title="Picture in Picture">📺</button>
            </div>
            <div class="episode-info">
              <h4 class="episode-title">${epTitle}</h4>
              <div style="display:flex; justify-content:space-between; align-items:center; margin-top:4px;">
                <span class="episode-duration">${ep.info?.duration || '45m'}</span>
                <div style="display:flex; gap:4px;">
                  <button class="episode-action-btn episode-copy-btn" data-url="${epStreamUrl}" data-title="${seriesData.title} - ${epTitle}" title="Copiar enlace directo">🔗</button>
                  <button class="episode-action-btn episode-add-m3u-btn" data-url="${epStreamUrl}" data-title="${seriesData.title} - ${epTitle}" data-cat="${seriesData.genre || 'Series'}" title="Añadir a Mi Lista M3U">➕</button>
                </div>
              </div>
            </div>
          </div>
        `;
      }).join('');

      // Event listeners para reproducción de episodios
      epContainer.querySelectorAll('.episode-play-overlay').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const url = e.currentTarget.getAttribute('data-url');
          const epTitle = e.currentTarget.getAttribute('data-title');
          this.close();
          playerModal.open({ title: epTitle, url, type: 'series' });
        });
      });

      // Copiar enlace directo del episodio
      epContainer.querySelectorAll('.episode-copy-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const url = e.currentTarget.getAttribute('data-url');
          const epTitle = e.currentTarget.getAttribute('data-title');
          if (window.copyStreamUrl) {
            window.copyStreamUrl(url, epTitle);
          } else {
            navigator.clipboard?.writeText(url);
            toast(`📋 Enlace copiado: ${epTitle}`, 'success');
          }
        });
      });

      // Añadir episodio a Mi Lista M3U
      epContainer.querySelectorAll('.episode-add-m3u-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const url = e.currentTarget.getAttribute('data-url');
          const epTitle = e.currentTarget.getAttribute('data-title');
          const cat = e.currentTarget.getAttribute('data-cat') || 'Series';
          if (window.addCustomM3UItem) {
            window.addCustomM3UItem({
              id: String(Date.now()),
              name: epTitle,
              category: cat,
              logo: seriesData.cover || '',
              url
            });
          }
        });
      });

      // PiP buttons for episodes
      epContainer.querySelectorAll('.episode-pip-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const url = e.currentTarget.getAttribute('data-url');
          const epTitle = e.currentTarget.getAttribute('data-title');
          this.close();
          playerModal.open({ title: epTitle, url, type: 'series' });
          setTimeout(() => playerModal.togglePiP(), 1200);
        });
      });
    };

    renderEpisodes(firstSeasonKey);

    // Event listeners para cambio de pestaña de temporada
    const tabs = body.querySelectorAll('.season-tab');
    tabs.forEach(tab => {
      tab.addEventListener('click', (e) => {
        tabs.forEach(t => t.classList.remove('active'));
        e.currentTarget.classList.add('active');
        const sNum = e.currentTarget.getAttribute('data-season');
        renderEpisodes(sNum);
      });
    });

    this._modalEl.classList.remove('hidden');
  }

  close() {
    this._modalEl.classList.add('hidden');
  }
}

export const mediaDetailModal = new MediaDetailModal();
