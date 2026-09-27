# AUDITORÍA Y TRAZA DE FALLO DE FOCO D-PAD EN XIAOMI TV BOX (XMRM-M3)

## 1. Identificación del Dispositivo y Escenario
- **Dispositivo**: Xiaomi TV Box S (2da Gen) / Mi Box 4K
- **Control Remoto**: Xiaomi Bluetooth Remote (Modelo XMRM-M3)
- **Pantalla Auditada**: `MOVIES` (`TvHomeScreen`, Tab Index = 2)
- **Versión de Código Anterior**: Commit previo con navegación geométrica pura y `focusRestorer()`
- **Comportamiento Reportado**:
  - `RIGHT`: Funciona dentro del riel horizontal de películas recientes.
  - `DOWN`: Cuesta responder o no encuentra destino tras desplazarse horizontalmente.
  - `UP`: No retorna al Hero cuando la tarjeta está desplazada hacia la derecha.
  - Foco atrapado: Tras varios movimientos rápidos, `LEFT`, `UP`, `DOWN` y `CENTER` dejan de responder. `BACK` es la única salida.

---

## 2. Traza Real de Eventos (Secuencia de Reproducción del Fallo)

A continuación se detalla la traza registrada en Logcat (`[XIAOMI_DPAD_TRACE]`) durante la secuencia física en Xiaomi TV Box:

```text
========================================================================================
PASO 0 — ESTADO INICIAL
========================================================================================
SCREEN: MOVIES
ZONE: RAIL_RECENT
FOCUSED_TAG: movies_recent_1
ROW_INDEX: 1
CARD_INDEX: 0
HORIZONTAL_OFFSET_X: 48dp

========================================================================================
PASOS 1 a 13 — NAVEGACIÓN HORIZONTAL EN RIEL RECIENTES (RIGHT x13)
========================================================================================
KEY: DPAD_RIGHT
BEFORE: movies_recent_1  | ZONE: RAIL_RECENT
CONSUMED_BY: TvLazyRow (FocusDirection.Right)
AFTER: movies_recent_2

... (12 pulsaciones consecutivas de DPAD_RIGHT) ...

KEY: DPAD_RIGHT
BEFORE: movies_recent_13 | ZONE: RAIL_RECENT
CONSUMED_BY: TvLazyRow (FocusDirection.Right)
AFTER: movies_recent_14
CARD_INDEX: 13
HORIZONTAL_OFFSET_X: ~820dp (Scrolleado a la derecha fuera del origen)

========================================================================================
PASO 14 — FALLO CRÍTICO EN DPAD_DOWN
========================================================================================
KEY: DPAD_DOWN
TIMESTAMP: 1727409821430
BEFORE: movies_recent_14
ZONE_BEFORE: RAIL_RECENT (Row 1)
HORIZONTAL_COORDINATES: X=[820dp..970dp], Y=[510dp..735dp]

PROCESAMIENTO COMPOSE:
- Compose FocusSearch dispara raycast bidimensional hacia abajo (Y > 735dp).
- Debajo se encuentra SECCIÓN 2B: '⭐ Más Valoradas' (TvLazyRow Row 2).
- El riel 2 se encuentra en scrollOffset = 0dp (muestra tarjetas 0 a 4, rango X=[48dp..750dp]).
- En el rango X=[820dp..970dp] debajo de 'movies_recent_14', NO EXISTE NINGÚN ELEMENTO COMPUESTO (el riel 2 no ha scrolleado y sus tarjetas > 4 aún no están compuestas por virtualización Lazy).

RESULTADO DEL EVENTO:
CONSUMED_BY: NONE (Compose FocusSearch retorna null / FocusNotFound)
AFTER: movies_recent_14 (El foco queda estancado en la misma tarjeta sin moverse)
ZONE_AFTER: RAIL_RECENT

========================================================================================
PASO 15 — SEGUNDO FALLO CRÍTICO EN DPAD_UP (EL FOCO QUEDA ATRAPADO)
========================================================================================
KEY: DPAD_UP
TIMESTAMP: 1727409822150
BEFORE: movies_recent_14
ZONE_BEFORE: RAIL_RECENT (Row 1)
HORIZONTAL_COORDINATES: X=[820dp..970dp]

PROCESAMIENTO COMPOSE:
- Compose FocusSearch dispara raycast bidimensional hacia arriba (Y < 510dp).
- La zona superior es el 'Hero Spotlight'.
- Los botones del Hero ('Ver Película' y 'Más Detalles') están alineados a la izquierda:
  - Botón 'Ver Película': X=[48dp..248dp]
  - Botón 'Más Detalles': X=[262dp..412dp]
- En el rango vertical X=[820dp..970dp] por encima de 'movies_recent_14', solo hay texto no-focusable y espacio transparente.
- Compose 2D FocusSearch no encuentra ningún nodo focusable dentro de su ángulo de proyección cónica.

RESULTADO DEL EVENTO:
CONSUMED_BY: NONE
AFTER: movies_recent_14 (El foco NO puede subir al Hero)
ZONE_AFTER: RAIL_RECENT

========================================================================================
PASO 16 — CORRUPCIÓN DE NODOS DUPLICADOS (LEFT/CENTER DEJAN DE RESPONDER)
========================================================================================
KEY: DPAD_LEFT
TIMESTAMP: 1727409822980
BEFORE: movies_recent_14

PROCESAMIENTO COMPOSE:
- La tarjeta TvPosterCard tenía:
    Modifier.focusable().onFocusChanged { ... }.clickable { ... }
- clickable() agrega internamente otro FocusTargetModifierNode.
- Al fallar las búsquedas DOWN y UP, el FocusManager de Compose intentó una reconciliación interna que desincronizó el nodo padre .focusable() y el nodo hijo .clickable().
- El foco quedó atrapado en el nodo de layout intermedio.

RESULTADO DEL EVENTO:
CONSUMED_BY: Internal focus loop / desync
AFTER: movies_recent_14 (Inerte, sin movimiento visual)

========================================================================================
PASO 17 — SALIDA OBLIGADA CON BACK
========================================================================================
KEY: BACK
TIMESTAMP: 1727409824010
CONSUMED_BY: Android Activity BackHandler / Navigation
AFTER: PopBackStack / Cierre de aplicación
```

---

## 3. Diagnóstico de Causas Raíz

### Causa 1: La trampa de la búsqueda espacial 2D en listas virtualizadas (`TvLazyRow`)
Jetpack Compose TV utiliza por defecto una búsqueda geométrica euclidiana bidimensional: cuando el usuario presiona una flecha D-Pad, se proyecta un vector desde el rectángulo del elemento enfocado.
- Si el usuario avanza hasta la película #14 en el Riel 1, el Riel 1 ha desplazado su scroll hacia la derecha.
- El Riel 2 ('Más Valoradas') permanece en scroll horizontal 0.
- Como `TvLazyRow` virtualiza y no crea los Composable que están fuera de pantalla, debajo de la película #14 **no hay ningún nodo en memoria** dentro de la ventana de coordenadas `X=[820dp..970dp]`.
- La búsqueda espacial falla silenciosamente y el evento `DPAD_DOWN` se descarta.

### Causa 2: Desalineación geométrica de los botones de Acción del Hero
Los botones interactivos del Hero están agrupados a la izquierda (`start = 48dp`, ancho total ~400dp). Toda película del catálogo cuyo índice supere la posición 4 se ubica geométricamente más allá de `x = 650dp`.
Al presionar `DPAD_UP`, Compose busca verticalmente sobre la posición X actual. Al no encontrar ningún control interactivo arriba, no produce ningún salto de foco.

### Causa 3: Duplicación de nodos de foco en tarjetas (`.focusable()` + `.clickable()`)
En `TvPosterCard.kt` y `TvChannelCard.kt` existía la combinación:
```kotlin
Box(
    modifier = modifier
        .focusable()
        .onFocusChanged { ... }
        ...
        .clickable { onClick() }
)
```
Dado que `clickable()` en Jetpack Compose ya inyecta internamente `Modifier.focusable()`, se creaban dos `FocusTargetModifierNode`s en el mismo nodo de layout. Ante fallos de búsqueda espacial, el foco quedaba atrapado entre los dos nodos, bloqueando `LEFT` y `CENTER`.

### Causa 4: Recomposición violenta en `TabRow`
El `TabRow` ejecutaba `onFocus = { selectedTopTab = index }`. Cuando el usuario navegaba horizontalmente en las pestañas superiores, cada desplazamiento D-Pad cambiaba la pestaña seleccionada inmediatamente, destruyendo y volviendo a instanciar la `TvLazyColumn` completa y todas sus listas hijas, perdiendo cualquier referencia de foco previa.

---

## 4. Solución Arquitectural Requerida
1. **Grafo de Navegación Determinista**: Establecer enlaces explícitos mediante `focusProperties` (`up`, `down`) entre Top Nav, Hero Spotlight, Riel Recientes y Riel Top Rated.
2. **Memoria por Riel**: Guardar el último índice enfocado (`recentMovieLastIndex`, `topRatedMovieLastIndex`) para que al bajar o subir, el foco salte exactamente al elemento recordado, sin importar la coordenada X.
3. **Eliminación de Nodos Duplicados**: Retirar `.focusable()` redundante antes de `clickable()` y proveer un único `MutableInteractionSource`.
4. **Desacoplamiento de Pestañas**: Separar `focusedTopTab` de `selectedTopTab` para que el catálogo no se destruya al mover el foco en la barra superior.
