# AUDITORÍA TÉCNICA Y ESPECIFICACIÓN DE ARQUITECTURA
# PROYECTO: LELOUCH WEB PLAYER — PLAYLIST M3U DINÁMICA Y SINCRONIZADA
**Documento:** `LELOUCH_DYNAMIC_M3U_AUDIT.md`  
**Fase:** FASE 1 — AUDITORÍA EXHAUSTIVA Y DISEÑO TÉCNICO (Sin modificación de código)  
**Fecha:** 29 de Septiembre de 2026  
**Autor:** Antigravity Architect Agent  

---

## 1. RESUMEN EJECUTIVO Y OBJETIVO DEL SISTEMA

El proyecto **LELOUCH Web Player** cuenta actualmente con un cliente web de alto rendimiento capaz de importar suscripciones Xtream Codes / M3U, clasificar canales en vivo, películas y series, gestionar un "Creador y Gestor de Lista M3U" personalizada (con funciones de *Copiar M3U*, *Descargar .m3u* y *Ver Mi Lista en TV en Vivo*), almacenar configuraciones en IndexedDB local y sincronizar cuentas/favoritos con una base de datos en la nube (Supabase PostgreSQL).

El nuevo objetivo consiste en **evolucionar el Creador M3U de una solución puramente estática/local en el navegador hacia un servicio M3U dinámico, persistente y sincronizado en la nube**.
Este sistema permitirá generar una URL pública permanente con el siguiente formato:
```
https://lelouch-web-player.vercel.app/api/playlist/<TOKEN>
```
Dicha URL responderá en tiempo real con un manifiesto `#EXTM3U` conforme a la especificación estándar para reproductores externos (VLC, IPTV Smarters, TiviMate, Smart IPTV, TV Boxes). Cuando el usuario agregue, elimine, renombre, reordene u oculte canales desde la interfaz web de LELOUCH, la URL del enlace **permanecerá inmutable**, mientras que su contenido se actualizará automáticamente en la siguiente petición HTTP del cliente reproductor.

---

## 2. AUDITORÍA EXHAUSTIVA DEL CÓDIGO EXISTENTE (ITEMS 1 AL 20)

### Item 1: Stack Exacto de la Aplicación
* **Frontend Web:** 
  * **Vanilla JavaScript Moderno (ES6+ Modules)** ejecutado de forma nativa en el navegador mediante `<script type="module" src="src/app.js"></script>` ([index.html:L972](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/index.html#L972)).
  * **No utiliza** Vite, React, Vue, Angular ni TypeScript en el cliente web. No existe archivo `package.json` ni proceso de bundling o compilación frontend (`node_modules` no existe en el workspace web).
  * **Estilos:** Vanilla CSS moderno organizado en tokens y variables custom en [src/styles/main.css](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/styles/main.css) (más de 3,000 líneas con estética glassmorphism oscura).
  * **Librerías externas en navegador:** `@phosphor-icons/web` (iconos vía CDN), [hls.min.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/libs/hls.min.js) (HLS v1.5.8 local) y [mpegts.min.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/libs/mpegts.min.js) (MPEG-TS demuxer local).
* **Backend y Servidor:**
  * **Vercel Serverless Functions:** Función Node.js estándar en [api/proxy.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/api/proxy.js) con runtime Node.js ES Modules.
  * **Hosting:** Vercel con reglas de enrutamiento en [vercel.json](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/vercel.json).
  * **Base de datos en la nube:** Supabase PostgreSQL (`https://rotupbdeljgfddywryhk.supabase.co`) consumida mediante PostgREST API nativa vía `fetch()`.

### Item 2: Implementación Real de `UrlParser`
* **Ubicación:** [iptv-app/src/modules/iptv/parsers/UrlParser.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/parsers/UrlParser.js#L1-L100)
* **Comportamiento real:**
  * Emplea la API estándar `new URL(trimmed)` (línea 25); no recurre a `split("&")` manual propenso a errores.
  * Itera `url.searchParams.entries()` para extraer credenciales (`username`, `password`) y parámetros auxiliares.
  * Aplica un override específico para `liontv.es` reasignando puerto 80 a 8080 para evitar cuellos de botella (líneas 41-43).
  * Detecta `sourceType` mediante `detectSourceType()`:
    * `/player_api.php` ➔ `SourceType.XTREAM_API`
    * `/get.php` con credenciales ➔ `SourceType.XTREAM_M3U`
    * `/xmltv.php` ➔ `SourceType.XMLTV`
    * Terminación `.m3u`, `.m3u8` o `type=m3u_plus` ➔ `SourceType.XTREAM_M3U` o `SourceType.M3U_REMOTE`
  * Devuelve un objeto con `maskedPassword: '********'` para la UI y la propiedad privada `_password` para inicialización de adapters.

### Item 3: Implementación Real de `XtreamAdapter`
* **Ubicación:** [iptv-app/src/modules/iptv/adapters/XtreamAdapter.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/adapters/XtreamAdapter.js#L1-L358)
* **Comportamiento real:**
  * Implementa `testConnection()`, `getAccountInfo()`, `getCategories()`, `getLiveChannels()`, `getMovies()`, `getSeries()`, `getMovieInfo(vodId)`, `getSeriesInfo(seriesId)` y `getEPG()`.
  * La función `fetchJson()` (línea 65) determina automáticamente si la petición debe dirigirse al proxy (`/api/proxy?target=...`) si el protocolo es HTTP o si la app corre bajo HTTPS para evadir bloqueos de contenido mixto (Mixed Content) y CORS.
  * Valida si el servidor devuelve HTML en lugar de JSON (error típico de paneles IPTV caídos o páginas de error de Cloudflare/Nginx, línea 101).
  * Tolerancia a fallos: utiliza `Promise.allSettled()` en `getCategories()` (línea 216) de forma que el fallo de un endpoint (ej. si el proveedor no tiene series) no cancela la carga de TV en vivo ni películas.
  * Registra telemetría de diagnósticos por endpoint en `this.diagnostics`.

### Item 4: Cómo se Importan Actualmente M3U y Xtream
* **Xtream Codes API:**
  * El flujo principal pasa por `IPTVService.connect(url)` ([IPTVService.js:L78](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/IPTVService.js#L78)).
  * Descarga progresiva con actualización visual: 1º Cuenta (`player_api.php`) ➔ 2º Categorías (`get_live_categories`, `get_vod_categories`, `get_series_categories`) ➔ 3º Canales Live (`get_live_streams`) emitiendo render inmediato en pantalla ➔ 4º VOD (`get_vod_streams`) ➔ 5º Series (`get_series`).
* **Importación de archivos / URLs M3U sin Xtream:**
  * `UrlParser.js` clasifica URLs que terminen en `.m3u`/`.m3u8` como `SourceType.M3U_REMOTE`.
  * **Hallazgo clave de la auditoría:** Actualmente en el proyecto **NO EXISTE un parser M3U de archivo de texto completo (`.m3u` / `.m3u8`)**. Si una URL es un archivo M3U puro y no una API Xtream, no existe un módulo que parsee las líneas `#EXTINF` para poblar el catálogo general de canales. La importación real funciona al 100% sobre endpoints JSON de Xtream (`player_api.php`).

### Item 5: Dónde se Guardan las Playlists Guardadas
* **Almacenamiento Local Primario (IndexedDB):**
  * Base de datos: `NexusIPTV_DB` (versión 1).
  * Object Store: `playlists`, con índice `{ keyPath: 'id' }` ([CacheService.js:L41-L44](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/CacheService.js#L41-L44)).
* **Puntero de Lista Activa (LocalStorage):**
  * Clave: `iptv_active_playlist_id` ([PlaylistService.js:L51](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/PlaylistService.js#L51)).
* **Respaldo en la Nube (Supabase PostgreSQL):**
  * Tabla: `public.playlists` sincronizada mediante `supabaseService.savePlaylist()` ([SupabaseService.js:L63-L100](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/SupabaseService.js#L63-L100)).

### Item 6: Qué Tablas Supabase Existen Realmente
Conforme a la auditoría del script SQL maestro ([docs/supabase_schema.sql](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/docs/supabase_schema.sql)) y la verificación en vivo contra la API REST de Supabase:
1. `public.playlists`:
   * Columnas: `id` (UUID), `name` (TEXT), `url` (TEXT UNIQUE), `server_url` (TEXT), `username` (TEXT), `password` (TEXT), `is_active` (BOOLEAN), `channels_count` (INTEGER), `movies_count` (INTEGER), `series_count` (INTEGER), `expires_at` (TIMESTAMPTZ), `created_at` (TIMESTAMPTZ), `updated_at` (TIMESTAMPTZ).
   * **Propósito actual:** Almacena credenciales y endpoints de proveedores/cuentas IPTV.
2. `public.favorites`:
   * Columnas: `id` (UUID), `content_id` (TEXT), `type` (TEXT: live/vod/series), `title` (TEXT), `logo` (TEXT), `stream_url` (TEXT), `category_name` (TEXT), `created_at` (TIMESTAMPTZ). Clave única `(content_id, type)`.
3. `public.watch_history` (declarada en schema):
   * Guarda progreso de reproducción para "Continuar viendo".
4. `public.channel_health` (declarada en schema):
   * Métricas de latencia y estado ONLINE/OFFLINE.
5. `public.catalog_cache` (declarada en schema):
   * `cache_key` (TEXT) y `data` (JSONB) para respaldo de catálogos masivos.
* **Tablas inexistentes actualmente:** No existen tablas para items de listas personalizadas (`playlist_items`) ni tokens de acceso (`playlist_access_tokens`).

### Item 7: RLS Existente (Row Level Security)
* En [docs/supabase_schema.sql:L88-L115](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/docs/supabase_schema.sql#L88-L115), RLS está habilitado en todas las tablas, pero configurado con **políticas totalmente permisivas para el rol `anon`**:
  ```sql
  CREATE POLICY "Acceso total playlists anon" ON public.playlists FOR ALL TO anon USING (true) WITH CHECK (true);
  GRANT ALL ON TABLE public.playlists TO anon;
  ```
* Esto permite al frontend web operar sin autenticación de usuario (sin Supabase Auth `auth.uid()`), pero expone las filas a cualquier cliente que posea la clave anónima pública si no se segmenta adecuadamente.

### Item 8: Cómo Está Implementado Actualmente el Creador de Lista M3U
* **Ubicación:** [iptv-app/src/app.js:L1866-L2136](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L1866-L2136)
* **Almacenamiento:** Constante `CUSTOM_M3U_STORAGE_KEY = 'lelouch_custom_m3u_list'` en `localStorage`.
* **Estructura del item actual en memoria:**
  ```javascript
  {
    id: String(item.id || Date.now()),
    name: item.name || 'Canal sin nombre',
    category: item.category || 'Personalizada',
    logo: item.logo || '',
    url: item.url, // URL de stream directa
    epgId: item.epgId || '',
    addedAt: Date.now()
  }
  ```
* **Puntos de entrada para agregar elementos:**
  1. Canales en TV en Vivo: botón `#btn-integrated-add-m3u` ([app.js:L222](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L222)).
  2. Películas VOD: botón `#btn-add-vod-custom-m3u` ([MediaDetailModal.js:L140](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/components/MediaDetailModal.js#L140)).
  3. Episodios de Series: botones `.episode-add-m3u-btn` ([MediaDetailModal.js:L318](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/components/MediaDetailModal.js#L318)).
  4. Formulario manual: botón `#btn-custom-item-add` con campos `#custom-item-name`, `#custom-item-cat`, `#custom-item-url` ([app.js:L2109](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L2109)).

### Item 9: Cómo Funciona "Copiar M3U"
* **Función:** `copyCustomM3UAll()` ([app.js:L1946-L1962](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L1946-L1962))
* Genera el texto plano en formato `#EXTM3U` llamando a `generateCustomM3UContent()` ([app.js:L1934](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L1934)).
* Escribe directamente en el portapapeles con `navigator.clipboard.writeText(content)` o mediante `prompt()` como fallback.
* **Limitación actual:** Copia el texto estático de la lista en ese milisegundo exacto; no genera ningún enlace dinámico.

### Item 10: Cómo Funciona "Descargar Lista (.m3u)"
* **Función:** `downloadCustomM3U()` ([app.js:L1964-L1981](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L1964-L1981))
* Crea un `Blob([content], { type: 'audio/x-mpegurl;charset=utf-8' })`.
* Crea un elemento dinámico `<a>`, le asigna `a.download = mi_lista_iptv_YYYY-MM-DD.m3u` y simula `a.click()`.
* **Limitación actual:** Es un archivo físico descargado en el disco del usuario que queda desactualizado en cuanto el usuario agrega o quita un canal.

### Item 11: Dónde se Almacena la Selección Actual de Canales y Películas
* Los items elegidos por el usuario para su lista residen única y exclusivamente en el navegador local bajo `localStorage.getItem('lelouch_custom_m3u_list')`.
* Si el usuario abre la web en otro navegador, en modo incógnito, o limpia datos de navegación, **la lista personalizada se pierde por completo**.

### Item 12: Cómo se Identifican Live, Movies y Series
* **En el modelo interno:**
  * Campo `streamType` en cada objeto: `'live'` para [LiveChannel](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/types/iptv.types.js#L68), `'vod'` para [Movie](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/types/iptv.types.js#L84), y las series contienen estructura jerárquica con arreglos `seasons` y `episodes` ([iptv.types.js:L105-L145](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/types/iptv.types.js#L105-L145)).
* **En URLs de streaming:**
  * Canales: `.../live/username/password/stream_id.m3u8` (o `.ts`) ([security.js:L67](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/utils/security.js#L67))
  * Películas: `.../movie/username/password/stream_id.mp4` (o `.mkv`) ([security.js:L80](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/utils/security.js#L80))
  * Episodios: `.../series/username/password/stream_id.mp4` ([security.js:L93](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/utils/security.js#L93))

### Item 13: Si Existe ID Estable de Catálogo
* En los catálogos de proveedores Xtream, el `stream_id` numérico (ej. `25481`) es estable **únicamente dentro de ese proveedor específico**.
* **No existe un ID global universal:** Si el usuario tiene dos cuentas (ej. Proveedor A y Proveedor B), ambos pueden tener un canal con `stream_id: 100`.
* En la lista custom actual (`lelouch_custom_m3u_list`), la desduplicación se efectúa comparando `x.url === item.url` ([app.js:L1893](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L1893)).

### Item 14: Cómo se Relaciona Cada Contenido con su Proveedor / Source
* **Actualmente no existe clave foránea ni relación explícita:** En el Creador M3U existente, el canal se guarda como un objeto plano que copia la URL completa resultante. La relación con el proveedor queda implícita e incrustada en el texto de la URL (`serverBaseUrl` y credenciales).
* Si las credenciales del proveedor cambian o se renuevan en `Playlists Guardadas`, las URLs de la lista custom quedan obsoletas porque fueron copiadas estáticamente.

### Item 15: Cómo se Almacenan Actualmente URL, Host, Username y Password
* **En `localStorage`:** Clave `lelouch_custom_m3u_list` almacena las URLs con usuario y contraseña planos.
* **En IndexedDB `NexusIPTV_DB`:**
  * Tabla `playlists`: almacena `url` completa con contraseña en claro en el query string, y campos separados `username`, `serverBaseUrl` y `password` (oculto en UI).
  * Tabla `catalogs`: almacena el catálogo completo descargado con las URLs de stream conteniendo usuario y contraseña planos.
* **En Supabase `public.playlists`:** La columna `url` almacena la URL de conexión completa con `username=...&password=...` en texto plano. La columna `password` se envía enmascarada como `"        "`.

### Item 16: Si Alguna Credencial Sensible Llega al Frontend
* **SÍ:** El frontend maneja las credenciales en texto plano:
  1. `UrlParser.js` extrae `_password` y lo devuelve en el objeto parseado ([UrlParser.js:L60](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/parsers/UrlParser.js#L60)).
  2. `XtreamAdapter.js` almacena `this.password = parsedUrl._password` para firmar peticiones ([XtreamAdapter.js:L142](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/adapters/XtreamAdapter.js#L142)).
  3. `security.js` construye las URLs de streaming finales interpolando usuario y contraseña directamente en el cliente ([security.js:L68, L81, L94](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/utils/security.js#L68)).
  4. La clave `SUPABASE_ANON_KEY` está hardcodeada en texto plano en [SupabaseService.js:L8](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/SupabaseService.js#L8).

### Item 17: Qué Backend `/api` Existe Actualmente
* Existe únicamente un endpoint en Vercel:
  * [api/proxy.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/api/proxy.js) (duplicado idéntico en [iptv-app/api/proxy.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/api/proxy.js)).
  * Responde a `/api/proxy?target=<URL_ENCODED>`.
  * Diseñado como streaming proxy para eludir restricciones CORS y Mixed Content.

### Item 18: Qué Proxies Existen
1. **Proxy Vercel Cloud (Producción):** [api/proxy.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/api/proxy.js), implementado con Node.js `Readable.fromWeb` y reescritura dinámica de manifiestos M3U8 para enrutar chunks `.ts` a través del proxy.
2. **Proxy Local PowerShell (Desarrollo Local):** [iptv-app/proxy.ps1](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/proxy.ps1), servidor HTTP local en PowerShell en el puerto 7878 para pruebas offline en Windows.

### Item 19: Cómo Está Desplegado en Vercel
* Archivo de configuración: [vercel.json](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/vercel.json)
  ```json
  {
    "cleanUrls": true,
    "functions": {
      "api/proxy.js": { "maxDuration": 60, "memory": 1024 }
    },
    "rewrites": [
      { "source": "/api/(.*)", "destination": "/api/$1" },
      { "source": "/", "destination": "/iptv-app/index.html" },
      { "source": "/src/(.*)", "destination": "/iptv-app/src/$1" },
      { "source": "/(.*)", "destination": "/iptv-app/$1" }
    ]
  }
  ```
* **Mapeo:** La regla `{ "source": "/api/(.*)", "destination": "/api/$1" }` garantiza que cualquier archivo dentro de `/api` sea ejecutado como Vercel Serverless Function bajo Node.js.

### Item 20: Qué Código se Puede Reutilizar sin Duplicarlo
* [UrlParser.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/parsers/UrlParser.js): Funciones de parseo de URLs y extracción de parámetros.
* [security.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/utils/security.js): Generadores de URL `buildLiveStreamUrl()`, `buildVodStreamUrl()`, `buildSeriesStreamUrl()` y máscaras de credenciales.
* [ExportService.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/ExportService.js): Lógica de formateo M3U Plus con directivas `#EXTM3U`, `tvg-id`, `tvg-name`, `tvg-logo`, `group-title` y etiquetas `catchup`.
* [SupabaseService.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/SupabaseService.js): Cliente HTTP REST configurado para interactuar con las tablas de Supabase.
* [app.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js): Funciones visuales y de interacción del Creador de Lista M3U (`setupCustomM3UManager`, `renderCustomM3UManager`, `updateCustomM3UBadges`, `addCustomM3UItem`).

---

## 3. AUDITORÍA DE DEPENDENCIAS EXTERNAS: `iptv-m3u-playlist-parser`

### Estado Actual en el Proyecto
* En el proyecto **no existe actualmente ninguna dependencia equivalente instalada**. No hay librerías npm en el frontend ni en el backend para parseo o generación M3U.

### Análisis del Repositorio de Referencia (`iptv-m3u-playlist-parser`)
Se ha analizado la especificación de [notsurewhoisthis/iptv-m3u-playlist-parser](https://github.com/notsurewhoisthis/iptv-m3u-playlist-parser):
1. **Capacidades que aporta:**
   * Parser riguroso de cabeceras `#EXTM3U` y atributos `#EXTINF` con soporte para:
     * Duración y nombre del canal
     * Atributos estándar: `tvg-id`, `tvg-name`, `tvg-logo`, `tvg-country`, `tvg-language`, `group-title`, `radio`
     * Directivas avanzadas: `#EXTGRP:`, `#EXTVLCOPT:http-user-agent=...`, `#EXTVLCOPT:http-referrer=...`
     * Propiedades Kodi: `#KODIPROP:inputstream.adaptive.manifest_type=hls`, `#KODIPROP:inputstream.adaptive.license_key=...`
     * Atributos de Catch-up: `catchup="default"`, `catchup-days="7"`, `catchup-source="?timeshift={utc}&lutc={lutc}"`
   * Función `generateM3U(items)`: serializador estructurado que produce un archivo M3U8 limpio y validado.
   * Clasificación de contenido: clasificación heurística de items en `live`, `movie`, `series` y `radio` analizando la URL y extensiones.
2. **Evaluación de limitaciones y rendimiento:**
   * **Advertencia técnica crítica:** Dicha librería procesa listas mediante manipulación de cadenas en memoria (`string split` y expresiones regulares lineales). Su soporte para *streaming parser* asíncrono para catálogos masivos (+100,000 entradas) **está explícitamente documentado como trabajo futuro (Future Work)** en su repositorio.
   * **Decisión de arquitectura:** Para listas personalizadas generadas por el usuario (que típicamente oscilan entre 20 y 2,000 canales seleccionados), un generador determinista basado en strings y buffers es ultrarrápido (<15 ms de respuesta). Para catálogos completos de proveedores gigantes (100K+ líneas), no se debe intentar serializar todo de golpe en una Serverless Function de Vercel sin límites de memoria.

---

## 4. DISEÑO DEL MODELO INTERNO UNIFICADO: `LelouchMediaItem`

Para que cualquier contenido —procedente de una API Xtream, un archivo M3U importado o un canal/enlace directo manual— converja en la misma estructura sin duplicaciones ni inconsistencias, se define la interfaz canónica `LelouchMediaItem`:

```typescript
interface LelouchMediaItem {
  // Identificación única
  id: string;                      // Identificador único (UUID o string compuesto)
  sourceId?: string;               // UUID de la cuenta en public.playlists (null si es directo)
  catalogItemId?: string;          // stream_id original del proveedor (ej. "32910")
  
  // Metadatos esenciales
  type: 'live' | 'vod' | 'series' | 'direct' | 'radio';
  name: string;                    // Nombre público del canal/película/episodio
  categoryName: string;            // Categoría o grupo ("Deportes", "Acción", etc.)
  logo?: string;                   // URL del logo o poster
  
  // Streaming y resolución
  streamUrl?: string;              // URL resuelta o enlace directo
  containerExtension?: string;     // 'm3u8' | 'ts' | 'mp4' | 'mkv'
  
  // EPG y Metadatos Avanzados
  epgId?: string;                  // Identificador para sincronización de guía EPG
  catchup?: {
    type?: string;                 // 'default' | 'append' | 'shift'
    days?: number;                 // Días disponibles de archivo (ej. 7)
    source?: string;               // Plantilla timeshift
  };
  
  // Directivas IPTV avanzadas
  httpUserAgent?: string;          // User-Agent específico si el stream lo requiere
  httpReferrer?: string;           // Referer header
  kodiProps?: Record<string, string>; // Propiedades Kodi DRM / adaptive
  
  // Orden y estado en la playlist
  sortOrder: number;               // Posición en la lista (0-indexed)
  isEnabled: boolean;              // true = visible, false = oculto
  addedAt: number;                 // Timestamp de inclusión
}
```

---

## 5. DISEÑO DE LA BASE DE DATOS PERSISTENTE (SUPABASE POSTGRESQL)

### Reutilización vs Nuevas Tablas
* La tabla existente `public.playlists` fue creada para almacenar **cuentas y conexiones a servidores proveedores** (contiene `url UNIQUE`, `server_url`, `username`, `password`). 
* No se debe forzar la inserción de listas personalizadas en `public.playlists` porque rompería la restricción `UNIQUE` en `url` y la semántica de suscripción.
* Por tanto, la auditoría demuestra que es necesario incorporar **3 entidades normalizadas** perfectamente coordinadas:

```
┌────────────────────────────────┐         ┌────────────────────────────────┐
│   public.playlists (Existente) │         │  public.custom_playlists (New) │
│   (Cuentas / Proveedores IPTV) │         │  (Listas creadas por usuario)  │
│   - id (UUID PK)               │         │  - id (UUID PK)                │
│   - name, server_url, username │         │  - name, description           │
│   - url, password              │         │  - version (INT incremental)   │
└──────────────┬─────────────────┘         └──────────────┬─────────────────┘
               │                                          │ 1
               │ 0..1 (source_id)                         │
               │                                          │ 1..N
               │       ┌──────────────────────────────────┴┐
               └──────►│ public.playlist_items (New)       │
                       │ - id (UUID PK)                    │
                       │ - playlist_id (FK -> custom_pl)   │
                       │ - source_id (FK -> playlists)     │
                       │ - catalog_item_id, item_type      │
                       │ - title, category_name, logo_url  │
                       │ - custom_stream_url (para direct) │
                       │ - sort_order, is_enabled          │
                       └───────────────────────────────────┘
                                          │ 1
                                          │
                                          │ 1..N
                       ┌──────────────────┴────────────────┐
                       │ public.playlist_access_tokens(New)│
                       │ - id (UUID PK)                    │
                       │ - playlist_id (FK -> custom_pl)   │
                       │ - token_hash (TEXT UNIQUE, SHA256)│
                       │ - token_prefix (TEXT, 8 chars)    │
                       │ - is_enabled (BOOLEAN)            │
                       │ - expires_at (TIMESTAMPTZ)        │
                       └───────────────────────────────────┘
```

### Script DDL Propuesto
```sql
-- 1. TABLA DE PLAYLISTS PERSONALIZADAS (Creador M3U persistente)
CREATE TABLE IF NOT EXISTS public.custom_playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL DEFAULT 'Mi Lista LELOUCH',
    description TEXT DEFAULT 'Lista personalizada generada en LELOUCH Web Player',
    version INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. TABLA DE ELEMENTOS DE LA PLAYLIST
CREATE TABLE IF NOT EXISTS public.playlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES public.custom_playlists(id) ON DELETE CASCADE,
    source_id UUID REFERENCES public.playlists(id) ON DELETE SET NULL,
    catalog_item_id TEXT,               -- stream_id original (si proviene de Xtream)
    item_type TEXT NOT NULL CHECK (item_type IN ('live', 'vod', 'series', 'direct', 'radio')),
    title TEXT NOT NULL,
    category_name TEXT DEFAULT 'Personalizada',
    logo_url TEXT,
    custom_stream_url TEXT,            -- URL directa (si es canal manual o enlace externo)
    epg_id TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_playlist_items_order 
    ON public.playlist_items(playlist_id, sort_order ASC) 
    WHERE is_enabled = TRUE;

-- 3. TABLA DE TOKENS DE ACCESO PÚBLICO SEGURO
CREATE TABLE IF NOT EXISTS public.playlist_access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES public.custom_playlists(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,   -- Hash SHA-256 del token secreto (Hex 64 chars)
    token_prefix TEXT NOT NULL,        -- Primeros 8 caracteres para que el usuario identifique el enlace en la UI
    name TEXT DEFAULT 'Enlace TV Box / VLC',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,            -- NULL = sin expiración
    last_accessed_at TIMESTAMPTZ,
    access_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tokens_lookup 
    ON public.playlist_access_tokens(token_hash) 
    WHERE is_enabled = TRUE;

-- Habilitar RLS
ALTER TABLE public.custom_playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_access_tokens ENABLE ROW LEVEL SECURITY;

-- Políticas permisivas para anon key en fase actual
CREATE POLICY "Acceso total custom_playlists anon" ON public.custom_playlists FOR ALL TO anon USING (true) WITH CHECK (true);
CREATE POLICY "Acceso total playlist_items anon" ON public.playlist_items FOR ALL TO anon USING (true) WITH CHECK (true);
CREATE POLICY "Acceso total playlist_access_tokens anon" ON public.playlist_access_tokens FOR ALL TO anon USING (true) WITH CHECK (true);

GRANT ALL ON TABLE public.custom_playlists TO anon;
GRANT ALL ON TABLE public.playlist_items TO anon;
GRANT ALL ON TABLE public.playlist_access_tokens TO anon;
```

---

## 6. DISEÑO CRIPTOGRÁFICO DE TOKENS DE ACCESO

### Generación y Almacenamiento Seguro
1. **Generación del Token Secreto:**
   * Se genera en el servidor o cliente mediante generador criptográfico seguro:
     * Node.js: `crypto.randomBytes(24).toString('base64url')` (ej. `lel_9aK2xLmP04vQr8Tw1ZaY`)
     * Navegador: `window.crypto.getRandomValues(new Uint8Array(24))` codificado en Base64URL.
   * **Reglas:** Nunca se usan IDs secuenciales, UUIDs predecibles, correos, nombres ni contraseñas del usuario.
2. **Almacenamiento (Zero-Knowledge Token Hash):**
   * El token plano **NUNCA se almacena en la base de datos**.
   * Se calcula su hash criptográfico SHA-256:
     ```javascript
     const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');
     ```
   * En la base de datos se guarda únicamente `token_hash` y un `token_prefix` (ej. `lel_9aK2...`) para mostrar en pantalla qué enlace está activo.
   * Si la base de datos o los logs se vieran comprometidos, ningún atacante podría reconstruir el token M3U válido.
3. **Gestión del Ciclo de Vida:**
   * **Desactivar:** Marca `is_enabled = false` (el endpoint responde 404 o 403 de inmediato).
   * **Regenerar:** Invalida el hash anterior, genera un nuevo token aleatorio, actualiza `token_hash`, e incrementa `version` en `custom_playlists`.
   * **Expiración opcional:** Si `expires_at < NOW()`, se rechaza el acceso.

---

## 7. DISEÑO TÉCNICO DEL ENDPOINT: `GET /api/playlist/[token]`

### Arquitectura en Vercel
* Conforme a la auditoría del archivo [vercel.json](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/vercel.json), se implementa como una función Serverless Node.js en:
  ```
  api/playlist.js
  ```
* Se añade una regla de reescritura en [vercel.json](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/vercel.json):
  ```json
  { "source": "/api/playlist/(.*)", "destination": "/api/playlist.js?token=$1" }
  ```
  Esto permite que URLs como `https://lelouch-web-player.vercel.app/api/playlist/lel_xK89...` se procesen limpiamente recibiendo el parámetro `token` en `req.query.token`.

### Flujo de Ejecución Paso a Paso
```
Cliente Externo (VLC / TV Box / TiviMate)
   │
   │ 1. GET /api/playlist/<token>
   ▼
Vercel Serverless Function (api/playlist.js)
   │
   │ 2. Sanitiza token y calcula token_hash = SHA256(token)
   │ 3. Consulta Supabase: valida token_hash, is_enabled=true, expires_at
   ▼
¿Token Válido y Activo?
   ├── NO  ──► Responde 404 Not Found (Texto plano: "Playlist not found or expired")
   └── SÍ  ──► Continúa
                 │
                 │ 4. Obtiene custom_playlist (id, name, version)
                 │ 5. Valida ETag (W/"pl-<id>-v<version>")
                 ▼
          ¿Coincide ETag con If-None-Match?
                 ├── SÍ  ──► Responde 304 Not Modified (0 bytes transferidos)
                 └── NO  ──► Continúa
                               │
                               │ 6. Obtiene playlist_items (WHERE is_enabled=true ORDER BY sort_order ASC)
                               │ 7. Obtiene fuentes relacionadas (public.playlists) para resolver credenciales
                               │ 8. Construye URLs de streaming autorizadas directas al servidor IPTV
                               │ 9. Serializa texto #EXTM3U enriquecido (tvg-id, tvg-logo, group-title)
                               │ 10. Actualiza asíncronamente last_accessed_at y access_count en DB
                               ▼
                        Responde 200 OK
                        Content-Type: application/vnd.apple.mpegurl; charset=utf-8
                        Cache-Control: public, max-age=60, s-maxage=60, stale-while-revalidate=300
```

### Reglas Críticas del Endpoint
1. **Sin Proxy de Vídeo:** Vercel **NUNCA** hace proxy de los bytes de vídeo. El endpoint entrega únicamente el archivo de texto plano `.m3u`. El reproductor del usuario solicita el vídeo directamente a la IP/dominio del servidor IPTV autorizado.
2. **Caché Inteligente y Sincronizada:**
   * Cabecera `Cache-Control: public, max-age=60, s-maxage=60, stale-while-revalidate=300`.
   * En CDN de Vercel se cachea por 60 segundos para evitar saturar Supabase si 50 reproductores solicitan la lista al mismo tiempo.
   * Soporte de `ETag` vinculado al `version` incremental de la playlist. Si el usuario modifica la lista en LELOUCH Web, `version` sube (ej. 3 ➔ 4), el `ETag` cambia y el cliente recibe la lista actualizada inmediatamente.
3. **Formato de Respuesta:**
   * Responde exclusivamente con texto plano M3U. **Jamás devuelve HTML ni JSON ni redirecciones falsas**.
4. **Seguridad de Claves:**
   * El endpoint utiliza variables de entorno del servidor en Vercel (`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` o anon key) en el backend seguro, **sin exponer jamás la service role key al navegador del cliente**.

---

## 8. UTILIDAD CENTRAL DE REDACCIÓN DE DATOS SENSIBLES

Se especifica el módulo universal [security.js](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/utils/security.js) para su uso obligatorio tanto en cliente como en backend:

```javascript
/**
 * Redacta credenciales y tokens en cualquier string, URL o mensaje de error.
 * Garantiza que contraseñas, tokens y URLs privadas jamás aparezcan en consola,
 * logs de Vercel, telemetría o modales de la interfaz.
 */
export function redactSensitiveData(input) {
  if (!input || typeof input !== 'string') return input;
  
  return input
    // 1. Ocultar contraseñas en query params (?password=... &pass=...)
    .replace(/([?&](?:password|pass|pwd)=)[^&]+/gi, '$1[REDACTED]')
    // 2. Ocultar contraseñas en URLs de streaming Xtream (/live/usuario/password/id.ext)
    .replace(/(\/(?:live|movie|series)\/[^\/]+\/)[^\/]+(\/\d+\.[a-z0-9]+)/gi, '$1[REDACTED]$2')
    // 3. Ocultar tokens en rutas (/api/playlist/lel_xxx)
    .replace(/(\/api\/playlist\/)[a-zA-Z0-9_-]{8,}/gi, '$1[TOKEN_REDACTED]')
    // 4. Ocultar Bearer tokens y API keys
    .replace(/(Bearer\s+)[a-zA-Z0-9_\-\.]{20,}/gi, '$1[TOKEN_REDACTED]');
}
```

---

## 9. ADVERTENCIA TÉCNICA Y CONFIDENCIALIDAD DE LA URL M3U

> [!WARNING]
> **Confidencialidad de la URL Dinámica M3U:**  
> La URL generada (`https://lelouch-web-player.vercel.app/api/playlist/<TOKEN>`) debe tratarse con la misma confidencialidad que una contraseña personal.
> 
> * **Razón:** Cualquier dispositivo o persona que obtenga esta URL podrá descargar la lista de canales y consumir las conexiones simultáneas del proveedor IPTV del usuario.
> * **Medidas obligatorias en el sistema:**
>   1. La interfaz web mostrará advertencias claras al usuario al generar el enlace.
>   2. El usuario dispondrá de un botón visible para **"Revocar y Regenerar Enlace"** con un solo clic. Si un enlace es compartido o filtrado por error, la regeneración invalida inmediatamente el token anterior sin obligar al usuario a rearmar su lista de canales.

---

## 10. SINCRONIZACIÓN FUTURA PARA LELOUCH TV Y MOBILE (SIN REPARSEO M3U)

Actualmente, las aplicaciones nativas Android ([lelouch-android](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/lelouch-android)) disponen de comunicación directa y cliente HTTP OkHttp.
* **Problema de usar M3U en nuestras propias apps:** Si LELOUCH TV o Mobile descargan el archivo de texto M3U generado, se verían obligadas a gastar ciclos de CPU en el televisor parseando strings, perdiendo metadatos relacionales e IDs nativos.
* **Solución de Sincronización Directa Diseñada:**
  1. El backend proveerá opcionalmente una variante JSON compacta:
     ```
     GET /api/playlist/<token>?format=json
     ```
     o validación rápida de versión:
     ```
     GET /api/playlist/<token>/version ➔ {"version": 4, "itemCount": 128, "updatedAt": "..."}
     ```
  2. Cuando LELOUCH TV Box inicie, realiza un `HEAD` o consulta ultraligera de 40 bytes para comparar el `version` remoto con el `version` guardado localmente en Room/DataStore.
  3. Si `version` no ha cambiado, **cero descargas y cero parseos**.
  4. Si `version` cambió, descarga el JSON estructurado ya normalizado directamente al repositorio local.
*(Nota: No se modificará el código de Android en esta fase).*

---

## 11. MATRIZ DE IMPACTO Y EVALUACIÓN DE COMPONENTES

| Componente Actual | Archivo / Líneas Reales | Reutilizar | Modificar | Crear Nuevo | Riesgo y Mitigación |
|---|---|:---:|:---:|:---:|---|
| **Vercel Serverless Gateway** | [vercel.json:L1-L16](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/vercel.json#L1-L16) | SÍ | SÍ | - | **Bajo:** Agregar rewrite para `/api/playlist/(.*)` sin alterar el proxy existente. |
| **Endpoint Dinámico M3U** | `api/playlist.js` | - | - | SÍ | **Bajo:** Nuevo archivo aislado en Node.js serverless. No interfiere con el reproductor web. |
| **Parser de URLs IPTV** | [UrlParser.js:L1-L100](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/parsers/UrlParser.js#L1-L100) | SÍ | - | - | **Nulo:** Código robusto y testeado, se reutiliza al 100%. |
| **Seguridad y Redacción** | [security.js:L1-L96](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/utils/security.js#L1-L96) | SÍ | SÍ | - | **Bajo:** Agregar `redactSensitiveData()` y helper de hash de tokens. |
| **Serializador M3U Plus** | [ExportService.js:L28-L55](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/ExportService.js#L28-L55) | SÍ | SÍ | - | **Bajo:** Extraer el motor de formateo `#EXTINF` a una función pura compartida entre web y serverless. |
| **Esquema de Base de Datos** | [docs/supabase_schema.sql:L1-L116](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/docs/supabase_schema.sql#L1-L116) | SÍ | SÍ | - | **Bajo:** Agregar tablas `custom_playlists`, `playlist_items` y `playlist_access_tokens` sin alterar `playlists` existente. |
| **Servicio de Supabase** | [SupabaseService.js:L1-L225](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/modules/iptv/services/SupabaseService.js#L1-L225) | SÍ | SÍ | - | **Medio:** Añadir métodos de sincronización de `custom_playlists` e items en la nube. |
| **Gestor Visual Creador M3U** | [app.js:L1866-L2136](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/src/app.js#L1866-L2136) | SÍ | SÍ | - | **Medio:** Mantener intactos *Copiar M3U*, *Descargar .m3u* y *Ver en TV en Vivo*; agregar el botón *Generar Enlace M3U* y modal de gestión de token. |

---

## 12. PLAN DE PRUEBAS DETALLADO

### 1. Pruebas Unitarias
* **Generación de Token:** Verificar que el token plano generado sea aleatorio de alta entropía (Base64URL) y que su SHA-256 coincida con el almacenado.
* **Serializador M3U:** Comprobar que un arreglo de items con caracteres especiales, acentos, atributos `tvg-id`, `tvg-logo` y comillas dobles genere un `#EXTM3U` válido que no rompa reproductores.
* **Redactor de Seguridad:** Comprobar que URLs con contraseñas en query params y paths queden redactadas a `[REDACTED]` en logs.

### 2. Pruebas de Integración Backend
* **Invocación del Endpoint:**
  * Petición sin token ➔ HTTP 400 Bad Request.
  * Token inexistente o corrupto ➔ HTTP 404 Not Found.
  * Token desactivado (`is_enabled=false`) ➔ HTTP 404/403.
  * Token válido con items ➔ HTTP 200 con `Content-Type: application/vnd.apple.mpegurl; charset=utf-8` y cuerpo con `#EXTM3U`.
* **Caché y ETag:**
  * Segunda petición con cabecera `If-None-Match` idéntica ➔ HTTP 304 Not Modified.
  * Tras modificar la lista (cambio de `version`), la siguiente petición devuelve HTTP 200 con nuevo ETag.

### 3. Prueba en Vercel Preview
* Despliegue de branch de prueba en Vercel para validar que la Serverless Function responda en <100ms a través del CDN edge de Vercel.

### 4. Prueba Física de Sincronización en Vivo (El Test Definitivo)
1. **Paso A:** En LELOUCH Web Player, armar una lista con 3 canales: **Canal A**, **Canal B** y **Canal C**.
2. **Paso B:** Pulsar **Generar Enlace M3U** y copiar la URL pública generada (`https://.../api/playlist/lel_xxx`).
3. **Paso C:** Cargar la URL en un reproductor físico externo (VLC en PC o TiviMate en TV Box). Verificar que los 3 canales reproducen fluidamente.
4. **Paso D:** En LELOUCH Web Player, eliminar el **Canal B** y añadir un nuevo canal **Canal D** (la lista ahora es **A, C, D**).
5. **Paso E:** En el reproductor externo, pulsar "Recargar / Actualizar Lista" utilizando **exactamente la misma URL del Paso B**.
6. **Resultado Esperado:** El reproductor externo refleja de inmediato la lista con **Canal A, Canal C y Canal D** sin haber modificado la URL configurada.

---

## 13. PLAN DE IMPLEMENTACIÓN POR COMMITS PEQUEÑOS Y REVERSIBLES

La implementación se dividirá estrictamente en commits atómicos una vez recibida la autorización:

* **Commit 1 (Database & Docs):**
  * `docs/supabase_custom_m3u_schema.sql`: Creación del script DDL para las tablas `custom_playlists`, `playlist_items` y `playlist_access_tokens`.
* **Commit 2 (Backend Function & Routing):**
  * `api/playlist.js`: Creación del endpoint Vercel Serverless para resolución de tokens y entrega de manifiestos M3U con ETag.
  * `vercel.json`: Incorporación de la regla de reescritura para `/api/playlist/(.*)`.
* **Commit 3 (Security & Core Services):**
  * `iptv-app/src/modules/iptv/utils/security.js`: Inclusión de `redactSensitiveData()` y utilidades de hashing.
  * `iptv-app/src/modules/iptv/services/SupabaseService.js`: Métodos para crear, editar, reordenar y persistir listas personalizadas y tokens en Supabase.
* **Commit 4 (Frontend UI & Interaction):**
  * `iptv-app/src/app.js` y `iptv-app/index.html`: Incorporación del botón "Generar Enlace M3U", modal de visualización de enlace permanente, botón de revocar/regenerar token, y sincronización en tiempo real del Creador de Lista M3U con la nube.
* **Commit 5 (Verification & QA):**
  * Ejecución de pruebas de integración y validación del flujo dinámico en Vercel.

---
**FIN DE LA AUDITORÍA DE FASE 1.**  
*El agente se detiene aquí a la espera de las instrucciones y autorización del usuario para proceder con las fases de implementación.*
