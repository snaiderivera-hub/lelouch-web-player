# Arquitectura de Reproductor Web Integrado (HLS.js + HTML5 Video)

Basado en las mejores prácticas observadas en `jvdillon/netv` y estándares modernos de streaming web.

---

## 1. Integración de Hls.js

Para reproducir flujos `.m3u8` en cualquier navegador (Chrome, Edge, Firefox, Brave):

```javascript
import Hls from 'hls.js';

function playStream(videoElement, streamUrl) {
  if (Hls.isSupported()) {
    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      backBufferLength: 90
    });
    hls.loadSource(streamUrl);
    hls.attachMedia(videoElement);
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      videoElement.play();
    });
    return hls;
  } else if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari / iOS nativo
    videoElement.src = streamUrl;
    videoElement.addEventListener('loadedmetadata', () => {
      videoElement.play();
    });
  }
}
```

---

## 2. Controles de Reproducción y UI Overlay

- **Overlay Superior**: Título del canal / película, badge de resolución (`FHD 1080p`, `HD 720p`, `4K`), botón de cerrar.
- **Overlay Inferior**:
  - Play / Pausa.
  - Barra de progreso (para VOD / Catchup).
  - Tiempo transcurrido / restante.
  - Selector de calidad de video (Vía `hls.levels`).
  - Selector de pistas de audio y subtítulos.
  - Modo Picture-in-Picture (PiP).
  - Pantalla completa (Fullscreen).

---

## 3. Atajos de Teclado (Navegación 10-Foot / HTPC)

- `Espacio` o `K`: Reproducir / Pausar.
- `F`: Alternar Pantalla Completa.
- `M`: Mutear / Desmutear.
- `Flecha Izquierda` / `Flecha Derecha`: Retroceder / Avanzar 5 segundos.
- `Flecha Arriba` / `Flecha Abajo`: Subir / Bajar volumen.
- `Esc`: Cerrar reproductor modal.
