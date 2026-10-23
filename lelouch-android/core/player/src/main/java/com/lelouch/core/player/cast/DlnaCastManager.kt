package com.lelouch.core.player.cast

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * FASE 38 · Casting DLNA/UPnP para móvil, sin Google Play Services ni dependencias extra.
 *
 * Solo se anuncia lo que realmente hace: descubrir renderers de la red local y enviarles
 * una URL (Play/Pause/Stop). No controla volumen, posición ni la lista de contenidos.
 *
 * Todas las llamadas de red ocurren en [Dispatchers.IO]; la UI nunca se bloquea.
 */
class DlnaCastManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val appContext = context.applicationContext

    /**
     * Busca renderers DLNA en la red local.
     *
     * Devuelve una lista vacía si no hay ninguno o si la red no permite multicast; nunca
     * lanza excepciones por falta de red.
     */
    suspend fun discover(timeoutMs: Long = 4_000L): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val lock = acquireMulticastLock()
        try {
            val locations = searchLocations(timeoutMs)
            if (locations.isEmpty()) return@withContext emptyList()
            // Las descripciones se piden en paralelo: en una LAN con varios equipos,
            // secuencialmente se superaría el tiempo que el usuario está dispuesto a esperar.
            coroutineScope {
                locations.map { location ->
                    async { runCatching { fetchDevice(location) }.getOrNull() }
                }.awaitAll()
                    .filterNotNull()
                    .distinctBy { it.udn }
                    .sortedBy { it.name.lowercase() }
            }
        } finally {
            lock?.let { runCatching { if (it.isHeld) it.release() } }
        }
    }

    /**
     * Envía [url] al renderer y **confirma** que el televisor ha empezado a reproducir.
     *
     * Muchos televisores muestran un diálogo de permiso: mientras está abierto aceptan la
     * orden pero descartan el `Play`. Por eso, si el estado no llega a PLAYING, se reenvía la
     * URI y el `Play` hasta [CAST_CONFIRM_TIMEOUT_MS]. Solo se devuelve éxito cuando el
     * renderer informa que reproduce; así la pantalla nunca pausa el móvil en falso.
     */
    suspend fun cast(
        device: DlnaDevice,
        url: String,
        title: String,
        isLive: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(url.isNotBlank()) { "URL vacía" }
            val metadata = DlnaProtocol.buildMetadata(
                url = url,
                title = title.ifBlank { "Lelouch" },
                mimeType = DlnaProtocol.mimeTypeFor(url, isLive)
            )

            // Un envío anterior puede dejar el renderer con otro medio cargado.
            runCatching { soap(device.controlUrl, "Stop", DlnaProtocol.stopBody()) }

            setUri(device, url, metadata).getOrThrow()
            soap(device.controlUrl, "Play", DlnaProtocol.playBody()).getOrThrow()
            awaitPlayback(device, url, metadata)
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rejection: Exception) {
            Result.failure(rejection)
        }
    }

    private fun setUri(device: DlnaDevice, url: String, metadata: String): Result<String> =
        soap(device.controlUrl, "SetAVTransportURI", DlnaProtocol.setAvTransportUriBody(url, metadata))

    /**
     * Espera a que el renderer informe PLAYING. Si sigue en STOPPED/NO_MEDIA_PRESENT
     * (típico con el diálogo de permiso abierto) reenvía la orden cada [RETRY_EVERY_MS].
     */
    private suspend fun awaitPlayback(device: DlnaDevice, url: String, metadata: String) {
        val deadline = System.currentTimeMillis() + CAST_CONFIRM_TIMEOUT_MS
        var lastState: String? = null
        var lastCommandAt = 0L
        var uriResends = 0
        var unsupportedProbes = 0

        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            when (val probe = probeTransport(device)) {
                is TransportProbe.State -> {
                    unsupportedProbes = 0
                    val info = probe.info
                    lastState = info.state
                    if (info.status.equals("ERROR_OCCURRED", ignoreCase = true)) {
                        error("El televisor informó de un error al abrir el contenido (estado: ${info.state}).")
                    }
                    when (info.state) {
                        "PLAYING", "TRANSITIONING" -> return
                        "STOPPED", "NO_MEDIA_PRESENT", "PAUSED_PLAYBACK" -> {
                            val now = System.currentTimeMillis()
                            if (now - lastCommandAt >= RETRY_EVERY_MS) {
                                if (info.state == "NO_MEDIA_PRESENT" && uriResends < MAX_URI_RESENDS) {
                                    // El televisor pudo descartar la URI mientras pedía permiso.
                                    runCatching { setUri(device, url, metadata) }
                                    uriResends++
                                }
                                runCatching { soap(device.controlUrl, "Play", DlnaProtocol.playBody()) }
                                lastCommandAt = now
                            }
                        }
                    }
                }
                // GetTransportInfo es obligatorio en AVTransport:1, pero algunos equipos
                // mínimos lo rechazan: se conserva el comportamiento anterior (orden aceptada).
                TransportProbe.Unsupported -> if (++unsupportedProbes >= 2) return
                // Error transitorio (timeout, TV ocupado): se sigue intentando hasta el plazo.
                TransportProbe.Failed -> Unit
            }
        }

        error(
            if (lastState == null) {
                "El televisor no respondió a la comprobación de reproducción. " +
                    "Comprueba que siga encendido y en la misma red Wi-Fi."
            } else {
                "El televisor aceptó la orden pero no inició la reproducción (estado: $lastState). " +
                    "Suele ocurrir con canales en vivo en HLS (.m3u8), que muchos televisores no " +
                    "admiten por DLNA, o si el televisor no puede acceder al enlace del proveedor. " +
                    "El móvil sigue reproduciendo."
            }
        )
    }

    private fun probeTransport(device: DlnaDevice): TransportProbe = try {
        val xml = soap(
            device.controlUrl,
            "GetTransportInfo",
            DlnaProtocol.getTransportInfoBody()
        ).getOrThrow()
        DlnaProtocol.parseTransportInfo(xml)
            ?.let { TransportProbe.State(it) }
            ?: TransportProbe.Unsupported
    } catch (rejected: DlnaCommandException) {
        if (rejected.upnpCode == 401 || rejected.httpStatus == 501) {
            TransportProbe.Unsupported
        } else {
            TransportProbe.Failed
        }
    } catch (_: Exception) {
        TransportProbe.Failed
    }

    suspend fun pause(device: DlnaDevice): Result<Unit> = send(device, "Pause", DlnaProtocol.pauseBody())

    suspend fun resume(device: DlnaDevice): Result<Unit> = send(device, "Play", DlnaProtocol.playBody())

    suspend fun stop(device: DlnaDevice): Result<Unit> = send(device, "Stop", DlnaProtocol.stopBody())

    private suspend fun send(device: DlnaDevice, action: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                soap(device.controlUrl, action, body).getOrThrow()
                Result.success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (rejection: Exception) {
                Result.failure(rejection)
            }
        }

    private fun soap(controlUrl: String, action: String, body: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPACTION", DlnaProtocol.soapActionHeader(action))
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .post(body.toRequestBody(XML_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val upnpCode = Regex("<errorCode>(\\d+)</errorCode>")
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                val detail = upnpCode?.let { " · error UPnP $it" }.orEmpty()
                throw DlnaCommandException(
                    message = "DLNA $action rechazado (HTTP ${response.code}$detail)",
                    httpStatus = response.code,
                    upnpCode = upnpCode
                )
            }
            text
        }
    }

    private suspend fun searchLocations(timeoutMs: Long): Set<String> {
        val locations = linkedSetOf<String>()
        val socket = DatagramSocket()
        try {
            socket.soTimeout = 350
            val payload = DlnaProtocol.searchMessage().toByteArray(Charsets.UTF_8)
            val target = InetSocketAddress(
                InetAddress.getByName(DlnaProtocol.ssdpAddress()),
                DlnaProtocol.SSDP_PORT
            )
            // UDP puede perder datagramas: se repite el M-SEARCH como hacen los clientes UPnP.
            repeat(3) {
                socket.send(DatagramPacket(payload, payload.size, target))
                delay(150)
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                DlnaProtocol.parseSsdpLocation(raw)?.let { locations += it }
            }
        } finally {
            runCatching { socket.close() }
        }
        return locations
    }

    private fun fetchDevice(location: String): DlnaDevice? {
        val request = Request.Builder().url(location).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val parsed = DlnaProtocol.parseDeviceDescription(body, location) ?: return null
            return DlnaDevice(
                name = parsed.friendlyName,
                udn = parsed.udn,
                controlUrl = parsed.controlUrl,
                location = parsed.location,
                modelName = parsed.modelName
            )
        }
    }

    /** Sin este lock, Android filtra los paquetes multicast y SSDP no recibe respuestas. */
    private fun acquireMulticastLock(): WifiManager.MulticastLock? = runCatching {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@runCatching null
        wifi.createMulticastLock("lelouch-dlna").apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()

    private companion object {
        val XML_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()

        /** Tiempo máximo para que el usuario acepte en el televisor y este empiece a reproducir. */
        const val CAST_CONFIRM_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 1_500L
        const val RETRY_EVERY_MS = 3_000L
        const val MAX_URI_RESENDS = 2
    }

    /** Resultado de consultar `GetTransportInfo`. */
    private sealed interface TransportProbe {
        data class State(val info: DlnaProtocol.TransportInfo) : TransportProbe
        data object Unsupported : TransportProbe
        data object Failed : TransportProbe
    }

    /** Error SOAP de un comando UPnP (permite distinguir «acción no soportada»). */
    private class DlnaCommandException(
        message: String,
        val httpStatus: Int,
        val upnpCode: Int?
    ) : IllegalStateException(message)
}
