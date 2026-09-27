package com.lelouch.feature.tv.focus

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import android.util.Log

/**
 * Zona de navegación dentro de la pantalla TV.
 */
enum class TvFocusZone(val label: String) {
    TOP_NAV("TOP_NAV"),
    HERO("HERO"),
    RAIL_RECENT("RAIL_RECENT"),
    RAIL_TOP_RATED("RAIL_TOP_RATED"),
    RAIL_LIVE("RAIL_LIVE"),
    RAIL_SERIES("RAIL_SERIES"),
    MODAL("MODAL"),
    PLAYER("PLAYER"),
    NONE("NONE")
}

/**
 * Estado observable en tiempo real para rastrear la navegación D-Pad en Xiaomi TV Box.
 */
@Stable
class FocusTracker {
    var lastKey by mutableStateOf("INICIO")
    var currentScreen by mutableStateOf("MOVIES")
    var currentZone by mutableStateOf(TvFocusZone.TOP_NAV)
    var currentTag by mutableStateOf("nav_movies")
    var previousTag by mutableStateOf("NONE")
    var lastRowIndex by mutableIntStateOf(0)
    var lastCardIndex by mutableIntStateOf(0)
    var eventConsumed by mutableStateOf(true)
    var targetDescription by mutableStateOf("initial")

    // Anchors explícitos para conectar las zonas verticalmente (Grafo Determinista)
    val navMoviesAnchor = FocusRequester()
    val heroPlayAnchor = FocusRequester()
    val railRecentAnchor = FocusRequester()
    val railTopRatedAnchor = FocusRequester()

    // Memoria por rail (Paso 6): recuerda el último índice enfocado en cada riel
    var recentMovieLastIndex by mutableIntStateOf(0)
    var topRatedMovieLastIndex by mutableIntStateOf(0)

    // Mapa de FocusRequesters para cada elemento individual en los rieles de Películas
    val recentMovieRequesters = mutableStateMapOf<Int, FocusRequester>()
    val topRatedMovieRequesters = mutableStateMapOf<Int, FocusRequester>()

    fun getRecentRequester(index: Int): FocusRequester {
        return recentMovieRequesters.getOrPut(index) { FocusRequester() }
    }

    fun getTopRatedRequester(index: Int): FocusRequester {
        return topRatedMovieRequesters.getOrPut(index) { FocusRequester() }
    }

    fun onFocusChanged(tag: String, zone: TvFocusZone, rowIndex: Int = 0, cardIndex: Int = 0) {
        if (currentTag != tag) {
            previousTag = currentTag
            currentTag = tag
            currentZone = zone
            lastRowIndex = rowIndex
            lastCardIndex = cardIndex

            if (zone == TvFocusZone.RAIL_RECENT) {
                recentMovieLastIndex = cardIndex
            } else if (zone == TvFocusZone.RAIL_TOP_RATED) {
                topRatedMovieLastIndex = cardIndex
            }

            Log.d(
                "XIAOMI_DPAD_TRACE",
                "FOCUS_TRANSITION: from=[$previousTag] to=[$currentTag] zone=[${zone.label}] cardIndex=[$cardIndex]"
            )
        }
    }

    fun recordKey(key: String, consumed: Boolean = true, nextTarget: String = "") {
        lastKey = key
        eventConsumed = consumed
        targetDescription = nextTarget
        Log.d(
            "XIAOMI_DPAD_TRACE",
            "KEY_EVENT: key=[$key] currentTag=[$currentTag] zone=[${currentZone.label}] consumed=[$consumed] nextTarget=[$nextTarget]"
        )
    }
}

val LocalFocusTracker = staticCompositionLocalOf<FocusTracker> {
    error("FocusTracker no provisto en el árbol de composición")
}
