# FASE 26 — Pruebas Unitarias y Fixtures Ficticios M3U

## 1. Política de Seguridad en Pruebas
**Regla Estricta:** Ningún archivo de prueba o fixture contiene jamás credenciales reales de proveedores IPTV ni URLs privadas. Todos los fixtures emplean dominios y credenciales ficticias (`fictitious-iptv.test`, `fake_user`, `fake_pass`, `user_demo`, etc.) conformes al estándar RFC 2606 y RFC 6761 para nombres de dominio de prueba reservados.

---

## 2. Catálogo de Fixtures Creados

Los 10 archivos de prueba residen en [iptv-app/tests/fixtures/](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/tests/fixtures/):

| Archivo Fixture | Propósito Técnico | Casos de Prueba Clave |
| :--- | :--- | :--- |
| **`test-basic-live.m3u`** | Estructura canónica M3U | Canales en vivo estándar, cabecera con EPG, detección de radio (`radio="true"`). |
| **`test-utf8.m3u`** | Caracteres internacionales y símbolos | **中文 (CJK)**, **Español y acentos** (`ñ`, `á`, `é`, `í`, `ó`, `ú`, `¿`, `¡`), **emojis** (🔴, ⚽, 🎬, 🍿, ⚡, 🏆), **comillas simples y dobles anidadas**, y **URLs con `&` y `=`**. |
| **`test-logo.m3u`** | Atributos de logo | Query params con `&` y `=`, URLs codificadas (`%C3%B1`), logos vacíos (`""`), logos ausentes y con espacios en blanco. |
| **`test-groups.m3u`** | Agrupación y categorización | `group-title`, sobrescritura mediante `#EXTGRP`, fallback a `General` y grupos con comas y comillas. |
| **`test-extvlcopt.m3u`** | Opciones VLC y encabezados HTTP | `#EXTVLCOPT:http-user-agent`, `#EXTVLCOPT:http-referrer`, `#EXTVLCOPT:http-cookie`, mayúsculas y sintaxis abreviada. |
| **`test-kodiprop.m3u`** | Extensiones DRM y Kodi | `#KODIPROP:inputstream.adaptive`, DASH MPD, Widevine DRM y HLS ClearKey con claves en JSON. |
| **`test-catchup.m3u`** | Timeshift y Catch-up | `catchup="default"`, `catchup-days="7"`, plantillas con `{duration}` y `{utc:...}`, horas y `tv-archive` legacy. |
| **`test-duplicates.m3u`** | Desduplicación multi-fuente (FASE 23) | Preservación de fuentes cruzadas (Fuente A vs Fuente B nunca se borran) y colapso de duplicados exactos y con slash final `/`. |
| **`test-movies.m3u`** | Catálogo VOD de películas | Detección de `mediaType="movie"`, duraciones en segundos y extensiones de contenedor (`.mp4`, `.mkv`). |
| **`test-series.m3u`** | Catálogo de series | Detección de `mediaType="series"`, patrones de episodios (`S01E01`) y grupos temáticos. |

---

## 3. Matriz de Cobertura de Casos Críticos

### A. Idiomas y Codificación UTF-8
- **中文 (Chino Simplificado / Tradicional):** `🇨🇳 CCTV-1 综合频道 HD (中文)`, `🎬 电影频道 CCTV-6 旗舰高清`.
- **Español & Acentos:** `🍿 Películas de Acción: "El Gran Escape" & 'La Venganza' (Edición 2026)`, `🔴 ¡ÚLTIMA HORA! ¿Qué ocurrió en España? — Señal 24h`, `🏆 Fútbol Total: Niño Maravilla 'El Clásico' 2026 ⚡`.
- **Emojis:** Verificación de que símbolos como 🔴, ⚽, 🎬, 🍿, ⚡, 🏆 no truncan el byte stream ni alteran el conteo de longitud de línea.

### B. Comillas y Caracteres de Delimitación
- El analizador de atributos `parseAttributes` soporta de forma estricta:
  - Comillas dobles con comillas simples internas: `group-title="Cine & Series, Temporada '2026' (4K UHD)"`.
  - Comillas simples con comillas dobles internas: `group-title='Cine "De Culto"'`.
  - Atributos sin comillas: `radio=true`.

### C. URLs Complejas
- Preservación íntegra de tokens Base64 con padding `=` o `==`:
  `http://.../3001.m3u8?lang=zh_CN&codec=h264&token=eyJhbGciOiJIUzI1NiJ9==`
- URLs con múltiples parámetros unidos por `&`:
  `http://.../4003.m3u8?league=la_liga&match=real_barca&token_val=ABC==DEF&p1=1&p2=2`

---

## 4. Ejecución de la Suite de Pruebas

La suite completa automatizada se encuentra en [iptv-app/tests/test_phase26.html](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/tests/test_phase26.html) y puede ejecutarse directamente abriendo el archivo en cualquier navegador web o mediante el motor headless:

```powershell
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --headless=new --disable-gpu --allow-file-access-from-files --dump-dom "file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/tests/test_phase26.html"
```

**Resultado de Validación:**
- 10 pruebas unitarias ejecutadas.
- **10 pruebas superadas con éxito (100% PASS).**
