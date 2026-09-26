# IPTV Data Architect & Web Player

Aplicación moderna para gestión, reproducción y análisis de catálogos IPTV (M3U, Xtream Codes y HLS).

---

## 🚀 Inicio Rápido

Para iniciar la aplicación en tu PC:
- **Doble clic en:** `EJECUTAR APP ESCRITORIO.bat`
  *(Inicia automáticamente el servidor web local en `http://localhost:8686`, el gateway CORS anti-SSRF y abre tu navegador).*

---

## 📁 Estructura del Proyecto

- **`iptv-app/`**: Aplicación web frontend (HTML, CSS vainilla, JavaScript modular ES Modules, Hls.js, mpegts.js).
- **`LIVE/`**: Base de datos de canales en vivo organizados por categorías en formato JSON.
- **`VOD/`**: Base de datos de películas y contenidos bajo demanda en formato JSON.
- **`tests/`**: Suite de pruebas QA, benchmarks de rendimiento, artefactos forenses y pruebas de salud de canales.
- **`docs/`**: Reportes de auditoría QA forense y refinamiento visual XALB.
- **`backups/`**: Copias de seguridad de versiones anteriores del proyecto.
- **`knowledge_base/`**: Base de conocimiento y especificaciones técnicas.
- **`IPTV-Data-Architect.exe`**: Lanzador nativo de Windows (AppHost) que gestiona el servidor y el gateway local.
- **`procesar_iptv.ps1`**: Utilidad PowerShell para convertir listas M3U/Xtream a la base de datos JSON local.
