# GRAFO DETERMINISTA DE NAVEGACIÓN D-PAD PARA MOVIES (XIAOMI TV BOX)

## 1. Arquitectura del Grafo Explícito

Para eliminar la dependencia en la búsqueda espacial euclidiana 2D (la cual falla ante virtualización de listas y anchos dispares), se implementó un grafo bidireccional determinista mediante `FocusRequester` y `Modifier.focusProperties`:

```text
                        ┌───────────────────────────────┐
                        │   TOP NAV (Pestaña Películas) │ ◄── [Anchor: navMoviesAnchor]
                        └───────────────▲───────────────┘
                                        │ UP
                                        │
                                        ▼ DOWN
                        ┌───────────────────────────────┐
                        │    HERO SPOTLIGHT BUTTONS     │ ◄── [Anchor: heroPlayAnchor]
                        │   [Ver Película] [Más Detalles]│
                        └───────────────▲───────────────┘
                                        │ UP (desde cualquier botón o tarjeta)
                                        │
                                        ▼ DOWN (salta a recentMovieLastIndex)
                        ┌───────────────────────────────┐
                        │   RIEL 1: PELÍCULAS RECIENTES  │
                        │  [#0] ◄─► [#1] ◄─► ... ◄─► [#14] │ ◄── [Memoria: recentMovieLastIndex]
                        └───────────────▲───────────────┘
                                        │ UP (salta a recentMovieLastIndex)
                                        │
                                        ▼ DOWN (salta a topRatedMovieLastIndex)
                        ┌───────────────────────────────┐
                        │   RIEL 2: MÁS VALORADAS (TOP) │
                        │  [#0] ◄─► [#1] ◄─► ... ◄─► [#8]  │ ◄── [Memoria: topRatedMovieLastIndex]
                        └───────────────▲───────────────┘
                                        │ DOWN = FocusRequester.Cancel (Límite Inferior)
                                        ▼
                                   [NO ESCAPE]
```

---

## 2. Reglas de Transición y Memoria por Riel

### Regla 1: Memoria de Riel Independiente (Paso 6)
Cada riel registra en tiempo real el índice de la última tarjeta que tuvo foco:
- Riel Recientes: `recentMovieLastIndex`
- Riel Más Valoradas: `topRatedMovieLastIndex`

**Comportamiento resultante**:
1. El usuario navega en Riel Recientes hasta la tarjeta `#14`.
2. Presiona `DPAD_DOWN`:
   - En lugar de proyectar un rayo hacia abajo (donde la tarjeta #14 de abajo no existe en memoria), salta explícitamente a `getTopRatedRequester(topRatedMovieLastIndex)`.
   - Si en el Riel 2 el usuario estaba en la tarjeta `#0`, cae exactamente en `#0`.
3. El usuario en Riel 2 navega a la derecha hasta `#8`.
4. Presiona `DPAD_UP`:
   - El Riel 2 salta explícitamente a `getRecentRequester(recentMovieLastIndex)`.
   - Como `recentMovieLastIndex` sigue valiendo `14`, el foco aterriza **inmediatamente y sin pérdida** en la tarjeta `#14` de Recientes.

### Regla 2: Retorno determinista al Hero (Paso 5)
- Presionar `DPAD_UP` desde **cualquier** tarjeta del Riel 1 (incluso la `#14` en `X = 850dp`) tiene asignado:
  ```kotlin
  up = focusTracker.heroPlayAnchor
  ```
  El foco se transfiere de inmediato al botón "Ver Película" del Hero, eliminando el fallo geométrico donde no había botones interactivos en esa columna vertical.

### Regla 3: Navegación en Límites / Edge Navigation (Paso 7)
- **Límite Izquierdo (Tarjeta #0)**: `left = FocusRequester.Cancel`. No se pierde el foco hacia la izquierda.
- **Límite Derecho (Última Tarjeta)**: `right = FocusRequester.Cancel`. Permanece en la última tarjeta.
- **Límite Inferior (Riel Más Valoradas)**: `down = FocusRequester.Cancel`. Nunca produce `FOCUS = NONE`.

---

## 3. Corrección de Nodos Duplicados en Modifiers (Paso 8)

Se eliminó la duplicidad de nodos focusables en `TvPosterCard.kt` y `TvChannelCard.kt`.

**Antes (Erróneo)**:
```kotlin
Box(
    modifier = modifier
        .focusable()             // <--- Nodo de foco 1
        .onFocusChanged { ... }
        .clickable { onClick() } // <--- Nodo de foco 2 (duplicado)
)
```

**Ahora (Corregido y Unificado)**:
```kotlin
val interactionSource = remember { MutableInteractionSource() }

Box(
    modifier = modifier
        .onFocusChanged {
            isFocused = it.isFocused
            if (it.isFocused) onFocused()
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onClick() } // <--- Único nodo de foco con interacción limpia
)
```

---

## 4. Desacoplamiento de Pestañas Superiores (Paso 10)

En `TvHomeScreen.kt`, se separó la variable observada del foco de la variable de selección:
```kotlin
var focusedTopTab by remember { mutableIntStateOf(2) }
var selectedTopTab by remember { mutableIntStateOf(2) }

Tab(
    modifier = tabModifier,
    selected = selectedTopTab == index,
    onFocus = { focusedTopTab = index },     // Solo registra posición de foco
    onClick = { selectedTopTab = index }      // Recomposición solo al presionar OK/CENTER
)
```
Esto erradica la destrucción y recreación en bucle de la `TvLazyColumn` durante la navegación horizontal en la barra de navegación.

---

## 5. Herramientas de Verificación Física en Xiaomi TV Box

### A. Focus Debug HUD en Pantalla (Paso 3)
Visible en la esquina superior derecha del televisor:
- **KEY**: Última tecla física presionada (`DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`, `BACK`).
- **ZONE**: Zona activa (`TOP_NAV`, `HERO`, `RAIL_RECENT`, `RAIL_TOP_RATED`).
- **FOCUS**: ID único del nodo enfocado (ej: `movies_recent_14`, `hero_play`, `nav_movies`).
- **INDEX**: `[Row 1 | Card #13]`.
- **RAIL MEMORY**: Registro en vivo de los últimos índices por fila.

### B. Laboratorio Aislado: DpadFocusLabScreen (Paso 11)
Disponible directamente seleccionando la nueva pestaña **"🧪 Lab"** en el Top Nav (o con `selectedTopTab == 6`):
- 0 llamadas de red (sin Xtream).
- 0 imágenes Coil.
- 0 instancias de ExoPlayer / Room.
- Matriz pura de botones:
  - `NAV`: `[NAV_LAB]` y `[NAV_BACK]`
  - `ROW A`: `[A1]` a `[A5]`
  - `ROW B`: `[B1]` a `[B5]`
  - `ROW C`: `[C1]` a `[C5]`
Permite certificar que el hardware de Xiaomi TV Box y el protocolo Bluetooth del control XMRM-M3 responden al 100% sin latencia.

---

## 6. Secuencia Obligatoria de Validación Física (Criterio de Éxito)

En el televisor Xiaomi TV Box:
1. Iniciar la aplicación (abre directamente en pestaña Películas con el HUD activo).
2. Ejecutar la secuencia de prueba:
   - `RIGHT x 14` (llega a la película 14 de Recientes)
   - `DOWN` (debe saltar limpiamente al Riel Top Rated en Card 0)
   - `RIGHT x 8` (avanza a la película 8 de Top Rated)
   - `UP` (debe saltar exactamente a la película 14 de Recientes)
   - `UP` (debe saltar al botón Hero "Ver Película")
   - `UP` (debe saltar a la pestaña "Películas" en Top Nav)
   - `DOWN` -> `DOWN` -> `DOWN` (no debe perderse ni pasar a `FOCUS = NONE`)
3. Verificar en HUD que `FOCUS` nunca muestre `NONE` y que ningún evento quede atrapado.
