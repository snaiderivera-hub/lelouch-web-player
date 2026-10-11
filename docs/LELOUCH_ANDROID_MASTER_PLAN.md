# LELOUCH ANDROID — MASTER PLAN & ARQUITECTURA TÉCNICA
**Documento Oficial de Investigación, Diseño y Hoja de Ruta (Sprint 0)**
*Versión:* 1.0.0  
*Fecha de Emisión:* 2026-09-26  
*Autor:* Antigravity Engineering  
*Estado:* **SPRINT 0 COMPLETADO — PENDIENTE DE APROBACIÓN PARA SPRINT 1**

---

## 1. Visión General del Producto

**LELOUCH WEB PLAYER** ([https://lelouch-web-player.vercel.app/](https://lelouch-web-player.vercel.app/)) representa el producto fundacional: un reproductor y organizador IPTV moderno, fluido y estéticamente superior.

El objetivo de este proyecto es construir **LELOUCH ANDROID NATIVO**, una aplicación 100% Kotlin orientada a tres formatos físicos:
1. **Android Phone** (Experiencia táctil vertical/horizontal, navegación por pestañas inferiores).
2. **Android Tablet** (Experiencia adaptativa con riel de navegación y vistas maestro-detalle).
3. **Android TV & Google TV** (Experiencia 10-foot UI, 100% controlada por control remoto D-Pad, carruseles cinematográficos estilo Xbox/Game Pass y reproducción a pantalla completa).

### Principios Fundamentales
* **LELOUCH Web Player NO se reemplaza ni se destruye:** Continúa como producto web activo e independiente en su directorio actual.
* **Cero WebViews como motor:** La app Android será **nativa pura** en Kotlin con Jetpack Compose y Android Media3 / ExoPlayer.
* **Directorio de desarrollo independiente:** Todo el código Android se creará dentro de la carpeta `/lelouch-android/`.

---

## 2. Fase 0 — Análisis Profundo de LELOUCH Web Player

Se analizó la base de código existente en `iptv-app/` para extraer su modelo de datos, lógica de negocio y tokens de diseño:

### 2.1 Modelo de Datos y Dominio Extraído
* **Categorías:** Identificador (`id`), nombre (`name`), tipo (`live | vod | series`), conteo dinámico (`itemCount`).
* **Canales en Vivo (`LiveChannel`):** `id`, `categoryId`, `categoryName`, `name`, `logo`, `streamUrl`, `epgId`, `rating`.
* **Películas VOD (`Movie`):** `id`, `categoryId`, `categoryName`, `name`, `logo`, `poster`, `streamUrl`, `containerExtension` (`mp4`, `mkv`), `rating`, `description`, `year`, `genre`, `duration`.
* **Series (`Series`):** `id`, `categoryId`, `categoryName`, `name`, `cover`, `rating`, `year`, `genre`, `plot`, `seasons` y `episodes` (lazy loading mediante `action=get_series_info`).
* **Guía Electrónica (`EPG`):** `title`, `start`, `end`, `description`, `progressPercentage` (cálculo Now / Next en tiempo real).
* **Control Parental:** Filtro regex multicapa para palabras clave adultas (`+18`, `xxx`, `adult`, `porn`, `erotic`), protección con PIN de 4 dígitos (por defecto desbloqueado/opcional).
* **Gestión de Playlists:** Almacenamiento múltiple de cuentas Xtream (`server`, `username`, `password`) y listas remotas M3U Plus.
* **Continuar Viendo (`Continue Watching`):** Registro de progreso con marca temporal (`positionMs`, `durationMs`, `updatedAt`).

### 2.2 Sistema de Diseño Web Extraído (Tokens de Color y Estilo)
Del archivo `iptv-app/src/styles/main.css`:
* **Fondo Raíz (`--bg-root`):** `#02070D` (Azul Noche Ultra Profundo)
* **Superficie Secundaria (`--bg-surface`):** `#07111C`
* **Tarjetas y Contenedores (`--bg-card`):** `#0A1724` (Hover/Focus: `#0E2033`)
* **Acento Eléctrico (`--accent-cyan`):** `#00E5FF` (Cian Neón característico de Lelouch)
* **Acento Secundario (`--accent-blue`):** `#0284C7`
* **Texto Primario (`--text-main`):** `#F4F8FB`
* **Texto Secundario (`--text-secondary`):** `#8191A3`
* **Bordes Sutiles:** `rgba(0, 229, 255, 0.12)` con resplandor `0 0 14px rgba(0, 229, 255, 0.18)`
* **Colores Funcionales de Sección:**
  * **Live TV:** `#EF4444` (Rojo vivo)
  * **Películas:** `#00E5FF` (Cian eléctrico)
  * **Series:** `#A855F7` (Púrpura neón)
  * **Deportes:** `#10B981` (Verde esmeralda)

### 2.3 Matriz de Correspondencia (Web Feature → Android Domain → Phone UI → TV UI)

| Web Feature | Dominio Android (`:domain`) | UI Phone / Tablet (`:feature-mobile`) | UI Android TV (`:feature-tv`) |
| :--- | :--- | :--- | :--- |
| **Home (Portal Dashboard)** | `GetDashboardMetricsUseCase`, `GetActiveSourceUseCase` | Grilla 2x2 táctil con 4 tarjetas gigantes, badges de conteo y acciones inferiores | Fila 4x1 de tarjetas gigantes con resplandor neón, escala D-Pad y barra inferior |
| **TV en Vivo** | `GetLiveChannelsUseCase`, `PlayStreamUseCase` | Pantalla vertical: Categorías en tabs + lista de canales. Al pulsar: reproductor en top o fullscreen apaisado | Layout especializado de 3 paneles simultáneos (Categorías \| Canales \| Reproductor integrado + EPG Now/Next) |
| **Películas (VOD)** | `GetMoviesPagedUseCase`, `GetMovieDetailUseCase` | Grilla responsiva de pósters táctiles (2 o 3 columnas) con buscador | Rieles horizontales estilo Xbox Game Pass por categoría, Hero dinámico que cambia con el foco, navegación por D-Pad |
| **Series** | `GetSeriesPagedUseCase`, `GetSeriesDetailUseCase` | Lista de pósters táctiles → Detalle con acordeón de temporadas y episodios | Carruseles horizontales de series → Pantalla de detalle con selector horizontal de temporadas y grilla de capítulos |
| **EPG Now / Next** | `GetEpgNowNextUseCase` | Tarjeta compacta debajo del reproductor de video | Panel inferior derecho bajo el video integrado con barra de progreso |
| **Continuar Viendo** | `GetContinueWatchingUseCase`, `SaveProgressUseCase` | Fila horizontal "Seguir Viendo" en Home con barra de progreso | Primer riel horizontal debajo del Hero principal con acción directa de reanudación |
| **Búsqueda Instantánea** | `SearchCatalogUseCase` | Barra superior de búsqueda, teclado táctil, resultados instantáneos agrupados | Vista de búsqueda TV con teclado virtual en pantalla o integración con búsqueda por voz |
| **Ajustes y Cuentas** | `ManagePlaylistsUseCase`, `ManageCredentialsUseCase` | Configuración en lista vertical con modales táctiles | Interfaz de ajustes en tarjetas de gran tamaño con selector D-pad |

### 2.4 Portal Dashboard Canónico (Diseño Aprobado para TV Box y Móvil — FASE 32)
El diseño fundacional del Home en Android TV y Móvil adopta la arquitectura del Portal Web:
1. **Cabecera Central:**
   - Logotipo y Título: `REPRODUCTOR LELOUCH` (tipografía bold con resplandor cyan).
   - Subtítulo: `M3U • XTREAM CODES • HLS`.
   - Chip de Cuenta Activa: Badge con nombre de servidor (ej: `liontv.es`), botón de recarga `[🔄 RECARGAR]` y fecha de vencimiento (`Vence: DD/MM/AAAA`).
2. **Las 4 Tarjetas Gigantes Hero (Portal Dashboard):**
   - 📺 **TV EN VIVO:** Acceso directo a canales en vivo, con badge numérico en tiempo real e integración de **"Mi Lista Personalizada"** (67 canales).
   - 🎬 **PELÍCULAS:** Acceso al catálogo de películas VOD categorizadas.
   - 🎞️ **SERIES:** Navegación por series, temporadas y capítulos.
   - ⚽ **DEPORTES:** Acceso directo a eventos deportivos y canales en vivo filtrados.
3. **Fila Inferior de Acciones Rápidas:**
   - `📄 Descargar M3U`: Exportación rápida de lista.
   - `🔄 Recargar Catálogo`: Refresco de caché y sincronización con el servidor.
   - `⚙️ Ajustes y Listas`: Gestión de cuentas externas y lista personalizada.
   - `🩺 Diagnóstico`: Test de latencia y estado de conexión.
4. **Comportamiento Adaptativo:**
   - **En Android TV (TV Box):** Disposición 4x1 horizontal centrada. Cada tarjeta escala (`scale = 1.08f`) y activa un halo de luz neón al recibir el foco del D-Pad.
   - **En Teléfono Móvil:** Disposición en cuadrícula responsiva 2x2 con cards ergonómicas de toque rápido.

---

## 3. Fase 1 — Estudio de Repositorios de Referencia y Licencias

Se examinaron las 6 referencias arquitectónicas establecidas en el requerimiento para extraer sus mejores prácticas e identificar sus términos de licenciamiento:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        MAPA DE ESTUDIO DE REFERENCIAS TÉCNICAS                         │
├───────────────────────┬───────────────────────────────┬────────────────┬───────────────┤
│ Repositorio           │ Propósito Principal Estudiado │ Licencia       │ Estado Legal  │
├───────────────────────┼───────────────────────────────┼────────────────┼───────────────┤
│ google/tv-samples     │ UI Canónica TV, D-Pad, Focus  │ Apache 2.0     │ Compatible    │
│ SERFF/iptv-app        │ Arquitectura IPTV en Media3   │ MIT            │ Compatible    │
│ isnow-git/strix       │ 100K+ Catálogos, Room FTS, TV │ GPLv3          │ Solo Patrones │
│ joshcoburn/XtreamlyTV │ Multiplataforma, Focus Restore│ MIT            │ Compatible    │
│ Thedurancode/owntv    │ Estrategia Media3 vs libmpv   │ GPLv3          │ Solo Patrones │
│ hevarrwandzi/tv-app   │ Diferencias Phone vs TV UI    │ MIT            │ Compatible    │
└───────────────────────┴───────────────────────────────┴────────────────┴───────────────┘
```

> [!IMPORTANT]
> **Política de Licenciamiento y Aislamiento de Código:**  
> Los repositorios bajo licencia **GPLv3** (`strix`, `owntv`) **NO serán copiados ni incorporados como código fuente**. Únicamente se extraen sus conceptos arquitectónicos abstractos (ej. uso de SQLite FTS para búsqueda, estrategia de virtualización Paging 3 y desacoplamiento de motores de reproducción). La base de código de LELOUCH Android será escrita de forma 100% original en Kotlin.

### Lecciones Clave de Cada Referencia:
1. **`google/tv-samples` (JetStreamCompose & TvMaterialCatalog):**
   * Emplear `androidx.tv.material3.*` (`Carousel`, `TvLazyRow`, `TvLazyColumn`, `StandardCardContainer`, `ImmersiveList`).
   * No usar `Modifier.clickable` tradicional en TV; usar `Modifier.focusable` y componentes que gestionen elevación y escalado de foco (`scale = CardDefaults.scale(focusedScale = 1.08f)`).
2. **`SERFF/iptv-app`:**
   * Demuestra que **Media3 ExoPlayer es completamente suficiente** para reproducir canales Xtream y M3U sin depender de reproductores externos complejos.
   * `WorkManager` para sincronizar EPG en segundo plano sin congelar la interfaz al iniciar la app.
3. **`isnow-git/strix`:**
   * Demuestra el peligro del "OutOfMemoryError" en Android TV Boxes de 1 GB de RAM.
   * Solución: **Room con tablas virtuales FTS4/FTS5** para indexar 90,000+ elementos y **Paging 3** para traer a memoria únicamente lo que cabe en pantalla.
4. **`joshcoburn/XtreamlyTV`:**
   * Algoritmo de restauración de foco: Al presionar "Back" desde el detalle de una película, el foco debe regresar exactamente al riel y al ítem donde el usuario estaba posicionado, no a la cabecera ni al primer ítem.
5. **`Thedurancode/owntv`:**
   * Estrategia de motor de video: OwnTV incluye `libmpv` debido a códecs exóticos en VOD antiguos. Sin embargo, compilar C++ / NDK aumenta el tamaño del APK en más de 40 MB y complica el soporte de arquitectura ABI.
   * **Decisión para Lelouch:** Diseñar una interfaz agnóstica `LelouchPlayer`. La implementación principal será `Media3ExoPlayerEngine`. Solo si en las pruebas de QA encontramos streams que Media3 no decodifique por hardware, evaluaremos extensiones de software FFmpeg o mpv.
6. **`hevarrwandzi/tv-app`:**
   * Confirmación de diseño: Los teléfonos usan navegación inferior táctil (Bottom Navigation), mientras que Android TV utiliza navegación lateral (Navigation Rail / Drawer) que se expande al enfocar con D-Pad hacia la izquierda.

---

## 4. Fase 2 — Stack Tecnológico Justificado

Cada dependencia seleccionada tiene un propósito concreto y validado para dispositivos móviles y televisores con recursos limitados:

```
┌────────────────────────────────────────────────────────────────────────┐
│                      STACK TECNOLÓGICO SELECCIONADO                    │
├──────────────────────────┬───────────────────────┬─────────────────────┤
│ Componente               │ Tecnología            │ Justificación       │
├──────────────────────────┼───────────────────────┼─────────────────────┤
│ Lenguaje                 │ Kotlin 2.0+           │ Seguridad nula,     │
│                          │                       │ Coroutines y K2     │
│ UI Móvil / Tablet        │ Jetpack Compose M3    │ Declarativo, fluido │
│ UI Android TV            │ Compose for TV (TV-M3)│ Optimizado para     │
│                          │                       │ D-Pad y 10-foot UI  │
│ Motor Multimedia         │ AndroidX Media3       │ Estándar Google con │
│                          │ (ExoPlayer 1.4+)      │ soporte HLS/TS/DASH │
│ Red HTTP                 │ OkHttp 4 + Retrofit 2 │ Pooling, timeouts y │
│                          │                       │ headers seguros     │
│ Serialización JSON       │ Kotlinx Serialization │ Ultrarrápido, cero  │
│                          │                       │ reflection en RAM   │
│ Base de Datos Local      │ Room 2.6+ con FTS5    │ Persistencia offline│
│                          │                       │ y búsqueda veloz    │
│ Paginación Masiva        │ Paging 3 Compose      │ Evita OOM en listas │
│                          │                       │ de 50K+ elementos   │
│ Carga de Imágenes        │ Coil 3.0+             │ Downsampling exacto │
│                          │                       │ y caché en disco    │
│ Credenciales Seguras     │ DataStore Preferences │ Encriptación sin    │
│                          │ + Android Keystore    │ texto plano         │
│ Tareas en Segundo Plano  │ WorkManager           │ Sincronización EPG  │
│                          │                       │ sin bloquear UI     │
│ Inyección Dependencias   │ Kotlin Direct Factory │ Ligero, sin KAPT ni │
│                          │ / AppContainer        │ overhead de Dagger  │
└──────────────────────────┴───────────────────────┴─────────────────────┘
```

---

## 5. Fase 3 — Arquitectura de Módulos (Clean Architecture Compartida)

Para garantizar que un solo core soporte Phone, Tablet y TV sin duplicar lógica de negocio, se adopta la siguiente estructura modular:

### 5.1 Diagrama de Arquitectura Unificada

```
                          ┌───────────────────────────┐
                          │   LELOUCH ANDROID APP     │
                          └─────────────┬─────────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 ▼                                             ▼
  ┌─────────────────────────────┐               ┌─────────────────────────────┐
  │  :feature-presentation-tv   │               │:feature-presentation-mobile │
  │  (Android TV / Google TV)   │               │   (Phone & Tablet Touch)    │
  │  - TvMaterial3 / D-Pad Nav  │               │   - Material3 / Touch Gestures│
  │  - Xbox-Style Rails         │               │   - BottomNav & Adaptive Rail│
  │  - Live TV Xbox Rails           │               │   - Fullscreen Landscape Rot │
  └──────────────┬──────────────┘               └──────────────┬──────────────┘
                 │                                             │
                 └──────────────────────┬──────────────────────┘
                                        ▼
                          ┌───────────────────────────┐
                          │   :core:designsystem      │
                          │   - LelouchColors         │
                          │   - LelouchTypography     │
                          │   - LelouchTheme (Mobile) │
                          │   - LelouchTvTheme (TV)   │
                          └─────────────┬─────────────┘
                                        │
                                        ▼
                          ┌───────────────────────────┐
                          │       :core:domain        │
                          │   - UseCases (Live/VOD)   │
                          │   - Domain Models         │
                          │   - ParentalControl Policy│
                          └─────────────┬─────────────┘
                                        │
                 ┌──────────────────────┼──────────────────────┐
                 ▼                      ▼                      ▼
  ┌─────────────────────────────┐┌──────────────┐┌────────────────────────────┐
  │         :core:data          ││ :core:player ││       :core:database       │
  │  - XtreamRepository         ││ - Media3     ││ - Room Database            │
  │  - M3uRepository            ││ - StreamDet. ││ - FTS5 Search Tables       │
  │  - EpgRepository            ││ - Lifecycle  ││ - Dao (Channels/VOD/Hist)  │
  └──────────────┬──────────────┘└──────────────┘└────────────────────────────┘
                 │
                 ▼
  ┌─────────────────────────────┐
  │        :core:network        │
  │  - OkHttpClient (Whitelisted│
  │    User-Agent, No-Leak)     │
  │  - Retrofit XtreamApi       │
  │  - Secure URL Builders      │
  └─────────────────────────────┘
```

---

## 6. Fase 4 — Estrategia de UI Adaptativa (Phone vs Tablet vs TV)

La aplicación detectará en tiempo de ejecución las capacidades de pantalla y hardware para desplegar la UI idónea:

```kotlin
// Determinación precisa del entorno de ejecución
val isTvDevice = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
val configuration = LocalConfiguration.current
val isTablet = configuration.screenWidthDp >= 600 && !isTvDevice
```

1. **Android TV (Modo Leanback 10-Foot UI):**
   * Orientación bloqueada en **Landscape**.
   * Ausencia total de gestos touch; navegación 100% por eventos `KeyEvent.KEYCODE_DPAD_*`.
   * Menú lateral izquierdo auto-ocultable que se despliega al mover el foco a la izquierda.
   * Elementos interactivos con reborde de selección cian (`#00E5FF`) y ampliación de escala de 8% a 10%.
2. **Android Phone:**
   * Orientación predeterminada **Portrait**; rotación automática a **Landscape Fullscreen** al iniciar la reproducción o girar el móvil.
   * Navegación por barra inferior estándar de 4 pestañas: *Inicio, En Vivo, Películas, Series*.
   * Buscador flotante siempre accesible y gestos de deslizamiento (*swipe*).
3. **Android Tablet:**
   * Layout responsivo de 2 columnas mediante `NavigationRail` en el borde izquierdo.
   * Espacio aprovechado para vistas Maestro-Detalle (ej. Lista de temporadas a la izquierda, capítulos a la derecha).

---

## 7. Fase 5 — Especificación Visual de Pantallas Clave (Android TV)

### 7.0 Arquetipo Visual Central: "Spotlight Hero + Top Navigation + Carruseles Dinámicos"
Basado y alineado con la **referencia visual aprobada** (interfaz cinemática estilo *EveryCine / Demon Slayer / Xbox Dashboard*):

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ [LC] LELOUCH       Inicio    En Vivo    Películas    Series    Animes    Favoritos        🔍   👤   │
│                                                                                                  │
│       DEMON SLAYER                                                                               │
│       KIMETSU NO YAIBA                                                                           │
│       🔴 EN VIVO / 4K UHD   ★ 9.2   2024   Acción / Fantasía                                     │
│       Tanjiro y sus aliados emprenden una nueva batalla decisiva contra las lunas superiores...  │
│                                                                                                  │
│       [▶ Reproducir / Sintonizar (OK)]     [+ Mi Lista]     [ℹ Más Información]                  │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Em Destaque / Continuar Viendo                                                                   │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐       │
│ │ [FOCO ACTIVO] │ │               │ │               │ │               │ │               │       │
│ │     DUNA      │ │ THE LAST OF US│ │  VINGADORES   │ │GODZILLA E KONG│ │STRANGER THINGS│  ...  │
│ │  PARTE DOIS   │ │               │ │   ULTIMATO    │ │               │ │               │       │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘       │
│ ⚽ Deportes en Directo / Canales Populares                                                       │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐       │
│ │ [ESPN HD]     │ │ [Fox Sports]  │ │ [TyC Sports]  │ │ [DirecTV Sp]  │ │ [GolTV HD]    │  ...  │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘       │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### Componentes del Arquetipo Visual:
1. **Barra de Navegación Superior Fina (Top Navigation Bar):**
   * **Izquierda:** Isotipo y marca LELOUCH en tipografía moderna blanca y cian neón (#00E5FF).
   * **Centro:** Pestañas horizontales principales (Inicio, En Vivo, Películas, Series, Animes, Favoritos). Al presionar ARRIBA con el D-pad desde cualquier riel, el usuario sube a la barra superior para cambiar de sección rápidamente.
   * **Derecha:** Accesos directos a Buscar (🔍) y Perfil / Ajustes (👤).
2. **Spotlight Hero Cinemático (50% a 55% superior):**
   * **Fondo:** Imagen panorámica (*backdrop*) en alta definición que abarca el ancho completo, con máscara de degradado vertical suave hacia el negro #02070D.
   * **Título Imponente:** Tipografía estilizada de gran tamaño (o logotipo oficial en PNG transparente si está disponible en la metadata).
   * **Badges y Metadatos:** Píldoras con calidad (4K UHD, FHD, HDR), calificación por estrellas, año y géneros.
   * **Sincronización Reactiva:** Al navegar con el D-pad a izquierda o derecha en el carrusel inferior, el fondo y el título del Hero cambian con un *crossfade* suave de 200ms sin congelar la app.
3. **Carruseles de Contenido Inferiores (Horizontal Rails):**
   * Encabezados de categoría claros (*"Em Destaque"*, *"Continuar Viendo"*, *"En Tendencia"*, etc.).
   * Tarjetas redondeadas (radio de curvatura de 12dp) con imágenes nítidas precargadas vía Coil con downsampling.
   * **Efecto de Foco D-Pad:**
     * La tarjeta enfocada escala al 108%.
     * Contorno brillante en cian Lelouch (#00E5FF, 2.5dp) con resplandor glow sutil.
     * Las tarjetas no enfocadas permanecen ligeramente atenuadas (opacidad 85%) para guiar naturalmente la vista del usuario a varios metros de distancia.

---

### 7.1 Catálogo de Películas y Series (Implementación del Arquetipo)
* **Películas:** Tarjetas verticales con proporción 2:3.
* **Series:** Tarjetas verticales 2:3 en los carruseles generales; al abrir una serie, pantalla de detalle con selector horizontal de temporadas y riel de episodios apaisados 16:9 con miniatura y sinopsis.
* **Memoria de Foco Exacta:**
  ```kotlin
  data class FocusMemoryState(val railId: String, val itemIndex: Int, val scrollOffset: Int)
  ```
  Al ingresar a los detalles o al reproductor y presionar BACK, la app restaura exactamente la misma tarjeta y carrusel donde estaba el usuario.

---

### 7.2 TV en Vivo — Adaptación del Arquetipo "Xbox Live Dashboard / Carruseles Cinemáticos"

Se descarta el antiguo diseño de 3 paneles (categorías-lista-reproductor). La televisión en vivo adopta este mismo lenguaje visual cinemático:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ [LC] LELOUCH       Inicio    [EN VIVO]    Películas    Series    Favoritos        🔍   👤        │
│                                                                                                  │
│ 🔴 SEÑAL EN DIRECTO: ESPN HD — UEFA CHAMPIONS LEAGUE (Cuartos de Final)                          │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━●─────────────── 65' (14:00 - 16:15)                                    │
│ 🔴 LIVE  1080p 60fps  ⚽ DEPORTES  |  ⏳ Siguiente: SportsCenter en Vivo (16:15)                  │
│                                                                                                  │
│ [▶ Ver en Pantalla Completa (OK)]     [⭐ Favorito (Mantener OK)]     [📅 Guía EPG Completa]      │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 🔥 Últimos Sintonizados (Zapping Rápido)                                                         │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐       │
│ │ [ESPN HD]     │ │ [Fox Sports]  │ │ [TyC Sports]  │ │ [DirecTV Sp]  │ │ [HBO Max HD]  │  ...  │
│ │ 🔴 En Vivo    │ │ 🔴 En Vivo    │ │ 🔴 En Vivo    │ │ 🔴 En Vivo    │ │ 🔴 En Vivo    │       │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘       │
│ ⭐ Canales Favoritos                                                                             │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐       │
│ │ [GolTV HD]    │ │ [Star Channel]│ │ [CNN Noticias]│ │ [TNT Sports]  │ │ [Warner TV]   │  ...  │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘       │
│ ⚽ Deportes en Vivo                                                                              │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐       │
│ │ [ESPN 2 HD]   │ │ [DAZN 1]      │ │ [TUDN HD]     │ │ [Win Sports]  │ │ [Movistar Dep]│  ...  │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘       │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### Comportamiento de Video de Fondo Activo al Navegar (Live Background Zapping):
1. **Video en Vivo como Fondo Cinemático:**
   * El canal seleccionado se reproduce **en tiempo real en la capa de fondo** de la pantalla, con un degradado sutil oscurecido (*vignette scrim* en `#02070D` al 70%) en la parte inferior para que las tarjetas del carrusel floten con perfecto contraste y legibilidad.
   * La mitad superior muestra la señal nítida y los metadatos dinámicos del programa actual (logo, título del evento, barra de progreso y badges).
2. **Conmutación Fluida de Fondo al Mover el Carrusel:**
   * A medida que el usuario recorre las tarjetas con el D-pad (`IZQUIERDA / DERECHA` o `ARRIBA / ABAJO` entre categorías), **el video de fondo conmuta automáticamente para mostrar la transmisión del canal enfocado**, tal como ocurre en el arquetipo visual.
   * **Debounce inteligente de 300ms:** Al desplazarse velozmente sobre múltiples tarjetas, la metadata (logo, nombre y EPG) cambia de inmediato a 60fps desde la base de datos local Room; en cuanto el usuario se detiene 300ms sobre un canal, ExoPlayer sintoniza la señal de video de fondo suavemente sin saturar la red ni el servidor Xtream.
3. **Pase Instantáneo a Pantalla Completa (Cero Latencia):**
   * Al presionar `OK`, el video **no se reinicia**: como la señal ya se encuentra reproduciéndose en el fondo, la interfaz simplemente desvanece los carruseles en una transición de 150ms hacia pantalla completa (100% Fullscreen).
   * Al presionar `BACK` desde la pantalla completa, los carruseles vuelven a emerger flotando sobre el video en vivo sin cortar la transmisión.
4. **Mini-Guía Carrusel HUD en Pantalla Completa:**
   * Estando en pantalla completa, presionar `ARRIBA` o `ABAJO` despliega un carrusel translúcido flotante en la parte inferior para seguir zappeando entre canales con la señal actual visible y sonando de fondo.


---

## 8. Fase 6 — Motor Multimedia Nativo (Media3 / ExoPlayer)

Se abandona el ecosistema de navegadores (Hls.js / mpegts.js) en favor del motor nativo Android de más alto rendimiento:

1. **Gestión de Streams:**
   * Utilizar `androidx.media3.exoplayer.ExoPlayer` preconfigurado con:
     * `DefaultLoadControl` optimizado para baja latencia en Live TV (buffer mínimo de 2.5s).
     * `DefaultTrackSelector` para selección automática de mejor pista de audio y resolución.
     * `DefaultRenderersFactory` con aceleración por hardware MediaCodec activada (`EXTENSION_RENDERER_MODE_PREFER`).
2. **Compatibilidad de Formatos:**
   * Detección dinámica de contenedor:
     * `.m3u8` o flujos HLS → `HlsMediaSource.Factory`.
     * `.ts` o flujos MPEG-TS directos → `ProgressiveMediaSource.Factory` con `TsExtractor`.
     * `.mp4` / `.mkv` (Películas y Series) → `ProgressiveMediaSource.Factory`.
3. **Control Estricto del Ciclo de Vida (Lifecycle):**
   * El reproductor se instancia y libera vinculándose a `LifecycleOwner`.
   * En eventos `onStop` / `onPause`, se detiene el buffer y se liberan las superficies de video para evitar fugas de memoria o que el audio continúe sonando en segundo plano.

---

## 9. Fase 7 — Manejo de Red, Seguridad y Almacenamiento

1. **Seguridad y Cero Fugas de Credenciales (Sanitización):**
   * Creación de un interceptor OkHttp `SanitizedLoggingInterceptor` que automáticamente enmascare contraseñas y parámetros sensibles en Logcat:
     `http://servidor:8880/live/usuario/**********/stream.ts`
   * Almacenamiento de credenciales mediante `EncryptedSharedPreferences` / `DataStore` cifrado con claves generadas en el **Android Keystore System**.
2. **User-Agent Whitelisted:**
   * Configuración de OkHttp para identificarse con encabezados autorizados por proveedores IPTV (`IPTVSmartersPlayer / VLC`), previniendo bloqueos `HTTP 403 Forbidden`.
3. **Persistencia con Room y Paging 3:**
   * Esquema SQLite con tablas indexadas para `channels`, `movies`, `series`, `categories`, `history`, `favorites`.
   * Implementación de tabla virtual `FTS5` (`movies_fts`, `channels_fts`) para permitir búsquedas instantáneas por aproximación (*fuzzy search*) en catálogos de más de 80,000 registros en menos de 10ms.

---

## 10. Hoja de Ruta de Implementación (12 Sprints)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    HOJA DE RUTA GENERAL LELOUCH ANDROID                    │
├───────────┬────────────────────────────────────────────┬───────────────────┤
│ Sprint    │ Objetivo Principal                         │ Estado            │
├───────────┼────────────────────────────────────────────┼───────────────────┤
│ SPRINT 0  │ Investigación, Arquitectura y Master Plan  │ COMPLETADO        │
│ SPRINT 1  │ Base del Proyecto, Design System, Nav Core │ Siguiente         │
│ SPRINT 2  │ Network, Auth Xtream/M3U y Room Database   │ Planificado       │
│ SPRINT 3  │ Motor de Video Media3 y Reproductor Base   │ Planificado       │
│ SPRINT 4  │ Catálogo Películas (Xbox Rails + Detalle)  │ Planificado       │
│ SPRINT 5  │ Catálogo Series (Temporadas y Capítulos)   │ Planificado       │
│ SPRINT 6  │ Búsqueda Local de Alto Rendimiento (FTS5)  │ Planificado       │
│ SPRINT 7  │ Favoritos, Historial y Continuar Viendo    │ Planificado       │
│ SPRINT 8  │ EPG (Guía Electrónica Now/Next + Timeline) │ Planificado       │
│ SPRINT 9  │ UI Táctil Adaptativa (Móvil y Tablet)      │ Planificado       │
│ SPRINT 10 │ Pulido D-Pad Android TV y Memoria de Foco  │ Planificado       │
│ SPRINT 11 │ Optimización Memoria, Seguridad y QA       │ Planificado       │
│ SPRINT 12 │ Compilación y Generación de APK Final      │ Planificado       │
└───────────┴────────────────────────────────────────────┴───────────────────┘
```

---

## 11. Conclusión y Solicitud de Aprobación para Sprint 1

El **Sprint 0** ha concluido satisfactoriamente con la totalidad de los requisitos de investigación analizados, la arquitectura de Clean Architecture unificada diseñada, la experiencia visual adaptada a móvil y televisor especificada, y la compatibilidad con LELOUCH Web preservada.

> [!NOTE]
> Conforme al mandato estricto de este sprint:
> * **No se ha modificado la aplicación web existente.**
> * **No se ha creado aún el proyecto en `/lelouch-android/`.**
> * **No se ha escrito código de producción.**
> 
> **Detención en este punto:** Quedo a la espera de tu revisión y aprobación de este Master Plan para dar inicio al **SPRINT 1 (Creación de la estructura del proyecto Android, Design System Lelouch y Room Database)**.
