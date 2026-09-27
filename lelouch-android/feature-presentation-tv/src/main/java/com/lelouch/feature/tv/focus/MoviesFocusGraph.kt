package com.lelouch.feature.tv.focus

import android.util.Log
import androidx.compose.ui.focus.FocusRequester

// ─────────────────────────────────────────────────────────────────────────────
// MOVIES FOCUS GRAPH — Single Owner Navigation
//
// Esta clase contiene ÚNICAMENTE la lógica de decisión de navegación para la
// pantalla MOVIES. Es pura (no depende de Compose UI) y puede testearse en
// unit tests sin un emulador o control remoto físico.
//
// OWNER ÚNICO: onPreviewKeyEvent en TvHomeScreen llama a `resolve()` y después
// ejecuta requestFocus() con el requester devuelto. Compose NO realiza ninguna
// búsqueda geométrica adicional porque el evento retorna `true` (consumido).
//
// NO EXTENDER a HOME, LIVE, SERIES, SEARCH, PLAYER ni MODALS todavía.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Destino de navegación calculado por el grafo.
 *
 * @param tag        Identificador semántico del elemento destino (coincide con los
 *                   tags registrados en FocusTracker).
 * @param zone       Zona de navegación a la que pertenece el destino.
 * @param index      Índice dentro de la zona (0 para TOP_NAV y HERO, posición en rail).
 * @param requester  FocusRequester real del composable destino. NULL indica que el
 *                   destino existe en el grafo pero el item aún no está compuesto
 *                   (TARGET_NOT_COMPOSED). El caller debe coordinar el scroll del
 *                   LazyListState antes de reintentar.
 */
data class NavTarget(
    val tag: String,
    val zone: TvFocusZone,
    val index: Int,
    val requester: FocusRequester?   // null → TARGET_NOT_COMPOSED
)

/** Señal especial: la tecla fue procesada pero el grafo dice "quédate donde estás". */
object NavCancel

/** Señal especial: esta tecla no pertenece al grafo de Movies (CENTER, BACK, etc.). */
object NavNotHandled

/**
 * Resultado sellado que devuelve [MoviesFocusGraph.resolve].
 */
sealed class NavResult {
    /** Hay un destino compuesto listo para recibir foco. */
    data class Ready(val target: NavTarget) : NavResult()

    /**
     * El destino existe en el grafo pero el índice solicitado no está compuesto
     * en el TvLazyRow todavía. El caller debe:
     *   1. Hacer scroll del LazyListState hacia [scrollToIndex].
     *   2. Esperar a que [FocusTracker.isComposed(zone, scrollToIndex)] sea true.
     *   3. Reintentar resolve() — si el resultado es Ready, ejecutar requestFocus().
     * El scroll es cancelable: si llega una nueva pulsación, el pending se descarta.
     */
    data class ScrollNeeded(
        val target: NavTarget,     // tag/zone/index del destino (requester aún null)
        val scrollToIndex: Int     // índice a revelar en el TvLazyRow
    ) : NavResult()

    /** La dirección no tiene destino en esta zona (borde): no mover foco, consumir evento. */
    object Cancel : NavResult()

    /** La tecla (CENTER/BACK/etc.) no es una dirección manejada por este grafo. */
    object NotHandled : NavResult()
}

/**
 * Grafo de navegación determinista para la pantalla MOVIES.
 *
 * @param tracker        FocusTracker compartido con TvHomeScreen.
 * @param navTabCount    Número real de tabs en la barra de navegación.
 * @param recentCount    Número de elementos en el rail RAIL_RECENT (displayMovies.size).
 * @param topRatedCount  Número de elementos en el rail RAIL_TOP_RATED.
 */
class MoviesFocusGraph(
    private val tracker: FocusTracker,
    private val navTabCount: Int,
    private val recentCount: Int,
    private val topRatedCount: Int
) {
    companion object {
        private const val TAG = "MOVIES_FOCUS_GRAPH"
    }

    /**
     * Calcula el [NavResult] para una pulsación direccional desde el estado actual
     * del [FocusTracker].
     *
     * @param key Nombre de la tecla: "DPAD_UP" | "DPAD_DOWN" | "DPAD_LEFT" | "DPAD_RIGHT"
     * @return [NavResult] que el caller (onPreviewKeyEvent) debe ejecutar.
     */
    fun resolve(key: String): NavResult {
        // Solo manejamos las cuatro direcciones
        if (key !in setOf("DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT")) {
            return NavResult.NotHandled
        }

        val zone  = tracker.currentZone
        val index = tracker.lastCardIndex

        val result: NavResult = when (key) {
            "DPAD_UP"    -> resolveUp(zone, index)
            "DPAD_DOWN"  -> resolveDown(zone, index)
            "DPAD_LEFT"  -> resolveLeft(zone, index)
            "DPAD_RIGHT" -> resolveRight(zone, index)
            else         -> NavResult.NotHandled
        }

        Log.d(TAG, "resolve: key=$key zone=${zone.label} index=$index → $result")
        return result
    }

    // ─────────────────────────────────────────────────────────────────
    // DPAD_UP
    // ─────────────────────────────────────────────────────────────────
    private fun resolveUp(zone: TvFocusZone, index: Int): NavResult = when (zone) {
        TvFocusZone.TOP_NAV -> {
            // Ya estamos en el techo — no mover foco, consumir tecla para evitar escape
            NavResult.Cancel
        }
        TvFocusZone.HERO -> {
            // HERO → TOP_NAV (nav_movies, tab 2)
            val req = tracker.getNavRequester(2)
            NavResult.Ready(NavTarget("nav_movies", TvFocusZone.TOP_NAV, 2, req))
        }
        TvFocusZone.RAIL_RECENT -> {
            // RAIL_RECENT → HERO (hero_play)
            val req = tracker.heroPlayAnchor
            NavResult.Ready(NavTarget("hero_play", TvFocusZone.HERO, 0, req))
        }
        TvFocusZone.RAIL_TOP_RATED -> {
            // TOP_RATED → RAIL_RECENT, conservando índice (política columnar)
            resolveVerticalToRail(
                targetZone      = TvFocusZone.RAIL_RECENT,
                preferredIndex  = index,
                railSize        = recentCount,
                tagPrefix       = "recent_col"   // tag semántico: la confirmación real viene de onFocusChanged
            )
        }
        else -> NavResult.NotHandled
    }

    // ─────────────────────────────────────────────────────────────────
    // DPAD_DOWN
    // ─────────────────────────────────────────────────────────────────
    private fun resolveDown(zone: TvFocusZone, index: Int): NavResult = when (zone) {
        TvFocusZone.TOP_NAV -> {
            // TOP_NAV → HERO (hero_play)
            val req = tracker.heroPlayAnchor
            NavResult.Ready(NavTarget("hero_play", TvFocusZone.HERO, 0, req))
        }
        TvFocusZone.HERO -> {
            // HERO → RAIL_RECENT conservando el último índice visitado del rail
            val targetIndex = tracker.recentMovieLastIndex.coerceIn(0, (recentCount - 1).coerceAtLeast(0))
            resolveVerticalToRail(
                targetZone      = TvFocusZone.RAIL_RECENT,
                preferredIndex  = targetIndex,
                railSize        = recentCount,
                tagPrefix       = "recent_col"
            )
        }
        TvFocusZone.RAIL_RECENT -> {
            // RAIL_RECENT → RAIL_TOP_RATED, conservando índice (política columnar — FIX F5)
            resolveVerticalToRail(
                targetZone      = TvFocusZone.RAIL_TOP_RATED,
                preferredIndex  = index,
                railSize        = topRatedCount,
                tagPrefix       = "top_col"
            )
        }
        TvFocusZone.RAIL_TOP_RATED -> {
            // Ya estamos en el suelo — no mover foco, consumir tecla
            NavResult.Cancel
        }
        else -> NavResult.NotHandled
    }

    // ─────────────────────────────────────────────────────────────────
    // DPAD_LEFT
    // ─────────────────────────────────────────────────────────────────
    private fun resolveLeft(zone: TvFocusZone, index: Int): NavResult = when (zone) {
        TvFocusZone.TOP_NAV -> {
            if (index <= 0) NavResult.Cancel
            else {
                val targetIndex = index - 1
                val req = tracker.getNavRequester(targetIndex)
                NavResult.Ready(NavTarget(tracker.getNavTabTag(targetIndex), TvFocusZone.TOP_NAV, targetIndex, req))
            }
        }
        TvFocusZone.HERO -> {
            // hero_detail → hero_play; hero_play → Cancel (borde izquierdo del hero)
            if (index == 0) NavResult.Cancel   // ya en hero_play
            else {
                val req = tracker.heroPlayAnchor
                NavResult.Ready(NavTarget("hero_play", TvFocusZone.HERO, 0, req))
            }
        }
        TvFocusZone.RAIL_RECENT -> {
            if (index <= 0) NavResult.Cancel
            else resolveRailHorizontal(TvFocusZone.RAIL_RECENT, index - 1, recentCount)
        }
        TvFocusZone.RAIL_TOP_RATED -> {
            if (index <= 0) NavResult.Cancel
            else resolveRailHorizontal(TvFocusZone.RAIL_TOP_RATED, index - 1, topRatedCount)
        }
        else -> NavResult.NotHandled
    }

    // ─────────────────────────────────────────────────────────────────
    // DPAD_RIGHT
    // ─────────────────────────────────────────────────────────────────
    private fun resolveRight(zone: TvFocusZone, index: Int): NavResult = when (zone) {
        TvFocusZone.TOP_NAV -> {
            val maxIndex = navTabCount - 1
            if (index >= maxIndex) NavResult.Cancel
            else {
                val targetIndex = index + 1
                val req = tracker.getNavRequester(targetIndex)
                NavResult.Ready(NavTarget(tracker.getNavTabTag(targetIndex), TvFocusZone.TOP_NAV, targetIndex, req))
            }
        }
        TvFocusZone.HERO -> {
            // hero_play → hero_detail; hero_detail → Cancel (borde derecho)
            if (index >= 1) NavResult.Cancel   // ya en hero_detail o más allá
            else {
                val req = tracker.heroDetailAnchor
                NavResult.Ready(NavTarget("hero_detail", TvFocusZone.HERO, 1, req))
            }
        }
        TvFocusZone.RAIL_RECENT -> {
            val maxIndex = recentCount - 1
            if (index >= maxIndex) NavResult.Cancel
            else resolveRailHorizontal(TvFocusZone.RAIL_RECENT, index + 1, recentCount)
        }
        TvFocusZone.RAIL_TOP_RATED -> {
            val maxIndex = topRatedCount - 1
            if (index >= maxIndex) NavResult.Cancel
            else resolveRailHorizontal(TvFocusZone.RAIL_TOP_RATED, index + 1, topRatedCount)
        }
        else -> NavResult.NotHandled
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Movimiento vertical hacia un rail.
     *
     * POLÍTICA COLUMNAR (FIX F5):
     * El índice destino es `preferredIndex` recortado al tamaño real del rail destino.
     * Esto garantiza:
     *   RECENT #14 → DOWN → TOP_RATED #14  (o #lastIndex si TOP_RATED solo tiene 10)
     *   TOP_RATED #5 → UP  → RECENT #5
     *
     * NO se usa el historial del rail destino ([tracker.recentMovieLastIndex] o
     * [tracker.topRatedMovieLastIndex]) para navegar verticalmente, porque produciría
     * saltos impredecibles. El historial solo se usa para la restauración desde HERO.
     */
    private fun resolveVerticalToRail(
        targetZone: TvFocusZone,
        preferredIndex: Int,
        railSize: Int,
        tagPrefix: String
    ): NavResult {
        if (railSize == 0) return NavResult.Cancel

        val targetIndex = preferredIndex.coerceIn(0, railSize - 1)
        val req = when (targetZone) {
            TvFocusZone.RAIL_RECENT     -> tracker.getRecentRequester(targetIndex)
            TvFocusZone.RAIL_TOP_RATED  -> tracker.getTopRatedRequester(targetIndex)
            else -> return NavResult.Cancel
        }

        // El tag semántico exacto (con streamId) lo confirmará onFocusChanged.
        // Usamos un tag de posición para la validación previa en recordKeyRequest.
        val tag = "${tagPrefix}_$targetIndex"

        return if (tracker.isComposed(targetZone, targetIndex)) {
            NavResult.Ready(NavTarget(tag, targetZone, targetIndex, req))
        } else {
            // El item existe en los datos pero el TvLazyRow aún no lo ha compuesto.
            // El caller debe hacer scroll primero.
            NavResult.ScrollNeeded(NavTarget(tag, targetZone, targetIndex, null), targetIndex)
        }
    }

    /**
     * Movimiento horizontal dentro de un rail.
     * El item puede necesitar scroll si está fuera del viewport del TvLazyRow.
     */
    private fun resolveRailHorizontal(zone: TvFocusZone, targetIndex: Int, railSize: Int): NavResult {
        if (targetIndex < 0 || targetIndex >= railSize) return NavResult.Cancel

        val req = when (zone) {
            TvFocusZone.RAIL_RECENT    -> tracker.getRecentRequester(targetIndex)
            TvFocusZone.RAIL_TOP_RATED -> tracker.getTopRatedRequester(targetIndex)
            else -> return NavResult.Cancel
        }
        val tag = when (zone) {
            TvFocusZone.RAIL_RECENT    -> "recent_col_$targetIndex"
            TvFocusZone.RAIL_TOP_RATED -> "top_col_$targetIndex"
            else -> "unknown"
        }

        return if (tracker.isComposed(zone, targetIndex)) {
            NavResult.Ready(NavTarget(tag, zone, targetIndex, req))
        } else {
            NavResult.ScrollNeeded(NavTarget(tag, zone, targetIndex, null), targetIndex)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UNIT TESTS (pura lógica de grafo, ejecutables sin emulador)
//
// Para correr: `./gradlew :feature-presentation-tv:testDebugUnitTest`
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tabla de casos de prueba del grafo de MOVIES.
 * Cada entrada es: zona inicial, índice inicial, tecla → zona esperada, índice esperado.
 *
 * Ejecutar con [runMoviesGraphTests]. No depende de ninguna clase de Compose UI.
 */
data class GraphTestCase(
    val fromZone: TvFocusZone,
    val fromIndex: Int,
    val key: String,
    val expectedZone: TvFocusZone?,   // null = Cancel esperado
    val expectedIndex: Int?           // null = Cancel esperado
)

val MOVIES_GRAPH_TEST_CASES = listOf(
    // ── TOP_NAV ──────────────────────────────────────────────────────────────
    GraphTestCase(TvFocusZone.TOP_NAV,        0, "DPAD_LEFT",  null,                         null), // nav_home + LEFT = CANCEL
    GraphTestCase(TvFocusZone.TOP_NAV,        0, "DPAD_RIGHT", TvFocusZone.TOP_NAV,           1),   // nav_home → nav_live
    GraphTestCase(TvFocusZone.TOP_NAV,        1, "DPAD_LEFT",  TvFocusZone.TOP_NAV,           0),   // nav_live → nav_home
    GraphTestCase(TvFocusZone.TOP_NAV,        1, "DPAD_RIGHT", TvFocusZone.TOP_NAV,           2),   // nav_live → nav_movies
    GraphTestCase(TvFocusZone.TOP_NAV,        2, "DPAD_LEFT",  TvFocusZone.TOP_NAV,           1),   // nav_movies → nav_live
    GraphTestCase(TvFocusZone.TOP_NAV,        2, "DPAD_RIGHT", TvFocusZone.TOP_NAV,           3),   // nav_movies → nav_series
    GraphTestCase(TvFocusZone.TOP_NAV,        3, "DPAD_LEFT",  TvFocusZone.TOP_NAV,           2),   // nav_series → nav_movies
    GraphTestCase(TvFocusZone.TOP_NAV,        2, "DPAD_DOWN",  TvFocusZone.HERO,              0),   // nav_movies → hero_play
    GraphTestCase(TvFocusZone.TOP_NAV,        0, "DPAD_UP",    null,                         null), // TOP_NAV + UP = CANCEL (techo)

    // ── HERO ─────────────────────────────────────────────────────────────────
    GraphTestCase(TvFocusZone.HERO,           0, "DPAD_UP",    TvFocusZone.TOP_NAV,           2),   // hero_play → nav_movies
    GraphTestCase(TvFocusZone.HERO,           0, "DPAD_RIGHT", TvFocusZone.HERO,              1),   // hero_play → hero_detail
    GraphTestCase(TvFocusZone.HERO,           0, "DPAD_LEFT",  null,                         null), // hero_play + LEFT = CANCEL (borde)
    GraphTestCase(TvFocusZone.HERO,           1, "DPAD_LEFT",  TvFocusZone.HERO,              0),   // hero_detail → hero_play
    GraphTestCase(TvFocusZone.HERO,           1, "DPAD_RIGHT", null,                         null), // hero_detail + RIGHT = CANCEL
    GraphTestCase(TvFocusZone.HERO,           1, "DPAD_UP",    TvFocusZone.TOP_NAV,           2),   // hero_detail → nav_movies

    // ── RAIL_RECENT → política columnar ───────────────────────────────────────
    GraphTestCase(TvFocusZone.RAIL_RECENT,    0, "DPAD_UP",    TvFocusZone.HERO,              0),   // recent_0 → hero_play
    GraphTestCase(TvFocusZone.RAIL_RECENT,    0, "DPAD_LEFT",  null,                         null), // recent_0 + LEFT = CANCEL
    GraphTestCase(TvFocusZone.RAIL_RECENT,    5, "DPAD_DOWN",  TvFocusZone.RAIL_TOP_RATED,   5),   // recent_5 → top_5 (columnar)
    GraphTestCase(TvFocusZone.RAIL_RECENT,   14, "DPAD_DOWN",  TvFocusZone.RAIL_TOP_RATED,   9),   // recent_14 → top_9 (top tiene 10 → lastIndex=9)
    GraphTestCase(TvFocusZone.RAIL_RECENT,    3, "DPAD_RIGHT", TvFocusZone.RAIL_RECENT,       4),   // recent_3 → recent_4

    // ── RAIL_TOP_RATED → política columnar ────────────────────────────────────
    GraphTestCase(TvFocusZone.RAIL_TOP_RATED, 5, "DPAD_UP",    TvFocusZone.RAIL_RECENT,       5),   // top_5 → recent_5 (columnar)
    GraphTestCase(TvFocusZone.RAIL_TOP_RATED, 9, "DPAD_UP",    TvFocusZone.RAIL_RECENT,       9),   // top_9 → recent_9
    GraphTestCase(TvFocusZone.RAIL_TOP_RATED, 0, "DPAD_DOWN",  null,                         null), // TOP_RATED + DOWN = CANCEL (suelo)
    GraphTestCase(TvFocusZone.RAIL_TOP_RATED, 9, "DPAD_DOWN",  null,                         null), // top_last + DOWN = CANCEL
    GraphTestCase(TvFocusZone.RAIL_TOP_RATED, 2, "DPAD_RIGHT", TvFocusZone.RAIL_TOP_RATED,   3),   // top_2 → top_3
    GraphTestCase(TvFocusZone.RAIL_TOP_RATED, 9, "DPAD_RIGHT", null,                         null)  // top_last + RIGHT = CANCEL
)

/**
 * Ejecuta todos los casos de prueba del grafo contra un [FocusTracker] temporal.
 * No requiere emulador. Imprime resultados en Logcat con tag "MOVIES_GRAPH_TEST".
 *
 * Parámetros de prueba: 7 tabs, 20 películas recientes, 10 top-rated.
 */
fun runMoviesGraphTests() {
    val TEST_TAG = "MOVIES_GRAPH_TEST"
    val NAV_TABS    = 7
    val RECENT_SIZE = 20
    val TOP_SIZE    = 10

    var pass = 0
    var fail = 0

    MOVIES_GRAPH_TEST_CASES.forEachIndexed { i, tc ->
        val tracker = FocusTracker().apply {
            currentZone  = tc.fromZone
            lastCardIndex = tc.fromIndex
        }

        val graph = MoviesFocusGraph(
            tracker        = tracker,
            navTabCount    = NAV_TABS,
            recentCount    = RECENT_SIZE,
            topRatedCount  = TOP_SIZE
        )

        // Marcar todos los índices como compuestos para simplificar los tests del grafo puro
        tracker.composedRecentIndices.addAll((0 until RECENT_SIZE).toList())
        tracker.composedTopRatedIndices.addAll((0 until TOP_SIZE).toList())

        val result = graph.resolve(tc.key)

        val expectedCancel = (tc.expectedZone == null)
        val ok = when {
            expectedCancel && result is NavResult.Cancel -> true
            !expectedCancel && result is NavResult.Ready ->
                result.target.zone == tc.expectedZone && result.target.index == tc.expectedIndex
            !expectedCancel && result is NavResult.ScrollNeeded ->
                result.target.zone == tc.expectedZone && result.target.index == tc.expectedIndex
            else -> false
        }

        if (ok) {
            pass++
            Log.d(TEST_TAG, "[$i] PASS — from=${tc.fromZone.label}#${tc.fromIndex} key=${tc.key} → ${result}")
        } else {
            fail++
            Log.e(TEST_TAG, "[$i] FAIL — from=${tc.fromZone.label}#${tc.fromIndex} key=${tc.key} expected=zone=${tc.expectedZone}#${tc.expectedIndex} got=$result")
        }
    }

    if (fail == 0) {
        Log.i(TEST_TAG, "✅ ALL $pass TESTS PASSED — Movies graph is correct")
    } else {
        Log.e(TEST_TAG, "❌ $fail FAILED / $pass PASSED — Movies graph has errors")
    }
}
