package com.lelouch.core.network.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Diagnóstico de una cuenta/lista IPTV medido desde el propio dispositivo.
 *
 * Responde a la pregunta «¿esta lista sirve?» con datos: estado de la cuenta, tamaño y
 * contenido de la lista, servidores, primer byte y bitrate de una muestra de canales, DNS y
 * latencia TCP. La lista se lee en streaming: nunca se retiene entera en memoria (44 MB en un
 * TV Box con heap de 256 MB provocarían GC y congelamientos).
 *
 * No guarda credenciales: los modelos solo exponen hosts, puertos y métricas.
 */
class IptvDiagnostics(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    data class Config(
        val serverUrl: String,
        val username: String = "",
        val password: String = "",
        /** Si no es null se descarga esta URL como lista (fuentes M3U). */
        val m3uListUrl: String? = null,
        val systemDnsServers: List<String> = emptyList(),
        val samples: Int = 3,
        val secondsPerSample: Int = 8
    )

    data class AccountInfo(
        val status: String,
        val authenticated: Boolean,
        val expiry: String?,
        val daysRemaining: Int?,
        val activeConnections: Int?,
        val maxConnections: Int?,
        val formats: List<String>,
        val isTrial: Boolean
    )

    data class PlaylistInfo(
        val entries: Int,
        val live: Int,
        val movies: Int,
        val series: Int,
        val sizeBytes: Long,
        val downloadMs: Long,
        val hostCounts: List<Pair<String, Int>>
    ) {
        val sizeMb: Double get() = sizeBytes / 1_000_000.0
        val downloadSeconds: Double get() = downloadMs / 1000.0
        val downloadMbps: Double get() = if (downloadMs > 0) sizeBytes * 8.0 / downloadMs / 1000.0 else 0.0
    }

    data class DnsInfo(
        val host: String,
        val routerServer: String?,
        val routerMs: Long?,
        val publicMs: Long?
    )

    data class HostInfo(
        val role: String,
        val host: String,
        val port: Int,
        val address: String?,
        val tcpAvgMs: Long?,
        val tcpOk: Int,
        val tcpAttempts: Int,
        val location: String? = null
    )

    data class ChannelProbe(
        val number: Int,
        val host: String,
        val port: Int,
        val ttfbMs: Long,
        val bitrateMbps: Double,
        val bytes: Long,
        val error: String? = null
    ) {
        val ok: Boolean get() = error == null
    }

    data class Report(
        val measuredAt: Long,
        val elapsedMs: Long,
        val account: AccountInfo?,
        val playlist: PlaylistInfo?,
        val dns: DnsInfo?,
        val panel: HostInfo?,
        val videoServer: HostInfo?,
        val channels: List<ChannelProbe>,
        val warnings: List<String>,
        val problems: List<String>
    ) {
        val verdict: String
            get() = when {
                problems.isNotEmpty() -> "NO SIRVE TAL COMO ESTÁ"
                warnings.isNotEmpty() -> "SIRVE CON AVISOS"
                else -> "SIRVE"
            }

        val verdictDetail: String
            get() = when {
                problems.isNotEmpty() -> problems.joinToString(" · ")
                warnings.isNotEmpty() -> warnings.joinToString(" · ")
                else -> "La cuenta está activa y entrega video sin problemas medibles."
            }
    }

    private data class PlaylistScan(
        val info: PlaylistInfo,
        val liveUrls: List<String>
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun run(
        config: Config,
        onProgress: suspend (String) -> Unit = {}
    ): Report = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val base = config.serverUrl.trim().trimEnd('/')
        val user = config.username.trim()
        val pass = config.password.trim()

        val account = if (user.isNotBlank() && pass.isNotBlank()) {
            onProgress("Consultando cuenta…")
            fetchAccount(base, user, pass)
        } else {
            null
        }

        val listUrl = config.m3uListUrl?.takeIf { it.isNotBlank() }
            ?: buildListUrl(base, user, pass)

        val playlist = try {
            if (listUrl.isBlank()) null else fetchPlaylist(listUrl, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("IptvDiagnostics", "Lista no descargada: ${error.message}")
            null
        }

        val panelHost = hostOf(base)
        val panelPort = portOf(base)
        val dns = panelHost?.let {
            onProgress("Midiendo DNS y latencia del panel…")
            measureDns(it, config.systemDnsServers)
        }
        val panel = if (panelHost != null) {
            probeTcp(
                role = "Panel",
                host = panelHost,
                port = panelPort,
                location = fetchLocation(panelHost)
            )
        } else {
            null
        }

        val channels = mutableListOf<ChannelProbe>()
        var videoServer: HostInfo? = null
        val sampleUrls = playlist?.let { sampleUrls(it.liveUrls, config.samples) }.orEmpty()
        sampleUrls.forEachIndexed { index, url ->
            onProgress("Probando canal ${index + 1} de ${sampleUrls.size}…")
            val probe = try {
                probeChannel(index + 1, url, config.secondsPerSample)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ChannelProbe(
                    number = index + 1,
                    host = hostOf(url) ?: "desconocido",
                    port = portOf(url),
                    ttfbMs = 0,
                    bitrateMbps = 0.0,
                    bytes = 0,
                    error = error.message ?: error.javaClass.simpleName
                )
            }
            channels.add(probe)
            if (videoServer == null && probe.ok) {
                videoServer = probeTcp(role = "Servidor de video", host = probe.host, port = probe.port)
            }
        }

        val warnings = mutableListOf<String>()
        val problems = mutableListOf<String>()

        account?.let { acc ->
            if (!acc.authenticated) problems.add("cuenta no válida (${acc.status})")
            val max = acc.maxConnections
            val active = acc.activeConnections
            if (max != null && active != null && active >= max) {
                warnings.add("conexiones al límite ($active/$max)")
            }
            if (acc.daysRemaining != null && acc.daysRemaining < 30) {
                warnings.add("la cuenta vence en ${acc.daysRemaining} días")
            }
            if (acc.isTrial) warnings.add("es una cuenta de prueba")
        }

        playlist?.let { list ->
            if (list.info.live == 0) problems.add("la lista no tiene canales en vivo")
            if (list.info.sizeMb > 15) {
                warnings.add("lista pesada (%.1f MB)".format(Locale.US, list.info.sizeMb))
            }
            if (list.info.downloadSeconds > 8) {
                warnings.add("descarga lenta (%.1f s)".format(Locale.US, list.info.downloadSeconds))
            }
        } ?: problems.add("no se pudo descargar la lista")

        if (channels.isNotEmpty() && channels.none { it.ok }) {
            problems.add("ningún canal de la muestra entregó video")
        } else {
            val slow = channels.count { it.ok && it.ttfbMs > 2_000 }
            if (slow > 0) warnings.add("$slow canal(es) tardan más de 2 s en arrancar")
            val failed = channels.count { !it.ok }
            if (failed > 0) warnings.add("$failed canal(es) de la muestra no abrieron")
            val lowBitrate = channels.count { it.ok && it.bitrateMbps <= 1.0 }
            if (lowBitrate > 0) warnings.add("$lowBitrate canal(es) con bitrate muy bajo")
        }

        videoServer?.let { server ->
            val ms = server.tcpAvgMs
            if (server.tcpOk == 0) problems.add("el servidor de video no acepta conexiones TCP")
            else if (ms != null && ms > 250) problems.add("servidor de video a $ms ms")
            else if (ms != null && ms > 120) warnings.add("servidor de video a $ms ms")
        }

        dns?.let { d ->
            val public = d.publicMs
            val router = d.routerMs
            if (public != null && public > 150) warnings.add("DNS lento (${public} ms)")
            if (router != null && public != null && router > public * 4 && router > 100) {
                warnings.add("el DNS del router tarda ${router} ms")
            }
        }

        Report(
            measuredAt = System.currentTimeMillis(),
            elapsedMs = System.currentTimeMillis() - started,
            account = account,
            playlist = playlist?.info,
            dns = dns,
            panel = panel,
            videoServer = videoServer,
            channels = channels,
            warnings = warnings.distinct(),
            problems = problems.distinct()
        )
    }

    // ─────────────────────────────────────────────── cuenta

    private fun fetchAccount(base: String, user: String, pass: String): AccountInfo? {
        if (base.isBlank()) return null
        val url = "$base/player_api.php?username=${encode(user)}&password=${encode(pass)}"
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                val info = json.parseToJsonElement(body).jsonObject["user_info"]?.jsonObject ?: return null
                val status = info["status"]?.jsonPrimitive?.contentOrNull ?: "desconocido"
                val authRaw = info["auth"]?.jsonPrimitive?.contentOrNull
                val expSeconds = info["exp_date"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                AccountInfo(
                    status = status,
                    authenticated = when (authRaw) {
                        null -> status.equals("Active", ignoreCase = true)
                        "1", "true" -> true
                        else -> false
                    },
                    expiry = expSeconds?.let {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(it * 1000L))
                    },
                    daysRemaining = expSeconds?.let {
                        ((it * 1000L - System.currentTimeMillis()) / 86_400_000.0).toInt()
                    },
                    activeConnections = info["active_cons"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    maxConnections = info["max_connections"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    formats = (info["allowed_output_formats"] as? JsonArray)
                        ?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                        .orEmpty(),
                    isTrial = info["is_trial"]?.jsonPrimitive?.contentOrNull.let { it == "1" || it == "true" }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("IptvDiagnostics", "Cuenta no consultada: ${error.message}")
            null
        }
    }

    private fun buildListUrl(base: String, user: String, pass: String): String {
        if (base.isBlank()) return ""
        return if (user.isNotBlank() && pass.isNotBlank()) {
            "$base/get.php?username=${encode(user)}&password=${encode(pass)}&type=m3u_plus"
        } else {
            base
        }
    }

    // ─────────────────────────────────────────────── lista (streaming)

    private suspend fun fetchPlaylist(
        url: String,
        onProgress: suspend (String) -> Unit
    ): PlaylistScan {
        require(url.isNotBlank()) { "No hay URL de lista" }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val started = System.nanoTime()
        val hostCounts = HashMap<String, Int>()
        val liveUrls = ArrayList<String>(1024)
        var entries = 0
        var live = 0
        var movies = 0
        var series = 0

        val playlistClient = client.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        playlistClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("La lista respondió HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("La lista no devolvió contenido")
            val counting = object : ForwardingSource(body.source()) {
                var total = 0L
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) total += read
                    return read
                }
            }
            val source = counting.buffer()
            var lastProgressAt = 0L
            while (true) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) continue

                entries++
                when {
                    trimmed.contains("/movie/") -> movies++
                    trimmed.contains("/series/") -> series++
                    else -> {
                        live++
                        val host = hostOf(trimmed)
                        if (host != null) hostCounts[host] = (hostCounts[host] ?: 0) + 1
                        if (liveUrls.size < MAX_STORED_LIVE_URLS) liveUrls.add(trimmed)
                    }
                }

                val now = System.currentTimeMillis()
                if (now - lastProgressAt > 2_000) {
                    lastProgressAt = now
                    onProgress("Lista: %.1f MB…".format(Locale.US, counting.total / 1_000_000.0))
                }
            }

            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            val sizeBytes = if (counting.total > 0) counting.total else body.contentLength().coerceAtLeast(0)
            return PlaylistScan(
                info = PlaylistInfo(
                    entries = entries,
                    live = live,
                    movies = movies,
                    series = series,
                    sizeBytes = sizeBytes,
                    downloadMs = elapsedMs,
                    hostCounts = hostCounts.entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { it.key to it.value }
                ),
                liveUrls = liveUrls
            )
        }
    }

    private fun sampleUrls(liveUrls: List<String>, samples: Int): List<String> {
        if (liveUrls.isEmpty() || samples <= 0) return emptyList()
        val count = minOf(samples, liveUrls.size)
        if (count == 1) return listOf(liveUrls[0])
        return (0 until count).map { index ->
            val position = (index.toLong() * (liveUrls.size - 1) / (count - 1)).toInt()
            liveUrls[position]
        }
    }

    // ─────────────────────────────────────────────── canales

    /**
     * @param startedAtNs instante en que se inició la petición. El primer byte se mide desde
     * ahí (no desde que llegan las cabeceras) para que el dato incluya conexión y respuesta
     * del servidor, que es lo que percibe el usuario al cambiar de canal.
     */
    private fun probeChannel(
        number: Int,
        url: String,
        seconds: Int,
        followPlaylist: Boolean = true,
        startedAtNs: Long = System.nanoTime()
    ): ChannelProbe {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val probeClient = client.newBuilder()
            .readTimeout(seconds + 8L, TimeUnit.SECONDS)
            .build()

        probeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return ChannelProbe(
                    number = number,
                    host = hostOf(url) ?: "desconocido",
                    port = portOf(url),
                    ttfbMs = 0,
                    bitrateMbps = 0.0,
                    bytes = 0,
                    error = "HTTP ${response.code}"
                )
            }

            val contentType = response.header("Content-Type").orEmpty()
            if (followPlaylist && (contentType.contains("mpegurl", ignoreCase = true) ||
                    contentType.contains("m3u", ignoreCase = true))
            ) {
                val playlistText = response.body?.string().orEmpty()
                val segment = playlistText.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (segment != null) {
                    val segmentUrl = response.request.url.resolve(segment)?.toString() ?: url
                    return probeChannel(
                        number = number,
                        url = segmentUrl,
                        seconds = seconds,
                        followPlaylist = false,
                        startedAtNs = startedAtNs
                    )
                }
            }

            val input = response.body?.byteStream()
                ?: return ChannelProbe(number, hostOf(url) ?: "desconocido", portOf(url), 0, 0.0, 0, "sin cuerpo")
            val started = System.nanoTime()
            val deadline = started + seconds * 1_000_000_000L
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            var firstByteNs = 0L

            while (System.nanoTime() < deadline) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                if (firstByteNs == 0L) firstByteNs = System.nanoTime() - startedAtNs
                total += read
            }

            val elapsedMs = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
            val finalUrl = response.request.url
            return if (total == 0L) {
                ChannelProbe(
                    number = number,
                    host = finalUrl.host,
                    port = finalUrl.port,
                    ttfbMs = 0,
                    bitrateMbps = 0.0,
                    bytes = 0,
                    error = "sin datos"
                )
            } else {
                ChannelProbe(
                    number = number,
                    host = finalUrl.host,
                    port = finalUrl.port,
                    ttfbMs = firstByteNs / 1_000_000,
                    bitrateMbps = (total * 8.0 / elapsedMs).roundToInt() / 1000.0,
                    bytes = total
                )
            }
        }
    }

    // ─────────────────────────────────────────────── red

    private fun measureDns(host: String, systemDnsServers: List<String>): DnsInfo {
        val router = systemDnsServers.firstOrNull { it.isNotBlank() }
        return DnsInfo(
            host = host,
            routerServer = router,
            routerMs = router?.let { dnsLookupMs(host, it) },
            publicMs = dnsLookupMs(host, "1.1.1.1")
        )
    }

    private fun probeTcp(
        role: String,
        host: String,
        port: Int,
        attempts: Int = 3,
        timeoutMs: Int = 4_000,
        location: String? = null
    ): HostInfo {
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
        val times = mutableListOf<Long>()
        if (address != null) {
            repeat(attempts) {
                val socket = Socket()
                try {
                    val started = System.nanoTime()
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    times.add((System.nanoTime() - started) / 1_000_000)
                } catch (_: Exception) {
                    // Un intento fallido no invalida los demás.
                } finally {
                    runCatching { socket.close() }
                }
            }
        }
        return HostInfo(
            role = role,
            host = host,
            port = port,
            address = address?.hostAddress,
            tcpAvgMs = times.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toLong(),
            tcpOk = times.size,
            tcpAttempts = attempts,
            location = location
        )
    }

    private fun fetchLocation(host: String): String? {
        return try {
            val address = InetAddress.getByName(host).hostAddress ?: return null
            val request = Request.Builder()
                .url("http://ip-api.com/json/$address?fields=status,country,city,isp")
                .build()
            client.newBuilder().readTimeout(6, TimeUnit.SECONDS).build()
                .newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    if (root["status"]?.jsonPrimitive?.contentOrNull != "success") return null
                    val city = root["city"]?.jsonPrimitive?.contentOrNull
                    val country = root["country"]?.jsonPrimitive?.contentOrNull
                    listOfNotNull(city, country).joinToString(", ").ifBlank { null }
                }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Consulta DNS A por UDP y devuelve el tiempo hasta la respuesta. No usa la caché del
     * sistema, por eso sirve para comparar el DNS del router con 1.1.1.1.
     */
    private fun dnsLookupMs(host: String, server: String, timeoutMs: Int = 3_000): Long? {
        return try {
            val id = (host.hashCode() * 31 + server.hashCode()) and 0xFFFF
            val query = buildDnsQuery(host, id)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName(server)
                val started = System.nanoTime()
                socket.send(DatagramPacket(query, query.size, address, DNS_PORT))
                val responseBuffer = ByteArray(512)
                val packet = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(packet)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                val responseId = ((responseBuffer[0].toInt() and 0xFF) shl 8) or
                    (responseBuffer[1].toInt() and 0xFF)
                if (responseId == id) elapsed else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildDnsQuery(host: String, id: Int): ByteArray {
        val labels = host.trimEnd('.').split('.').filter { it.isNotBlank() }
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val out = ByteArray(size)
        out[0] = ((id shr 8) and 0xFF).toByte()
        out[1] = (id and 0xFF).toByte()
        out[2] = 0x01 // Recursion desired
        out[5] = 0x01 // QDCOUNT = 1
        var index = 12
        labels.forEach { label ->
            out[index++] = label.length.toByte()
            label.toByteArray(Charsets.US_ASCII).forEach { out[index++] = it }
        }
        out[index++] = 0
        out[index++] = 0
        out[index++] = 1 // QTYPE = A
        out[index++] = 0
        out[index] = 1 // QCLASS = IN
        return out
    }

    // ─────────────────────────────────────────────── utilidades

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun portOf(url: String): Int = runCatching {
        val uri = URI(url)
        if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
    }.getOrDefault(80)

    companion object {
        private const val USER_AGENT = "IPTVSmartersPlayer"
        private const val DNS_PORT = 53
        private const val MAX_STORED_LIVE_URLS = 5_000
    }
}
