/**
 * Controlador de Teclas y Control Remoto para Smart TVs VIDAA (Toshiba / Hisense)
 * Soporta navegación D-Pad, Teclas Numéricas, Botones de Color y Control Multimedia.
 */
(function() {
    'use strict';

    // Constantes de teclas W3C TV / VIDAA
    const TV_KEYS = {
        // Direccionales D-Pad
        LEFT: 37,
        UP: 38,
        RIGHT: 39,
        DOWN: 40,
        ENTER: 13,
        
        // Tecla Atrás / Retorno
        BACK: 10009,   // Tizen / VIDAA
        BACK_ALT: 461, // WebOS / VIDAA
        ESCAPE: 27,
        BACKSPACE: 8,

        // Controles multimedia
        PLAY: 415,
        PAUSE: 19,
        STOP: 413,
        FAST_FWD: 417,
        REWIND: 412,
        PLAY_PAUSE: 10252,

        // Teclas de color
        RED: 403,
        GREEN: 404,
        YELLOW: 405,
        BLUE: 406
    };

    window.addEventListener('keydown', function(event) {
        const code = event.keyCode || event.which;

        // 1. Manejo inteligente de la tecla ATRÁS en la Smart TV
        if (code === TV_KEYS.BACK || code === TV_KEYS.BACK_ALT || code === TV_KEYS.ESCAPE) {
            // Si el reproductor de video está a pantalla completa, salir del reproductor
            const playerModal = document.getElementById('player-modal');
            if (playerModal && playerModal.classList.contains('active')) {
                event.preventDefault();
                event.stopPropagation();
                if (window.PlayerModal && typeof window.PlayerModal.close === 'function') {
                    window.PlayerModal.close();
                } else {
                    playerModal.classList.remove('active');
                }
                return false;
            }

            // Si hay un modal de detalle abierto, cerrarlo
            const detailModal = document.getElementById('media-detail-modal');
            if (detailModal && detailModal.classList.contains('active')) {
                event.preventDefault();
                event.stopPropagation();
                detailModal.classList.remove('active');
                return false;
            }
        }

        // 2. Teclas de Acceso Rápido por Color (Control Remoto TV)
        switch(code) {
            case TV_KEYS.RED: // 🔴 En Vivo
                event.preventDefault();
                const liveTab = document.querySelector('[data-tab="live"]');
                if (liveTab) liveTab.click();
                break;
            case TV_KEYS.GREEN: // 🟢 Películas
                event.preventDefault();
                const moviesTab = document.querySelector('[data-tab="movies"]');
                if (moviesTab) moviesTab.click();
                break;
            case TV_KEYS.YELLOW: // 🟡 Series
                event.preventDefault();
                const seriesTab = document.querySelector('[data-tab="series"]');
                if (seriesTab) seriesTab.click();
                break;
            case TV_KEYS.BLUE: // 🔵 Ajustes
                event.preventDefault();
                const settingsBtn = document.getElementById('btn-open-settings');
                if (settingsBtn) settingsBtn.click();
                break;
        }

        // 3. Controles Multimedia
        if (code === TV_KEYS.PLAY || code === TV_KEYS.PAUSE || code === TV_KEYS.PLAY_PAUSE) {
            const videoEl = document.querySelector('video');
            if (videoEl) {
                event.preventDefault();
                if (videoEl.paused) {
                    videoEl.play();
                } else {
                    videoEl.pause();
                }
            }
        }
    }, true);

    console.log('[VIDAA] Driver de control remoto activado exitosamente para Toshiba VIDAA TV.');
})();

