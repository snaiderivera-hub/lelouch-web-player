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

## 4. Arquitectura de Navegación Superior y 4 Estados Visuales (FOCUSED != SELECTED)

Para erradicar la trampa de foco interna y la falta de indicación visual de `androidx.tv.material3.TabRow`, se implementó una barra superior mediante `Row` estándar de Compose con `FocusRequester` deterministas (`navRequesters[0..6]`) y 4 estados visuales de alto contraste:

```text
ESTADO 1: FOCUSED + SELECTED (Pestaña actual con foco activo)
- Fondo: Cyan Neón Sólido (#00E5FF)
- Borde: Blanco Puro 2.5dp
- Texto: Negro (#000000), FontWeight.Black

ESTADO 2: FOCUSED (Pestaña destino con foco, pero no seleccionada)
- Fondo: Superficie Oscura (#1F293D)
- Borde: Cyan Neón 2.5dp
- Texto: Blanco Puro (#FFFFFF), FontWeight.Bold

ESTADO 3: SELECTED (Pantalla activa, pero foco en Hero o Rieles)
- Fondo: Cyan Translúcido (alpha = 0.18f)
- Borde: Cyan Translúcido 1.5dp (alpha = 0.6f)
- Texto: Cyan Neón (#00E5FF), FontWeight.Bold
- Indicador: Punto Cyan de 6dp

ESTADO 4: NORMAL (Inactivo y sin foco)
- Fondo: Transparente
- Borde: Transparente 1.0dp
- Texto: Gris Secundario (#94A3B8), FontWeight.Medium
```

### Separación de Foco y Selección:
- `isThisTabFocused`: Actualizado inmediatamente por `onFocusChanged`. Muestra el cursor del D-pad sin cambiar de pantalla.
- `selectedTopTab`: Actualizado **únicamente** al presionar `DPAD_CENTER`, `ENTER` o `OK`.

---

## 5. Herramientas de Verificación Física en Xiaomi TV Box

### A. Focus Debug HUD de Ciclo Completo
Visible en la esquina superior derecha del televisor, **exclusivamente condicionado a compilaciones DEBUG** (`BuildConfig.DEBUG`):

```text
┌────────────────────────────────────────────────────────┐
│ XIAOMI D-PAD TRACE                             MOVIES  │
│ KEY: DPAD_UP                          CONSUMED: TRUE   │
│ ┌────────────────────────────────────────────────────┐ │
│ │ RESULT: SUCCESS                                    │ │ ◄── [Código de Colores Dinámico]
│ │ BEFORE: hero_play [HERO | #0]                      │ │
│ │ TARGET: nav_movies [TOP_NAV | #2]                  │ │
│ │ AFTER:  nav_movies [TOP_NAV | #2]                  │ │
│ └────────────────────────────────────────────────────┘ │
│ MEMORY:  RECENT=#0 | TOP_RATED=#0                      │
└────────────────────────────────────────────────────────┘
```

#### Estados Estrictos de `RESULT`:
- `SUCCESS` (Verde `#10B981`): Confirmado **únicamente** cuando `onFocusChanged` certifica que el Composable destino obtuvo foco real (`isFocused = true`).
- `UNCHANGED` (Amarillo `#FFD700`): El evento no desplazó el foco (el foco permaneció en el elemento de origen).
- `TARGET_NOT_COMPOSED` (Naranja `#FF8C00`): El destino solicitado en `TvLazyRow` no ha sido compuesto por Compose (virtualización fuera de memoria).
- `FOCUS_LOST` (Rojo `#EF4444`): Ningún control en la jerarquía retuvo o ganó foco (`currentTag = NONE`).
- `REQUEST_FAILED` (Magenta `#EC4899`): El foco se movió a un elemento distinto del destino esperado.

---

## 6. Secuencia Obligatoria de Validación Física (Contrato MOVIES → TOP NAV)

Empezar en: **MOVIES / RECENT CARD #0**

1. `UP`
   - Esperado: `HERO / hero_play`
   - HUD: BEFORE: `movies_recent_...`, TARGET: `hero_play`, AFTER: `hero_play`, RESULT: `SUCCESS`
2. `UP`
   - Esperado: `TOP_NAV / nav_movies` (Destino explícito, NO `nav_home`, NO `nav_live`)
   - HUD: BEFORE: `hero_play`, TARGET: `nav_movies`, AFTER: `nav_movies`, RESULT: `SUCCESS`
   - Visual: Tab "Películas" en estado **FOCUSED + SELECTED** (Fondo Cyan, Texto Negro, Borde Blanco 2.5dp).
3. `LEFT`
   - Esperado: `TOP_NAV / nav_live`
   - Visual: "En Vivo" = **FOCUSED** (Borde Cyan 2.5dp, Texto Blanco), "Películas" = **SELECTED** (Fondo Cyan suave + punto cyan).
4. `LEFT`
   - Esperado: `TOP_NAV / nav_home`
   - Visual: "Inicio" = **FOCUSED**, "Películas" = **SELECTED**.
5. `RIGHT`
   - Esperado: `TOP_NAV / nav_live`
6. `RIGHT`
   - Esperado: `TOP_NAV / nav_movies`
   - Visual: "Películas" = **FOCUSED + SELECTED**.
7. `RIGHT`
   - Esperado: `TOP_NAV / nav_series`
   - Visual: "Series" = **FOCUSED**, "Películas" = **SELECTED**.
8. `LEFT`
   - Esperado: `TOP_NAV / nav_movies`
   - Visual: "Películas" = **FOCUSED + SELECTED**.
9. `DOWN`
   - Esperado: `HERO / hero_play`
   - Visual: Botón "Ver Película" enfocado en Cyan con borde blanco 2.5dp.
10. `DOWN`
   - Esperado: `RAIL_RECENT / recentMovieLastIndex` (#0 o último recordado).

Cada movimiento requiere **EXACTAMENTE UNA pulsación**. El elemento enfocado permanece **VISUALMENTE IDENTIFICABLE** en todo momento.
