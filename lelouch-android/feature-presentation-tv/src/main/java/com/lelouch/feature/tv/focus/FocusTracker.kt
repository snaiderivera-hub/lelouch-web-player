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
 *
 * SUCCESS         → onFocusChanged del destino confirmó recepción Y coincide con el target solicitado.
 * UNCHANGED       → onFocusChanged no disparó (el foco no se movió en absoluto).
 * FOCUS_LOST      → El foco salió del árbol sin destino conocido.
 * TARGET_NOT_COMPOSED → El item del grafo no estaba en la ventana visible del TvLazyRow.
 * REQUEST_FAILED  → onFocusChanged disparó pero en un elemento distinto al target.
 * ACTION_CENTER   → Tecla CENTER/ENTER — no es un movimiento direccional, no produce FocusResult.
 */
enum class FocusResult(val label: String) {
    SUCCESS("SUCCESS"),
    UNCHANGED("UNCHANGED"),
    FOCUS_LOST("FOCUS_LOST"),
    TARGET_NOT_COMPOSED("TARGET_NOT_COMPOSED"),
    REQUEST_FAILED("REQUEST_FAILED"),
    ACTION_CENTER("ACTION_CENTER")   // CENTER no genera resultado de movimiento
}

/**
 * Estado observable en tiempo real para rastrear el ciclo completo de cada KeyEvent
 * en Xiaomi TV Box (XMRM-M3): BEFORE → REQUESTED TARGET → AFTER → RESULT.
 *
 * onFocusChanged es el único punto que puede marcar SUCCESS.
 * recordKeyRequest() solo puede marcar UNCHANGED o TARGET_NOT_COMPOSED como estado inicial.
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

    // Memoria de posición en cada rail (usado SOLO para restauración desde HERO, NO para navegación columnar)
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
            TvFocusZone.RAIL_RECENT     -> composedRecentIndices.contains(index)
            TvFocusZone.RAIL_TOP_RATED  -> composedTopRatedIndices.contains(index)
            else -> true // Top Nav y Hero siempre están compuestos
        }
    }

    /**
     * Resuelve el [FocusRequester] real para una zona+índice dados.
     * Utilizado por onPreviewKeyEvent para ejecutar requestFocus() sin acceder directamente
     * a los mapas internos.
     *
     * @return FocusRequester listo para requestFocus(), o null si el índice no está compuesto.
     */
    fun resolveFocusRequester(zone: TvFocusZone, index: Int): FocusRequester? {
        return when (zone) {
            TvFocusZone.TOP_NAV        -> getNavRequester(index)
            TvFocusZone.HERO           -> if (index == 0) heroPlayAnchor else heroDetailAnchor
            TvFocusZone.RAIL_RECENT    -> if (isComposed(zone, index)) getRecentRequester(index) else null
            TvFocusZone.RAIL_TOP_RATED -> if (isComposed(zone, index)) getTopRatedRequester(index) else null
            else                       -> null
        }
    }

    /**
     * Registra el inicio de un evento de tecla. Congela BEFORE, registra REQUESTED TARGET.
     * NO llama requestFocus() — esa responsabilidad pertenece exclusivamente a onPreviewKeyEvent.
     * NO utiliza delays.
     *
     * Para CENTER (ACTION_CENTER): registrar sin estado de movimiento.
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

        // Para teclas de acción (CENTER) no congela BEFORE ni registra movimiento
        if (key == "DPAD_CENTER") {
            actualResult = FocusResult.ACTION_CENTER
            Log.d("XIAOMI_DPAD_TRACE", "ACTION_CENTER: consumed=[$consumed]")
            return
        }

        // Congelar estado BEFORE
        beforeTag   = currentTag
        beforeZone  = currentZone
        beforeIndex = lastCardIndex

        // Registrar REQUESTED TARGET
        requestedTargetTag   = targetTag
        requestedTargetZone  = targetZone
        requestedTargetIndex = targetIndex

        // Estado inicial: UNCHANGED hasta que onFocusChanged del destino confirme recepción
        // (TARGET_NOT_COMPOSED se establece si el destino no está en pantalla)
        actualResult = if (!isComposed(targetZone, targetIndex)) {
            FocusResult.TARGET_NOT_COMPOSED
        } else {
            FocusResult.UNCHANGED
        }

        Log.d(
            "XIAOMI_DPAD_TRACE",
            "KEY_REQUEST: key=[$key] before=[$beforeTag, ${beforeZone.label}, #$beforeIndex] " +
            "target=[$requestedTargetTag, ${requestedTargetZone.label}, #$requestedTargetIndex] " +
            "initialResult=[${actualResult.label}]"
        )
    }

    /**
     * Se invoca EXCLUSIVAMENTE cuando el Composable destino dispara onFocusChanged con isFocused = true.
     *
     * SUCCESS ESTRICTO: requiere que tag, zone Y cardIndex coincidan con el target solicitado.
     * (eliminada la validación permisiva con OR que podía producir falsos SUCCESS)
     */
    fun onFocusChanged(
        tag: String,
        zone: TvFocusZone,
        rowIndex: Int = 0,
        cardIndex: Int = 0,
        isFocused: Boolean = true
    ) {
        if (!isFocused) return

        // El elemento destino confirmó que TIENE foco real
        if (currentTag != tag || currentZone != zone || lastCardIndex != cardIndex) {
            previousTag      = currentTag
            previousZone     = currentZone
            previousCardIndex = lastCardIndex

            currentTag    = tag
            currentZone   = zone
            lastRowIndex  = rowIndex
            lastCardIndex = cardIndex

            if (zone == TvFocusZone.RAIL_RECENT) {
                recentMovieLastIndex = cardIndex
            } else if (zone == TvFocusZone.RAIL_TOP_RATED) {
                topRatedMovieLastIndex = cardIndex
            }

            // SUCCESS ESTRICTO: tag, zone E index deben coincidir con el target solicitado.
            // Para CENTER (ACTION_CENTER) o inicio sin navegación, no actualizar resultado.
            actualResult = if (actualResult == FocusResult.ACTION_CENTER) {
                FocusResult.ACTION_CENTER
            } else if (
                tag == requestedTargetTag &&
                zone == requestedTargetZone &&
                cardIndex == requestedTargetIndex
            ) {
                FocusResult.SUCCESS
            } else if (tag == beforeTag && zone == beforeZone && cardIndex == beforeIndex) {
                FocusResult.UNCHANGED
            } else {
                // El foco llegó a un lugar real pero diferente al target solicitado
                FocusResult.REQUEST_FAILED
            }

            Log.d(
                "XIAOMI_DPAD_TRACE",
                "FOCUS_CONFIRMED: after=[$currentTag, ${currentZone.label}, #$cardIndex] " +
                "result=[${actualResult.label}]"
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
