package com.lelouch.feature.tv.focus

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester

/**
 * Zonas de navegación dentro de la pantalla TV.
 */
enum class TvFocusZone(val label: String) {
    TOP_NAV("TOP_NAV"),
    HERO("HERO"),
    RAIL_RECENT("RAIL_RECENT"),
    RAIL_TOP_RATED("RAIL_TOP_RATED"),
    MODAL("MODAL"),
    PLAYER("PLAYER"),
    NONE("NONE")
}

/**
 * Resultado estricto del ciclo de evento D-Pad según confirmación real de Compose.
 */
enum class FocusResult(val label: String) {
    SUCCESS("SUCCESS"),
    UNCHANGED("UNCHANGED"),
    FOCUS_LOST("FOCUS_LOST"),
    TARGET_NOT_COMPOSED("TARGET_NOT_COMPOSED"),
    REQUEST_FAILED("REQUEST_FAILED")
}

/**
 * Estado observable en tiempo real para rastrear el ciclo completo de cada KeyEvent
 * en Xiaomi TV Box (XMRM-M3): BEFORE -> REQUESTED TARGET -> AFTER -> RESULT.
 */
@Stable
class FocusTracker {
    var lastKey by mutableStateOf("INICIO")
    var currentScreen by mutableStateOf("MOVIES")
    var eventConsumed by mutableStateOf(true)

    // 1. ESTADO BEFORE (Estado anterior al KeyEvent)
    var beforeTag by mutableStateOf("nav_movies")
    var beforeZone by mutableStateOf(TvFocusZone.TOP_NAV)
    var beforeIndex by mutableIntStateOf(2)

    // 2. ESTADO REQUESTED TARGET (Destino calculado por el grafo)
    var requestedTargetTag by mutableStateOf("nav_movies")
    var requestedTargetZone by mutableStateOf(TvFocusZone.TOP_NAV)
    var requestedTargetIndex by mutableIntStateOf(2)

    // 3. ESTADO AFTER / ACTUAL CURRENT (Confirmado ÚNICAMENTE por onFocusChanged real)
    var currentTag by mutableStateOf("nav_movies")
    var currentZone by mutableStateOf(TvFocusZone.TOP_NAV)
    var lastCardIndex by mutableIntStateOf(2)
    var lastRowIndex by mutableIntStateOf(0)

    // 4. HISTORIAL PREVIO (Paso anterior registrado)
    var previousTag by mutableStateOf("NONE")
    var previousZone by mutableStateOf(TvFocusZone.NONE)
    var previousCardIndex by mutableIntStateOf(0)

    // 5. RESULTADO ESTRICTO DEL CICLO
    var actualResult by mutableStateOf(FocusResult.SUCCESS)

    // Anchors explícitos para conectar las zonas verticalmente (Grafo Determinista)
    val navMoviesAnchor = FocusRequester()
    val heroPlayAnchor = FocusRequester()
    val heroDetailAnchor = FocusRequester()
    val railRecentAnchor = FocusRequester()
    val railTopRatedAnchor = FocusRequester()

    // Memoria por rail (Paso 6): recuerda el último índice enfocado en cada riel
    var recentMovieLastIndex by mutableIntStateOf(0)
    var topRatedMovieLastIndex by mutableIntStateOf(0)

    // Mapa de FocusRequesters para cada elemento individual en los rieles de Películas
    val recentMovieRequesters = mutableStateMapOf<Int, FocusRequester>()
    val topRatedMovieRequesters = mutableStateMapOf<Int, FocusRequester>()

    // Registro de índices actualmente compuestos por TvLazyRow en la UI
    val composedRecentIndices = mutableStateListOf<Int>()
    val composedTopRatedIndices = mutableStateListOf<Int>()

    val navRequesters = mutableStateMapOf<Int, FocusRequester>()

    fun getNavRequester(index: Int): FocusRequester {
        return navRequesters.getOrPut(index) {
            if (index == 2) navMoviesAnchor else FocusRequester()
        }
    }

    fun getNavTabTag(index: Int): String = when (index) {
        0 -> "nav_home"
        1 -> "nav_live"
        2 -> "nav_movies"
        3 -> "nav_series"
        4 -> "nav_favorites"
        5 -> "nav_admin"
        else -> "nav_lab"
    }

    fun getRecentRequester(index: Int): FocusRequester {
        return recentMovieRequesters.getOrPut(index) { FocusRequester() }
    }

    fun getTopRatedRequester(index: Int): FocusRequester {
        return topRatedMovieRequesters.getOrPut(index) { FocusRequester() }
    }

    fun isComposed(zone: TvFocusZone, index: Int): Boolean {
        return when (zone) {
            TvFocusZone.RAIL_RECENT -> composedRecentIndices.contains(index)
            TvFocusZone.RAIL_TOP_RATED -> composedTopRatedIndices.contains(index)
            else -> true // Top Nav y Hero siempre están compuestos
        }
    }

    /**
     * Registra el inicio de un evento de tecla. Congela BEFORE, calcula REQUESTED TARGET
     * y comprueba si el destino está compuesto o si el foco permanece UNCHANGED.
     * NO UTILIZA DELAYS.
     */
    fun recordKeyRequest(
        key: String,
        targetTag: String,
        targetZone: TvFocusZone,
        targetIndex: Int = 0,
        consumed: Boolean = true
    ) {
        lastKey = key
        eventConsumed = consumed

        // Congelar estado BEFORE
        beforeTag = currentTag
        beforeZone = currentZone
        beforeIndex = lastCardIndex

        // Registrar REQUESTED TARGET
        requestedTargetTag = targetTag
        requestedTargetZone = targetZone
        requestedTargetIndex = targetIndex

        // Validar si el destino ya fue compuesto por Compose TvLazyRow
        if (!isComposed(targetZone, targetIndex)) {
            actualResult = FocusResult.TARGET_NOT_COMPOSED
        } else {
            // Inicialmente UNCHANGED hasta que onFocusChanged del destino confirme recepción
            actualResult = FocusResult.UNCHANGED
        }

        Log.d(
            "XIAOMI_DPAD_TRACE",
            "KEY_REQUEST: key=[$key] before=[$beforeTag, ${beforeZone.label}, #$beforeIndex] target=[$requestedTargetTag, ${requestedTargetZone.label}, #$requestedTargetIndex] initialResult=[${actualResult.label}]"
        )
    }

    /**
     * Se invoca EXCLUSIVAMENTE cuando el Composable destino dispara onFocusChanged con isFocused = true.
     * TARGET != AFTER: Certifica si se alcanzó SUCCESS o si hubo REQUEST_FAILED.
     */
    fun onFocusChanged(
        tag: String,
        zone: TvFocusZone,
        rowIndex: Int = 0,
        cardIndex: Int = 0,
        isFocused: Boolean = true
    ) {
        if (!isFocused) {
            if (currentTag == tag) {
                // El elemento actual perdió foco
            }
            return
        }

        // El elemento destino confirmó que TIENE foco real
        if (currentTag != tag || currentZone != zone || lastCardIndex != cardIndex) {
            previousTag = currentTag
            previousZone = currentZone
            previousCardIndex = lastCardIndex

            currentTag = tag
            currentZone = zone
            lastRowIndex = rowIndex
            lastCardIndex = cardIndex

            if (zone == TvFocusZone.RAIL_RECENT) {
                recentMovieLastIndex = cardIndex
            } else if (zone == TvFocusZone.RAIL_TOP_RATED) {
                topRatedMovieLastIndex = cardIndex
            }

            // Comprobar si el foco aterrizó en el target solicitado
            actualResult = if (tag == requestedTargetTag || (zone == requestedTargetZone && cardIndex == requestedTargetIndex)) {
                FocusResult.SUCCESS
            } else if (tag == beforeTag) {
                FocusResult.UNCHANGED
            } else {
                FocusResult.REQUEST_FAILED
            }

            Log.d(
                "XIAOMI_DPAD_TRACE",
                "FOCUS_CONFIRMED: after=[$currentTag, ${currentZone.label}, #$cardIndex] result=[${actualResult.label}]"
            )
        }
    }

    fun onFocusLost() {
        actualResult = FocusResult.FOCUS_LOST
        Log.e("XIAOMI_DPAD_TRACE", "FOCUS_LOST: No active element in focus hierarchy!")
    }
}

val LocalFocusTracker = staticCompositionLocalOf<FocusTracker> {
    error("FocusTracker no provisto en el árbol de composición")
}
