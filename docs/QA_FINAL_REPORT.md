# REPORTE FORENSE DE ASEGURAMIENTO DE CALIDAD (QA) POST-IMPLEMENTACIÓN
**Proyecto:** Nexus IPTV Web Player  
**Entorno de Ejecución:** Windows 10/11, PowerShell 5.1 / 7, Edge Headless (Blink Engine), C# .NET Desktop Host  
**Fecha:** 25 de Septiembre de 2026  
**Auditor:** Senior QA Engineer, Security Reviewer & Performance Engineer  
**Estado General:** **PASS (Aprobado sin bloqueos críticos)**

---

## 1. RESUMEN EJECUTIVO: PASS / FAIL POR MÓDULO

| Módulo / Componente | Resultado | Evidencia / Observaciones |
| :--- | :---: | :--- |
| **Arquitectura UI & Routing** | **PASS** | Enrutamiento por Hash (`#home`, `#live`, `#movies`, `#series`, `#settings`) verificado. Carga síncrona/diferida en ES Module garantizada. |
| **Proxy Anti-SSRF Gateway** | **PASS** | 7/7 vectores de ataque bloqueados (Loopback, RFC 1918, file://, localhost). Tráfico IPTV legítimo permitido. |
| **Protección de Credenciales** | **PASS** | 0 contraseñas en DOM, 0 en consola, 0 en URLs de diagnóstico, 0 en localStorage. Aislamiento en IndexedDB. |
| **Dashboard Home** | **PASS** | Contadores 100% dinámicos en tiempo real. 0 métricas fijas (`8951`, `85874`, `8080`) en `src/`. |
| **Módulo Series** | **PASS** | 67 categorías, 8,080 series detectadas. Lazy loading de temporadas/episodios bajo demanda vía `get_series_info`. |
| **Módulo Movies (VOD)** | **PASS** | Catálogo paginado (48 items/página), filtros por categoría y búsqueda instantánea. `get_vod_info` lazy loading verificado en 10 películas. |
| **Módulo Live TV** | **PASS** | 20 cambios de canal ejecutados. Destrucción de pipelines (`hls.destroy()`, `mpegts.destroy()`) y limpieza de listeners verificada. |
| **Guía EPG** | **PASS** | `get_short_epg` activo (169 ms latencia inicial). Cache TTL de 5 minutos en memoria verificado. Canales sin guía muestran estado EMPTY sin fallos. |
| **Motor de Búsqueda Global** | **PASS** | 10 consultas reales medidas contra Live, VOD y Series. Min: 230.69 ms, Avg: 626.45 ms, Max: 1008.44 ms. Tolerante a acentos y mayúsculas. |
| **Persistencia IndexedDB** | **PASS** | Base `nexus_iptv_db` con 4 almacenes. Carga desde red: ~4,088 ms vs. Carga desde caché: 45 ms. Recarga con invalidación comprobada. |
| **Motor de Reproducción** | **PASS** | Hls.js, mpegts.js y HTML5 Video integrados. Reintentos exponenciales (2s, 4s, 6s). Throttling de historial a 6s. Calidad en Auto ABR. |
| **Diseño Responsive (Desktop)** | **PASS** | Evaluado y capturado en 1920x1080, 1600x900 y 1366x768. Live TV de 3 paneles operativo sin colisiones. |

---

## 2. CAPTURAS REALIZADAS Y EVIDENCIA VISUAL

Todas las capturas se generaron en alta resolución mediante el navegador Edge en modo Headless con renderizado de composited stages:

*Directorio de artefactos:* `LIVE_VOD_ALVARADO2023_20260924\tests\qa_artifacts\`

1. **Home:**
   - 1920x1080: [screenshot_home_1080p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_home_1080p.png) (440 KB)
   - 1600x900: [screenshot_home_900p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_home_900p.png) (341 KB)
   - 1366x768: [screenshot_home_768p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_home_768p.png) (282 KB)
2. **Live TV (Layout 3 Paneles: Categorías + Canales + Reproductor/EPG):**
   - 1920x1080: [screenshot_live_1080p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_live_1080p.png) (403 KB)
   - 1600x900: [screenshot_live_900p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_live_900p.png) (327 KB)
   - 1366x768: [screenshot_live_768p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_live_768p.png) (273 KB)
3. **Películas (VOD Grid & Filtros):**
   - 1920x1080: [screenshot_movies_1080p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_movies_1080p.png) (469 KB)
   - 1600x900: [screenshot_movies_900p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_movies_900p.png) (354 KB)
   - 1366x768: [screenshot_movies_768p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_movies_768p.png) (247 KB)
4. **Series (Catálogo & Lazy Loading):**
   - 1920x1080: [screenshot_series_1080p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_series_1080p.png) (430 KB)
   - 1600x900: [screenshot_series_900p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_series_900p.png) (329 KB)
   - 1366x768: [screenshot_series_768p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_series_768p.png) (247 KB)
5. **Ajustes & Playlists:**
   - 1920x1080: [screenshot_settings_1080p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_settings_1080p.png) (410 KB)
   - 1600x900: [screenshot_settings_900p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_settings_900p.png) (314 KB)
   - 1366x768: [screenshot_settings_768p.png](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/tests/qa_artifacts/screenshot_settings_768p.png) (216 KB)

*Inspección visual forense:*
- **Overflow:** 0 desbordamientos horizontales o solapamientos detectados.
- **Scroll:** Scroll independiente por contenedor (listas de canales y categorías no rompen la barra de navegación superior).
- **Cards & Modales:** Centrados con `z-index: 1000` y `backdrop-filter: blur(8px)`.
- **Adaptación en 1366x768:** La vista de 3 paneles de Live TV mantiene sus proporciones (`240px` categorías, `320px` canales, `flex: 1` para el visor de video y EPG).

---

## 3. CONSOLA DEL NAVEGADOR Y TELEMETRÍA DE RED

Durante las pruebas automatizadas y de navegación en vivo:

```
ERRORS = 0
WARNINGS = 0
FAILED REQUESTS = 0
```

- **console.error:** 0 incidentes.
- **console.warn:** 0 advertencias no controladas (manejadores try/catch registran avisos benignos exclusivamente cuando el usuario no tiene conexión configurada).
- **Unhandled Promise Rejections:** 0.
- **Errores 404 / CORS:** 0 en peticiones hacia la aplicación local o el proxy CORS.
- **TypeError / ReferenceError:** 0.

---

## 4. AUDITORÍA DEL DASHBOARD HOME Y VERIFICACIÓN DE MÉTRICAS

### Datos Reales vs. Hardcoding
Se auditó la totalidad del código fuente en `src/` mediante análisis léxico ripgrep buscando los valores señalados en la orden:
- `8951`: **0 coincidencias** en código de producción `src/`. *(Aparecía únicamente en archivos de datos brutos estáticos `LIVE/_resumen.json`).*
- `85874`: **0 coincidencias** en `src/`.
- `8080`: **0 coincidencias** hardcodeadas como métrica en `src/`.

### Origen de las Métricas en Home
Las tarjetas del dashboard obtienen sus datos directamente de los objetos computados en memoria tras la sincronización con el proveedor:
- **Canales en Vivo:** `iptvService.state.live.length`
- **Películas VOD:** `iptvService.state.movies.length`
- **Series:** `iptvService.state.series.length`
- **Deportes:** Filtrado dinámico `iptvService.state.live.filter(c => /deport|sport|espn|fox|gol|laliga/i.test(c.category_name)).length`
- **Vencimiento y Días Restantes:** Calculado a partir de `iptvService.state.account.exp_date` (Unix timestamp) comparado con `Date.now()`.
- **Estado de Cuenta:** Derivado de `iptvService.state.account.status` (`"Active"` -> `"Activa"`).

---

## 5. MÓDULO SERIES — PRUEBA FORENSE END-TO-END

### Verificación de Peticiones y Lazy Loading
Se constató que la aplicación **NUNCA** descarga los episodios de todas las series de forma anticipada (lo que generaría miles de solicitudes HTTP y colapsaría al proveedor). 

1. **Categorías de Series (`get_series_categories`):**
   - 67 categorías recibidas en **389 ms**.
2. **Listado General de Series (`get_series`):**
   - 8,080 series registradas recibidas en **4,290 ms**.
3. **Detalle Bajo Demanda (`get_series_info`):**
   - Se probó una muestra de 10 series reales de 3 categorías distintas, verificando la carga perezosa de temporadas y episodios:

| ID Serie | Nombre de la Serie | Temporadas | Episodios Totales | Latencia `get_series_info` |
| :---: | :--- | :---: | :---: | :---: |
| **18841** | Tan cerca de ti, nace el amor (2026) | 1 | 85 | 1,151 ms |
| **11900** | Un novio por suscripción (2026) | 1 | 20 | 662 ms |
| **11851** | Mi Rival (2026) | 1 | 52 | 751 ms |
| **19134** | Shaque: No confíes en nadie (2026) | 1 | 7 | 542 ms |
| **19133** | Un mundo diferente (2026) | 1 | 10 | 515 ms |
| **19125** | Casi Hermanos (2026) | 1 | 2 | 573 ms |
| **19124** | Habeas Corpus (2026) | 1 | 8 | 542 ms |
| **19123** | El Escándalo (2026) | 1 | 8 | 514 ms |
| **19119** | Cuatro manos, dos sonatas (2026) | 1 | 8 | 546 ms |
| **19118** | American Hostage (2026) | 1 | 2 | 946 ms |

*Resultado:* El modal de detalle renderiza las temporadas y listas de capítulos exclusivamente al hacer clic sobre una serie. La reproducción construye el stream URL mediante el formato Xtream: `http://{server}/series/{user}/{pass}/{episode_id}.{container}`.

---

## 6. MÓDULO PELÍCULAS (VOD) — PRUEBA COMPLETA

Se validó la paginación a 48 películas por vista, la búsqueda en tiempo real dentro del catálogo cinematográfico y la obtención de metadatos enriquecidos:

### Muestra de 10 Películas Probadas (`get_vod_info`):

| VOD ID | Título | Plot/Sinopsis | Elenco (Cast) | Director | Latencia |
| :---: | :--- | :---: | :---: | :---: | :---: |
| **2015780** | Codigo: Venganza | Presente | Presente | Presente | 455 ms |
| **2015579** | 42: La verdadera historia de una leyenda americana | Presente | Presente | Presente | 459 ms |
| **1582289** | Treason (2020) | Presente | Presente | Presente | 436 ms |
| **1585370** | Yakuza Princess (2021) | Presente | Presente | Presente | 425 ms |
| **1533488** | War in the USA (2025) | Presente | No provisto | Presente | 425 ms |
| **1571175** | Zara Larsson - Honor The Light (2023) | Presente | Presente | Presente | 449 ms |
| **1570331** | UFC BJJ 2: Tackett vs Canuto (2025) | Presente | Presente | No provisto | 466 ms |
| **1999431** | Beekeeper: El protector (2024) 4K | Presente | Presente | Presente | 462 ms |
| **1831447** | El mañana nunca muere - 1997 | Presente | Presente | Presente | 425 ms |
| **1966037** | Hiroshima 2 (1986) | Presente | Presente | Presente | 457 ms |

*Resultado:* `get_vod_info` se solicita bajo demanda con almacenamiento en caché local (`Map`), evitando re-consultar películas ya vistas en la misma sesión.

---

## 7. MÓDULO LIVE TV — CICLO DE VIDA DEL PLAYER Y FUGAS DE MEMORIA

Se simularon **20 cambios consecutivos de canal** a través de diversas categorías (Nacionales, Gran Hermano, Noticias Alemania FHD, Deportes):

```
Canales cambiados secuencialmente: 20
Llamadas de limpieza: 20/20 confirmadas
```

### Protocolo de Destrucción Ejecutado en Cada Cambio:
1. `_clearRetry()`: Cancelación de temporizadores activos de reconexión.
2. `hls.destroy()`: Destrucción de pipelines de segmentación, aborto de peticiones de fragmentos en vuelo y liberación de MediaSource.
3. `mpegts.destroy()`: Desacople de `MediaElement`, pausa de buffers y destrucción del worker.
4. `videoEl.pause(); videoEl.removeAttribute('src'); videoEl.load()`: Vaciado del buffer nativo de hardware.
5. `_progressThrottleTimer`: Cancelación de throttling de IndexedDB.

*Diagnóstico de Fugas:* Tras 20 cambios continuos, los listeners asociados al elemento `<video>` permanecen en 1 única instancia reutilizada. No existe acumulación lineal de instancias de workers de HLS ni elementos huérfanos en memoria.

---

## 8. GUÍA DE PROGRAMACIÓN (EPG)

- **Consulta a la API (`get_short_epg`):** Latencia de respuesta remota: **169 ms**.
- **Cálculo de Now / Next:** La función `epgService.getNowAndNext()` procesa timestamps UNIX en Base64, calcula la hora de inicio y fin, y computa el progreso en porcentaje `Math.min(100, Math.max(0, ((now - start) / (end - start)) * 100))`.
- **Caché TTL de 5 Minutos:** En peticiones consecutivas al mismo canal dentro de 300,000 ms, los datos se sirven desde la memoria interna sin emitir llamadas de red HTTP.
- **Canales sin EPG (Estado EMPTY):** Canales sin programación o con identificador inválido (probado con `stream_id=999999999`) retornan estructura vacía `{ epg_listings: [] }`. La interfaz responde mostrando `"Sin información de guía"` con barras en estado inactivo sin lanzar excepciones ni corromper el render del reproductor.

---

## 9. MOTOR DE BÚSQUEDA GLOBAL — MEDICIONES REALES

Se evaluó el tiempo de respuesta de `SearchService` ejecutando 10 términos de búsqueda distintos sobre el catálogo unificado en memoria:

| # | Consulta | Término Normalizado | Coincidencias | Latencia Real Medida |
| :---: | :--- | :--- | :---: | :---: |
| 1 | `2024` | `2024` (año / filtro) | 51 | **230.69 ms** (Mínimo) |
| 2 | `batman` | `batman` | 40 | 299.71 ms |
| 3 | `espn` | `espn` | 28 | 623.82 ms |
| 4 | `mexico` | `mexico` (sin tilde) | 15 | 630.58 ms |
| 5 | `hbo` | `hbo` | 37 | 655.25 ms |
| 6 | `spider` | `spider` (parcial) | 33 | 658.82 ms |
| 7 | `disney` | `disney` | 40 | 661.23 ms |
| 8 | `noticias` | `noticias` | 7 | 689.38 ms |
| 9 | `futbol` | `futbol` (fútbol/futbol) | 6 | 806.63 ms |
| 10 | `avengers` | `avengers` | 23 | **1,008.44 ms** (Máximo) |

### Resumen Estadístico:
- **Mínimo:** `230.69 ms`
- **Promedio:** `626.45 ms`
- **Máximo:** `1,008.44 ms`

*Comportamiento:* Las búsquedas limpian tildes mediante `normalize('NFD').replace(/[\u0300-\u036f]/g, '')`, admiten mayúsculas y minúsculas indistintamente y agrupan los resultados en 3 secciones claras: Canales, Películas y Series.

---

## 10. PERSISTENCIA EN INDEXEDDB Y CACHÉ

### Configuración del Motor de Almacenamiento Local:
- **Base de Datos:** `nexus_iptv_db` (Versión 1)
- **Object Stores:**
  - `catalogs`: Catálogo en vivo, películas, series y categorías.
  - `playlists`: Lista de servidores registrados.
  - `favorites`: Elementos marcados por el usuario.
  - `history`: Posición de reproducción y progreso.

### Tiempos Medidos:
- **Descarga e indexación remota desde la red:** `4,088 ms`
- **Carga y restauración local desde IndexedDB:** `45 ms` *(aceleración de 90x)*

### Invalidación de Caché:
El botón **"Recargar Catálogo"** y la opción de configuración invalidan las entradas mediante `cacheService.delete()` y vuelven a sincronizar los endpoints en red, mostrando la barra de progreso animada.

---

## 11. GESTIÓN DE PLAYLISTS Y PROTECCIÓN DE CREDENCIALES

### Requisitos de Seguridad Verificados:
- **DOM Visible:** En la vista de Ajustes, la contraseña nunca se inyecta en el DOM. Se muestra fijamente como `••••••••`. El usuario es enmascarado (ej. `Her*****`).
- **Consola:** No existen sentencias `console.log` que impriman objetos de configuración con passwords.
- **LocalStorage:** **No se utiliza.** Todo almacenamiento estructurado se realiza en IndexedDB.
- **Logs de Diagnóstico:** Todas las URLs de diagnóstico sanitizan la contraseña mediante la función `buildSafeLogUrl()`, transformando las consultas en `player_api.php?username=Hermanos503&password=[REDACTED]&action=...`.

### Documentación Técnica de Almacenamiento:
1. **Dónde se almacena:** En el almacén privado del navegador del usuario bajo IndexedDB (`nexus_iptv_db` -> store `playlists`).
2. **Formato:** Objeto JSON con campos `id`, `name`, `url` (original para llamadas al backend del proveedor), `serverBaseUrl`, `username`, `maskedUsername`, `sourceType`, `isActive`, `createdAt`, `lastUsedAt`.
3. **Protección:** Aislado bajo la directiva de seguridad del navegador (Same-Origin Policy para `http://localhost:8686`). No se transmite a ningún servidor de terceros, únicamente al proveedor configurado a través del gateway local.

---

## 12. SEGURIDAD DEL PROXY LOCAL Y AUDITORÍA ANTI-SSRF

Se auditó tanto `proxy.ps1` como el host nativo compilado `AppHost.cs` (`IPTV-Data-Architect.exe`).

### Matriz de Pruebas de Intrusión / SSRF:

| Vector Probado | Destino Solicitado | Código Esperado | Código Obtenido | Veredicto |
| :--- | :--- | :---: | :---: | :---: |
| Loopback IPv4 | `http://127.0.0.1/admin` | **403** | 403 Forbidden | **PASS** |
| Localhost Hostname | `http://localhost:8080/secret` | **403** | 403 Forbidden | **PASS** |
| Red Privada Clase A (RFC 1918) | `http://10.0.0.1/` | **403** | 403 Forbidden | **PASS** |
| Red Privada Clase C (RFC 1918) | `http://192.168.1.1/router` | **403** | 403 Forbidden | **PASS** |
| Red Privada Clase B (RFC 1918) | `http://172.16.0.1/` | **403** | 403 Forbidden | **PASS** |
| Esquema Local de Archivos | `file:///C:/Windows/win.ini` | **403** | 403 Forbidden | **PASS** |
| Parámetro Vacío | *(Sin target)* | **400** | 400 Bad Request | **PASS** |
| Proveedor IPTV Legítimo | `http://liontv.es:80/live/...` | **200** | 200 OK | **PASS** |

*Allowlist y Validación Real:*
La función `IsSafeTargetUri` verifica que el esquema sea obligatoriamente `http` o `https`, rechaza nombres de host locales (`localhost`, `127.0.0.1`, `::1`, `0.0.0.0`), resuelve la IP y descarta los rangos `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` y `169.254.0.0/16`. Cualquier host público legítimo pasa la validación y es transmitido con cabeceras CORS permisivas.

---

## 13. AUDITORÍA DE RED Y EXPOSICIÓN DE CREDENCIALES

| Tipo de Exposición | Estado | Detalle |
| :--- | :---: | :--- |
| **UI Exposure** | **Ninguna** | Enmascaramiento completo en HTML (`••••••••` y `Usr***`). |
| **Console Exposure** | **Ninguna** | 0 coincidencias en logs de JavaScript. |
| **Storage Exposure** | **Controlada** | Almacenado localmente en IndexedDB bajo el origen local, sin texto plano en LocalStorage. |
| **Network Necessity** | **Inevitable** | El protocolo Xtream Codes requiere `username` y `password` en los query params de `player_api.php` y en las rutas de streams `/live/{user}/{pass}/{id}.m3u8`. El proxy local enmascara estas cadenas en sus logs de consola a `[REDACTED]`. |

---

## 14. MOTOR DE REPRODUCCIÓN (PLAYER CAPABILITIES)

1. **HLS.js (v1.4.12):** Utilizado para streams `.m3u8` y transmisiones en vivo con decodificación HLS. Auto-recuperación de errores de decodificación mediante `recoverMediaError()`.
2. **mpegts.js (v1.7.3):** Utilizado para streams directos en formato contenedor MPEG-TS (`.ts`).
3. **HTML5 Video Nativo:** Utilizado como fallback para archivos directos MP4/MKV.
4. **Controles Verificados:** Reproducir, Pausar, Control de Volumen, Pantalla Completa, Picture-in-Picture (PiP).
5. **Selector de Calidad:** Opera en modo **Auto ABR (Adaptive Bitrate)** mediante los algoritmos internos de Hls.js (`currentLevel = -1`). **No existe un selector manual de resolución en la interfaz gráfica**; la afirmación ha sido rectificada formalmente.

---

## 15. ADAPTABILIDAD RESPONSIVE

Se realizaron pruebas de layout en tres resoluciones de escritorio estándar:
- **1920x1080 (Full HD Desktop):** Distribución balanceada. El reproductor ocupa el 65% del ancho en Live TV.
- **1600x900 (Laptop Estándar):** Márgenes auto-ajustados, tipografía fluida y cards de tamaño consistente.
- **1366x768 (Laptop Compacta):** El layout de 3 columnas de Live TV (`Categorías 240px` + `Canales 320px` + `Player auto`) se mantiene completamente operativo sin solapamientos ni desbordes.

---

## 16. BUGS ENCONTRADOS Y CORREGIDOS

### Bug 1: Carga Diferida del Módulo ES e Inicialización de la App
- **Severidad:** Media / Regresión silenciosa.
- **Causa:** En `src/app.js`, la llamada `document.addEventListener('DOMContentLoaded', initApp)` fallaba si el navegador terminaba de parsear el DOM antes de evaluar el módulo ES (`document.readyState === 'complete'`), impidiendo que `initApp()` se disparara de inmediato.
- **Corrección:** Se implementó verificación de `document.readyState`:
  ```javascript
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
  } else {
    initApp();
  }
  ```

### Bug 2: Bloqueo de SSRF en AppHost.cs de Escritorio
- **Severidad:** Alta (Seguridad).
- **Causa:** El ejecutable `IPTV-Data-Architect.exe` interceptaba el puerto 7878 pero carecía de la función `IsSafeTargetUri`, retornando 502 en lugar de 403 al recibir destinos locales prohibidos.
- **Corrección:** Se portó la lógica de validación de IPs privadas a `AppHost.cs`, se recompiló con `csc.exe` y se verificó el retorno de HTTP 403 Forbidden.

### Bug 3: Extracción de 10 Películas en Suite de Telemetría
- **Severidad:** Baja (Pruebas).
- **Causa:** El script de prueba tomaba los primeros 5 archivos VOD en lugar de iterar hasta completar las 10 películas requeridas.
- **Corrección:** Se actualizó `tests/qa_forensic_suite.ps1` para extraer y verificar exactamente 10 películas con metadatos reales de plot, cast y director.

---

## 17. LIMITACIONES REALES IDENTIFICADAS

1. **Selector Manual de Calidad:** Hls.js ajusta el bitrate dinámicamente según el ancho de banda disponible (ABR), pero la UI no dispone de un menú desplegable para forzar resoluciones específicas (1080p, 720p, etc.).
2. **Dependencia del Gateway Proxy:** Debido a restricciones CORS de los navegadores web modernos, las llamadas al protocolo Xtream Codes sobre HTTP sin cabeceras `Access-Control-Allow-Origin` requieren que el proxy local (`proxy.ps1` o el binario de escritorio) se encuentre en ejecución.
3. **EPG Extendido Limitado por el Proveedor:** Ciertos canales de proveedores externos no publican guía EPG o solo proporcionan el programa actual, lo cual es manejado limpiamente mediante el estado EMPTY sin interrumpir la transmisión.

---

## 18. VEREDICTO FINAL

El sistema **Nexus IPTV Web Player** ha superado todas las pruebas funcionales, de seguridad, de persistencia y de rendimiento. Las credenciales de usuario se encuentran adecuadamente protegidas, el catálogo se procesa de forma eficiente bajo demanda, y no existen métricas simuladas ni fugas de memoria en el reproductor.

**Dictamen de QA:** **APROBADO PARA PRODUCCIÓN LOCAL**
