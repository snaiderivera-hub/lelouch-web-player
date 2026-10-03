/**
 * @module app.js
 * Orquestador principal de LELOUCH IPTV Web Player.
 * Gestiona el enrutamiento, la UI dinámica, el reproductor integrado,
 * el catálogo VOD/Series, la búsqueda global instantánea y los ajustes de playlists.
 */

import { iptvService } from './modules/iptv/services/IPTVService.js';
import { playlistService } from './modules/iptv/services/PlaylistService.js';
import { cacheService } from './modules/iptv/services/CacheService.js';
import { searchService } from './modules/iptv/services/SearchService.js';
import { playerService, PlayerState } from './modules/iptv/services/PlayerService.js';
import { downloadJson, groupByCategory, downloadM3UPlus } from './modules/iptv/services/ExportService.js';
import { buildLiveStreamUrl, buildVodStreamUrl, buildSeriesStreamUrl, redactSensitiveUrl, redactSensitiveText } from './modules/iptv/utils/security.js';
import { parseIPTVUrl } from './modules/iptv/parsers/UrlParser.js';
import { XtreamAdapter } from './modules/iptv/adapters/XtreamAdapter.js';
import { playerModal } from './components/PlayerModal.js';
import { mediaDetailModal } from './components/MediaDetailModal.js';
import { channelHealthService, HealthStatus } from './modules/iptv/services/ChannelHealthService.js';
import { parentalControlService } from './modules/iptv/services/ParentalControlService.js';
import { supabaseService } from './modules/iptv/services/SupabaseService.js';
import { PlaylistDeduplicator } from './services/playlist/PlaylistDeduplicator.js';

// FASE 19: Protección global activa de logs contra fugas de credenciales
(function initSafeLogging() {
  if (typeof console === 'undefined') return;
  const originalLog = console.log;
  const originalWarn = console.warn;
  const originalError = console.error;

  const sanitizeArgs = (args) => {
    return args.map(arg => {
      if (typeof arg === 'string') return redactSensitiveUrl(arg);
      if (arg instanceof Error || (arg && typeof arg === 'object' && ('message' in arg || 'stack' in arg))) {
        try {
          const desc = Object.getOwnPropertyDescriptor(arg, 'message');
          if (!desc || desc.writable || desc.set) {
            arg.message = redactSensitiveUrl(arg.message);
          }
        } catch {}
        try {
          const desc = Object.getOwnPropertyDescriptor(arg, 'stack');
          if (!desc || desc.writable || desc.set) {
            arg.stack = redactSensitiveUrl(arg.stack);
          }
        } catch {}
        return arg;
      }
      if (typeof arg === 'object' && arg !== null) {
        try {
          return JSON.parse(redactSensitiveUrl(JSON.stringify(arg)));
        } catch {
          return arg;
        }
      }
      return arg;
    });
  };

  console.log = function(...args) {
    originalLog.apply(console, sanitizeArgs(args));
  };
  console.warn = function(...args) {
    originalWarn.apply(console, sanitizeArgs(args));
  };
  console.error = function(...args) {
    originalError.apply(console, sanitizeArgs(args));
  };
})();

// ── Constantes de paginación ──
const PAGE_SIZE_MOVIES = 48;
const PAGE_SIZE_SERIES = 48;

// ── Estado local de la interfaz ──
const uiState = {
  activePage: 'home',
  selectedLiveCategory: '',
  liveSearch: '',
  currentLiveChannel: null,
  hideOfflineChannels: typeof localStorage !== 'undefined' && localStorage.getItem('iptv_hide_offline') === 'true',
  onlyMyCategories: typeof localStorage !== 'undefined' && localStorage.getItem('iptv_only_my_cats') === 'true',
  moviesFiltered: [],
  moviesPage: 1,
  moviesCategoryFilter: '',
  moviesSearch: '',
  seriesFiltered: [],
  seriesPage: 1,
  seriesCategoryFilter: '',
  seriesSearch: '',
};

// ── Utilidades DOM ──
const $ = (id) => document.getElementById(id);

function toast(msg, type = 'info', dur = 3500) {
  const el = $('toast');
  if (!el) return;
  el.textContent = msg;
  el.className = `toast-notification show ${type}`;
  setTimeout(() => {
    el.className = 'toast-notification';
  }, dur);
}

function escHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
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

function createPosterPlaceholderSvg(title, category = '', icon = '🎬') {
  const safeTitle = String(title || 'Título').slice(0, 24).replace(/[<>&"]/g, '');
  const safeCat = String(category || '').slice(0, 20).replace(/[<>&"]/g, '');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="360" viewBox="0 0 240 360">` +
    `<defs><linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#1e293b"/><stop offset="50%" stop-color="#0f172a"/><stop offset="100%" stop-color="#020617"/></linearGradient></defs>` +
    `<rect width="240" height="360" fill="url(#g)" rx="10"/>` +
    `<circle cx="120" cy="130" r="38" fill="#1e293b" stroke="#334155" stroke-width="2"/>` +
    `<text x="120" y="142" font-family="system-ui,sans-serif" font-size="28" text-anchor="middle" fill="#38bdf8">${icon}</text>` +
    `<text x="120" y="205" font-family="system-ui,sans-serif" font-size="12" font-weight="bold" text-anchor="middle" fill="#f1f5f9">${safeTitle}</text>` +
    `<text x="120" y="225" font-family="system-ui,sans-serif" font-size="10" text-anchor="middle" fill="#94a3b8">${safeCat}</text>` +
    `<text x="120" y="270" font-family="system-ui,sans-serif" font-size="9" text-anchor="middle" fill="#64748b" letter-spacing="1">PORTADA NO DISPONIBLE</text>` +
    `</svg>`;
  try {
    return 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg)));
  } catch (e) {
    return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
  }
}

function debounce(fn, ms) {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
  };
}

// ════════════ ENRUTAMIENTO Y NAVEGACIÓN ════════════
const pages = ['home', 'live', 'movies', 'series', 'settings'];

function navigateTo(page, options = {}) {
  pages.forEach((p) => {
    $(`page-${p}`)?.classList.toggle('active', p === page);
    $(`nav-${p}`)?.classList.toggle('active', p === page);
  });

  // Detener reproductor de Live TV al salir hacia Películas, Series, Ajustes o Inicio
  if (page !== 'live') {
    playerService.stop();
    const vid = document.getElementById('integrated-video');
    if (vid) {
      vid.pause();
      vid.removeAttribute('src');
      vid.load();
    }
    const plPH = document.getElementById('integrated-player-placeholder');
    if (plPH) plPH.classList.remove('hidden');
    const spinner = document.getElementById('integrated-player-spinner');
    if (spinner) spinner.classList.add('hidden');
    stopLiveStats();
    uiState.currentLiveChannel = null;
  }

  uiState.activePage = page;

  if (page === 'home') {
    renderHomeDynamicSections();
  } else if (page === 'live') {
    if (options.category) {
      uiState.selectedLiveCategory = options.category;
    }
    renderLiveTVView();
    // FASE 18: Disparador 3 - Al entrar a Live
    checkAndSyncCustomM3UVersion('enter_live');
  } else if (page === 'movies') {
    renderMoviesPage();
  } else if (page === 'series') {
    renderSeriesPage();
  } else if (page === 'settings') {
    renderSettingsPlaylists();
  }

  window.scrollTo({ top: 0, behavior: 'smooth' });
}

document.querySelectorAll('.nav-btn').forEach((btn) => {
  btn.addEventListener('click', () => navigateTo(btn.dataset.page));
});

$('brand-logo-btn')?.addEventListener('click', () => navigateTo('home'));

// ════════════ INICIALIZACIÓN DE LA APLICACIÓN ════════════
async function initApp() {
  console.log('%c⚡ LELOUCH IPTV WEB PLAYER v2.0', 'color:#00e5ff;font-weight:bold;font-size:14px');

  // Inicializar reproductor integrado de TV
  const integratedVideo = $('integrated-video');
  if (integratedVideo) {
    playerService.attachVideoElement(integratedVideo);
    playerService.onStateChange(({ state, message }) => {
      const badge = $('integrated-quality-badge');
      const spinner = $('integrated-player-spinner');
      const spinnerText = $('integrated-spinner-text');

      if (state === PlayerState.LOADING || state === PlayerState.RECONNECTING) {
        spinner?.classList.remove('hidden');
        $('integrated-player-placeholder')?.classList.add('hidden');
        if (spinnerText) spinnerText.textContent = message || 'Cargando stream...';
        if (badge) badge.textContent = 'CARGANDO';
      } else if (state === PlayerState.PLAYING) {
        spinner?.classList.add('hidden');
        $('integrated-player-placeholder')?.classList.add('hidden');
        if (badge) badge.textContent = 'EN VIVO';
        if (playerService.currentMedia?.type === 'live' && playerService.currentMedia?.id) {
          channelHealthService._cache.set(String(playerService.currentMedia.id), {
            channelId: String(playerService.currentMedia.id),
            status: HealthStatus.ONLINE,
            latencyMs: 100,
            timestamp: Date.now()
          });
        }
      } else if (state === PlayerState.ERROR) {
        spinner?.classList.remove('hidden');
        if (spinnerText) spinnerText.textContent = `❌ ${message}`;
        if (badge) badge.textContent = 'ERROR';
        toast(message, 'error', 5000);
        if (playerService.currentMedia?.type === 'live' && playerService.currentMedia?.id) {
          channelHealthService.markOffline(playerService.currentMedia.id, message);
          if (uiState.hideOfflineChannels) {
            renderLiveChannelsList();
          }
        }
      }
    });

    const fmtSelect = $('select-stream-format');
    if (fmtSelect) {
      fmtSelect.value = playerService.streamFormat || 'ts';
      fmtSelect.addEventListener('change', (e) => {
        playerService.setStreamFormat(e.target.value);
        const labels = { ts: '⚡ MPEG-TS (.ts)', auto: '🔄 Auto (HLS/TS)', hls: '🌐 HLS (.m3u8)' };
        toast(`Formato de emisión: ${labels[e.target.value] || e.target.value}`, 'info', 2500);
      });
    }

    // ── Stats button ──
    $('btn-integrated-stats')?.addEventListener('click', () => toggleLiveStats());

    // ── Copiar enlace directo del canal actual ──
    $('btn-integrated-copy-url')?.addEventListener('click', () => {
      const ch = uiState.currentLiveChannel;
      if (!ch || !ch.streamUrl) {
        toast('Selecciona un canal en vivo primero.', 'info');
        return;
      }
      copyStreamUrl(ch.streamUrl, ch.name);
    });

    // ── Añadir canal actual a Mi Lista M3U ──
    $('btn-integrated-add-m3u')?.addEventListener('click', () => {
      const ch = uiState.currentLiveChannel;
      if (!ch || !ch.streamUrl) {
        toast('Selecciona un canal en vivo primero.', 'info');
        return;
      }
      addCustomM3UItem({
        id: ch.id || String(Date.now()),
        name: ch.name,
        category: ch.categoryName || 'Canales en Vivo',
        logo: ch.logo || '',
        url: ch.streamUrl,
        epgId: ch.epgChannelId || ''
      });
    });

    // ── Fav button en reproductor integrado ──
    $('btn-integrated-fav')?.addEventListener('click', async () => {
      const ch = uiState.currentLiveChannel;
      if (!ch) { toast('Selecciona un canal primero.', 'info', 2000); return; }
      const added = await cacheService.toggleFavorite({
        id: ch.id, type: 'live', name: ch.name, logo: ch.logo,
        categoryName: ch.categoryName, streamUrl: ch.streamUrl
      });
      const btn = $('btn-integrated-fav');
      if (btn) { btn.textContent = added ? '★' : '☆'; btn.classList.toggle('active', added); }
      toast(added ? `⭐ ${ch.name} en favoritos` : `${ch.name} quitado de favoritos`, 'info');
      renderHomeDynamicSections();
    });

    // ── PiP button ──
    $('btn-integrated-pip')?.addEventListener('click', () => {
      const v = $('integrated-video');
      if (!v) return;
      if (document.pictureInPictureElement) {
        document.exitPictureInPicture().catch(() => {});
      } else if (document.pictureInPictureEnabled) {
        v.requestPictureInPicture().catch(() => {});
      }
    });
  }

  // Escuchar actualizaciones parciales progresivas para renderizar TV en vivo y métricas al instante
  iptvService.onPartialUpdate((partialState) => {
    updateAllViews(partialState);
  });

  // Comprobar si la playlist activa guardada es la lista personalizada
  const savedActiveId = localStorage.getItem('iptv_active_playlist_id');
  if (savedActiveId === 'custom_lelouch_playlist') {
    const customList = getCustomM3UList();
    if (customList && customList.length > 0) {
      await activateCustomM3UAsMainPlaylist();
    }
  }

  // Comprobar playlist activa guardada
  let activePlaylist = await playlistService.getActive();
  if (!activePlaylist) {
    try {
      activePlaylist = await playlistService.addOrUpdate(
        'http://liontv.es:8080/get.php?username=Hermanos503&password=BysckXDynC&type=m3u_plus&output=m3u8',
        'LionTV (Principal)'
      );
    } catch (e) {
      console.warn('[App] No se pudo inicializar playlist por defecto:', e);
    }
  }

  // SOLO conectar si la playlist activa NO es la lista personalizada interna (evita 403 en vercel.app)
  const isActiveCustom = savedActiveId === 'custom_lelouch_playlist' || activePlaylist?.id === 'custom_lelouch_playlist';
  if (!isActiveCustom && activePlaylist && activePlaylist.url) {
    // Normalizar URLs antiguas de LionTV que tenían puerto 80 para evitar bloqueos del proveedor
    if (activePlaylist.url.includes('liontv.es:80/')) {
      activePlaylist.url = activePlaylist.url.replace('liontv.es:80/', 'liontv.es:8080/');
    }
    try {
      await iptvService.connect(activePlaylist.url);
      const restored = await iptvService.tryRestoreFromCache();
      if (restored) {
        updateAllViews(iptvService.state);
        toast(`✓ Catálogo restaurado desde caché persistente (${iptvService.state.live.length} canales)`, 'success');
      } else {
        await loadCatalogWithProgress(activePlaylist.url);
      }
    } catch (err) {
      console.warn('[App] Error al conectar con playlist activa:', err);
      const restored = await iptvService.tryRestoreFromCache();
      if (restored) {
        updateAllViews(iptvService.state);
      } else {
        $('empty-welcome-banner').style.display = 'block';
      }
    }
  } else if (isActiveCustom) {
    $('empty-welcome-banner').style.display = 'none';
  } else {
    // Si no hay ninguna playlist guardada, mostrar sugerencia de configuración
    $('empty-welcome-banner').style.display = 'block';
  }

  // Atajos globales y enrutamiento por hash (#live, #movies, #series, #settings)
  setupGlobalShortcuts();
  setupSearch();
  setupSettingsTabs();
  setupHeroActions();
  setupParentalControl();
  setupCategoryManager();
  setupCustomM3UManager();
  setupPlaylistImportModal();
  setupLifecycleSyncListeners(); // FASE 18: Disparadores 2 (background) y 4 (botón Actualizar)
  updateActivePlaylistUI();

  // FASE 18: Disparador 1 - Al abrir aplicación (app_open)
  setTimeout(() => {
    checkAndSyncCustomM3UVersion('app_open');
  }, 350);

  const handleHashRoute = () => {
    const hash = window.location.hash.replace('#', '');
    if (['home', 'live', 'movies', 'series', 'settings'].includes(hash)) {
      navigateTo(hash);
    }
  };
  handleHashRoute();
  window.addEventListener('hashchange', handleHashRoute);
}

// ════════════ CARGA DE CATÁLOGO CON PROGRESO ════════════
async function loadCatalogWithProgress(url) {
  const progressBox = $('import-progress-box');
  const progressTitle = $('import-progress-title');
  const progressPercent = $('import-progress-percent');
  const progressFill = $('import-progress-fill');
  const progressLog = $('import-progress-log');

  const homeBanner = $('home-loading-banner');
  const homeText = $('home-loading-text');
  const homePercent = $('home-loading-percent');
  const homeFill = $('home-loading-bar-fill');
  const reloadBtn = $('hero-reload-btn');

  if (homeBanner) homeBanner.style.display = 'block';
  progressBox?.classList.remove('hidden');
  if (progressLog) progressLog.innerHTML = '';
  if (reloadBtn) reloadBtn.classList.add('loading');

  iptvService.onProgress(({ step, percent, detail }) => {
    const fullText = step + (detail ? ` — ${detail}` : '');
    if (progressTitle) progressTitle.textContent = fullText;
    if (progressPercent) progressPercent.textContent = `${percent}%`;
    if (progressFill) progressFill.style.width = `${percent}%`;

    if (homeText) homeText.textContent = fullText;
    if (homePercent) homePercent.textContent = `${percent}%`;
    if (homeFill) homeFill.style.width = `${percent}%`;

    if (progressLog) {
      const entry = document.createElement('div');
      entry.textContent = `[${percent}%] ${step} ${detail || ''}`;
      progressLog.appendChild(entry);
      progressLog.scrollTop = progressLog.scrollHeight;
    }
  });

  try {
    await iptvService.connect(url);
    const state = await iptvService.importAll();
    await playlistService.addOrUpdate(url);
    updateAllViews(state);
    toast('✓ Catálogo importado y guardado correctamente.', 'success');
    const emptyBanner = $('empty-welcome-banner');
    if (emptyBanner) emptyBanner.style.display = 'none';
  } catch (err) {
    if (iptvService.state?.live?.length > 0) {
      updateAllViews(iptvService.state);
      toast(`ℹ Catálogo parcialmente cargado (${iptvService.state.live.length} canales en vivo disponibles)`, 'info', 5000);
      const emptyBanner = $('empty-welcome-banner');
      if (emptyBanner) emptyBanner.style.display = 'none';
    } else {
      toast(`❌ Error al importar: ${err.message}`, 'error', 7000);
      throw err;
    }
  } finally {
    if (reloadBtn) reloadBtn.classList.remove('loading');
    setTimeout(() => {
      progressBox?.classList.add('hidden');
      if (homeBanner) homeBanner.style.display = 'none';
    }, 2500);
  }
}

// ════════════ ACTUALIZACIÓN DE VISTAS ════════════
function updateAllViews(state) {
  updateHeaderAccount(state.account);
  updateHomeMetrics(state);
  updateBadges(state);
  renderHomeDynamicSections();
  setupLiveCategories(state.categories.live, state.live);
  setupMoviesView(state.movies, state.categories.vod);
  setupSeriesView(state.series, state.categories.series);
  renderDiagnostics(iptvService.getDiagnostics());
  updateSettingsCategoriesPreview();
}

function updateHeaderAccount(account) {
  const dot = $('account-status-dot');
  const user = $('account-pill-user');
  const expiry = $('account-pill-expiry');

  if (!account) {
    if (dot) dot.className = 'account-dot';
    if (user) user.textContent = 'Activa';
    if (expiry) expiry.textContent = '—';
    return;
  }

  const isActive = account.isActive !== false;
  if (dot) dot.className = `account-dot ${isActive ? 'active' : ''}`;
  if (user) user.textContent = isActive ? 'Activa' : (account.status || 'Inactiva');
  if (expiry) {
    expiry.textContent = account.expiresAt
      ? account.expiresAt.toLocaleDateString('es-ES')
      : (account.daysRemaining != null ? `${account.daysRemaining} días` : '2026-09-24');
  }
}

function updateHomeMetrics(state) {
  const account = state.account;
  if (account) {
    const elStatus = $('home-account-status');
    if (elStatus) elStatus.textContent = account.isActive ? 'Activa' : (account.status || 'Inactiva');
    const elExp = $('home-account-expiry');
    if (elExp) {
      elExp.textContent = account.expiresAt
        ? account.expiresAt.toLocaleDateString('es-ES')
        : (account.daysRemaining != null ? `${account.daysRemaining} días` : 'Ilimitada');
    }
    const elDays = $('home-account-days');
    if (elDays) elDays.textContent = account.daysRemaining != null ? `${account.daysRemaining} días` : '—';
  }

  const elLive = $('count-live-home');
  if (elLive) elLive.textContent = (state.live?.length || 0).toLocaleString();
  const elMovies = $('count-movies-home');
  if (elMovies) elMovies.textContent = (state.movies?.length || 0).toLocaleString();
  const elSeries = $('count-series-home');
  if (elSeries) elSeries.textContent = (state.series?.length || 0).toLocaleString();
  const elSports = $('count-sports-home');
  if (elSports) elSports.textContent = (state.sportsCount || 0).toLocaleString();

  if (state.live?.length > 0 || state.movies?.length > 0) {
    const emptyBanner = $('empty-welcome-banner');
    if (emptyBanner) emptyBanner.style.display = 'none';
  }
}

function updateBadges(state) {
  const bLive = $('badge-live');
  if (bLive) bLive.textContent = formatCompactNumber(state.live?.length || 0);
  const bMovies = $('badge-movies');
  if (bMovies) bMovies.textContent = formatCompactNumber(state.movies?.length || 0);
  const bSeries = $('badge-series');
  if (bSeries) bSeries.textContent = formatCompactNumber(state.series?.length || 0);
}

function formatCompactNumber(num) {
  if (num >= 1000) return `${Math.floor(num / 1000)}k`;
  return String(num);
}

// ════════════ HOME: DASHBOARD & METRICS ════════════
function setupHeroActions() {
  const triggerReload = async () => {
    toast('Recargando catálogo desde el servidor...', 'info');
    try {
      const state = await iptvService.refresh();
      updateAllViews(state);
      toast('✓ Catálogo actualizado con éxito.', 'success');
    } catch (e) {
      toast(`Error al recargar: ${e.message}`, 'error');
    }
  };

  $('hero-reload-btn')?.addEventListener('click', triggerReload);
  $('action-sidebar-reload')?.addEventListener('click', triggerReload);

  $('card-nav-live')?.addEventListener('click', () => navigateTo('live'));
  $('card-nav-movies')?.addEventListener('click', () => navigateTo('movies'));
  $('card-nav-series')?.addEventListener('click', () => navigateTo('series'));
  $('card-nav-sports')?.addEventListener('click', () => navigateTo('live', { category: '__SPORTS__' }));
  $('card-action-m3u')?.addEventListener('click', () => window.appExport('m3u'));

  $('action-goto-settings')?.addEventListener('click', () => navigateTo('settings'));
  $('action-goto-diag')?.addEventListener('click', () => {
    navigateTo('settings');
    setTimeout(() => {
      document.querySelector('[data-tab="diagnostics"]')?.click();
    }, 100);
  });

  $('btn-goto-settings-connect')?.addEventListener('click', () => navigateTo('settings'));
}

async function renderHomeDynamicSections() {
  // 1. Continuar viendo
  const history = await cacheService.getHistory(8);
  const contSection = $('section-continue-watching');
  const contRow = $('continue-watching-row');

  if (contRow && history.length > 0) {
    contSection.style.display = 'block';
    contRow.innerHTML = history.map((item) => `
      <div class="history-card" data-url="${escHtml(item.streamUrl)}" data-time="${item.currentTime || 0}" data-type="${item.type}" data-title="${escHtml(item.title)}">
        <div class="history-thumb-wrap">
          <img class="history-thumb" src="${escHtml(item.poster || '')}" alt="${escHtml(item.title)}" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22160%22 height=%2290%22 fill=%22%230A1724%22><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%238191A3%22 font-size=%2220%22>▶</text></svg>'"/>
          <div class="history-progress-bar">
            <div class="history-progress-fill" style="width:${item.progressPercent || 0}%"></div>
          </div>
        </div>
        <div class="history-info">
          <div class="history-title">${escHtml(item.title)}</div>
        </div>
      </div>
    `).join('');

    contRow.querySelectorAll('.history-card').forEach((card) => {
      card.addEventListener('click', () => {
        const url = card.dataset.url;
        const startTime = parseFloat(card.dataset.time) || 0;
        const title = card.dataset.title;
        const type = card.dataset.type;
        playerModal.open({ title, url, type, startTime });
      });
    });
  } else if (contSection) {
    contSection.style.display = 'none';
  }

  // 2. Favoritos
  const favs = await cacheService.getFavorites();
  const favSection = $('section-favorites');
  const favRow = $('favorites-row');

  if (favRow && favs.length > 0) {
    favSection.style.display = 'block';
    favRow.innerHTML = favs.slice(0, 10).map((fav) => `
      <div class="history-card" data-id="${fav.id}" data-url="${escHtml(fav.streamUrl)}" data-type="${fav.type}" data-title="${escHtml(fav.name)}">
        <div class="history-thumb-wrap">
          <img class="history-thumb" src="${escHtml(fav.logo || '')}" alt="${escHtml(fav.name)}" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22160%22 height=%2290%22 fill=%22%230A1724%22><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%238191A3%22 font-size=%2220%22>⭐</text></svg>'"/>
        </div>
        <div class="history-info">
          <div class="history-title">${escHtml(fav.name)}</div>
        </div>
      </div>
    `).join('');

    favRow.querySelectorAll('.history-card').forEach((card) => {
      card.addEventListener('click', () => {
        const url = card.dataset.url;
        const title = card.dataset.title;
        const type = card.dataset.type;
        if (type === 'live') {
          navigateTo('live');
          const ch = iptvService.state.live.find(c => String(c.id) === card.dataset.id);
          if (ch) selectLiveChannel(ch);
        } else {
          playerModal.open({ title, url, type });
        }
      });
    });
  } else if (favSection) {
    favSection.style.display = 'none';
  }
}

// ════════════ GESTIÓN DE CATEGORÍAS VISIBLES ════════════
function getHiddenCategories() {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem('iptv_hidden_categories') : null;
    if (!raw) return { live: [], movies: [], series: [] };
    const parsed = JSON.parse(raw);
    return {
      live: Array.isArray(parsed.live) ? parsed.live : [],
      movies: Array.isArray(parsed.movies) ? parsed.movies : [],
      series: Array.isArray(parsed.series) ? parsed.series : []
    };
  } catch {
    return { live: [], movies: [], series: [] };
  }
}

function saveHiddenCategories(hidden) {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('iptv_hidden_categories', JSON.stringify(hidden));
    }
  } catch (e) {
    console.warn('[App] Error guardando categorías ocultas:', e);
  }
}

// ════════════ CATEGORÍAS CREADAS POR EL USUARIO (ARMA TU LISTA) ════════════
const USER_CATS_KEY = 'lelouch_user_created_categories';

export function getUserCreatedCategories() {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(USER_CATS_KEY) : null;
    let cats = raw ? JSON.parse(raw) : [];

    // Auto-detectar e incluir categorías del Creador de Lista M3U (CUSTOM_M3U_STORAGE_KEY)
    let m3uList = [];
    try {
      m3uList = getCustomM3UList();
    } catch {
      const m3uRaw = typeof localStorage !== 'undefined' ? localStorage.getItem('lelouch_custom_m3u_list') : null;
      if (m3uRaw) m3uList = JSON.parse(m3uRaw);
    }

    const m3uCats = new Map();
    m3uList.forEach(item => {
      const cname = (item.category || item.group || item.custom_group || item.direct_group || item.categoryName || 'Mi Lista').trim();
      const nKey = cname.toLowerCase();
      if (!m3uCats.has(nKey)) {
        m3uCats.set(nKey, { name: cname, items: [] });
      }
      m3uCats.get(nKey).items.push(item);
    });

    m3uCats.forEach(({ name, items }, nKey) => {
      let existing = cats.find(c => (c.name || '').trim().toLowerCase() === nKey);
      const itemIds = items.map(it => String(it.id || it.url));
      if (!existing) {
        let hash = 0;
        for (let i = 0; i < nKey.length; i++) hash = ((hash << 5) - hash) + nKey.charCodeAt(i);
        existing = {
          id: 'ucat_' + Math.abs(hash),
          name: name,
          channelIds: itemIds,
          items: items,
          createdAt: Date.now()
        };
        cats.push(existing);
      } else {
        const merged = new Set([...(existing.channelIds || []).map(String), ...itemIds]);
        existing.channelIds = [...merged];
        existing.items = items;
      }
    });

    return cats;
  } catch (e) {
    console.warn('[UserCats] Error leyendo categorías del usuario:', e);
    return [];
  }
}

export function getUserCategoryChannels(ucat) {
  if (!ucat) return [];
  const ucatNameLower = (ucat.name || '').trim().toLowerCase();
  
  // Conjunto de identificadores asociados a la categoría (con extracción de stream IDs si son URLs)
  const idSet = new Set();
  (ucat.channelIds || []).forEach(rawId => {
    const sId = String(rawId || '').trim();
    if (!sId) return;
    idSet.add(sId);
    if (sId.startsWith('custom_')) {
      idSet.add(sId.replace('custom_', ''));
    }
    const urlMatch = sId.match(/\/(\d+)\.(m3u8|ts|mp4)/);
    if (urlMatch) {
      idSet.add(urlMatch[1]);
    }
  });

  // 1. Canales de la lista M3U personalizada (memoria/IndexedDB/localStorage)
  let m3uList = [];
  try {
    m3uList = getCustomM3UList();
  } catch {
    const m3uRaw = typeof localStorage !== 'undefined' ? localStorage.getItem('lelouch_custom_m3u_list') : null;
    if (m3uRaw) m3uList = JSON.parse(m3uRaw);
  }

  const customItems = m3uList.filter(it => {
    const itCat = (it.category || it.group || it.custom_group || it.direct_group || it.categoryName || '').trim().toLowerCase();
    return itCat === ucatNameLower;
  });

  // Si ucat tiene items asociados directamente en memoria
  if (customItems.length === 0 && Array.isArray(ucat.items) && ucat.items.length > 0) {
    customItems.push(...ucat.items);
  }

  const customUrlSet = new Set(customItems.map(it => (it.url || it.streamUrl || '').trim()).filter(Boolean));
  const customNameSet = new Set(customItems.map(it => (it.name || '').trim().toLowerCase()).filter(Boolean));

  // Alimentar idSet con IDs y URLs de customItems
  customItems.forEach(it => {
    if (it.id) idSet.add(String(it.id).trim());
    const url = (it.url || it.streamUrl || '').trim();
    if (url) {
      idSet.add(url);
      const m = url.match(/\/(\d+)\.(m3u8|ts|mp4)/);
      if (m) idSet.add(m[1]);
    }
  });

  // 2. Canales del servidor que coincidan por ID, stream_id, URL, Nombre o Categoría
  const serverChannels = (iptvService.state?.live || []).filter(c => {
    const cId = String(c.id || '').trim();
    const cStreamId = c.stream_id ? String(c.stream_id).trim() : '';
    const cUrl = (c.streamUrl || '').trim();
    const cName = (c.name || '').trim().toLowerCase();
    const cCategory = (c.categoryName || '').trim().toLowerCase();

    // Coincidencia directa por ID de canal o stream_id
    if (idSet.has(cId) || (cStreamId && idSet.has(cStreamId))) return true;

    // Coincidencia por URL de streaming
    if (cUrl && (idSet.has(cUrl) || customUrlSet.has(cUrl))) return true;

    // Coincidencia por nombre de canal
    if (cName && customNameSet.has(cName)) return true;

    // Si la categoría de usuario coincide exactamente con la categoría del servidor y el canal está en idSet
    if (cCategory === ucatNameLower && idSet.size > 0) {
      if (idSet.has(cId) || idSet.has(cStreamId) || idSet.has(cUrl)) return true;
    }

    return false;
  });

  // 3. Agregar items de custom M3U que no estén en el servidor (preserva canales personalizados)
  const existingUrls = new Set(serverChannels.map(c => (c.streamUrl || '').trim()).filter(Boolean));
  const existingNames = new Set(serverChannels.map(c => (c.name || '').trim().toLowerCase()));

  customItems.forEach(item => {
    const streamUrl = (item.url || item.streamUrl || '').trim();
    const itemName = (item.name || '').trim().toLowerCase();

    if (!existingUrls.has(streamUrl) && !existingNames.has(itemName)) {
      serverChannels.push({
        id: item.id || `custom_${streamUrl || Date.now()}`,
        name: item.name || 'Canal',
        categoryName: item.category || item.group || ucat.name,
        logo: item.logo || '',
        streamUrl: streamUrl,
        epgChannelId: item.epgId || item.tvgId || ''
      });
      if (streamUrl) existingUrls.add(streamUrl);
      if (itemName) existingNames.add(itemName);
    }
  });

  // 4. Fallback de rescate si serverChannels sigue vacío pero la categoría coincide con una del servidor
  if (serverChannels.length === 0 && idSet.size > 0) {
    const byCategoryAndId = (iptvService.state?.live || []).filter(c => {
      const cCategory = (c.categoryName || '').trim().toLowerCase();
      const cId = String(c.id || '').trim();
      return cCategory === ucatNameLower && (idSet.has(cId) || idSet.has(c.streamUrl));
    });
    if (byCategoryAndId.length > 0) {
      serverChannels.push(...byCategoryAndId);
    }
  }

  // 5. Fallback si el usuario tiene canales respaldados en ucat.channels
  if (serverChannels.length === 0 && Array.isArray(ucat.channels) && ucat.channels.length > 0) {
    ucat.channels.forEach(ch => {
      serverChannels.push(ch);
    });
  }

  return serverChannels;
}


export function saveUserCreatedCategories(cats) {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(USER_CATS_KEY, JSON.stringify(cats));
    }
  } catch (e) {
    console.warn('[UserCats] Error guardando categorías del usuario:', e);
  }
}

export function createUserCategory(name, channelIds = []) {
  const cats = getUserCreatedCategories();
  const newCat = {
    id: 'ucat_' + Date.now(),
    name: (name || 'Mi Categoría').trim(),
    channelIds: [...new Set(channelIds.map(String))],
    createdAt: Date.now()
  };
  cats.push(newCat);
  saveUserCreatedCategories(cats);
  return newCat;
}

export function updateUserCategory(id, name, channelIds) {
  const cats = getUserCreatedCategories();
  const idx = cats.findIndex(c => c.id === id);
  if (idx !== -1) {
    cats[idx].name = (name || cats[idx].name).trim();
    if (channelIds !== undefined) {
      cats[idx].channelIds = [...new Set(channelIds.map(String))];
    }
    saveUserCreatedCategories(cats);
  }
}

export function deleteUserCategory(id) {
  let cats = getUserCreatedCategories();
  cats = cats.filter(c => c.id !== id);
  saveUserCreatedCategories(cats);
}

export function addChannelToUserCategory(catId, channelId) {
  const cats = getUserCreatedCategories();
  const target = cats.find(c => c.id === catId);
  if (target) {
    const sId = String(channelId);
    if (!target.channelIds.map(String).includes(sId)) {
      target.channelIds.push(sId);
      saveUserCreatedCategories(cats);
      return true;
    }
  }
  return false;
}

// ════════════ LIVE TV (3 PANELES) ════════════
function setupLiveCategories(categories, channels) {
  const container = $('live-categories-list');
  if (!container) return;

  const hiddenLive = new Set(getHiddenCategories().live);

  // Si la categoría seleccionada actualmente fue ocultada, restablecer a Todos
  if (uiState.selectedLiveCategory && !uiState.selectedLiveCategory.startsWith('__USER_CAT_') && uiState.selectedLiveCategory !== '__SPORTS__' && hiddenLive.has(uiState.selectedLiveCategory)) {
    uiState.selectedLiveCategory = '';
  }

  const sportsKeywords = ['sport', 'deport', 'futbol', 'football', 'espn', 'fox sport', 'laliga', 'nba'];

  // Agrupamiento de canales del proveedor
  const catCounts = new Map();
  channels.forEach((ch) => {
    catCounts.set(ch.categoryName, (catCounts.get(ch.categoryName) || 0) + 1);
  });

  const renderCatList = (filterTerm = '') => {
    container.innerHTML = '';
    const userCats = getUserCreatedCategories();

    // 1. SECCIÓN DESTACADA: MIS CATEGORÍAS CREADAS (ARMA TU LISTA)
    if (userCats.length > 0) {
      const uheader = document.createElement('div');
      uheader.style.cssText = 'padding:6px 10px; font-size:0.73rem; font-weight:800; text-transform:uppercase; letter-spacing:0.8px; color:var(--accent-cyan); display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid rgba(0,229,255,0.15); margin-bottom:4px;';
      uheader.innerHTML = `
        <span>⭐ MIS CATEGORÍAS (${userCats.length})</span>
        <button id="btn-mini-add-cat" style="background:none; border:none; color:var(--accent-cyan); cursor:pointer; font-weight:700; font-size:0.75rem;" title="Crear nueva categoría">+ Crear</button>
      `;
      uheader.querySelector('#btn-mini-add-cat')?.addEventListener('click', (e) => {
        e.stopPropagation();
        openCustomCatCreatorDialog();
      });
      container.appendChild(uheader);

      userCats.forEach(ucat => {
        if (filterTerm && !ucat.name.toLowerCase().includes(filterTerm.toLowerCase())) return;
        const item = document.createElement('div');
        const isActive = uiState.selectedLiveCategory === `__USER_CAT_${ucat.id}`;
        item.className = `cat-list-item is-user-created ${isActive ? 'active' : ''}`;
        const catChannels = getUserCategoryChannels(ucat);
        const count = catChannels.length || (ucat.channelIds || []).length;
        item.innerHTML = `<span style="font-weight:600;">⭐ ${escHtml(ucat.name)}</span><span class="badge-mini" style="background:rgba(0,229,255,0.2); color:var(--accent-cyan); font-weight:700;">${count}</span>`;
        item.addEventListener('click', () => {
          uiState.selectedLiveCategory = `__USER_CAT_${ucat.id}`;
          renderLiveTVView();
        });
        container.appendChild(item);
      });
    }

    // 2. SI ESTÁ ACTIVO "VER SOLO MIS CATEGORÍAS", NO MOSTRAR LAS CATEGORÍAS DEL SERVIDOR
    if (uiState.onlyMyCategories) {
      if (userCats.length === 0) {
        const emptyNotice = document.createElement('div');
        emptyNotice.style.cssText = 'padding:1.5rem 0.8rem; text-align:center; color:var(--text-secondary);';
        emptyNotice.innerHTML = `
          <div style="font-size:1.6rem; margin-bottom:0.5rem;">⭐</div>
          <strong style="color:var(--text-main); font-size:0.9rem;">Aún no has creado categorías</strong>
          <p style="margin:6px 0 12px; font-size:0.78rem;">Crea tu propia categoría y añade tus canales favoritos para armar tu lista.</p>
          <button id="btn-create-first-cat-live" class="btn btn-primary btn-sm" style="width:100%; font-weight:700;">+ Crear Mi Categoría</button>
        `;
        emptyNotice.querySelector('#btn-create-first-cat-live')?.addEventListener('click', () => openCustomCatCreatorDialog());
        container.appendChild(emptyNotice);
      } else if (!uiState.selectedLiveCategory || !uiState.selectedLiveCategory.startsWith('__USER_CAT_')) {
        uiState.selectedLiveCategory = `__USER_CAT_${userCats[0].id}`;
      }
      $('live-cats-count').textContent = userCats.length;
      return;
    }

    // 3. CATEGORÍAS DEL SERVIDOR IPTV
    if (userCats.length > 0) {
      const srvHeader = document.createElement('div');
      srvHeader.style.cssText = 'padding:8px 10px 4px; font-size:0.72rem; font-weight:700; text-transform:uppercase; letter-spacing:0.8px; color:var(--text-muted); border-top:1px solid var(--border-subtle); margin-top:8px;';
      srvHeader.textContent = '📺 CATEGORÍAS DEL PROVEEDOR';
      container.appendChild(srvHeader);
    }

    // Opción Todos (muestra solo canales de categorías visibles)
    const visibleChannelsCount = channels.filter(ch => !hiddenLive.has(ch.categoryName)).length;
    const allItem = document.createElement('div');
    allItem.className = `cat-list-item ${!uiState.selectedLiveCategory ? 'active' : ''}`;
    allItem.innerHTML = `<span>📺 Todos los canales</span><span class="badge-mini">${visibleChannelsCount}</span>`;
    allItem.addEventListener('click', () => {
      uiState.selectedLiveCategory = '';
      renderLiveTVView();
    });
    container.appendChild(allItem);

    // Opción Deportes (Agrupada dinámicamente)
    const sportsCount = iptvService.state.sportsCount || 0;
    if (sportsCount > 0) {
      const sportsItem = document.createElement('div');
      sportsItem.className = `cat-list-item ${uiState.selectedLiveCategory === '__SPORTS__' ? 'active' : ''}`;
      sportsItem.innerHTML = `<span>⚽ Deportes (${sportsCount})</span><span class="badge-mini">LIVE</span>`;
      sportsItem.addEventListener('click', () => {
        uiState.selectedLiveCategory = '__SPORTS__';
        renderLiveTVView();
      });
      container.appendChild(sportsItem);
    }

    const uniqueCats = [...catCounts.keys()].sort();
    let visibleCount = userCats.length;
    uniqueCats.forEach((cat) => {
      if (filterTerm && !cat.toLowerCase().includes(filterTerm.toLowerCase())) return;
      // Control parental: ocultar categorías adultas cuando está bloqueado
      if (parentalControlService.isRestricted(cat)) return;
      // Ocultar categorías desmarcadas por el usuario en el gestor de categorías
      if (hiddenLive.has(cat)) return;

      const count = catCounts.get(cat);
      visibleCount++;
      const item = document.createElement('div');
      item.className = `cat-list-item ${uiState.selectedLiveCategory === cat ? 'active' : ''}`;
      item.innerHTML = `<span>${escHtml(cat)}</span><span class="badge-mini">${count}</span>`;
      item.addEventListener('click', () => {
        uiState.selectedLiveCategory = cat;
        renderLiveTVView();
      });
      container.appendChild(item);
    });

    $('live-cats-count').textContent = visibleCount;
  };

  renderCatList();

  // Botón directo rápido Crear Categoría en barra de categorías
  $('btn-quick-create-cat')?.addEventListener('click', () => {
    openCustomCatCreatorDialog();
  });

  $('live-cat-search')?.addEventListener('input', (e) => {
    renderCatList(e.target.value.trim());
  });

  // Búsqueda de canales
  $('live-channel-search')?.addEventListener('input', debounce((e) => {
    uiState.liveSearch = e.target.value.trim().toLowerCase();
    renderLiveChannelsList();
  }, 200));

  // Controles de transporte y pantalla completa
  $('btn-integrated-rewind')?.addEventListener('click', () => {
    playerService.rewind(10);
  });

  $('btn-integrated-forward')?.addEventListener('click', () => {
    playerService.forward(10);
  });

  $('btn-integrated-pip')?.addEventListener('click', () => {
    const video = $('integrated-video');
    if (document.pictureInPictureElement) {
      document.exitPictureInPicture().catch(() => {});
    } else if (video && document.pictureInPictureEnabled) {
      video.requestPictureInPicture().catch(() => {});
    }
  });

  $('btn-integrated-fullscreen')?.addEventListener('click', () => {
    const video = $('integrated-video');
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    } else if (video) {
      video.requestFullscreen().catch(() => {});
    }
  });

  // Toggle para ocultar canales caídos
  const toggleHide = $('toggle-hide-offline');
  if (toggleHide) {
    toggleHide.checked = uiState.hideOfflineChannels;
    toggleHide.addEventListener('change', (e) => {
      uiState.hideOfflineChannels = e.target.checked;
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('iptv_hide_offline', String(e.target.checked));
      }
      renderLiveChannelsList();
      toast(e.target.checked ? '👁️ Canales caídos ocultados' : '👁️ Mostrando todos los canales', 'info', 2000);
    });
  }

  // Botón para verificar disponibilidad en la categoría actual
  const btnCheckHealth = $('btn-check-category-health');
  if (btnCheckHealth) {
    btnCheckHealth.addEventListener('click', async () => {
      let channels = iptvService.state.live || [];
      if (uiState.selectedLiveCategory === '__SPORTS__') {
        const sportsKeywords = ['sport', 'deport', 'futbol', 'football', 'espn', 'fox sport', 'laliga', 'nba', 'nfl', 'ufc'];
        channels = channels.filter((c) => {
          const cat = (c.categoryName || '').toLowerCase();
          const name = (c.name || '').toLowerCase();
          return sportsKeywords.some(kw => cat.includes(kw) || name.includes(kw));
        });
      } else if (uiState.selectedLiveCategory) {
        channels = channels.filter((c) => c.categoryName === uiState.selectedLiveCategory);
      }

      if (channels.length === 0) {
        toast('No hay canales para verificar.', 'info');
        return;
      }

      const toCheck = channels.slice(0, 100);
      btnCheckHealth.classList.add('running');
      btnCheckHealth.textContent = `⏳ 0/${toCheck.length}`;
      toast(`🔍 Verificando ${toCheck.length} canales con comprobación dual segura...`, 'info', 2500);

      try {
        await channelHealthService.checkBatch(toCheck, {
          onProgress: ({ current, total }) => {
            btnCheckHealth.textContent = `⏳ ${current}/${total}`;
          }
        });
        toast(`✓ Verificación completada (${toCheck.length} canales analizados)`, 'success', 3000);
      } catch (err) {
        console.warn('[App] Error al verificar canales:', err);
      } finally {
        btnCheckHealth.classList.remove('running');
        btnCheckHealth.textContent = '⚡ Verificar';
        renderLiveChannelsList();
      }
    });
  }
}

function renderLiveTVView() {
  const catTitle = $('current-category-title');
  if (catTitle) {
    if (!uiState.selectedLiveCategory) catTitle.textContent = 'Todos los Canales';
    else if (uiState.selectedLiveCategory === '__SPORTS__') catTitle.textContent = '⚽ Deportes en Vivo';
    else if (uiState.selectedLiveCategory.startsWith('__USER_CAT_')) {
      const ucatId = uiState.selectedLiveCategory.replace('__USER_CAT_', '');
      const ucat = getUserCreatedCategories().find(c => c.id === ucatId);
      catTitle.textContent = ucat ? `⭐ ${ucat.name}` : 'Mi Categoría';
    }
    else catTitle.textContent = uiState.selectedLiveCategory;
  }

  // Actualizar clase activa en menú de categorías
  document.querySelectorAll('.cat-list-item').forEach((item) => {
    const isAll = item.textContent.includes('Todos') && !uiState.selectedLiveCategory;
    const isSports = item.textContent.includes('Deportes') && uiState.selectedLiveCategory === '__SPORTS__';
    const isCat = item.textContent.startsWith(uiState.selectedLiveCategory);
    item.classList.toggle('active', isAll || isSports || isCat);
  });

  renderLiveChannelsList();
}

function renderLiveChannelsList() {
  const container = $('live-channels-list');
  if (!container) return;

  let channels = iptvService.state.live || [];
  const hiddenLive = new Set(getHiddenCategories().live);

  // Filtrado si es una categoría creada por el usuario
  if (uiState.selectedLiveCategory && uiState.selectedLiveCategory.startsWith('__USER_CAT_')) {
    const ucatId = uiState.selectedLiveCategory.replace('__USER_CAT_', '');
    const userCats = getUserCreatedCategories();
    const ucat = userCats.find(c => c.id === ucatId);
    if (ucat) {
      channels = getUserCategoryChannels(ucat);
    } else {
      channels = [];
    }
  } else {
    // Filtrado de canales pertenecientes a categorías ocultadas por el usuario
    channels = channels.filter((c) => !hiddenLive.has(c.categoryName));

    // Filtrado por categoría
    if (uiState.selectedLiveCategory === '__SPORTS__') {
      const sportsKeywords = ['sport', 'deport', 'futbol', 'football', 'espn', 'fox sport', 'laliga', 'nba', 'nfl', 'ufc'];
      channels = channels.filter((c) => {
        const cat = (c.categoryName || '').toLowerCase();
        const name = (c.name || '').toLowerCase();
        return sportsKeywords.some(kw => cat.includes(kw) || name.includes(kw));
      });
    } else if (uiState.selectedLiveCategory) {
      channels = channels.filter((c) => c.categoryName === uiState.selectedLiveCategory);
    }
  }

  // Filtrado por búsqueda
  if (uiState.liveSearch) {
    channels = channels.filter((c) =>
      c.name.toLowerCase().includes(uiState.liveSearch) ||
      c.categoryName.toLowerCase().includes(uiState.liveSearch)
    );
  }

  // Filtrado de canales caídos (OFFLINE)
  if (uiState.hideOfflineChannels) {
    channels = channels.filter((c) => !channelHealthService.isChannelOffline(c.id));
  }

  $('live-channels-count').textContent = channels.length;
  container.innerHTML = '';

  if (channels.length === 0) {
    const isUserCat = uiState.selectedLiveCategory && uiState.selectedLiveCategory.startsWith('__USER_CAT_');
    container.innerHTML = `
      <div class="empty-state" style="padding:2.5rem 1.5rem; text-align:center;">
        <div style="font-size:2rem; margin-bottom:0.5rem;">${isUserCat ? '⭐' : '📺'}</div>
        <div class="empty-title">${isUserCat ? 'Categoría vacía' : 'Sin canales'}</div>
        <div class="empty-sub" style="margin-top:6px;">
          ${isUserCat 
            ? 'Esta categoría no tiene canales aún. Busca cualquier canal y presiona ➕ para agregarlo aquí.' 
            : 'No se encontraron canales disponibles en esta categoría.'}
        </div>
      </div>
    `;
    return;
  }

  // Paginación o lista visible (primeros 150 canales para óptimo DOM)
  const visibleSlice = channels.slice(0, 150);

  visibleSlice.forEach((ch) => {
    const healthStatus = channelHealthService.getChannelStatus(ch.id);
    let healthDot = '';
    if (healthStatus === HealthStatus.ONLINE) {
      healthDot = '<span class="health-dot online" title="Canal activo y verificado"></span>';
    } else if (healthStatus === HealthStatus.OFFLINE) {
      healthDot = '<span class="health-dot offline" title="Canal no disponible / caído"></span>';
    } else if (healthStatus === HealthStatus.DEGRADED) {
      healthDot = '<span class="health-dot degraded" title="Canal con latencia alta"></span>';
    }

    const row = document.createElement('div');
    row.className = `channel-row ${uiState.currentLiveChannel?.id === ch.id ? 'active' : ''} ${healthStatus === HealthStatus.OFFLINE ? 'is-offline' : ''}`;
    row.innerHTML = `
      ${ch.logo
        ? `<img class="channel-logo-img" src="${escHtml(ch.logo)}" alt="" loading="lazy" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'">`
        : ''}
      <div class="channel-logo-ph" style="${ch.logo ? 'display:none' : ''}">📺</div>
      <div class="channel-meta">
        <div class="channel-title">${healthDot}${escHtml(ch.name)}</div>
        <div class="channel-cat-sub">${escHtml(ch.categoryName)}</div>
      </div>
      <div class="channel-row-actions">
        <button class="channel-action-btn channel-copy-btn" title="Copiar enlace de streaming directo">🔗</button>
        <button class="channel-action-btn channel-add-m3u-btn" title="Añadir a Mi Categoría / Lista M3U">➕</button>
        <button class="channel-fav-btn" title="Favorito">☆</button>
      </div>
    `;

    // Clic en canal para reproducir en el reproductor integrado
    row.addEventListener('click', (e) => {
      if (e.target.closest('.channel-row-actions')) return;
      selectLiveChannel(ch);
    });

    // Copiar enlace directo
    const copyBtn = row.querySelector('.channel-copy-btn');
    if (copyBtn) {
      copyBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        copyStreamUrl(ch.streamUrl, ch.name);
      });
    }

    // Añadir a Mi Categoría / Lista M3U
    const addM3uBtn = row.querySelector('.channel-add-m3u-btn');
    if (addM3uBtn) {
      addM3uBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        openQuickAddToCatModal(ch);
      });
    }

    // Botón favorito
    const favBtn = row.querySelector('.channel-fav-btn');
    cacheService.isFavorite(ch.id).then((isFav) => {
      if (isFav) {
        favBtn.textContent = '★';
        favBtn.classList.add('active');
      }
    });

    favBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const added = await cacheService.toggleFavorite({
        id: ch.id,
        type: 'live',
        name: ch.name,
        logo: ch.logo,
        categoryName: ch.categoryName,
        streamUrl: ch.streamUrl
      });
      favBtn.textContent = added ? '★' : '☆';
      favBtn.classList.toggle('active', added);
      toast(added ? `⭐ ${ch.name} agregado a favoritos` : `${ch.name} eliminado de favoritos`, 'info');
      renderHomeDynamicSections();
    });

    container.appendChild(row);
  });
}

async function selectLiveChannel(ch) {
  uiState.currentLiveChannel = ch;

  // Actualizar fila activa en lista
  document.querySelectorAll('.channel-row').forEach((r) => r.classList.remove('active'));

  // Actualizar top bar
  $('integrated-channel-name').textContent = ch.name;

  // Actualizar botón de favorito del reproductor integrado
  const favBtn = $('btn-integrated-fav');
  if (favBtn) {
    cacheService.isFavorite(ch.id).then(isFav => {
      favBtn.textContent = isFav ? '★' : '☆';
      favBtn.classList.toggle('active', isFav);
    });
  }

  // Iniciar reproducción
  playerService.play({
    id: ch.id,
    title: ch.name,
    url: ch.streamUrl,
    type: 'live'
  });

  // Consultar y actualizar EPG
  $('epg-now-title').textContent = 'Consultando guía...';
  $('epg-now-time').textContent = '—';
  $('epg-now-progress').style.width = '0%';
  $('epg-now-desc').textContent = '';
  $('epg-next-title').textContent = '—';
  $('epg-next-time').textContent = '—';

  try {
    const epg = await iptvService.getEPG(ch.id);
    if (epg.now) {
      $('epg-now-title').textContent = epg.now.title;
      $('epg-now-time').textContent = `${epg.now.startTimeStr} - ${epg.now.stopTimeStr}`;
      $('epg-now-progress').style.width = `${epg.now.progress || 0}%`;
      $('epg-now-desc').textContent = epg.now.description || '';
    } else {
      $('epg-now-title').textContent = 'Emisión en directo';
      $('epg-now-desc').textContent = 'Guía electrónica no provista por el servidor para este canal.';
    }

    if (epg.next) {
      $('epg-next-title').textContent = epg.next.title;
      $('epg-next-time').textContent = `${epg.next.startTimeStr} - ${epg.next.stopTimeStr}`;
      $('epg-next-desc').textContent = epg.next.description || '';
    } else {
      $('epg-next-title').textContent = 'Programación habitual';
    }
  } catch {
    $('epg-now-title').textContent = 'Emisión en directo';
  }
}

// ════════════ MOVIES (CATÁLOGO VOD) ════════════
function setupMoviesView(movies, categories) {
  const sel = $('movies-cat-filter');
  if (!sel) return;

  const hiddenMovies = new Set(getHiddenCategories().movies);
  sel.innerHTML = '<option value="">Todas las categorías</option>';
  const cats = [...new Set(movies.map((m) => m.categoryName))]
    .filter(cat => !parentalControlService.isRestricted(cat))
    .filter(cat => !hiddenMovies.has(cat))
    .sort();
  cats.forEach((cat) => {
    const opt = document.createElement('option');
    opt.value = cat;
    opt.textContent = cat;
    sel.appendChild(opt);
  });

  sel.addEventListener('change', (e) => {
    uiState.moviesCategoryFilter = e.target.value;
    uiState.moviesPage = 1;
    renderMoviesPage();
  });

  $('movies-search-input')?.addEventListener('input', debounce((e) => {
    uiState.moviesSearch = e.target.value.trim().toLowerCase();
    uiState.moviesPage = 1;
    renderMoviesPage();
  }, 250));

  uiState.moviesFiltered = movies;
  renderMoviesPage();
}

function renderMoviesPage() {
  let items = iptvService.state.movies || [];
  const hiddenMovies = new Set(getHiddenCategories().movies);

  // Control parental: ocultar contenido adulto cuando está bloqueado
  items = items.filter(m => !parentalControlService.isRestricted(m.categoryName, m.name));

  // Filtrado de categorías ocultadas por el usuario
  items = items.filter(m => !hiddenMovies.has(m.categoryName));

  if (uiState.moviesCategoryFilter) {
    items = items.filter((m) => m.categoryName === uiState.moviesCategoryFilter);
  }
  if (uiState.moviesSearch) {
    items = items.filter((m) =>
      m.name.toLowerCase().includes(uiState.moviesSearch) ||
      m.categoryName.toLowerCase().includes(uiState.moviesSearch)
    );
  }

  const total = items.length;
  const totalPages = Math.ceil(total / PAGE_SIZE_MOVIES);
  const page = Math.min(uiState.moviesPage, totalPages || 1);
  const slice = items.slice((page - 1) * PAGE_SIZE_MOVIES, page * PAGE_SIZE_MOVIES);

  $('movies-catalog-info').textContent = `${total.toLocaleString()} películas`;
  const grid = $('movies-grid');
  grid.innerHTML = '';

  if (slice.length === 0) {
    grid.innerHTML = `<div class="empty-state" style="grid-column:1/-1;padding:3rem"><div class="empty-icon">🎬</div><div class="empty-title">Sin películas</div></div>`;
    $('movies-pagination').innerHTML = '';
    return;
  }

  slice.forEach((movie) => {
    const card = document.createElement('div');
    card.className = 'media-poster-card';
    const ratingHtml = movie.rating ? `<span class="poster-rating-badge">⭐ ${movie.rating}</span>` : '';
    const yearHtml = movie.year ? `<span class="poster-year-badge">${movie.year}</span>` : '';

    const rawPoster = (movie.logo || movie.poster || '').trim();
    const fallbackSvg = createPosterPlaceholderSvg(movie.name, movie.categoryName, '🎬');
    let posterSrc = rawPoster;
    if (posterSrc && typeof window !== 'undefined' && window.location.protocol === 'https:' && posterSrc.startsWith('http://')) {
      posterSrc = getProxyUrl(posterSrc);
    }
    if (!posterSrc) {
      posterSrc = fallbackSvg;
    }

    card.innerHTML = `
      <div class="poster-img-wrap">
        <img class="poster-img" src="${escHtml(posterSrc)}" alt="${escHtml(movie.name)}" loading="lazy" onerror="this.onerror=null;this.src='${fallbackSvg}';"/>
        ${ratingHtml}
        ${yearHtml}
      </div>
      <div class="poster-info-box">
        <div class="poster-title">${escHtml(movie.name)}</div>
        <div class="poster-cat">${escHtml(movie.categoryName)}</div>
      </div>
    `;

    card.addEventListener('click', async () => {
      toast(`Cargando información de ${movie.name}...`, 'info', 1500);
      try {
        const info = await iptvService.getMovieInfo(movie.id);
        mediaDetailModal.openVod(movie, info);
      } catch {
        mediaDetailModal.openVod(movie, null);
      }
    });

    grid.appendChild(card);
  });

  renderPagination('movies-pagination', page, totalPages, (p) => {
    uiState.moviesPage = p;
    renderMoviesPage();
  });
}

// ════════════ SERIES (CATÁLOGO PAGINADO CON LAZY LOADING) ════════════
function setupSeriesView(series, categories) {
  const sel = $('series-cat-filter');
  if (!sel) return;

  const hiddenSeries = new Set(getHiddenCategories().series);
  sel.innerHTML = '<option value="">Todas las categorías</option>';
  const cats = [...new Set(series.map((s) => s.categoryName))]
    .filter(cat => !parentalControlService.isRestricted(cat))
    .filter(cat => !hiddenSeries.has(cat))
    .sort();
  cats.forEach((cat) => {
    const opt = document.createElement('option');
    opt.value = cat;
    opt.textContent = cat;
    sel.appendChild(opt);
  });

  sel.addEventListener('change', (e) => {
    uiState.seriesCategoryFilter = e.target.value;
    uiState.seriesPage = 1;
    renderSeriesPage();
  });

  $('series-search-input')?.addEventListener('input', debounce((e) => {
    uiState.seriesSearch = e.target.value.trim().toLowerCase();
    uiState.seriesPage = 1;
    renderSeriesPage();
  }, 250));

  uiState.seriesFiltered = series;
  renderSeriesPage();
}

function renderSeriesPage() {
  let items = iptvService.state.series || [];
  const hiddenSeries = new Set(getHiddenCategories().series);

  // Control parental: ocultar contenido adulto cuando está bloqueado
  items = items.filter(s => !parentalControlService.isRestricted(s.categoryName, s.name));

  // Filtrado de categorías ocultadas por el usuario
  items = items.filter(s => !hiddenSeries.has(s.categoryName));

  if (uiState.seriesCategoryFilter) {
    items = items.filter((s) => s.categoryName === uiState.seriesCategoryFilter);
  }
  if (uiState.seriesSearch) {
    items = items.filter((s) =>
      s.name.toLowerCase().includes(uiState.seriesSearch) ||
      s.categoryName.toLowerCase().includes(uiState.seriesSearch)
    );
  }

  const total = items.length;
  const totalPages = Math.ceil(total / PAGE_SIZE_SERIES);
  const page = Math.min(uiState.seriesPage, totalPages || 1);
  const slice = items.slice((page - 1) * PAGE_SIZE_SERIES, page * PAGE_SIZE_SERIES);

  $('series-catalog-info').textContent = `${total.toLocaleString()} series`;
  const grid = $('series-grid');
  grid.innerHTML = '';

  if (slice.length === 0) {
    grid.innerHTML = `<div class="empty-state" style="grid-column:1/-1;padding:3rem"><div class="empty-icon">🎭</div><div class="empty-title">Sin series</div></div>`;
    $('series-pagination').innerHTML = '';
    return;
  }

  slice.forEach((s) => {
    const card = document.createElement('div');
    card.className = 'media-poster-card';
    const ratingHtml = s.rating ? `<span class="poster-rating-badge">⭐ ${s.rating}</span>` : '';
    const yearHtml = s.year ? `<span class="poster-year-badge">${s.year}</span>` : '';

    const rawPoster = (s.logo || s.poster || '').trim();
    const fallbackSvg = createPosterPlaceholderSvg(s.name, s.categoryName, '🎭');
    let posterSrc = rawPoster;
    if (posterSrc && typeof window !== 'undefined' && window.location.protocol === 'https:' && posterSrc.startsWith('http://')) {
      posterSrc = getProxyUrl(posterSrc);
    }
    if (!posterSrc) {
      posterSrc = fallbackSvg;
    }

    card.innerHTML = `
      <div class="poster-img-wrap">
        <img class="poster-img" src="${escHtml(posterSrc)}" alt="${escHtml(s.name)}" loading="lazy" onerror="this.onerror=null;this.src='${fallbackSvg}';"/>
        ${ratingHtml}
        ${yearHtml}
      </div>
      <div class="poster-info-box">
        <div class="poster-title">${escHtml(s.name)}</div>
        <div class="poster-cat">${escHtml(s.categoryName)}</div>
      </div>
    `;

    card.addEventListener('click', async () => {
      toast(`Cargando temporadas de ${s.name}...`, 'info', 1500);
      try {
        const info = await iptvService.getSeriesInfo(s.id);
        const parsed = iptvService._parsedUrl;
        const streamUrlBuilder = (epId, ext) => {
          return buildSeriesStreamUrl(parsed.serverBaseUrl, parsed.username, parsed._password, epId, ext);
        };
        mediaDetailModal.openSeries(s, info, streamUrlBuilder);
      } catch (err) {
        toast(`Error al cargar serie: ${err.message}`, 'error');
      }
    });

    grid.appendChild(card);
  });

  renderPagination('series-pagination', page, totalPages, (p) => {
    uiState.seriesPage = p;
    renderSeriesPage();
  });
}

// ════════════ BÚSQUEDA GLOBAL INSTANTÁNEA ════════════
function setupSearch() {
  const input = $('global-search-input');
  const clearBtn = $('search-clear-btn');
  const dropdown = $('global-search-dropdown');
  const content = $('search-dropdown-content');

  let currentSelectedIndex = -1;

  const getVisibleRows = () => content?.querySelectorAll('.search-item-row') || [];

  const updateKeyboardSelection = (newIndex) => {
    const rows = getVisibleRows();
    if (rows.length === 0) return;

    rows.forEach(r => r.classList.remove('keyboard-selected'));
    if (newIndex >= 0 && newIndex < rows.length) {
      currentSelectedIndex = newIndex;
      const target = rows[currentSelectedIndex];
      target.classList.add('keyboard-selected');
      target.scrollIntoView({ block: 'nearest' });
    } else {
      currentSelectedIndex = -1;
    }
  };

  const onSearch = debounce((q) => {
    currentSelectedIndex = -1;
    if (!q || q.length < 2) {
      dropdown?.classList.add('hidden');
      return;
    }

    // Límites específicos para Search Overlay XALB: Live 3, Movies 8, Series 4
    const parentalFilter = (entry) => !parentalControlService.isRestricted(entry.category, entry.title);
    const res = searchService.search(q, { live: 3, movies: 8, series: 4 }, parentalFilter);
    if (res.totalMatches === 0) {
      content.innerHTML = `<div class="empty-state" style="padding:1.5rem"><div class="empty-title">Sin resultados para "${escHtml(q)}"</div></div>`;
      dropdown?.classList.remove('hidden');
      return;
    }

    let html = `
      <div class="search-group-title">
        <span>Resultados de búsqueda</span>
        <span class="search-latency-badge">⚡ ${res.searchTimeMs} ms</span>
      </div>
    `;

    // Canales Live (Máx 3)
    if (res.live.length > 0) {
      html += `<div class="search-group-title" style="margin-top:0.35rem"><span>📺 Live TV</span><span>${res.live.length}</span></div>`;
      res.live.forEach((ch) => {
        html += `
          <div class="search-item-row" data-type="live" data-id="${ch.id}">
            <div class="search-item-thumb">
              ${ch.image ? `<img src="${escHtml(ch.image)}" alt="" onerror="this.style.display='none';this.parentElement.textContent='📺'">` : '📺'}
            </div>
            <div class="search-item-text">
              <div class="search-item-name">${escHtml(ch.title)}</div>
              <div class="search-item-sub">${escHtml(ch.category || 'Canal')}</div>
            </div>
          </div>
        `;
      });
    }

    // Películas (Máx 8)
    if (res.movies.length > 0) {
      html += `<div class="search-group-title" style="margin-top:0.35rem"><span>🎬 Películas VOD</span><span>${res.movies.length}</span></div>`;
      res.movies.forEach((m) => {
        html += `
          <div class="search-item-row" data-type="movie" data-id="${m.id}">
            <div class="search-item-thumb">
              ${m.image ? `<img src="${escHtml(m.image)}" alt="" onerror="this.style.display='none';this.parentElement.textContent='🎬'">` : '🎬'}
            </div>
            <div class="search-item-text">
              <div class="search-item-name">${escHtml(m.title)}</div>
              <div class="search-item-sub">${escHtml(m.category || 'VOD')} ${m.year ? `· ${m.year}` : ''}</div>
            </div>
          </div>
        `;
      });
    }

    // Series (Máx 4)
    if (res.series.length > 0) {
      html += `<div class="search-group-title" style="margin-top:0.35rem"><span>🎞 Series</span><span>${res.series.length}</span></div>`;
      res.series.forEach((s) => {
        html += `
          <div class="search-item-row" data-type="series" data-id="${s.id}">
            <div class="search-item-thumb">
              ${s.image ? `<img src="${escHtml(s.image)}" alt="" onerror="this.style.display='none';this.parentElement.textContent='🎞'">` : '🎞'}
            </div>
            <div class="search-item-text">
              <div class="search-item-name">${escHtml(s.title)}</div>
              <div class="search-item-sub">${escHtml(s.category || 'Serie')}</div>
            </div>
          </div>
        `;
      });
    }

    content.innerHTML = html;
    dropdown?.classList.remove('hidden');

    content.querySelectorAll('.search-item-row').forEach((row) => {
      row.addEventListener('click', () => {
        dropdown?.classList.add('hidden');
        input.value = '';
        clearBtn?.classList.add('hidden');

        const type = row.dataset.type;
        const id = row.dataset.id;

        if (type === 'live') {
          navigateTo('live');
          const ch = iptvService.state.live.find(c => String(c.id || c.stream_id) === id);
          if (ch) selectLiveChannel(ch);
        } else if (type === 'movie') {
          const m = iptvService.state.movies.find(c => String(c.id || c.stream_id) === id);
          if (m) {
            iptvService.getMovieInfo(m.id).then(info => mediaDetailModal.openVod(m, info));
          }
        } else if (type === 'series') {
          const s = iptvService.state.series.find(c => String(c.id || c.series_id) === id);
          if (s) {
            iptvService.getSeriesInfo(s.id).then(info => {
              const parsed = iptvService._parsedUrl;
              mediaDetailModal.openSeries(s, info, (epId, ext) => buildSeriesStreamUrl(parsed.serverBaseUrl, parsed.username, parsed._password, epId, ext));
            });
          }
        }
      });
    });
  }, 160);

  input?.addEventListener('input', (e) => {
    const val = e.target.value.trim();
    clearBtn?.classList.toggle('hidden', !val);
    onSearch(val);
  });

  // Navegación por teclado: ArrowUp, ArrowDown, Enter, Escape
  input?.addEventListener('keydown', (e) => {
    const rows = getVisibleRows();
    if (rows.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      const next = currentSelectedIndex < rows.length - 1 ? currentSelectedIndex + 1 : 0;
      updateKeyboardSelection(next);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const prev = currentSelectedIndex > 0 ? currentSelectedIndex - 1 : rows.length - 1;
      updateKeyboardSelection(prev);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (currentSelectedIndex >= 0 && rows[currentSelectedIndex]) {
        rows[currentSelectedIndex].click();
      }
    } else if (e.key === 'Escape') {
      dropdown?.classList.add('hidden');
      input.blur();
    }
  });

  clearBtn?.addEventListener('click', () => {
    input.value = '';
    clearBtn.classList.add('hidden');
    dropdown?.classList.add('hidden');
    currentSelectedIndex = -1;
  });

  document.addEventListener('click', (e) => {
    if (!e.target.closest('#global-search-box')) {
      dropdown?.classList.add('hidden');
      currentSelectedIndex = -1;
    }
  });
}

// ════════════ AJUSTES: PESTAÑAS, PLAYLISTS, DIAGNÓSTICO, EXPORTAR ════════════
function setupSettingsTabs() {
  document.querySelectorAll('.settings-tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.settings-tab-btn').forEach((b) => b.classList.remove('active'));
      document.querySelectorAll('.settings-tab-content').forEach((c) => c.classList.remove('active'));

      btn.classList.add('active');
      $(`tab-${btn.dataset.tab}`)?.classList.add('active');

      if (btn.dataset.tab === 'playlists') renderSettingsPlaylists();
      if (btn.dataset.tab === 'diagnostics') renderDiagnostics(iptvService.getDiagnostics());
      if (btn.dataset.tab === 'custom-m3u') renderCustomM3UManager();
    });
  });

  // Guardar playlist
  $('btn-save-playlist')?.addEventListener('click', async () => {
    const url = $('settings-url-input').value.trim();
    if (!url) {
      toast('Por favor ingresa una URL válida.', 'error');
      return;
    }

    if (url.includes('/api/playlist/')) {
      toast('💡 Esta es tu URL de lista sincronizada en la nube. Ya está lista en la pestaña "Creador de Lista M3U" o pulsando "TV en Vivo".', 'info', 5000);
      const customTabBtn = document.querySelector('.settings-tab-btn[data-tab="custom-m3u"]');
      if (customTabBtn) customTabBtn.click();
      return;
    }

    try {
      await loadCatalogWithProgress(url);
      $('settings-url-input').value = '';
      renderSettingsPlaylists();
    } catch (e) {
      console.error(e);
    }
  });
}

async function renderSettingsPlaylists() {
  const container = $('saved-playlists-list');
  if (!container) return;

  const customList = getCustomM3UList();
  const isCustomActive = (localStorage.getItem('iptv_active_playlist_id') === 'custom_lelouch_playlist');

  let customCardHtml = '';
  if (customList && customList.length > 0) {
    customCardHtml = `
      <div class="playlist-card-row custom-m3u-featured-banner ${isCustomActive ? 'active' : ''}" style="background:linear-gradient(135deg, rgba(6,182,212,0.1) 0%, rgba(16,185,129,0.08) 100%); border:1.5px solid ${isCustomActive ? 'rgba(16,185,129,0.6)' : 'rgba(6,182,212,0.45)'}; border-radius:14px; margin-bottom:1.5rem; box-shadow:0 6px 24px ${isCustomActive ? 'rgba(16,185,129,0.12)' : 'rgba(6,182,212,0.1)'}; position:relative; overflow:hidden;">
        <div style="position:absolute; top:0; left:0; width:4px; height:100%; background:${isCustomActive ? 'linear-gradient(180deg, #10b981, #059669)' : 'linear-gradient(180deg, #00e5ff, #10b981)'};"></div>
        <div class="pl-info" style="padding-left:6px;">
          <div class="pl-title-row" style="flex-wrap:wrap; gap:6px; align-items:center;">
            <span class="pl-name" style="color:var(--accent-cyan); font-weight:800; font-size:1.02rem; display:inline-flex; align-items:center; gap:6px;">
              <span>⭐</span> Tu Lista Generada LELOUCH
            </span>
            ${isCustomActive
              ? '<span class="pl-tag-active" style="background:linear-gradient(90deg,#10b981,#059669);color:#fff;font-size:0.65rem;font-weight:800;padding:0.2rem 0.65rem;border-radius:99px;letter-spacing:0.05em;">✓ EN USO</span>'
              : ''}
            <span class="badge-mini" style="background:rgba(16,185,129,0.2); color:#10b981; border:1px solid rgba(16,185,129,0.4); font-size:10px; font-weight:700;">☁️ Sincronizada en Supabase</span>
            <span class="badge-mini" style="background:rgba(6,182,212,0.18); color:var(--accent-cyan); border:1px solid rgba(6,182,212,0.35); font-size:10px; font-weight:700;">${customList.length} canales</span>
          </div>
          <div class="pl-sub" style="margin-top:5px; color:var(--text-secondary); font-size:0.8rem;">
            <span>Creada con el <b>Creador de Lista M3U</b></span> •
            <span>Lista para consumir vía: <b>Copiar M3U</b>, <b>Descargar .m3u</b> o <b>URL Nube</b></span>
          </div>
        </div>
        <div class="pl-actions" style="flex-wrap:wrap; gap:6px;">
          ${isCustomActive
            ? '<button class="btn btn-sm" style="background:linear-gradient(90deg,#10b981,#059669);color:#fff;opacity:0.85;cursor:default;pointer-events:none;font-weight:700;" disabled>✓ En uso</button>'
            : '<button id="btn-featured-custom-activate" class="btn btn-primary btn-sm" style="font-weight:700; background:linear-gradient(135deg, #00e5ff, #0088ff); color:#02070d; border:none; box-shadow:0 2px 10px rgba(0,229,255,0.25);" title="Activar Mi Lista LELOUCH como la playlist activa">⚡ Activar</button>'
          }
          <button id="btn-featured-custom-play" class="btn btn-secondary btn-sm" style="font-weight:700;" title="Ver y reproducir tus canales en TV en Vivo ahora">
            📺 Ver en TV en Vivo
          </button>
          <button id="btn-featured-custom-manage" class="btn btn-secondary btn-sm" style="border-color:rgba(6,182,212,0.4); color:var(--accent-cyan);" title="Ir a la pestaña Creador M3U para editar, ordenar o agregar canales">
            🔧 Abrir Creador M3U
          </button>
          <button id="btn-featured-custom-url" class="btn btn-secondary btn-sm" title="Ver o copiar enlace M3U público con token">
            🌐 Enlace M3U
          </button>
        </div>
      </div>
    `;
  }

  const lists = await playlistService.getAll();
  if (lists.length === 0 && !customCardHtml) {
    container.innerHTML = `<div class="empty-state"><div class="empty-title">Sin playlists guardadas</div><div class="empty-sub">Ingresa un enlace arriba para registrar tu primera cuenta.</div></div>`;
    return;
  }

  const providerCardsHtml = lists.map((pl) => {
    const isThisActive = !isCustomActive && pl.isActive;
    return `
    <div class="playlist-card-row ${isThisActive ? 'active' : ''}">
      <div class="pl-info">
        <div class="pl-title-row">
          <span class="pl-name">${escHtml(pl.name)}</span>
          ${isThisActive
            ? '<span class="pl-tag-active" style="background:linear-gradient(90deg,#10b981,#059669);color:#fff;font-size:0.65rem;font-weight:800;padding:0.2rem 0.65rem;border-radius:99px;letter-spacing:0.05em;">✓ EN USO</span>'
            : ''}
          <span class="badge-mini" style="background:rgba(56,189,248,0.15); color:#38bdf8; font-size:10px; margin-left:8px; border:1px solid rgba(56,189,248,0.3)">☁️ En la Nube</span>
        </div>
        <div class="pl-sub">
          <span>Servidor: ${escHtml(pl.serverBaseUrl)}</span> •
          <span>Usuario: ${escHtml(pl.username || '—')}</span> •
          <span>Contraseña: ••••••••</span>
        </div>
      </div>
      <div class="pl-actions">
        ${isThisActive
          ? '<button class="btn btn-sm" style="background:linear-gradient(90deg,#10b981,#059669);color:#fff;opacity:0.85;cursor:default;pointer-events:none;" disabled>✓ En uso</button>'
          : `<button class="btn btn-primary btn-sm btn-activate-pl" data-id="${pl.id}">⚡ Activar</button>`}
        <button class="btn btn-secondary btn-sm btn-extract-pl" data-id="${pl.id}" style="color:var(--accent-cyan); border-color:rgba(0,229,255,0.4); font-weight:700;" title="Extraer contenido de esta cuenta para alimentar Mi Lista LELOUCH">📥 Extraer a Mi Lista</button>
        <button class="btn btn-secondary btn-sm btn-edit-pl" data-id="${pl.id}">✏️ Modificar</button>
        <button class="btn btn-secondary btn-sm btn-reload-pl" data-id="${pl.id}">🔄 Recargar</button>
        <button class="btn btn-danger btn-sm btn-delete-pl" data-id="${pl.id}">🗑️ Eliminar</button>
      </div>
    </div>
  `;
  }).join('');

  container.innerHTML = customCardHtml + providerCardsHtml;

  // Listeners de la tarjeta destacada de la lista personalizada
  $('btn-featured-custom-activate')?.addEventListener('click', async () => {
    await activateCustomM3UAsMainPlaylist();
  });
  $('btn-featured-custom-play')?.addEventListener('click', () => {
    $('btn-custom-m3u-play-live')?.click();
  });
  $('btn-featured-custom-manage')?.addEventListener('click', () => {
    const customTabBtn = document.querySelector('.settings-tab-btn[data-tab="custom-m3u"]');
    if (customTabBtn) customTabBtn.click();
  });
  $('btn-featured-custom-url')?.addEventListener('click', () => {
    generateAndShowPublicM3ULink();
  });

  // Evento extraer contenido a Mi Lista LELOUCH
  container.querySelectorAll('.btn-extract-pl').forEach((btn) => {
    btn.addEventListener('click', () => {
      openPlaylistImportModal(btn.dataset.id);
    });
  });

  // Eventos de botones de proveedores
  container.querySelectorAll('.btn-activate-pl').forEach((btn) => {
    btn.addEventListener('click', async () => {
      localStorage.removeItem('iptv_active_playlist_id');
      const active = await playlistService.activate(btn.dataset.id);
      if (active) {
        toast(`Activando ${active.name}...`, 'info');
        await loadCatalogWithProgress(active.url);
        renderSettingsPlaylists();
        updateActivePlaylistUI();
      }
    });
  });

  container.querySelectorAll('.btn-reload-pl').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const lists = await cacheService.getPlaylists();
      const pl = lists.find(p => p.id === btn.dataset.id);
      if (pl) {
        await loadCatalogWithProgress(pl.url);
        renderSettingsPlaylists();
        updateActivePlaylistUI();
      }
    });
  });

  container.querySelectorAll('.btn-edit-pl').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const lists = await cacheService.getPlaylists();
      const pl = lists.find(p => p.id === btn.dataset.id);
      if (pl) {
        const inputUrl = $('settings-url-input');
        if (inputUrl) {
          inputUrl.value = pl.url;
          inputUrl.focus();
          window.scrollTo({ top: 0, behavior: 'smooth' });
          toast('Puedes modificar la URL y luego pulsar Guardar.', 'info');
        }
      }
    });
  });

  container.querySelectorAll('.btn-delete-pl').forEach((btn) => {
    btn.addEventListener('click', async () => {
      await playlistService.remove(btn.dataset.id);
      toast('Playlist eliminada.', 'info');
      renderSettingsPlaylists();
      updateActivePlaylistUI();
    });
  });
}

function renderDiagnostics(diagnostics) {
  const container = $('diagnostics-table-wrap');
  if (!container) return;

  if (!diagnostics || diagnostics.length === 0) {
    container.innerHTML = `<div class="empty-state"><div class="empty-icon">🔬</div><div class="empty-title">Sin datos de diagnóstico</div></div>`;
    return;
  }

  container.innerHTML = `
    <table class="debug-table">
      <thead>
        <tr>
          <th>Endpoint</th>
          <th>Estado HTTP</th>
          <th>Latencia</th>
          <th>Registros</th>
          <th>Resultado</th>
        </tr>
      </thead>
      <tbody>
        ${diagnostics.map((d) => `
          <tr>
            <td style="max-width:320px;overflow:hidden;text-overflow:ellipsis">${escHtml(d.endpoint)}</td>
            <td>${d.httpStatus ?? '—'}</td>
            <td>${d.responseTimeMs ? `${d.responseTimeMs} ms` : '—'}</td>
            <td>${d.recordCount ?? '—'}</td>
            <td class="${d.success ? 'dbg-ok' : 'dbg-err'}">${d.success ? '✓ OK' : '✗ ERROR'}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>
  `;
}

// ════════════ EXPORTACIÓN DE ARCHIVOS ════════════
window.appExport = function (type) {
  const state = iptvService.state;
  if (!state.connected && (!state.live || state.live.length === 0)) {
    toast('No hay catálogo cargado para exportar.', 'error');
    return;
  }

  const sanitizeName = (s) => String(s || '').replace(/[^a-zA-Z0-9\-_]/g, '_').slice(0, 60);

  switch (type) {
    case 'account':
      downloadJson({ account: state.account, server: state.server }, 'account.json');
      toast('📥 Descargando account.json...', 'success');
      break;

    case 'live': {
      const groups = groupByCategory(state.live);
      Object.entries(groups).forEach(([cat, items]) => {
        downloadJson(items, `LIVE_${sanitizeName(cat)}.json`);
      });
      toast(`📥 Descargando ${Object.keys(groups).length} archivos LIVE...`, 'success');
      break;
    }

    case 'movies': {
      const groups = groupByCategory(state.movies);
      Object.entries(groups).forEach(([cat, items]) => {
        downloadJson(items, `VOD_${sanitizeName(cat)}.json`);
      });
      toast(`📥 Descargando ${Object.keys(groups).length} archivos VOD...`, 'success');
      break;
    }

    case 'series': {
      const groups = groupByCategory(state.series);
      Object.entries(groups).forEach(([cat, items]) => {
        downloadJson(items, `SERIES_${sanitizeName(cat)}.json`);
      });
      toast(`📥 Descargando ${Object.keys(groups).length} archivos SERIES...`, 'success');
      break;
    }

    case 'm3u': {
      if (!state.live || state.live.length === 0) {
        toast('No hay canales en vivo para exportar.', 'error');
        return;
      }
      downloadM3UPlus(state.live, iptvService._parsedUrl, 'lista_iptv_catchup.m3u8');
      toast('📜 Generando lista M3U Plus con etiquetas Catchup...', 'success');
      break;
    }
  }
};

// ════════════ UTILIDAD COPIAR ENLACES DIRECTOS ════════════
export function copyStreamUrl(url, name = 'Stream') {
  if (!url) {
    toast('⚠️ No hay enlace disponible para este contenido', 'warning');
    return;
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url).then(() => {
      toast(`📋 Enlace copiado al portapapeles:\n${name}`, 'success', 3500);
    }).catch(() => {
      prompt(`Copia el enlace directo de "${name}":`, url);
    });
  } else {
    prompt(`Copia el enlace directo de "${name}":`, url);
  }
}
window.copyStreamUrl = copyStreamUrl;

// ════════════ CREADOR Y GESTOR DE LISTA M3U PERSONALIZADA ════════════
const CUSTOM_M3U_STORAGE_KEY = 'lelouch_custom_m3u_list';
let _customM3UMemoryCache = null;

export function getCustomM3UList() {
  if (_customM3UMemoryCache !== null) {
    return _customM3UMemoryCache;
  }
  try {
    const raw = localStorage.getItem(CUSTOM_M3U_STORAGE_KEY);
    _customM3UMemoryCache = raw ? JSON.parse(raw) : [];
  } catch {
    _customM3UMemoryCache = [];
  }
  return _customM3UMemoryCache;
}

const CUSTOM_M3U_VERSION_KEY = 'custom_m3u_version_v1';

export function getLocalM3UVersion() {
  try {
    return Number(localStorage.getItem(CUSTOM_M3U_VERSION_KEY) || 1);
  } catch {
    return 1;
  }
}

export function setLocalM3UVersion(ver) {
  try {
    localStorage.setItem(CUSTOM_M3U_VERSION_KEY, String(ver));
  } catch {}
}

let _cloudSyncTimeout = null;
export function scheduleCustomM3UCloudSync() {
  if (!supabaseService.isAvailable) return;
  if (_cloudSyncTimeout) clearTimeout(_cloudSyncTimeout);
  _cloudSyncTimeout = setTimeout(async () => {
    try {
      const pl = await supabaseService.getOrCreateDefaultCustomPlaylist('Mi Lista LELOUCH');
      if (pl?.id) {
        const currentList = getCustomM3UList();
        // Sincronizar todos los items a la nube por lotes (evita truncamiento)
        await supabaseService.syncPlaylistItems(pl.id, currentList);
        const serverInfo = await supabaseService.getPlaylistVersion(pl.id);
        if (serverInfo?.version) {
          setLocalM3UVersion(serverInfo.version);
        }
        console.log(`☁️ [CloudSync] Lista sincronizada con Supabase (${currentList.length} ítems, v${serverInfo?.version || 'N/A'}).`);
      }
    } catch (e) {
      console.warn('[CloudSync] Error sincronizando en segundo plano con Supabase:', e);
    }
  }, 1500);
}

/**
 * FASE 18 — Sincronización Determinista (Lelouch TV / Teléfono / Web)
 * No hace consultas cada segundo.
 * Disparadores exactos:
 * 1. al abrir aplicación (app_open)
 * 2. al volver del background (resume_from_background / window_focus)
 * 3. al entrar a Live (enter_live)
 * 4. botón Actualizar (user_click)
 */
let _isLifecycleSyncing = false;
export async function checkAndSyncCustomM3UVersion(triggerSource = 'manual') {
  if (_isLifecycleSyncing) return { inSync: true, busy: true };
  if (!supabaseService.isAvailable) return { inSync: true };

  _isLifecycleSyncing = true;
  try {
    const pl = await supabaseService.getOrCreateDefaultCustomPlaylist('Mi Lista LELOUCH');
    if (!pl?.id) return { inSync: true };

    const localVer = getLocalM3UVersion();
    const syncCheck = await supabaseService.checkPlaylistSync(pl.id, localVer);

    if (syncCheck.inSync) {
      console.log(`📱 [Lifecycle Sync FASE 18 (${triggerSource})] ¿mi versión local (${localVer}) = servidor (${syncCheck.serverVersion})? SÍ -> No hago nada.`);
      if (triggerSource === 'user_click') {
        toast(`✅ Tu lista está al día (versión ${syncCheck.serverVersion}).`, 'info', 2500);
      }
      return syncCheck;
    } else {
      console.log(`📱 [Lifecycle Sync FASE 18 (${triggerSource})] ¿mi versión local (${localVer}) = servidor (${syncCheck.serverVersion})? NO -> Sincronizo manifest JSON.`);
      
      // Descarga nativa de Playlist Manifest JSON (FASE 17) sin re-parsear M3U
      const manifest = await supabaseService.getPlaylistManifest(pl.id);
      if (manifest?.items) {
        const localList = getCustomM3UList();
        // Guardrail MREA: Si el servidor devuelve 0 items pero existen canales locales, NO vaciar la lista
        if (manifest.items.length === 0 && localList.length > 0) {
          console.warn('[Lifecycle Sync] El servidor reporta 0 items pero existen canales locales. Preservando lista local y sincronizando hacia la nube.');
          scheduleCustomM3UCloudSync();
          return syncCheck;
        }

        const mapped = manifest.items.map(it => ({
          id: it.id,
          name: it.name || it.direct_name || 'Canal',
          category: it.group || it.direct_group || 'General',
          logo: it.logo || it.direct_logo || '',
          url: it.streamUrl || it.resolved_stream_url || it.direct_url,
          epgId: it.tvgId || it.tvg_id || '',
          addedAt: Date.now()
        }));

        if (mapped.length > 0) {
          saveCustomM3UList(mapped);
          setLocalM3UVersion(syncCheck.serverVersion);
          renderCustomM3UManager();

          if (uiState.activePage === 'live') {
            renderLiveTVView();
          }

          toast(`🔄 Lista sincronizada con la nube (v${syncCheck.serverVersion}, ${mapped.length} canales).`, 'success', 3000);
        }
      }
      return syncCheck;
    }
  } catch (e) {
    console.warn('[Lifecycle Sync] Error durante comprobación determinista:', e);
    return { inSync: true };
  } finally {
    _isLifecycleSyncing = false;
  }
}
window.checkAndSyncCustomM3UVersion = checkAndSyncCustomM3UVersion;

let _lastVisibilitySync = 0;
const MIN_VISIBILITY_SYNC_INTERVAL_MS = 6000;

export function setupLifecycleSyncListeners() {
  // FASE 18: Disparador 2 - Al volver del background
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      const now = Date.now();
      if (now - _lastVisibilitySync >= MIN_VISIBILITY_SYNC_INTERVAL_MS) {
        _lastVisibilitySync = now;
        console.log('📱 [Lifecycle Sync FASE 18] Volviendo de background (document visible)...');
        checkAndSyncCustomM3UVersion('resume_from_background');
      }
    }
  });

  window.addEventListener('focus', () => {
    const now = Date.now();
    if (now - _lastVisibilitySync >= MIN_VISIBILITY_SYNC_INTERVAL_MS) {
      _lastVisibilitySync = now;
      console.log('📱 [Lifecycle Sync FASE 18] Ventana enfocada (window focus)...');
      checkAndSyncCustomM3UVersion('window_focus');
    }
  });

  // FASE 18: Disparador 4 - Botón Actualizar
  $('btn-sync-refresh-live')?.addEventListener('click', async () => {
    const btn = $('btn-sync-refresh-live');
    const origHtml = btn ? btn.innerHTML : '';
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Actualizando...';
    }
    try {
      await checkAndSyncCustomM3UVersion('user_click');
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = origHtml;
      }
    }
  });
}
window.setupLifecycleSyncListeners = setupLifecycleSyncListeners;

export function saveCustomM3UList(list) {
  _customM3UMemoryCache = list;
  try {
    localStorage.setItem(CUSTOM_M3U_STORAGE_KEY, JSON.stringify(list));
  } catch (quotaErr) {
    console.warn('[App] LocalStorage quota alcanzada. Utilizando persistencia en IndexedDB:', quotaErr.name || quotaErr.message);
    try {
      localStorage.setItem(CUSTOM_M3U_STORAGE_KEY, JSON.stringify(list.slice(0, 500)));
    } catch {}
  }
  cacheService.set(CUSTOM_M3U_STORAGE_KEY, list, 365 * 24 * 3600 * 1000).catch(e => {
    console.warn('[App] Error guardando en IndexedDB:', e);
  });
  updateCustomM3UBadges();
  scheduleCustomM3UCloudSync();
}

export function addCustomM3UItem(item) {
  if (!item || !item.url) {
    toast('URL de streaming inválida', 'error');
    return;
  }
  const list = getCustomM3UList();

  // FASE 23: Deduplicación multi-factor (provider, tvg-id, stream URL normalizada, nombre, grupo)
  // No decide automáticamente que canales con el mismo nombre de distintas fuentes son idénticos.
  const normCandidateUrl = PlaylistDeduplicator.normalizeStreamUrl(item.url);
  const candidateSource = item.sourceId || item.providerId || null;

  // Comprobar si ya existe el MISMO stream exacto o mismo ID en la misma fuente
  const duplicateIndex = list.findIndex(existing => {
    const normExistingUrl = PlaylistDeduplicator.normalizeStreamUrl(existing.url);
    const existingSource = existing.sourceId || existing.providerId || null;

    // 1. Mismo stream técnico normalizado (mismo servidor y canal)
    if (normCandidateUrl && normExistingUrl && normCandidateUrl === normExistingUrl) {
      return true;
    }
    // 2. Misma fuente y mismo ID
    if (candidateSource && existingSource && candidateSource === existingSource) {
      if (item.id && existing.id && String(item.id) === String(existing.id)) return true;
    }
    return false;
  });

  if (duplicateIndex >= 0) {
    toast(`ℹ️ "${item.name}" ya está en tu lista personalizada`, 'info');
    return;
  }

  // Si tiene el mismo nombre pero proviene de OTRA fuente o URL distinta, se preserva y notifica
  const sameNameDiffSource = list.some(x => {
    return PlaylistDeduplicator.normalizeString(x.name) === PlaylistDeduplicator.normalizeString(item.name);
  });

  list.push({
    id: item.id || String(Date.now()),
    sourceId: candidateSource,
    name: item.name || 'Canal sin nombre',
    category: item.category || 'Personalizada',
    logo: item.logo || '',
    url: item.url,
    epgId: item.epgId || '',
    addedAt: Date.now()
  });
  saveCustomM3UList(list);

  if (sameNameDiffSource) {
    toast(`➕ "${item.name}" añadido (fuente alternativa conservada)`, 'success', 3000);
  } else {
    toast(`➕ "${item.name}" agregado a tu Lista M3U (${list.length} en total)`, 'success');
  }
  renderCustomM3UManager();
}
window.addCustomM3UItem = addCustomM3UItem;

export function removeCustomM3UItem(index) {
  const list = getCustomM3UList();
  if (index >= 0 && index < list.length) {
    const removed = list.splice(index, 1);
    saveCustomM3UList(list);
    toast(`🗑 "${removed[0]?.name}" eliminado de tu lista`, 'info');
    renderCustomM3UManager();
  }
}
window.removeCustomM3UItem = removeCustomM3UItem;

export function clearCustomM3UList() {
  const list = getCustomM3UList();
  if (list.length === 0) return;
  if (!confirm(`¿Estás seguro de vaciar los ${list.length} elementos de tu lista personalizada?`)) return;
  saveCustomM3UList([]);
  toast('🗑 Lista personalizada vaciada', 'info');
  renderCustomM3UManager();
}
window.clearCustomM3UList = clearCustomM3UList;

export function generateCustomM3UContent() {
  const list = getCustomM3UList();
  let m3u = '#EXTM3U name="Mi Lista Personalizada LELOUCH"\n\n';
  list.forEach(item => {
    const logoAttr = item.logo ? ` tvg-logo="${item.logo}"` : '';
    const idAttr = item.epgId ? ` tvg-id="${item.epgId}"` : '';
    const groupAttr = item.category ? ` group-title="${item.category}"` : ' group-title="Personalizada"';
    m3u += `#EXTINF:-1${idAttr} tvg-name="${item.name}"${logoAttr}${groupAttr},${item.name}\n${item.url}\n\n`;
  });
  return m3u;
}

export function copyCustomM3UAll() {
  const list = getCustomM3UList();
  if (list.length === 0) {
    toast('Tu lista personalizada está vacía. Añade canales primero.', 'warning');
    return null;
  }
  const content = generateCustomM3UContent();
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(content).then(() => {
      toast(`📋 ¡Lista M3U copiada! (${list.length} canales en formato #EXTM3U)`, 'success', 3500);
    }).catch(() => {
      prompt('Copia todo el contenido de tu lista M3U:', content);
    });
  } else {
    prompt('Copia todo el contenido de tu lista M3U:', content);
  }
  return content;
}
window.copyCustomM3UAll = copyCustomM3UAll;
window.generateCustomM3UContent = generateCustomM3UContent;

export function downloadCustomM3U() {
  const list = getCustomM3UList();
  if (list.length === 0) {
    toast('Tu lista personalizada está vacía. Añade canales primero.', 'warning');
    return null;
  }
  const content = generateCustomM3UContent();
  const blob = new Blob([content], { type: 'audio/x-mpegurl;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  const filename = `mi_lista_iptv_${new Date().toISOString().slice(0, 10)}.m3u`;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  toast(`⬇ Lista M3U descargada (${list.length} elementos)`, 'success');
  return { content, filename, blob };
}
export async function activateCustomM3UAsMainPlaylist() {
  const customList = getCustomM3UList();
  if (!customList || customList.length === 0) {
    toast('Tu lista personalizada está vacía. Añade canales primero.', 'warning');
    return;
  }

  // 1. Guardar como activa en localStorage
  localStorage.setItem('iptv_active_playlist_id', 'custom_lelouch_playlist');

  // 2. Clasificar items en Live, Películas y Series
  const liveItems = [];
  const movieItems = [];
  const seriesItems = [];

  customList.forEach((item, index) => {
    const url = item.url || '';
    const isMovie = url.includes('/movie/') || (item.category && item.category.toLowerCase().includes('película'));
    const isSeries = url.includes('/series/') || (item.category && item.category.toLowerCase().includes('serie'));

    const baseObj = {
      id: item.id || `custom_${index}`,
      streamId: parseInt(item.id, 10) || (90000 + index),
      seriesId: parseInt(item.id, 10) || (90000 + index),
      num: index + 1,
      name: item.name || 'Título',
      title: item.name || 'Título',
      streamIcon: item.logo || '',
      poster: item.logo || '',
      cover: item.logo || '',
      categoryId: item.category || 'Mi Lista LELOUCH',
      categoryName: item.category || 'Mi Lista LELOUCH',
      streamUrl: item.url,
      containerExtension: isMovie ? (url.split('.').pop()?.split('?')[0] || 'mp4') : 'mp4',
      epgChannelId: item.epgId || '',
      isFavorite: false
    };

    if (isMovie) {
      movieItems.push(baseObj);
    } else if (isSeries) {
      seriesItems.push(baseObj);
    } else {
      liveItems.push(baseObj);
    }
  });

  // 3. Generar categorías para cada sección
  const buildCategories = (items) => {
    const catMap = new Map();
    items.forEach(it => {
      const cName = it.categoryName || 'General';
      const cId = it.categoryId || cName;
      if (!catMap.has(cName)) catMap.set(cName, { categoryId: cId, categoryName: cName, id: cId, name: cName, itemCount: 0 });
      catMap.get(cName).itemCount++;
    });
    return [...catMap.values()];
  };

  const liveCats = buildCategories(liveItems);
  const vodCats = buildCategories(movieItems);
  const seriesCats = buildCategories(seriesItems);

  // 4. Configurar estado completo de IPTVService
  iptvService.state.live = liveItems;
  iptvService.state.movies = movieItems;
  iptvService.state.series = seriesItems;
  iptvService.state.categories = {
    live: liveCats,
    vod: vodCats,
    series: seriesCats
  };
  iptvService.state.sportsCount = liveItems.filter(c => {
    const n = (c.name || '').toLowerCase();
    const cat = (c.categoryName || '').toLowerCase();
    return n.includes('espn') || n.includes('fox sport') || n.includes('deport') || cat.includes('deport') || cat.includes('sport') || n.includes('tudn') || n.includes('dazn');
  }).length;
  iptvService.state.account = {
    username: 'LELOUCH (Mi Lista)',
    maxConnections: 1,
    expireDate: 'Permanente',
    status: 'Active'
  };
  iptvService.state.server = {
    serverUrl: window.location.origin || 'https://lelouch-web-player.vercel.app'
  };
  iptvService.state.connected = true;

  // 5. Reindexar búsqueda
  searchService.buildIndex({
    live: liveItems,
    movies: movieItems,
    series: seriesItems
  });

  // 6. Persistir en caché local
  const prefix = 'cat_custom_lelouch_';
  try {
    await Promise.all([
      cacheService.set(`${prefix}account`, { account: iptvService.state.account, server: iptvService.state.server, updatedAt: Date.now() }),
      cacheService.set(`${prefix}categories`, iptvService.state.categories),
      cacheService.set(`${prefix}live`, liveItems),
      cacheService.set(`${prefix}movies`, movieItems),
      cacheService.set(`${prefix}series`, seriesItems)
    ]);
  } catch (e) {
    console.warn('[CustomM3U] Error en caché local:', e);
  }

  // 7. Sincronizar en la tabla 'playlists' de Supabase (FASE 32)
  try {
    let tokenInfo = getStoredTokenInfo();
    const token = tokenInfo?.token || 'pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R';
    const publicUrl = `${window.location.origin || 'https://lelouch-web-player.vercel.app'}/api/playlist?token=${token}`;
    await supabaseService.savePlaylist({
      name: '⭐ Mi Lista Personalizada LELOUCH',
      url: publicUrl,
      serverBaseUrl: window.location.origin || 'https://lelouch-web-player.vercel.app',
      username: 'LELOUCH',
      password: '••••••••',
      isActive: true,
      channelsCount: customList.length,
      moviesCount: 0,
      seriesCount: 0
    });
  } catch (e) {
    console.warn('[CustomM3U] Error sincronizando a Supabase playlists:', e);
  }

  // 8. Desactivar las demás listas en playlistService
  try {
    const allLists = await playlistService.getAll();
    for (const pl of allLists) {
      if (pl.isActive) {
        pl.isActive = false;
        supabaseService.savePlaylist({ ...pl, isActive: false }).catch(() => {});
      }
    }
  } catch {}

  // 9. Actualizar toda la interfaz
  updateAllViews(iptvService.state);
  renderSettingsPlaylists();
  updateActivePlaylistUI();

  toast(`⚡ ¡Mi Lista Personalizada LELOUCH activada! (${customList.length} canales en uso)`, 'success', 3500);
}
window.activateCustomM3UAsMainPlaylist = activateCustomM3UAsMainPlaylist;

window.downloadCustomM3U = downloadCustomM3U;

export function playCustomStream(url, title) {
  if (!url) return;
  if (uiState.activePage === 'live') {
    playerService.play({
      id: String(Date.now()),
      title: title || 'Canal Personalizado',
      url: url,
      type: 'live'
    });
    const titleEl = $('integrated-channel-name');
    if (titleEl) titleEl.textContent = title || 'Canal Personalizado';
  } else {
    playerModal.open({
      title: title || 'Canal Personalizado',
      url: url,
      type: 'live'
    });
  }
  toast(`▶️ Reproduciendo: ${title}`, 'info');
}
window.playCustomStream = playCustomStream;

export function updateCustomM3UBadges() {
  const count = getCustomM3UList().length;
  const badge = $('custom-m3u-badge');
  if (badge) badge.textContent = `${count} ${count === 1 ? 'ítem' : 'ítems'}`;
}

export function renderCustomM3UManager() {
  const container = $('custom-m3u-list-container');
  if (!container) return;
  updateCustomM3UBadges();

  const list = getCustomM3UList();
  if (list.length === 0) {
    container.innerHTML = `
      <div style="text-align:center; padding:3rem 1.5rem; background:rgba(255,255,255,0.01); border:1px dashed var(--border-subtle); border-radius:12px;">
        <span style="font-size:3rem; display:block; margin-bottom:0.75rem;">📋</span>
        <h4 style="margin:0 0 0.5rem 0; color:var(--text-primary);">Aún no has añadido elementos a tu lista</h4>
        <p style="margin:0 auto 1.25rem; max-width:520px; font-size:0.85rem; color:var(--text-secondary);">
          Navega por la sección <b>TV en Vivo</b> o <b>Películas</b> y pulsa el botón <b>➕</b> para coleccionar tus canales favoritos, o bien pega tus enlaces directos arriba para construir tu lista M3U a tu gusto.
        </p>
      </div>
    `;
    return;
  }

  container.innerHTML = `
    <div style="overflow-x:auto;">
      <table class="custom-m3u-table" style="width:100%; border-collapse:collapse; font-size:0.85rem;">
        <thead>
          <tr style="border-bottom:1px solid var(--border-subtle); text-align:left; color:var(--text-secondary); font-size:0.75rem;">
            <th style="padding:10px 8px; width:40px;">#</th>
            <th style="padding:10px 8px;">Canal / Contenido</th>
            <th style="padding:10px 8px;">Categoría</th>
            <th style="padding:10px 8px;">Enlace Directo</th>
            <th style="padding:10px 8px; text-align:right;">Acciones</th>
          </tr>
        </thead>
        <tbody>
          ${list.map((item, idx) => `
            <tr style="border-bottom:1px solid rgba(255,255,255,0.04);">
              <td style="padding:10px 8px; color:var(--text-muted); font-size:0.75rem;">${idx + 1}</td>
              <td style="padding:10px 8px; font-weight:600; color:var(--text-primary);">
                <div style="display:flex; align-items:center; gap:8px;">
                  ${item.logo ? `<img src="${escHtml(item.logo)}" style="width:26px; height:26px; object-fit:contain; border-radius:4px;" onerror="this.style.display='none'">` : '📺'}
                  <span>${escHtml(item.name)}</span>
                </div>
              </td>
              <td style="padding:10px 8px;">
                <span class="badge-mini" style="font-size:0.72rem; background:rgba(255,255,255,0.06); color:var(--text-secondary); padding:2px 8px; border-radius:4px;">${escHtml(item.category)}</span>
              </td>
              <td style="padding:10px 8px; max-width:280px;">
                <div class="m3u-url-code" title="${escHtml(item.url)}" style="font-family:'JetBrains Mono',monospace; font-size:0.72rem; color:var(--accent-cyan); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; background:rgba(6,182,212,0.06); padding:4px 8px; border-radius:4px; border:1px solid rgba(6,182,212,0.15);">
                  ${escHtml(item.url)}
                </div>
              </td>
              <td style="padding:10px 8px; text-align:right;">
                <div style="display:inline-flex; gap:6px;">
                  <button class="btn btn-secondary btn-sm" onclick="window.copyStreamUrl('${escHtml(item.url)}', '${escHtml(item.name)}')" title="Copiar enlace directo">🔗 Copiar</button>
                  <button class="btn btn-secondary btn-sm" onclick="window.playCustomStream('${escHtml(item.url)}', '${escHtml(item.name)}')" title="Probar reproducción">▶️ Ver</button>
                  <button class="btn btn-secondary btn-sm" style="color:#ef4444; border-color:rgba(239,68,68,0.25);" onclick="window.removeCustomM3UItem(${idx})" title="Eliminar de mi lista">🗑</button>
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

const M3U_TOKEN_STORAGE_KEY = 'lelouch_m3u_token_info_v1';

function getStoredTokenInfo() {
  try {
    const raw = localStorage.getItem(M3U_TOKEN_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function saveStoredTokenInfo(info) {
  try {
    if (!info) {
      localStorage.removeItem(M3U_TOKEN_STORAGE_KEY);
    } else {
      localStorage.setItem(M3U_TOKEN_STORAGE_KEY, JSON.stringify(info));
    }
  } catch {}
}

function formatM3UDate(dateVal) {
  const d = dateVal ? new Date(dateVal) : new Date();
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();
  return `${day}/${month}/${year}`;
}

function updateTokenModalUI(tokenInfo, itemCount) {
  const input = $('custom-m3u-permalink-input');
  const statusIndicator = $('token-status-indicator');
  const statusDot = $('token-status-dot');
  const statusText = $('token-status-text');
  const itemsCountElem = $('token-items-count');
  const lastUpdatedElem = $('token-last-updated');
  const toggleBtn = $('btn-token-modal-toggle-status');
  const toggleBtnText = $('btn-toggle-status-text');

  const fullUrl = `${window.location.origin}/api/playlist?token=${tokenInfo.token}`;
  if (input) input.value = fullUrl;

  const totalItems = itemCount ?? getCustomM3UList().length;
  if (itemsCountElem) itemsCountElem.textContent = totalItems;
  if (lastUpdatedElem) lastUpdatedElem.textContent = formatM3UDate(tokenInfo.updatedAt || tokenInfo.createdAt);

  const isActive = tokenInfo.enabled !== false;
  if (statusIndicator) {
    statusIndicator.style.color = isActive ? '#10b981' : '#ef4444';
  }
  if (statusDot) {
    statusDot.style.color = isActive ? '#10b981' : '#ef4444';
  }
  if (statusText) {
    statusText.textContent = isActive ? 'Activo' : 'Desactivado';
  }

  if (toggleBtn) {
    if (isActive) {
      toggleBtn.style.color = '#ef4444';
      toggleBtn.style.borderColor = 'rgba(239,68,68,0.35)';
      toggleBtn.style.background = 'rgba(239,68,68,0.06)';
      if (toggleBtnText) toggleBtnText.textContent = '⛔ Desactivar enlace';
    } else {
      toggleBtn.style.color = '#10b981';
      toggleBtn.style.borderColor = 'rgba(16,185,129,0.35)';
      toggleBtn.style.background = 'rgba(16,185,129,0.06)';
      if (toggleBtnText) toggleBtnText.textContent = '✅ Activar enlace';
    }
  }
}

export async function generateAndShowPublicM3ULink(forceRegenerate = false) {
  const list = getCustomM3UList();
  if (list.length === 0) {
    toast('Tu lista personalizada está vacía. Añade canales primero.', 'warning');
    return;
  }

  const btn = $('btn-custom-m3u-generate-link');
  const originalHtml = btn ? btn.innerHTML : '';
  if (btn) {
    btn.disabled = true;
    btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Conectando...';
  }

  try {
    // 1. Obtener o inicializar la playlist principal en Supabase
    const playlist = await supabaseService.getOrCreateDefaultCustomPlaylist('Mi Lista LELOUCH');
    if (!playlist || !playlist.id) {
      toast('No se pudo sincronizar con la nube (Supabase no disponible).', 'error');
      return;
    }

    // 2. Sincronizar todos los items de la lista con Supabase
    await supabaseService.syncPlaylistItems(playlist.id, list);

    let tokenInfo = getStoredTokenInfo();

    // Si forzamos regeneración o no existe token previo válido:
    if (forceRegenerate || !tokenInfo || !tokenInfo.token || tokenInfo.playlistId !== playlist.id) {
      if (forceRegenerate && (tokenInfo?.playlistId || playlist.id)) {
        // INVALIDAR TODOS LOS TOKENS PREVIOS EN SUPABASE (FASE 14)
        await supabaseService.invalidateAllAccessTokens(tokenInfo?.playlistId || playlist.id);
      }

      const tokenObj = await supabaseService.createAccessToken(playlist.id, 'Enlace M3U LELOUCH');
      if (!tokenObj || !tokenObj.token) {
        toast('No se pudo generar el token seguro de acceso.', 'error');
        return;
      }

      tokenInfo = {
        token: tokenObj.token,
        tokenId: tokenObj.id,
        playlistId: playlist.id,
        enabled: true,
        createdAt: tokenObj.created_at || new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      saveStoredTokenInfo(tokenInfo);
    } else {
      // Si ya existía, sincronizamos fecha de última actualización
      tokenInfo.updatedAt = new Date().toISOString();
      saveStoredTokenInfo(tokenInfo);
    }

    // 3. Actualizar la vista del modal con las métricas y estado
    updateTokenModalUI(tokenInfo, list.length);

    const modal = $('custom-m3u-token-modal');
    if (modal) modal.classList.remove('hidden');

    if (forceRegenerate) {
      toast('🔄 ¡Enlace M3U regenerado! El token anterior quedó invalidado.', 'success', 3500);
    } else {
      toast('🌐 Enlace M3U listo y sincronizado.', 'success', 2500);
    }
  } catch (err) {
    console.error('Error generando enlace M3U público:', err);
    toast('Ocurrió un error al preparar el enlace M3U.', 'error');
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerHTML = originalHtml;
    }
  }
}
window.generateAndShowPublicM3ULink = generateAndShowPublicM3ULink;

export function setupCustomM3UManager() {
  updateCustomM3UBadges();

  $('btn-custom-m3u-copy-all')?.addEventListener('click', () => copyCustomM3UAll());
  $('btn-custom-m3u-download')?.addEventListener('click', () => downloadCustomM3U());
  $('btn-custom-m3u-generate-link')?.addEventListener('click', () => generateAndShowPublicM3ULink());
  $('btn-custom-m3u-clear')?.addEventListener('click', () => clearCustomM3UList());

  // Listeners de consumo rápido dentro del modal (FASE 30)
  $('btn-token-modal-quick-copy')?.addEventListener('click', () => copyCustomM3UAll());
  $('btn-token-modal-quick-download')?.addEventListener('click', () => downloadCustomM3U());

  // Listeners del modal de enlace permanente (FASE 14)
  $('btn-close-token-modal')?.addEventListener('click', () => {
    $('custom-m3u-token-modal')?.classList.add('hidden');
  });

  // Cerrar al hacer clic en el backdrop
  $('custom-m3u-token-modal')?.addEventListener('click', (e) => {
    if (e.target.id === 'custom-m3u-token-modal') {
      $('custom-m3u-token-modal')?.classList.add('hidden');
    }
  });

  // [📋 Copiar enlace]
  $('btn-token-modal-copy')?.addEventListener('click', () => {
    const input = $('custom-m3u-permalink-input');
    if (!input || !input.value) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(input.value).then(() => {
        toast('📋 ¡Enlace M3U copiado al portapapeles!', 'success');
      }).catch(() => {
        input.select();
        document.execCommand('copy');
        toast('📋 ¡Enlace M3U copiado al portapapeles!', 'success');
      });
    } else {
      input.select();
      document.execCommand('copy');
      toast('📋 ¡Enlace M3U copiado al portapapeles!', 'success');
    }
  });

  // Seleccionar automáticamente al hacer clic en el input de la URL
  $('custom-m3u-permalink-input')?.addEventListener('click', (e) => {
    e.target.select();
  });

  // [🔄 Regenerar enlace]: invalida el token previo y genera uno nuevo
  $('btn-token-modal-regenerate')?.addEventListener('click', async () => {
    const confirmed = confirm(
      '¿Regenerar enlace M3U?\n\n' +
      '⚠️ El enlace anterior quedará INVALIDADO de inmediato y dejará de funcionar en cualquier reproductor externo.\n\n' +
      '¿Deseas continuar?'
    );
    if (!confirmed) return;

    const regenBtn = $('btn-token-modal-regenerate');
    const originalText = regenBtn ? regenBtn.innerHTML : '';
    if (regenBtn) {
      regenBtn.disabled = true;
      regenBtn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Regenerando...';
    }

    try {
      await generateAndShowPublicM3ULink(true);
    } finally {
      if (regenBtn) {
        regenBtn.disabled = false;
        regenBtn.innerHTML = originalText;
      }
    }
  });

  // [⛔ Desactivar enlace] / [✅ Activar enlace]: alterna el estado del token
  $('btn-token-modal-toggle-status')?.addEventListener('click', async () => {
    let tokenInfo = getStoredTokenInfo();
    if (!tokenInfo || !tokenInfo.tokenId) {
      toast('No hay un token activo para modificar.', 'warning');
      return;
    }

    const nextState = tokenInfo.enabled === false ? true : false;
    const toggleBtn = $('btn-token-modal-toggle-status');
    const originalContent = toggleBtn ? toggleBtn.innerHTML : '';
    if (toggleBtn) {
      toggleBtn.disabled = true;
      toggleBtn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Guardando...';
    }

    try {
      const ok = await supabaseService.setAccessTokenEnabled(tokenInfo.tokenId, nextState);
      if (ok) {
        tokenInfo.enabled = nextState;
        tokenInfo.updatedAt = new Date().toISOString();
        saveStoredTokenInfo(tokenInfo);
        updateTokenModalUI(tokenInfo, getCustomM3UList().length);
        if (nextState) {
          toast('✅ Enlace M3U reactivado.', 'success');
        } else {
          toast('⛔ Enlace M3U desactivado.', 'warning');
        }
      } else {
        toast('No se pudo cambiar el estado del enlace.', 'error');
      }
    } catch (e) {
      console.error('Error alternando estado del token:', e);
      toast('Error al actualizar el estado del token.', 'error');
    } finally {
      if (toggleBtn) {
        toggleBtn.disabled = false;
        toggleBtn.innerHTML = originalContent;
      }
    }
  });

  $('btn-custom-m3u-play-live')?.addEventListener('click', () => {
    const list = getCustomM3UList();
    if (list.length === 0) {
      toast('Tu lista personalizada está vacía. Añade canales primero.', 'warning');
      return;
    }

    // Activar modo "Solo Mis Categorías"
    uiState.onlyMyCategories = true;
    try {
      localStorage.setItem('iptv_only_my_cats', 'true');
    } catch {}

    const userCats = getUserCreatedCategories();
    if (userCats.length > 0) {
      uiState.selectedLiveCategory = `__USER_CAT_${userCats[0].id}`;
    }

    // Cambiar a TV en Vivo
    switchPage('live');
    if (iptvService.state?.live) {
      setupLiveCategories(iptvService.state.categories.live, iptvService.state.live);
      renderLiveTVView();
    }
    toast(`📺 Viendo tu lista personalizada (${list.length} canales)`, 'success', 3000);
  });

  $('btn-custom-item-add')?.addEventListener('click', () => {
    const nameInput = $('custom-item-name');
    const catInput = $('custom-item-cat');
    const urlInput = $('custom-item-url');

    const name = nameInput?.value?.trim();
    const cat = catInput?.value?.trim() || 'Manual';
    const url = urlInput?.value?.trim();

    if (!url) {
      toast('Por favor introduce la URL directa de streaming.', 'error');
      urlInput?.focus();
      return;
    }

    addCustomM3UItem({
      id: String(Date.now()),
      name: name || 'Canal sin nombre',
      category: cat,
      url: url,
      logo: ''
    });

    if (nameInput) nameInput.value = '';
    if (catInput) catInput.value = '';
    if (urlInput) urlInput.value = '';
  });
}

// ════════════ IMPORTADOR MASIVO A MI LISTA LELOUCH ════════════
let _importModalState = {
  sourcePlaylistId: null,
  sourcePlaylistObj: null,
  contentType: 'movies',
  categories: [],
  selectedCatNames: new Set()
};

export async function openPlaylistImportModal(preferredSourceId = null) {
  const modal = $('playlist-import-modal');
  if (!modal) return;

  const lists = await playlistService.getAll();
  const providerLists = lists.filter(p => p.id !== 'custom_lelouch_playlist');
  if (providerLists.length === 0) {
    toast('No tienes listas proveedoras registradas para extraer contenido.', 'warning');
    return;
  }

  const select = $('import-modal-source-select');
  if (select) {
    select.innerHTML = providerLists.map(p => `
      <option value="${p.id}" ${p.id === preferredSourceId || (!preferredSourceId && p.isActive) ? 'selected' : ''}>
        ${escHtml(p.name)} (${escHtml(p.serverBaseUrl)})
      </option>
    `).join('');
  }

  modal.classList.remove('hidden');
  await refreshImportModalData();
}
window.openPlaylistImportModal = openPlaylistImportModal;

async function refreshImportModalData() {
  const select = $('import-modal-source-select');
  const sourceId = select?.value;
  const listContainer = $('import-categories-list');
  const subtitle = $('import-modal-subtitle');
  const statusPill = $('import-modal-status-pill');
  if (!listContainer) return;

  listContainer.innerHTML = `
    <div style="color:var(--text-muted); text-align:center; padding:2.5rem; font-size:0.9rem;">
      <span style="font-size:1.8rem; display:block; margin-bottom:8px;">⏳</span>
      Cargando catálogo y categorías del proveedor...
    </div>
  `;
  if (statusPill) statusPill.textContent = 'Consultando...';

  const lists = await playlistService.getAll();
  const provider = lists.find(p => p.id === sourceId) || lists[0];
  if (!provider) {
    listContainer.innerHTML = `<div style="color:#ef4444; text-align:center; padding:2rem;">No se encontró la cuenta proveedora.</div>`;
    return;
  }

  if (subtitle) {
    subtitle.textContent = `Extrayendo de: ${provider.name} (${provider.serverBaseUrl})`;
  }

  _importModalState.sourcePlaylistId = sourceId;
  _importModalState.sourcePlaylistObj = provider;
  _importModalState.selectedCatNames.clear();

  const type = _importModalState.contentType; // 'movies' | 'series' | 'live'
  let categories = [];

  try {
    // 1. Verificar si ya tenemos el catálogo completo en iptvService en memoria
    if (iptvService.state?.connected && iptvService.state.server?.serverUrl?.includes(provider.serverBaseUrl)) {
      let items = [];
      if (type === 'movies') items = iptvService.state.movies || [];
      else if (type === 'series') items = iptvService.state.series || [];
      else if (type === 'live') items = iptvService.state.live || [];

      if (items.length > 0) {
        const catMap = new Map();
        items.forEach(it => {
          const cName = it.categoryName || 'General';
          const cId = it.categoryId || cName;
          if (!catMap.has(cName)) catMap.set(cName, { id: cId, name: cName, count: 0, items: [] });
          const entry = catMap.get(cName);
          entry.count++;
          entry.items.push(it);
        });
        categories = [...catMap.values()].sort((a, b) => b.count - a.count);
      }
    }

    // 2. Si no estaba en memoria, consultar la caché de IndexedDB
    if (categories.length === 0) {
      const serverBase = (provider.serverBaseUrl || '').replace(/\/+$/, '');
      const prefix = `cat_${btoa(unescape(encodeURIComponent(serverBase))).slice(0, 12)}_`;

      const cachedItems = await cacheService.get(`${prefix}${type}`);
      if (cachedItems && cachedItems.length > 0) {
        const catMap = new Map();
        cachedItems.forEach(it => {
          const cName = it.categoryName || 'General';
          const cId = it.categoryId || cName;
          if (!catMap.has(cName)) catMap.set(cName, { id: cId, name: cName, count: 0, items: [] });
          const entry = catMap.get(cName);
          entry.count++;
          entry.items.push(it);
        });
        categories = [...catMap.values()].sort((a, b) => b.count - a.count);
      } else {
        const cachedCats = await cacheService.get(`${prefix}categories`);
        if (cachedCats) {
          const targetCats = type === 'movies' ? cachedCats.vod : (type === 'series' ? cachedCats.series : cachedCats.live);
          if (targetCats && targetCats.length > 0) {
            categories = targetCats.map(c => ({
              id: c.id,
              name: c.name,
              count: c.itemCount || 0,
              items: null
            }));
          }
        }
      }
    }

    // 3. Si aún no hay categorías, conectar directamente con XtreamAdapter del proveedor
    if (categories.length === 0 && provider.url) {
      const parsed = parseIPTVUrl(provider.url);
      const adapter = new XtreamAdapter(parsed);
      const allCats = await adapter.getCategories();
      const targetCats = type === 'movies' ? allCats.vod : (type === 'series' ? allCats.series : allCats.live);
      if (targetCats && targetCats.length > 0) {
        categories = targetCats.map(c => ({
          id: c.id,
          name: c.name,
          count: c.itemCount || 0,
          items: null
        }));
      }
    }
  } catch (err) {
    console.error('[ImportModal] Error al obtener categorías del proveedor:', err);
  }

  if (statusPill) statusPill.textContent = 'Listo';
  _importModalState.categories = categories;
  renderImportModalCategoryList();
}

function renderImportModalCategoryList() {
  const listContainer = $('import-categories-list');
  const searchInput = $('import-cat-search');
  if (!listContainer) return;

  const query = (searchInput?.value || '').trim().toLowerCase();
  const filtered = query
    ? _importModalState.categories.filter(c => c.name.toLowerCase().includes(query))
    : _importModalState.categories;

  if (filtered.length === 0) {
    listContainer.innerHTML = `
      <div class="empty-state" style="padding:2.5rem; text-align:center;">
        <div class="empty-icon" style="font-size:2rem;">🔍</div>
        <div class="empty-title" style="color:var(--text-secondary); font-size:1rem; margin-top:0.5rem;">
          ${query ? 'No hay categorías que coincidan con la búsqueda.' : 'No se encontraron categorías en esta sección para este proveedor.'}
        </div>
      </div>
    `;
    updateImportSelectedCountLabel();
    return;
  }

  const unitLabel = _importModalState.contentType === 'movies' ? 'películas' : (_importModalState.contentType === 'series' ? 'series' : 'canales');

  listContainer.innerHTML = '';
  filtered.forEach(c => {
    const isChecked = _importModalState.selectedCatNames.has(c.name);
    const row = document.createElement('div');
    row.className = `cat-checkbox-row`;
    row.style.marginBottom = '6px';
    if (isChecked) {
      row.style.borderColor = 'rgba(0,229,255,0.45)';
      row.style.background = 'rgba(0,229,255,0.08)';
    }

    row.innerHTML = `
      <div class="cat-row-left">
        <input type="checkbox" class="cat-row-check import-cat-check" ${isChecked ? 'checked' : ''} />
        <span class="cat-row-name" style="font-weight:600; color:${isChecked ? 'var(--accent-cyan)' : 'var(--text-primary)'};" title="${escHtml(c.name)}">${escHtml(c.name)}</span>
      </div>
      <div class="cat-row-badges" style="display:flex; align-items:center; gap:8px;">
        <span class="cat-count-badge">${c.count ? `${c.count} ${unitLabel}` : unitLabel}</span>
        <button type="button" class="btn btn-secondary btn-sm btn-extract-single-cat" style="font-size:0.75rem; padding:2px 8px; color:var(--accent-cyan);" title="Añadir únicamente esta categoría">
          ⚡ Añadir
        </button>
      </div>
    `;

    const chk = row.querySelector('.cat-row-check');
    const toggle = (e) => {
      const willBeChecked = (e.target === chk) ? chk.checked : !chk.checked;
      chk.checked = willBeChecked;
      if (willBeChecked) {
        _importModalState.selectedCatNames.add(c.name);
        row.style.borderColor = 'rgba(0,229,255,0.45)';
        row.style.background = 'rgba(0,229,255,0.08)';
        row.querySelector('.cat-row-name').style.color = 'var(--accent-cyan)';
      } else {
        _importModalState.selectedCatNames.delete(c.name);
        row.style.borderColor = 'rgba(255,255,255,0.06)';
        row.style.background = 'rgba(255,255,255,0.03)';
        row.querySelector('.cat-row-name').style.color = 'var(--text-primary)';
      }
      updateImportSelectedCountLabel();
    };

    row.addEventListener('click', toggle);
    chk.addEventListener('click', (e) => e.stopPropagation());
    chk.addEventListener('change', toggle);

    row.querySelector('.btn-extract-single-cat')?.addEventListener('click', async (e) => {
      e.stopPropagation();
      const btn = e.currentTarget;
      const origText = btn.innerHTML;
      btn.disabled = true;
      btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Añadiendo...';
      try {
        await executeCategoryImport([c.name]);
        btn.innerHTML = '✓ ¡Añadida!';
        btn.style.color = '#10b981';
      } catch (err) {
        console.error('Error importando categoría:', err);
        btn.innerHTML = origText;
      } finally {
        setTimeout(() => {
          if (btn) {
            btn.disabled = false;
            btn.innerHTML = origText;
            btn.style.color = 'var(--accent-cyan)';
          }
        }, 3000);
      }
    });

    listContainer.appendChild(row);
  });

  updateImportSelectedCountLabel();
}

function updateImportSelectedCountLabel() {
  const lbl = $('import-selected-count-label');
  if (!lbl) return;

  const count = _importModalState.selectedCatNames.size;
  let totalItems = 0;
  _importModalState.categories.forEach(c => {
    if (_importModalState.selectedCatNames.has(c.name)) {
      totalItems += c.count || 0;
    }
  });

  const unitLabel = _importModalState.contentType === 'movies' ? 'películas' : (_importModalState.contentType === 'series' ? 'series' : 'canales');
  const countStr = totalItems > 0 ? ` (~${totalItems} ${unitLabel})` : '';
  lbl.innerHTML = `<span><strong>${count}</strong> categorías seleccionadas${countStr}</span>`;
}

function showImportSuccessModal(addedCount, totalCount, type = 'movies') {
  document.getElementById('import-success-dialog')?.remove();

  const typeLabel = type === 'movies' ? 'películas' : (type === 'series' ? 'series' : 'canales');
  const modal = document.createElement('div');
  modal.id = 'import-success-dialog';
  modal.className = 'modal-overlay';
  modal.style.zIndex = '10000';
  modal.style.display = 'flex';
  modal.style.alignItems = 'center';
  modal.style.justifyContent = 'center';
  modal.innerHTML = `
    <div class="cat-modal-content" style="max-width: 470px; width: 92%; text-align: center; padding: 2.2rem 1.8rem; background: var(--bg-surface, #131722); border: 1.5px solid rgba(0, 229, 255, 0.45); border-radius: 16px; box-shadow: 0 20px 50px rgba(0, 0, 0, 0.85), 0 0 30px rgba(0, 229, 255, 0.25); animation: fadeIn 0.25s ease-out;">
      <div style="font-size: 3.8rem; margin-bottom: 0.8rem; line-height: 1;">🎉</div>
      <h3 style="font-size: 1.4rem; font-weight: 700; color: #fff; margin-bottom: 0.5rem; letter-spacing: 0.5px;">
        ¡Extracción Exitosa!
      </h3>
      <p style="color: rgba(255,255,255,0.8); font-size: 0.98rem; line-height: 1.5; margin-bottom: 1.5rem;">
        ${addedCount > 0 
          ? `Se han agregado <strong style="color: var(--accent-cyan, #00e5ff); font-size: 1.2rem;">+${addedCount.toLocaleString()}</strong> ${typeLabel} a <strong>Tu Lista Maestra LELOUCH</strong>.`
          : `Los títulos de las categorías seleccionadas ya estaban en tu lista (0 duplicados agregados).`
        }
        <br>
        <span style="display: inline-block; margin-top: 10px; font-size: 0.88rem; color: rgba(255,255,255,0.7); background: rgba(0,229,255,0.08); border: 1px solid rgba(0,229,255,0.25); padding: 5px 14px; border-radius: 20px;">
          Total acumulado en tu lista: <strong style="color:#fff;">${totalCount.toLocaleString()}</strong> elementos
        </span>
      </p>
      <div style="display: flex; gap: 12px; justify-content: center; flex-wrap: wrap;">
        <button id="btn-success-modal-activate" class="btn btn-primary" style="background: linear-gradient(135deg, #00e5ff, #0077ff); color: #000; font-weight: 700; padding: 0.75rem 1.4rem; border: none; border-radius: 8px; cursor: pointer; box-shadow: 0 4px 15px rgba(0,229,255,0.35);">
          📺 Ver Mi Lista en TV en Vivo
        </button>
        <button id="btn-success-modal-close" class="btn btn-secondary" style="background: rgba(255,255,255,0.1); color: #fff; padding: 0.75rem 1.4rem; border: 1px solid rgba(255,255,255,0.15); border-radius: 8px; cursor: pointer;">
          ✓ Listo, continuar
        </button>
      </div>
    </div>
  `;

  document.body.appendChild(modal);

  modal.querySelector('#btn-success-modal-close')?.addEventListener('click', () => {
    modal.remove();
  });

  modal.querySelector('#btn-success-modal-activate')?.addEventListener('click', async () => {
    modal.remove();
    await activateCustomM3UAsMainPlaylist();
    navigateTo('live');
  });
}

async function executeCategoryImport(catNames) {
  if (!catNames || catNames.length === 0) {
    toast('Selecciona al menos una categoría para importar.', 'warning');
    return;
  }

  const provider = _importModalState.sourcePlaylistObj;
  if (!provider) return;

  const type = _importModalState.contentType;
  const statusPill = $('import-modal-status-pill');
  if (statusPill) statusPill.textContent = 'Extrayendo...';

  const doImportBtn = $('btn-do-import-to-custom');
  const originalBtnHtml = doImportBtn ? doImportBtn.innerHTML : '';
  if (doImportBtn) {
    doImportBtn.disabled = true;
    doImportBtn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Extrayendo títulos... Por favor espera';
  }

  toast(`⏳ Extrayendo contenido de ${catNames.length} categorías...`, 'info', 2500);

  try {
    const selectedCats = _importModalState.categories.filter(c => catNames.includes(c.name));
    let itemsToImport = [];

    // 1. Si los items ya estaban en memoria:
    const hasItems = selectedCats.every(c => Array.isArray(c.items) && c.items.length > 0);
    if (hasItems) {
      selectedCats.forEach(c => itemsToImport.push(...c.items));
    } else {
      // 2. Si no estaban en memoria, buscar en caché de IndexedDB o descargar con XtreamAdapter
      const serverBase = (provider.serverBaseUrl || '').replace(/\/+$/, '');
      const prefix = `cat_${btoa(unescape(encodeURIComponent(serverBase))).slice(0, 12)}_`;
      const cachedItems = await cacheService.get(`${prefix}${type}`);

      if (cachedItems && cachedItems.length > 0) {
        itemsToImport = cachedItems.filter(it => catNames.includes(it.categoryName));
      }

      // Si aún no tenemos los items, descargarlos del servidor Xtream
      if (itemsToImport.length === 0 && provider.url) {
        const parsed = parseIPTVUrl(provider.url);
        const adapter = new XtreamAdapter(parsed);
        const selectedCatIds = new Set(selectedCats.map(c => String(c.id)));
        const catMapById = new Map(selectedCats.map(c => [String(c.id), c.name]));

        if (selectedCats.length > 5) {
          toast(`⚡ Descargando catálogo masivo (${selectedCats.length} categorías)...`, 'info', 3500);
          const action = type === 'movies' ? 'get_vod_streams' : (type === 'series' ? 'get_series' : 'get_live_streams');
          try {
            const allStreams = await adapter._fetchAction(action);
            if (Array.isArray(allStreams)) {
              allStreams.forEach(s => {
                const sCatId = String(s.category_id || '');
                if (selectedCatIds.has(sCatId)) {
                  const catName = catMapById.get(sCatId) || 'Importados';
                  const actualStreamId = s.stream_id || s.id || s.series_id || '';
                  const actualPass = parsed._password || parsed.password || (parsed.url ? (parsed.url.match(/[?&]password=([^&]+)/)?.[1] || '') : '');
                  itemsToImport.push({
                    id: actualStreamId,
                    streamId: actualStreamId,
                    name: s.name || s.title,
                    categoryName: catName,
                    logo: s.stream_icon || s.cover || '',
                    containerExtension: s.container_extension || 'mp4',
                    streamUrl: type === 'movies'
                      ? buildVodStreamUrl(parsed.serverBaseUrl, parsed.username, actualPass, actualStreamId, s.container_extension || 'mp4')
                      : (type === 'series'
                          ? `${parsed.serverBaseUrl}/series/${parsed.username}/${actualPass}/${actualStreamId}.mp4`
                          : buildLiveStreamUrl(parsed.serverBaseUrl, parsed.username, actualPass, actualStreamId, 'm3u8'))
                  });
                }
              });
            }
          } catch (e) {
            console.warn('[ImportModal] Error en descarga masiva:', e);
          }
        } else {
          for (const cat of selectedCats) {
            try {
              const action = type === 'movies' 
                ? `get_vod_streams&category_id=${cat.id}`
                : (type === 'series' ? `get_series&category_id=${cat.id}` : `get_live_streams&category_id=${cat.id}`);
              const rawStreams = await adapter._fetchAction(action);
              if (Array.isArray(rawStreams)) {
                rawStreams.forEach(s => {
                  const actualStreamId = s.stream_id || s.id || s.series_id || '';
                  const actualPass = parsed._password || parsed.password || (parsed.url ? (parsed.url.match(/[?&]password=([^&]+)/)?.[1] || '') : '');
                  itemsToImport.push({
                    id: actualStreamId,
                    streamId: actualStreamId,
                    name: s.name || s.title,
                    categoryName: cat.name,
                    logo: s.stream_icon || s.cover || '',
                    containerExtension: s.container_extension || 'mp4',
                    streamUrl: type === 'movies'
                      ? buildVodStreamUrl(parsed.serverBaseUrl, parsed.username, actualPass, actualStreamId, s.container_extension || 'mp4')
                      : (type === 'series'
                          ? `${parsed.serverBaseUrl}/series/${parsed.username}/${actualPass}/${actualStreamId}.mp4`
                          : buildLiveStreamUrl(parsed.serverBaseUrl, parsed.username, actualPass, actualStreamId, 'm3u8'))
                  });
                });
              }
            } catch (e) {
              console.warn(`[ImportModal] Error extrayendo categoría ${cat.name}:`, e);
            }
          }
        }
      }
    }

    if (itemsToImport.length === 0) {
      toast('No se encontraron elementos para importar en las categorías seleccionadas.', 'warning');
      if (statusPill) statusPill.textContent = 'Listo';
      return;
    }

    const currentList = getCustomM3UList();
    const existingUrls = new Set(currentList.map(x => PlaylistDeduplicator.normalizeStreamUrl(x.url)));
    let addedCount = 0;

    for (const it of itemsToImport) {
      const streamUrl = it.streamUrl;
      if (!streamUrl) continue;
      const normUrl = PlaylistDeduplicator.normalizeStreamUrl(streamUrl);
      if (!existingUrls.has(normUrl)) {
        existingUrls.add(normUrl);
        currentList.push({
          id: String(it.id || it.streamId || (Date.now() + Math.random())),
          sourceId: provider.id,
          name: it.name || it.title || 'Título',
          category: it.categoryName || 'Importados',
          logo: it.logo || it.poster || it.cover || it.streamIcon || '',
          url: streamUrl,
          epgId: it.epgChannelId || '',
          addedAt: Date.now()
        });
        addedCount++;
      }
    }

    saveCustomM3UList(currentList);
    scheduleCustomM3UCloudSync();

    $('playlist-import-modal')?.classList.add('hidden');
    renderSettingsPlaylists();
    renderCustomM3UManager();
    updateCustomM3UBadges();

    if (localStorage.getItem('iptv_active_playlist_id') === 'custom_lelouch_playlist') {
      await activateCustomM3UAsMainPlaylist();
    }

    // Notificación en Toast
    toast(`🎉 ¡Se extrajeron y añadieron ${addedCount} títulos a Tu Lista LELOUCH! (Total: ${currentList.length})`, 'success', 5000);

    // Modal visual inconfundible de confirmación
    showImportSuccessModal(addedCount, currentList.length, type);
  } finally {
    if (doImportBtn) {
      doImportBtn.disabled = false;
      doImportBtn.innerHTML = originalBtnHtml;
    }
    if (statusPill) statusPill.textContent = 'Listo';
  }
}

function setupPlaylistImportModal() {
  const modal = $('playlist-import-modal');
  const closeBtn = $('btn-close-import-modal');
  const cancelBtn = $('btn-cancel-import-modal');
  const doImportBtn = $('btn-do-import-to-custom');
  const selectAllBtn = $('btn-import-select-all');
  const deselectAllBtn = $('btn-import-deselect-all');
  const searchInput = $('import-cat-search');
  const sourceSelect = $('import-modal-source-select');

  closeBtn?.addEventListener('click', () => modal?.classList.add('hidden'));
  cancelBtn?.addEventListener('click', () => modal?.classList.add('hidden'));

  sourceSelect?.addEventListener('change', () => {
    refreshImportModalData();
  });

  document.querySelectorAll('.import-type-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.import-type-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      _importModalState.contentType = btn.dataset.type || 'movies';
      refreshImportModalData();
    });
  });

  searchInput?.addEventListener('input', () => {
    renderImportModalCategoryList();
  });

  selectAllBtn?.addEventListener('click', () => {
    const searchVal = (searchInput?.value || '').trim().toLowerCase();
    const targets = searchVal
      ? _importModalState.categories.filter(c => c.name.toLowerCase().includes(searchVal))
      : _importModalState.categories;
    targets.forEach(c => _importModalState.selectedCatNames.add(c.name));
    renderImportModalCategoryList();
  });

  deselectAllBtn?.addEventListener('click', () => {
    _importModalState.selectedCatNames.clear();
    renderImportModalCategoryList();
  });

  doImportBtn?.addEventListener('click', async () => {
    await executeCategoryImport([..._importModalState.selectedCatNames]);
  });
}

// ════════════ PAGINACIÓN REUTILIZABLE ════════════
function renderPagination(containerId, currentPage, totalPages, onPage) {
  const container = $(containerId);
  if (!container) return;
  container.innerHTML = '';
  if (totalPages <= 1) return;

  const max = 7;
  let pagesList = [];

  if (totalPages <= max) {
    for (let i = 1; i <= totalPages; i++) pagesList.push(i);
  } else {
    pagesList = [1];
    if (currentPage > 3) pagesList.push('…');
    for (let i = Math.max(2, currentPage - 1); i <= Math.min(totalPages - 1, currentPage + 1); i++) {
      pagesList.push(i);
    }
    if (currentPage < totalPages - 2) pagesList.push('…');
    pagesList.push(totalPages);
  }

  if (currentPage > 1) {
    const btn = document.createElement('button');
    btn.className = 'page-btn';
    btn.textContent = '←';
    btn.addEventListener('click', () => onPage(currentPage - 1));
    container.appendChild(btn);
  }

  pagesList.forEach((p) => {
    if (p === '…') {
      const sep = document.createElement('span');
      sep.style.color = 'var(--text-muted)';
      sep.textContent = '…';
      container.appendChild(sep);
      return;
    }
    const btn = document.createElement('button');
    btn.className = `page-btn ${p === currentPage ? 'active' : ''}`;
    btn.textContent = p;
    btn.addEventListener('click', () => onPage(p));
    container.appendChild(btn);
  });

  if (currentPage < totalPages) {
    const btn = document.createElement('button');
    btn.className = 'page-btn';
    btn.textContent = '→';
    btn.addEventListener('click', () => onPage(currentPage + 1));
    container.appendChild(btn);
  }
}

// ════════════ ATAJOS DE TECLADO GLOBALES ════════════
function setupGlobalShortcuts() {
  window.addEventListener('keydown', (e) => {
    // Si el foco está en un input, ignorar atajos
    if (['INPUT', 'SELECT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return;

    // Si el modal de reproducción está abierto, ignorar para que el modal gestione sus atajos
    const modal = $('player-modal');
    if (modal && !modal.classList.contains('hidden')) return;

    if (e.key === '/') {
      e.preventDefault();
      $('global-search-input')?.focus();
    } else if (e.key === '1') {
      navigateTo('home');
    } else if (e.key === '2') {
      navigateTo('live');
    } else if (e.key === '3') {
      navigateTo('movies');
    } else if (e.key === '4') {
      navigateTo('series');
    } else if (e.key === ' ' || e.key === 'k') {
      if (uiState.activePage === 'live') {
        e.preventDefault();
        playerService.togglePlay();
      }
    } else if (e.key === 'ArrowRight' || e.key === 'l' || e.key === 'L') {
      if (uiState.activePage === 'live') {
        e.preventDefault();
        playerService.forward(10);
      }
    } else if (e.key === 'ArrowLeft' || e.key === 'j' || e.key === 'J') {
      if (uiState.activePage === 'live') {
        e.preventDefault();
        playerService.rewind(10);
      }
    } else if (e.key === 'f' || e.key === 'F') {
      if (uiState.activePage === 'live') {
        e.preventDefault();
        const video = $('integrated-video');
        if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
        else if (video) video.requestFullscreen().catch(() => {});
      }
    } else if (e.key === 'm' || e.key === 'M') {
      if (uiState.activePage === 'live') {
        e.preventDefault();
        playerService.toggleMute();
      }
    }
  });
}

// Iniciar aplicación de forma segura (soporta carga diferida de módulo ES)
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}

// ════════════ LIVE STATS EN TIEMPO REAL ════════════
let _statsInterval = null;
let _statsVisible = false;
let _statsRafId = null;
let _lastBytesLoaded = 0;
let _lastBytesTime = 0;

function toggleLiveStats() {
  const overlay = $('live-stats-overlay');
  const btn = $('btn-integrated-stats');
  if (!overlay) return;
  _statsVisible = !_statsVisible;
  overlay.classList.toggle('hidden', !_statsVisible);
  if (btn) btn.classList.toggle('active', _statsVisible);
  if (_statsVisible) {
    startLiveStats();
    toast('📊 Estadísticas en tiempo real activadas', 'info', 1800);
  } else {
    stopLiveStats();
  }
}

function startLiveStats() {
  stopLiveStats();
  _statsRafId = requestAnimationFrame(updateLiveStats);
}

function stopLiveStats() {
  if (_statsRafId) { cancelAnimationFrame(_statsRafId); _statsRafId = null; }
  if (_statsInterval) { clearInterval(_statsInterval); _statsInterval = null; }
}

function updateLiveStats() {
  if (!_statsVisible) return;
  const video = $('integrated-video');
  if (!video) { _statsRafId = requestAnimationFrame(updateLiveStats); return; }

  // ── Buffer ──
  let bufferSec = 0;
  if (video.buffered.length > 0) {
    bufferSec = video.buffered.end(video.buffered.length - 1) - video.currentTime;
  }
  const bufEl = $('stat-buffer');
  if (bufEl) bufEl.textContent = isFinite(bufferSec) ? `${bufferSec.toFixed(1)}s` : '—';

  // ── Latencia de red aproximada (currentTime vs buffered end) ──
  let latency = 0;
  if (video.buffered.length > 0) {
    latency = video.buffered.end(video.buffered.length - 1) - video.currentTime;
  }
  const latEl = $('stat-latency');
  if (latEl) latEl.textContent = isFinite(latency) ? `${(latency * 1000).toFixed(0)} ms` : '—';

  // ── Frames caídos ──
  const dropEl = $('stat-dropped');
  if (dropEl) {
    const quality = video.getVideoPlaybackQuality?.();
    dropEl.textContent = quality ? quality.droppedVideoFrames : '0';
  }

  // ── Bitrate estimado desde Hls.js stats si disponible ──
  const bitrateEl = $('stat-bitrate');
  const qualEl = $('stat-quality');
  const codecEl = $('stat-codec');

  // Intentar obtener stats de Hls.js (si hay una instancia activa)
  const hls = playerService._hls;
  if (hls) {
    const level = hls.levels?.[hls.currentLevel];
    if (level) {
      if (bitrateEl) bitrateEl.textContent = `${Math.round((level.bitrate || 0) / 1000)}`;
      if (qualEl) qualEl.textContent = level.height ? `${level.height}p` : 'Auto';
    }
    const stats = hls.bandwidthEstimate;
    if (stats && bitrateEl) bitrateEl.textContent = `${Math.round(stats / 1000)}`;
    if (codecEl && level) codecEl.textContent = level.videoCodec || level.audioCodec || '—';
  } else if (playerService._mpegtsPlayer) {
    if (qualEl) qualEl.textContent = video.videoWidth ? `${video.videoWidth}×${video.videoHeight}` : 'Auto';
    try {
      const stats = playerService._mpegtsPlayer.statistics; // Getter in mpegts.js
      if (stats && stats.speed && bitrateEl) {
        bitrateEl.textContent = `${Math.round(stats.speed * 8)}`;
      } else if (bitrateEl) {
        bitrateEl.textContent = '—';
      }
    } catch (e) {
      if (bitrateEl) bitrateEl.textContent = '—';
    }
  } else if (video.videoWidth) {
    // HTML5 nativo: no hay acceso al bitrate, mostrar resolución
    if (qualEl) qualEl.textContent = video.videoWidth ? `${video.videoWidth}×${video.videoHeight}` : 'Auto';
    if (bitrateEl) bitrateEl.textContent = '—';
    if (codecEl) codecEl.textContent = '—';
  }

  _statsRafId = requestAnimationFrame(updateLiveStats);
}

// ════════════ PLAYLIST ACTIVA – INDICADOR EN HEADER Y HERO ════════════
async function updateActivePlaylistUI() {
  try {
    const isCustomActive = (localStorage.getItem('iptv_active_playlist_id') === 'custom_lelouch_playlist');
    let name;
    if (isCustomActive) {
      name = '⭐ Mi Lista Personalizada LELOUCH';
    } else {
      const lists = await playlistService.getAll();
      const active = lists.find(p => p.isActive) || lists[0];
      name = active?.name || 'Sin playlist';
    }

    const headerName = $('header-active-pl-name');
    if (headerName) headerName.textContent = name;

    const heroChip = $('hero-playlist-name');
    if (heroChip) heroChip.textContent = name;
  } catch (e) {
    console.warn('[App] No se pudo actualizar indicador de playlist activa:', e);
  }
}

// ════════════ CONTROL PARENTAL ════════════
function updateParentalUI() {
  const isEnabled = parentalControlService.isEnabled;
  const isUnlocked = parentalControlService.isUnlocked;

  // Header button
  const icon = $('parental-status-icon');
  const label = $('parental-status-label');
  if (icon) icon.textContent = (!isEnabled || isUnlocked) ? '🔓' : '🔒';
  if (label) label.textContent = (!isEnabled || isUnlocked) ? '+18 Visible' : '+18 Bloqueado';

  const btn = $('btn-header-parental');
  if (btn) {
    btn.classList.toggle('parental-unlocked', !isEnabled || isUnlocked);
    btn.classList.toggle('parental-locked', isEnabled && !isUnlocked);
    btn.title = (!isEnabled || isUnlocked) 
      ? 'Contenido para adultos (+18 / XXX) visible' 
      : 'Contenido para adultos (+18) bloqueado. Haz clic para desbloquear con PIN';
  }

  // Settings tab badges
  const badgeEnabled = $('parental-badge-enabled');
  const badgeDisabled = $('parental-badge-disabled');
  const badgeUnlocked = $('parental-badge-unlocked');
  const title = $('parental-settings-title');
  const sub = $('parental-settings-sub');
  const toggle = $('parental-enabled-toggle');

  if (toggle) toggle.checked = isEnabled;

  if (badgeEnabled) badgeEnabled.style.display = (isEnabled && !isUnlocked) ? 'inline-flex' : 'none';
  if (badgeDisabled) badgeDisabled.style.display = !isEnabled ? 'inline-flex' : 'none';
  if (badgeUnlocked) badgeUnlocked.style.display = (isEnabled && isUnlocked) ? 'inline-flex' : 'none';

  if (title) {
    if (!isEnabled) title.textContent = '⚪ Protección Desactivada';
    else if (isUnlocked) title.textContent = '🔓 Sesión Desbloqueada';
    else title.textContent = '🔒 Protección Activa';
  }
  if (sub) {
    if (!isEnabled) sub.textContent = 'El control parental está desactivado. Las categorías adultas son visibles para todos.';
    else if (isUnlocked) sub.textContent = 'Acceso desbloqueado para esta sesión. Al cerrar el navegador se vuelve a bloquear.';
    else sub.textContent = 'Las categorías adultas están ocultas. Ingresa el PIN para desbloquear.';
  }

  // Render detected adult categories list
  renderParentalDetectedCategories();

  // Refresh live / movie / series views with new filter state
  if (iptvService.state?.live?.length > 0) {
    setupLiveCategories(iptvService.state.categories.live, iptvService.state.live);
    renderLiveTVView();
    setupMoviesView(iptvService.state.movies, iptvService.state.categories.vod);
    setupSeriesView(iptvService.state.series, iptvService.state.categories.series);
  }
}

function renderParentalDetectedCategories() {
  const container = $('parental-detected-cats');
  if (!container) return;
  const allCats = [
    ...(iptvService.state?.live || []).map(c => c.categoryName),
    ...(iptvService.state?.movies || []).map(m => m.categoryName),
    ...(iptvService.state?.series || []).map(s => s.categoryName),
  ];
  const unique = [...new Set(allCats)];
  const adultCats = unique.filter(cat => parentalControlService.isAdult(cat));

  if (adultCats.length === 0) {
    container.innerHTML = '<div class="empty-state" style="padding:1rem"><div class="empty-sub">No se detectaron categorías adultas en el catálogo actual.</div></div>';
    return;
  }

  container.innerHTML = adultCats.map(cat => `
    <span class="parental-cat-tag">\uD83D\uDEAB ${escHtml(cat)}</span>
  `).join('');
}

function openParentalPinModal(onSuccess) {
  const modal = $('parental-pin-modal');
  if (!modal) return;
  const input = $('parental-pin-input');
  const errEl = $('parental-pin-error');
  if (input) { input.value = ''; }
  if (errEl) errEl.classList.add('hidden');
  modal.classList.remove('hidden');
  setTimeout(() => input?.focus(), 100);

  const confirm = () => {
    const pin = input?.value?.trim() || '';
    const ok = parentalControlService.unlock(pin);
    if (ok) {
      modal.classList.add('hidden');
      if (errEl) errEl.classList.add('hidden');
      updateParentalUI();
      toast('🔓 Categorías adultas desbloqueadas para esta sesión.', 'success');
      onSuccess?.();
    } else {
      if (errEl) errEl.classList.remove('hidden');
      if (input) { input.value = ''; input.focus(); }
    }
  };

  const cancel = () => { modal.classList.add('hidden'); };

  const confirmBtn = $('btn-parental-pin-confirm');
  const cancelBtn = $('btn-parental-pin-cancel');
  // Replace event listeners each time
  const newConfirm = confirmBtn?.cloneNode(true);
  const newCancel = cancelBtn?.cloneNode(true);
  if (newConfirm) { confirmBtn.replaceWith(newConfirm); newConfirm.addEventListener('click', confirm); }
  if (newCancel) { cancelBtn.replaceWith(newCancel); newCancel.addEventListener('click', cancel); }

  const newInput = $('parental-pin-input');
  if (newInput) {
    newInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') confirm();
      if (e.key === 'Escape') cancel();
    });
  }

  modal.addEventListener('click', (e) => {
    if (e.target === modal) cancel();
  }, { once: true });
}

function setupParentalControl() {
  // Por defecto, asegurar que los canales para adultos (+18 / XXX) estén visibles
  if (typeof localStorage !== 'undefined' && localStorage.getItem('iptv_parental_pin') === null) {
    localStorage.setItem('iptv_parental_enabled', 'false');
  }

  // Header button: unlock or lock toggle
  $('btn-header-parental')?.addEventListener('click', () => {
    if (!parentalControlService.isEnabled) {
      toast('🔞 Los canales para adultos están visibles. Si deseas bloquearlos con un PIN, ve a Ajustes > Control Parental.', 'info', 3500);
      return;
    }
    if (parentalControlService.isUnlocked) {
      parentalControlService.lock();
      updateParentalUI();
      toast('🔒 Categorías adultas bloqueadas nuevamente.', 'info');
    } else {
      openParentalPinModal(() => {});
    }
  });

  // Settings tab: unlock button
  $('btn-parental-toggle-unlock')?.addEventListener('click', () => {
    if (!parentalControlService.isEnabled) {
      toast('El control parental está desactivado.', 'info', 2000);
      return;
    }
    if (parentalControlService.isUnlocked) {
      toast('Ya está desbloqueado para esta sesión.', 'info', 2000);
      return;
    }
    openParentalPinModal(() => {});
  });

  // Settings tab: relock button
  $('btn-parental-relock')?.addEventListener('click', () => {
    parentalControlService.lock();
    updateParentalUI();
    toast('🔒 Categorías adultas bloqueadas.', 'info');
  });

  // Settings tab: change PIN
  $('btn-parental-change-pin')?.addEventListener('click', () => {
    const cur = $('parental-current-pin')?.value?.trim() || '';
    const nw = $('parental-new-pin')?.value?.trim() || '';
    const conf = $('parental-confirm-pin')?.value?.trim() || '';
    if (nw !== conf) { toast('Los nuevos PINes no coinciden.', 'error'); return; }
    const result = parentalControlService.changePin(cur, nw);
    if (result.ok) {
      toast('✅ PIN cambiado exitosamente.', 'success');
      $('parental-current-pin').value = '';
      $('parental-new-pin').value = '';
      $('parental-confirm-pin').value = '';
    } else {
      toast(`❌ ${result.error}`, 'error');
    }
  });

  // Settings tab: toggle enable/disable
  const toggle = $('parental-enabled-toggle');
  const disablePinInput = $('parental-disable-pin');

  toggle?.addEventListener('change', (e) => {
    const enabling = e.target.checked;
    if (enabling) {
      const result = parentalControlService.setEnabled(true, '');
      if (result.ok) { updateParentalUI(); toast('🛡️ Control parental activado.', 'success'); }
    } else {
      // Need PIN to disable
      if (disablePinInput) disablePinInput.style.display = 'block';
      toast('Ingresa el PIN para desactivar la protección.', 'info', 2500);
    }
  });

  disablePinInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const pin = disablePinInput.value.trim();
      const result = parentalControlService.setEnabled(false, pin);
      if (result.ok) {
        disablePinInput.style.display = 'none';
        disablePinInput.value = '';
        updateParentalUI();
        toast('⚠️ Control parental desactivado. Toda la programación está visible.', 'info', 4000);
      } else {
        toast(`❌ ${result.error}`, 'error');
        disablePinInput.value = '';
      }
    }
  });

  // Listen for parental state changes
  parentalControlService.onChange(() => updateParentalUI());

  // Initial UI update
  updateParentalUI();
}

// ════════════ GESTOR DE CATEGORÍAS VISIBLES (MODAL Y PREVIEW) ════════════
let _catManagerScope = 'live'; // 'live' | 'movies' | 'series'
let _catWorkingHidden = { live: [], movies: [], series: [] };

function updateSettingsCategoriesPreview() {
  const container = $('settings-categories-preview');
  if (!container) return;

  const hidden = getHiddenCategories();
  const liveTotal = new Set((iptvService.state?.live || []).map(c => c.categoryName)).size;
  const liveHidden = (hidden.live || []).length;
  const liveVisible = Math.max(0, liveTotal - liveHidden);

  const moviesTotal = new Set((iptvService.state?.movies || []).map(m => m.categoryName)).size;
  const moviesHidden = (hidden.movies || []).length;
  const moviesVisible = Math.max(0, moviesTotal - moviesHidden);

  const seriesTotal = new Set((iptvService.state?.series || []).map(s => s.categoryName)).size;
  const seriesHidden = (hidden.series || []).length;
  const seriesVisible = Math.max(0, seriesTotal - seriesHidden);

  container.innerHTML = `
    <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:1rem; margin-top:0.75rem;">
      <div style="background:rgba(255,255,255,0.03); border:1px solid rgba(255,255,255,0.08); border-radius:0.75rem; padding:1rem;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.4rem;">
          <strong style="color:#00e5ff; font-size:0.95rem;">📺 TV en Vivo</strong>
          <span class="badge-mini">${liveVisible}/${liveTotal}</span>
        </div>
        <div style="font-size:0.8rem; color:var(--text-muted);">${liveHidden > 0 ? `🚫 ${liveHidden} categorías ocultas` : '✓ Todas visibles'}</div>
        <button class="btn btn-secondary btn-sm" style="margin-top:0.75rem; width:100%" onclick="window.appOpenCatManager('live')">Editar TV</button>
      </div>

      <div style="background:rgba(255,255,255,0.03); border:1px solid rgba(255,255,255,0.08); border-radius:0.75rem; padding:1rem;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.4rem;">
          <strong style="color:#38bdf8; font-size:0.95rem;">🎬 Películas</strong>
          <span class="badge-mini">${moviesVisible}/${moviesTotal}</span>
        </div>
        <div style="font-size:0.8rem; color:var(--text-muted);">${moviesHidden > 0 ? `🚫 ${moviesHidden} categorías ocultas` : '✓ Todas visibles'}</div>
        <button class="btn btn-secondary btn-sm" style="margin-top:0.75rem; width:100%" onclick="window.appOpenCatManager('movies')">Editar Películas</button>
      </div>

      <div style="background:rgba(255,255,255,0.03); border:1px solid rgba(255,255,255,0.08); border-radius:0.75rem; padding:1rem;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.4rem;">
          <strong style="color:#818cf8; font-size:0.95rem;">🎭 Series</strong>
          <span class="badge-mini">${seriesVisible}/${seriesTotal}</span>
        </div>
        <div style="font-size:0.8rem; color:var(--text-muted);">${seriesHidden > 0 ? `🚫 ${seriesHidden} categorías ocultas` : '✓ Todas visibles'}</div>
        <button class="btn btn-secondary btn-sm" style="margin-top:0.75rem; width:100%" onclick="window.appOpenCatManager('series')">Editar Series</button>
      </div>
    </div>
  `;
}

function openCategoryManager(initialScope = 'live') {
  const modal = $('category-manager-modal');
  if (!modal) return;

  _catManagerScope = initialScope;
  const current = getHiddenCategories();
  _catWorkingHidden = {
    live: [...(current.live || [])],
    movies: [...(current.movies || [])],
    series: [...(current.series || [])]
  };

  const searchInput = $('cat-manager-search');
  if (searchInput) searchInput.value = '';

  // Actualizar tabs de scope
  document.querySelectorAll('.cat-scope-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.scope === _catManagerScope);
  });

  renderCategoryManagerList();
  modal.classList.remove('hidden');
}
window.appOpenCatManager = openCategoryManager;

function renderCategoryManagerList() {
  const listEl = $('cat-manager-list');
  const counterEl = $('cat-modal-counter');
  const searchInput = $('cat-manager-search');
  if (!listEl) return;

  let items = [];
  let unitLabel = 'elementos';

  if (_catManagerScope === 'live') {
    items = iptvService.state?.live || [];
    unitLabel = 'canales';
  } else if (_catManagerScope === 'movies') {
    items = iptvService.state?.movies || [];
    unitLabel = 'películas';
  } else if (_catManagerScope === 'series') {
    items = iptvService.state?.series || [];
    unitLabel = 'series';
  }

  // Agrupar items por categoría
  const counts = new Map();
  items.forEach((it) => {
    const cat = it.categoryName || 'Sin categoría';
    counts.set(cat, (counts.get(cat) || 0) + 1);
  });

  const allCats = [...counts.keys()].sort((a, b) => a.localeCompare(b, 'es', { sensitivity: 'base' }));
  const hiddenSet = new Set(_catWorkingHidden[_catManagerScope] || []);

  const searchTerm = (searchInput?.value || '').trim().toLowerCase();
  const filteredCats = searchTerm
    ? allCats.filter(c => c.toLowerCase().includes(searchTerm))
    : allCats;

  const total = allCats.length;
  const hiddenCount = allCats.filter(c => hiddenSet.has(c)).length;
  const visibleCount = total - hiddenCount;

  if (counterEl) {
    counterEl.innerHTML = `
      <span>Mostrando <strong>${visibleCount}</strong> de <strong>${total}</strong> categorías</span>
      <span style="color:${hiddenCount > 0 ? '#ef4444' : 'var(--text-muted)'}">${hiddenCount > 0 ? `(${hiddenCount} ocultas)` : '(todas visibles)'}</span>
    `;
  }

  if (filteredCats.length === 0) {
    listEl.innerHTML = `
      <div class="empty-state" style="padding:2rem">
        <div class="empty-icon">🔍</div>
        <div class="empty-title">Sin resultados</div>
        <div class="empty-sub">${searchTerm ? 'No hay categorías que coincidan con la búsqueda.' : 'No hay categorías cargadas en esta sección.'}</div>
      </div>
    `;
    return;
  }

  listEl.innerHTML = '';
  filteredCats.forEach((cat) => {
    const isAdult = parentalControlService.isAdult(cat);
    const isHidden = hiddenSet.has(cat);
    const count = counts.get(cat) || 0;

    const row = document.createElement('div');
    row.className = `cat-checkbox-row ${isHidden ? 'is-hidden' : ''}`;
    row.innerHTML = `
      <div class="cat-row-left">
        <input type="checkbox" class="cat-row-check" ${!isHidden ? 'checked' : ''} />
        <span class="cat-row-name" title="${escHtml(cat)}">${escHtml(cat)}</span>
      </div>
      <div class="cat-row-badges">
        ${isAdult ? '<span class="cat-adult-tag">🔞 +18</span>' : ''}
        <span class="cat-count-badge">${count} ${unitLabel}</span>
      </div>
    `;

    const toggleRow = (e) => {
      const chk = row.querySelector('.cat-row-check');
      const shouldBeVisible = (e.target === chk) ? chk.checked : !chk.checked;
      chk.checked = shouldBeVisible;

      if (shouldBeVisible) {
        hiddenSet.delete(cat);
        row.classList.remove('is-hidden');
      } else {
        hiddenSet.add(cat);
        row.classList.add('is-hidden');
      }

      _catWorkingHidden[_catManagerScope] = [...hiddenSet];

      // Actualizar contador
      const currentHidden = allCats.filter(c => hiddenSet.has(c)).length;
      const currentVisible = total - currentHidden;
      if (counterEl) {
        counterEl.innerHTML = `
          <span>Mostrando <strong>${currentVisible}</strong> de <strong>${total}</strong> categorías</span>
          <span style="color:${currentHidden > 0 ? '#ef4444' : 'var(--text-muted)'}">${currentHidden > 0 ? `(${currentHidden} ocultas)` : '(todas visibles)'}</span>
        `;
      }
    };

    row.addEventListener('click', toggleRow);
    const chk = row.querySelector('.cat-row-check');
    chk.addEventListener('click', (e) => e.stopPropagation());
    chk.addEventListener('change', toggleRow);

    listEl.appendChild(row);
  });
}

function setupCategoryManager() {
  const modal = $('category-manager-modal');
  const closeBtn = $('btn-cat-modal-close');
  const cancelBtn = $('btn-cat-manager-cancel');
  const saveBtn = $('btn-cat-manager-save');
  const searchInput = $('cat-manager-search');
  const showAllBtn = $('btn-cat-show-all');
  const hideAdultBtn = $('btn-cat-hide-adult');
  const hideAllBtn = $('btn-cat-hide-all');
  const openLiveBtn = $('btn-open-cat-manager');
  const openSettingsBtn = $('btn-open-cat-manager-settings');

  // Botón abrir desde TV en Vivo
  openLiveBtn?.addEventListener('click', () => openCategoryManager('live'));

  // Botón abrir desde Ajustes
  openSettingsBtn?.addEventListener('click', () => openCategoryManager('live'));

  // Botón extraer desde Gestión de Categorías
  $('btn-open-import-from-cat-manager')?.addEventListener('click', () => openPlaylistImportModal());

  // Tabs de scope (TV, Películas, Series)
  document.querySelectorAll('.cat-scope-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.cat-scope-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      _catManagerScope = btn.dataset.scope || 'live';
      if (searchInput) searchInput.value = '';
      renderCategoryManagerList();
    });
  });

  // Búsqueda en tiempo real
  searchInput?.addEventListener('input', () => {
    renderCategoryManagerList();
  });

  // Botón Mostrar Todas
  showAllBtn?.addEventListener('click', () => {
    _catWorkingHidden[_catManagerScope] = [];
    renderCategoryManagerList();
    toast('✓ Todas las categorías marcadas como visibles', 'info', 2000);
  });

  // Botón Ocultar +18 / XXX
  hideAdultBtn?.addEventListener('click', () => {
    let items = [];
    if (_catManagerScope === 'live') items = iptvService.state?.live || [];
    else if (_catManagerScope === 'movies') items = iptvService.state?.movies || [];
    else if (_catManagerScope === 'series') items = iptvService.state?.series || [];

    const adultCats = [...new Set(items.map(it => it.categoryName))]
      .filter(cat => parentalControlService.isAdult(cat));

    const hiddenSet = new Set(_catWorkingHidden[_catManagerScope] || []);
    adultCats.forEach(c => hiddenSet.add(c));
    _catWorkingHidden[_catManagerScope] = [...hiddenSet];

    renderCategoryManagerList();
    toast(`🔞 ${adultCats.length} categorías para adultos desmarcadas`, 'info', 2500);
  });

  // Botón Ocultar Todas
  hideAllBtn?.addEventListener('click', () => {
    let items = [];
    if (_catManagerScope === 'live') items = iptvService.state?.live || [];
    else if (_catManagerScope === 'movies') items = iptvService.state?.movies || [];
    else if (_catManagerScope === 'series') items = iptvService.state?.series || [];

    const allCats = [...new Set(items.map(it => it.categoryName))];
    _catWorkingHidden[_catManagerScope] = [...allCats];
    renderCategoryManagerList();
    toast('✖ Todas las categorías desmarcadas para ocultar', 'info', 2000);
  });

  // Botón Guardar y Aplicar
  saveBtn?.addEventListener('click', () => {
    saveHiddenCategories(_catWorkingHidden);
    modal?.classList.add('hidden');

    // Refrescar vistas
    if (iptvService.state?.live?.length > 0) {
      setupLiveCategories(iptvService.state.categories.live, iptvService.state.live);
      renderLiveTVView();
      setupMoviesView(iptvService.state.movies, iptvService.state.categories.vod);
      setupSeriesView(iptvService.state.series, iptvService.state.categories.series);
    }
    updateSettingsCategoriesPreview();
    toast('✓ Preferencias de categorías guardadas y aplicadas', 'success');
  });

  // Botones Cerrar / Cancelar
  const closeModal = () => modal?.classList.add('hidden');
  closeBtn?.addEventListener('click', closeModal);
  cancelBtn?.addEventListener('click', closeModal);

  modal?.addEventListener('click', (e) => {
    if (e.target === modal) closeModal();
  });

  // ── SECCIÓN CATEGORÍAS CREADAS POR EL USUARIO (MODAL) ──
  const btnOpenCreateDialog = $('btn-open-create-cat-dialog');
  btnOpenCreateDialog?.addEventListener('click', () => {
    openCustomCatCreatorDialog();
  });

  const toggleOnlyMyListBtn = $('btn-toggle-only-my-list');
  const updateToggleBtnUI = () => {
    if (!toggleOnlyMyListBtn) return;
    if (uiState.onlyMyCategories) {
      toggleOnlyMyListBtn.style.background = 'var(--accent-cyan)';
      toggleOnlyMyListBtn.style.color = '#02070D';
      toggleOnlyMyListBtn.style.fontWeight = 'bold';
      toggleOnlyMyListBtn.innerHTML = '⭐ Ver Solo Mis Categorías (ACTIVO)';
    } else {
      toggleOnlyMyListBtn.style.background = '';
      toggleOnlyMyListBtn.style.color = '';
      toggleOnlyMyListBtn.style.fontWeight = '';
      toggleOnlyMyListBtn.innerHTML = '⭐ Ver Solo Mis Categorías';
    }
  };
  updateToggleBtnUI();

  toggleOnlyMyListBtn?.addEventListener('click', () => {
    uiState.onlyMyCategories = !uiState.onlyMyCategories;
    try {
      localStorage.setItem('iptv_only_my_cats', String(uiState.onlyMyCategories));
    } catch {}
    updateToggleBtnUI();
    if (iptvService.state?.live) {
      setupLiveCategories(iptvService.state.categories.live, iptvService.state.live);
      renderLiveTVView();
    }
    toast(
      uiState.onlyMyCategories
        ? '⭐ Modo "Solo Mis Categorías" activado (categorías del servidor ocultas)'
        : '📺 Mostrando todas las categorías de nuevo',
      'info',
      2500
    );
  });

  renderUserCategoriesChips();
}

// ════════════ MODALES DE CATEGORÍAS CREADAS POR EL USUARIO ════════════
export function renderUserCategoriesChips() {
  const chipsContainer = $('user-created-cats-chips');
  const countBadge = $('user-cat-count-badge');
  if (!chipsContainer) return;

  const cats = getUserCreatedCategories();
  if (countBadge) countBadge.textContent = `${cats.length} creada${cats.length === 1 ? '' : 's'}`;

  chipsContainer.innerHTML = '';
  if (cats.length === 0) {
    chipsContainer.innerHTML = `<span style="font-size:0.8rem; color:var(--text-muted); font-style:italic;">No has creado categorías todavía. Haz clic en "+ Crear Nueva Categoría" arriba para armar tu lista.</span>`;
    return;
  }

  cats.forEach(cat => {
    const chip = document.createElement('div');
    chip.className = 'user-cat-chip';
    const chList = getUserCategoryChannels(cat);
    const count = chList.length || (cat.channelIds || []).length;
    chip.innerHTML = `
      <span>⭐ <strong>${escHtml(cat.name)}</strong> (${count})</span>
      <button class="chip-btn-edit" title="Editar categoría y canales">✏️</button>
      <button class="chip-btn-del" title="Eliminar categoría">🗑️</button>
    `;

    chip.querySelector('.chip-btn-edit')?.addEventListener('click', (e) => {
      e.stopPropagation();
      openCustomCatCreatorDialog(cat.id);
    });

    chip.querySelector('.chip-btn-del')?.addEventListener('click', (e) => {
      e.stopPropagation();
      if (confirm(`¿Eliminar tu categoría "${cat.name}"?`)) {
        deleteUserCategory(cat.id);
        renderUserCategoriesChips();
        if (iptvService.state?.live) {
          if (uiState.selectedLiveCategory === `__USER_CAT_${cat.id}`) {
            uiState.selectedLiveCategory = '';
          }
          setupLiveCategories(iptvService.state.categories.live, iptvService.state.live);
          renderLiveTVView();
        }
        toast(`Categoría "${cat.name}" eliminada`, 'info');
      }
    });

    chipsContainer.appendChild(chip);
  });
}

export function openCustomCatCreatorDialog(catIdToEdit = null, preselectedChannelId = null) {
  const dialog = $('custom-cat-creator-dialog');
  if (!dialog) return;

  const nameInput = $('custom-cat-name-input');
  const editIdInput = $('custom-cat-editing-id');
  const searchInput = $('custom-cat-channel-search');
  const counterEl = $('custom-cat-selected-counter');
  const pickerList = $('custom-cat-channels-picker-list');
  const saveBtn = $('btn-save-custom-cat');
  const cancelBtn = $('btn-cancel-custom-cat');
  const closeBtn = $('btn-close-cat-creator');

  let selectedChannelIds = new Set();

  if (catIdToEdit) {
    const cats = getUserCreatedCategories();
    const existing = cats.find(c => c.id === catIdToEdit);
    if (existing) {
      if (nameInput) nameInput.value = existing.name;
      if (editIdInput) editIdInput.value = existing.id;
      selectedChannelIds = new Set((existing.channelIds || []).map(String));
    }
  } else {
    if (nameInput) nameInput.value = '';
    if (editIdInput) editIdInput.value = '';
    if (preselectedChannelId) {
      selectedChannelIds.add(String(preselectedChannelId));
    }
  }

  const updateCounter = () => {
    if (counterEl) {
      counterEl.textContent = `${selectedChannelIds.size} canal${selectedChannelIds.size === 1 ? '' : 'es'} seleccionado${selectedChannelIds.size === 1 ? '' : 's'}`;
    }
  };
  updateCounter();

  const allChannels = iptvService.state?.live || [];

  const renderPickerChannels = (query = '') => {
    if (!pickerList) return;
    pickerList.innerHTML = '';

    const lowerQuery = query.toLowerCase().trim();
    let filtered = allChannels;
    if (lowerQuery) {
      filtered = allChannels.filter(c =>
        (c.name || '').toLowerCase().includes(lowerQuery) ||
        (c.categoryName || '').toLowerCase().includes(lowerQuery)
      );
    }

    if (filtered.length === 0) {
      pickerList.innerHTML = `<div style="text-align:center; padding:1.5rem; color:var(--text-muted); font-size:0.85rem;">No se encontraron canales con "${escHtml(query)}"</div>`;
      return;
    }

    // Renderizar primeros 120 canales para fluidez
    const slice = filtered.slice(0, 120);
    slice.forEach(ch => {
      const chId = String(ch.id);
      const isChecked = selectedChannelIds.has(chId);
      const row = document.createElement('label');
      row.className = 'custom-cat-picker-item';
      row.innerHTML = `
        <input type="checkbox" value="${escHtml(chId)}" ${isChecked ? 'checked' : ''} />
        ${ch.logo ? `<img src="${escHtml(ch.logo)}" style="width:24px; height:24px; object-fit:contain; border-radius:3px;" onerror="this.style.display='none'">` : ''}
        <span style="flex:1; font-size:0.85rem; color:var(--text-main); font-weight:500;">${escHtml(ch.name)}</span>
        <span style="font-size:0.75rem; color:var(--text-muted);">${escHtml(ch.categoryName)}</span>
      `;

      const chk = row.querySelector('input');
      chk.addEventListener('change', () => {
        if (chk.checked) selectedChannelIds.add(chId);
        else selectedChannelIds.delete(chId);
        updateCounter();
      });

      pickerList.appendChild(row);
    });
  };

  renderPickerChannels(searchInput?.value || '');

  if (searchInput) {
    searchInput.oninput = debounce((e) => {
      renderPickerChannels(e.target.value);
    }, 150);
  }

  const closeDialog = () => {
    dialog.classList.add('hidden');
    if (nameInput) nameInput.value = '';
    if (editIdInput) editIdInput.value = '';
  };

  if (closeBtn) closeBtn.onclick = closeDialog;
  if (cancelBtn) cancelBtn.onclick = closeDialog;

  if (saveBtn) {
    saveBtn.onclick = () => {
      const name = (nameInput?.value || '').trim();
      if (!name) {
        toast('Por favor escribe un nombre para tu categoría', 'warning');
        nameInput?.focus();
        return;
      }

      const editId = editIdInput?.value;
      let finalCatId = editId;

      if (editId) {
        updateUserCategory(editId, name, [...selectedChannelIds]);
        toast(`✓ Categoría "${name}" actualizada`, 'success');
      } else {
        const created = createUserCategory(name, [...selectedChannelIds]);
        finalCatId = created.id;
        toast(`⭐ Categoría "${name}" creada exitosamente`, 'success');
      }

      // Añadir también a la lista custom M3U automáticamente
      const idSet = selectedChannelIds;
      allChannels.filter(c => idSet.has(String(c.id))).forEach(ch => {
        addCustomM3UItem({
          id: ch.id || String(Date.now()),
          name: ch.name,
          category: name,
          logo: ch.logo || '',
          url: ch.streamUrl,
          epgId: ch.epgChannelId || ''
        });
      });

      closeDialog();
      renderUserCategoriesChips();

      // Refrescar paneles y seleccionar automáticamente la nueva categoría
      if (iptvService.state?.live) {
        uiState.selectedLiveCategory = `__USER_CAT_${finalCatId}`;
        setupLiveCategories(iptvService.state.categories.live, iptvService.state.live);
        renderLiveTVView();
      }
    };
  }

  dialog.classList.remove('hidden');
  nameInput?.focus();
}

export function openQuickAddToCatModal(channel) {
  const modal = $('quick-add-to-cat-modal');
  if (!modal) return;
  const titleEl = $('quick-add-channel-title');
  if (titleEl) titleEl.textContent = channel.name || 'Canal';

  const listEl = $('quick-add-cat-options-list');
  if (listEl) {
    listEl.innerHTML = '';
    const userCats = getUserCreatedCategories();

    if (userCats.length === 0) {
      listEl.innerHTML = `<div style="font-size:0.8rem; color:var(--text-muted); padding:0.5rem 0;">Aún no tienes categorías creadas. Crea una abajo:</div>`;
    } else {
      userCats.forEach(cat => {
        const hasChannel = (cat.channelIds || []).map(String).includes(String(channel.id));
        const item = document.createElement('div');
        item.style.cssText = 'display:flex; justify-content:space-between; align-items:center; padding:8px 12px; background:rgba(255,255,255,0.04); border-radius:8px; border:1px solid var(--border-subtle);';
        item.innerHTML = `
          <span style="font-size:0.85rem; font-weight:600; color:var(--text-main);">⭐ ${escHtml(cat.name)}</span>
          <button class="btn btn-sm ${hasChannel ? 'btn-secondary' : 'btn-primary'}" style="font-size:0.75rem; padding:3px 8px;">
            ${hasChannel ? '✓ Ya incluido' : '+ Agregar aquí'}
          </button>
        `;
        const btn = item.querySelector('button');
        if (!hasChannel) {
          btn.addEventListener('click', () => {
            addChannelToUserCategory(cat.id, channel.id);
            addCustomM3UItem({
              id: channel.id || String(Date.now()),
              name: channel.name,
              category: cat.name,
              logo: channel.logo || '',
              url: channel.streamUrl,
              epgId: channel.epgChannelId || ''
            });
            toast(`✓ "${channel.name}" añadido a "${cat.name}"`, 'success');
            modal.classList.add('hidden');
            if (iptvService.state?.live) {
              setupLiveCategories(iptvService.state.categories.live, iptvService.state.live);
              renderLiveChannelsList();
            }
          });
        }
        listEl.appendChild(item);
      });
    }
  }

  // Botón crear nueva categoría y añadir
  const btnNewCat = $('btn-quick-add-new-cat');
  if (btnNewCat) {
    btnNewCat.onclick = () => {
      modal.classList.add('hidden');
      openCustomCatCreatorDialog(null, channel.id);
    };
  }

  const btnClose = $('btn-close-quick-add');
  if (btnClose) btnClose.onclick = () => modal.classList.add('hidden');

  modal.classList.remove('hidden');
}

