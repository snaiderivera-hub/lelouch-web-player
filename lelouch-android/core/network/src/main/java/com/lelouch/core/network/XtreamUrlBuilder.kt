package com.lelouch.core.network

object XtreamUrlBuilder {

    /**
     * Construye la URL de reproducción para canales en directo.
     * IMPORTANTE: No codificar el carácter '@' en '%40' si el nombre de usuario empieza por '@',
     * puesto que Nginx en servidores Xtream Codes espera el '@' literal en la ruta.
     */
    fun buildLiveStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: Int,
        extension: String = "ts"
    ): String {
        val base = serverUrl.trimEnd('/')
        return "$base/live/$username/$password/$streamId.$extension"
    }

    /**
     * Construye la URL de reproducción para películas VOD.
     */
    fun buildVodStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: Int,
        extension: String = "mp4"
    ): String {
        val base = serverUrl.trimEnd('/')
        val cleanExt = extension.trimStart('.').ifEmpty { "mp4" }
        return "$base/movie/$username/$password/$streamId.$cleanExt"
    }

    /**
     * Construye la URL de reproducción para episodios de series.
     */
    fun buildSeriesStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        episodeId: Int,
        extension: String = "mp4"
    ): String {
        val base = serverUrl.trimEnd('/')
        val cleanExt = extension.trimStart('.').ifEmpty { "mp4" }
        return "$base/series/$username/$password/$episodeId.$cleanExt"
    }
}
