package com.lelouch.core.model

/**
 * Filtro centralizado y riguroso de contenido para adultos (+18 / XXX / Contenido Explícito).
 * Diseñado conforme a los estándares de filtrado parental de reproductores IPTV líderes
 * (TiviMate, IPTV Smarters, OTT Navigator).
 *
 * Utiliza límites de palabra (\b) para términos cortos o ambiguos para evitar falsos positivos
 * (ej. "canal" nunca coincide con "anal", "transmisión" nunca coincide con "trans").
 */
object AdultContentFilter {

    private val adultSubstrings = listOf(
        "xxx", "porn", "porno", "adulto", "adults", "erotic", "erotico", "erotica",
        "playboy", "venus", "penthouse", "brazzers", "redlight", "hustler",
        "dorcel", "man-x", "vivid", "bangbros", "realitykings", "naughty", "stars exclusive",
        "cento x cento", "onlyfans", "only fans", "pornbox", "webcam",
        "forbiden", "taboo", "hardcore", "softcore", "blowjob", "hentai", "milf", "mylf",
        "shemale", "creampie", "fetish", "dildo", "masturbat", "vixen",
        "first class pov", "atk girlfriends", "swinger", "cumshot", "lesbiana"
    )

    private val adultWordBoundaryRegex = Regex(
        "(?i)\\b(adult|anal|hot|sexy|strip|trans|gay|gays|amateur|amateurs|babe|babes|cams|boobs|pussy|tits|fuck|bitch|slut|lesbian|sensual|interracial|squirt)\\b"
    )

    private val x4Regex = Regex(
        "(?i)\\bx4\\b|[|\\-:]\\s*x4\\b|x4-logo|pb/x4"
    )

    fun isAdult(
        name: String,
        categoryName: String = "",
        categoryId: String = "",
        streamIcon: String? = null,
        streamUrl: String? = null
    ): Boolean {
        val text = "$name $categoryName $categoryId ${streamIcon.orEmpty()} ${streamUrl.orEmpty()}".lowercase()
        // 1. Emojis universales (+18, 18+, ❌, ✖️)
        if (text.contains("❌") || text.contains("✖️") || text.contains("+18") || text.contains("18+")) return true
        // 2. Canales X4 específicos de proveedores Xtream/LionTV
        if (x4Regex.containsMatchIn(text)) return true
        // 3. Términos directos no ambiguos
        if (adultSubstrings.any { text.contains(it) }) return true
        // 4. Regex con límites de palabra para evitar falsos positivos
        if (adultWordBoundaryRegex.containsMatchIn(text)) return true
        return false
    }
}
