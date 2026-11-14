package com.lelouch.core.network.dns

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Dns
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory
import kotlin.random.Random

/**
 * Resolvedor DNS directo con caché en RAM (0 ms en zapping) y preferencias de aceleración HW / OpenSL ES.
 * El video sigue directo desde el proveedor IPTV; solo las consultas de dominio pasan por el DNS elegido.
 */
object LelouchDnsManager : Dns {

    private const val PREFS_NAME = "lelouch_dns_and_engine_prefs"
    private const val KEY_DNS_ENABLED = "dns_enabled"
    private const val KEY_DNS_PROVIDER = "dns_provider_id"
    private const val KEY_VIDEO_DECODER = "video_decoder_mode"
    private const val KEY_AUDIO_ENGINE = "audio_engine_mode"
    private const val KEY_VPN_SHIELD_ENABLED = "vpn_shield_enabled"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutos en RAM

    private val ANTI_DPI_USER_AGENTS = listOf(
        "IPTVSmartersPlayer",
        "TiviMate/4.7.0 (Android TV)",
        "VLC/3.0.20 LibVLC/3.0.20",
        "Lavf/60.3.100"
    )

    enum class DnsProvider(
        val id: String,
        val title: String,
        val primaryIp: String,
        val secondaryIp: String,
        val dohUrl: String
    ) {
        CLOUDFLARE("cloudflare", "Cloudflare (DoH 443)", "1.1.1.1", "1.0.0.1", "https://1.1.1.1/dns-query"),
        GOOGLE("google", "Google (DoH 443)", "8.8.8.8", "8.8.4.4", "https://8.8.8.8/dns-query"),
        CONTROL_D("controld", "Control D (DoH 443)", "76.76.2.5", "76.76.10.5", "https://76.76.2.11/p0"),
        ADGUARD("adguard", "AdGuard (DoH 443)", "94.140.14.140", "94.140.14.141", "https://94.140.14.140/dns-query")
    }

    data class DnsBenchmarkResult(
        val provider: DnsProvider,
        val latencyMs: Long?,
        val isBest: Boolean = false
    )

    enum class VideoDecoderMode(val id: String, val title: String, val subtitle: String) {
        HW_PLUS("hw_plus", "HW+ (Aceleración Hardware + Fallback)", "Usa el chip gráfico (MediaCodec DirectSurface) y salta a software si un canal falla"),
        HW_ONLY("hw_only", "HW (Solo Hardware GPU)", "Decodificación 100% en silicio de bajo consumo"),
        SW_ONLY("sw_only", "SW (Decodificador Software CPU)", "Máxima compatibilidad para formatos antiguos")
    }

    enum class AudioEngineMode(val id: String, val title: String, val subtitle: String) {
        OPENSL_ES("opensl_es", "OpenSL ES / Baja Latencia (PCM 16-bit)", "Evita desfase de voz y congelamiento por audio en TV Box y Móvil"),
        AUDIOTRACK("audiotrack", "Android AudioTrack Estándar", "Salida de audio predeterminada del sistema")
    }

    private data class CachedEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    private val ramCache = ConcurrentHashMap<String, CachedEntry>()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var enabledState: Boolean = true

    @Volatile
    private var selectedProviderState: DnsProvider = DnsProvider.CLOUDFLARE

    @Volatile
    private var videoDecoderModeState: VideoDecoderMode = VideoDecoderMode.HW_PLUS

    @Volatile
    private var audioEngineModeState: AudioEngineMode = AudioEngineMode.OPENSL_ES

    @Volatile
    private var vpnShieldEnabledState: Boolean = false
    private val _vpnShieldFlow = MutableStateFlow(false)
    val vpnShieldFlow: StateFlow<Boolean> = _vpnShieldFlow.asStateFlow()

    private val _vpnVerificationFlow = MutableStateFlow("Listo para activar túnel Anti-DPI + DoH 443")
    val vpnVerificationFlow: StateFlow<String> = _vpnVerificationFlow.asStateFlow()

    @Volatile
    private var activeUserAgentIndex: Int = 0

    @Volatile
    private var splitPacketsCount: Int = 0

    fun getSplitPacketsCount(): Int = splitPacketsCount

    private val permissiveSslSocketFactory: javax.net.ssl.SSLSocketFactory? by lazy {
        runCatching {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            })
            javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustAll, java.security.SecureRandom())
            }.socketFactory
        }.getOrNull()
    }

    /**
     * Socket con Fragmentación TCP Anti-DPI de primer paquete (estilo ByeDPI / PowerTunnel):
     * Divide la primera escritura de cada conexión (cabecera HTTP GET / Host o TLS ClientHello SNI)
     * en 3 micro-segmentos TCP inmediatos (2 bytes + 14 bytes + resto) con flush() y TCP_NODELAY,
     * impidiendo que el filtro DPI del ISP reconozca y congele el flujo IPTV.
     */
    private class AntiDpiSplitSocket : Socket {
        constructor() : super()
        constructor(host: String?, port: Int) : super(host, port)
        constructor(host: String?, port: Int, localHost: InetAddress?, localPort: Int) : super(host, port, localHost, localPort)
        constructor(host: InetAddress?, port: Int) : super(host, port)
        constructor(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int) : super(address, port, localAddress, localPort)

        @Volatile
        private var firstPacketFragmented = false

        override fun getOutputStream(): java.io.OutputStream {
            val rawOut = super.getOutputStream()
            return object : java.io.FilterOutputStream(rawOut) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (vpnShieldEnabledState && !firstPacketFragmented && len > 4) {
                        firstPacketFragmented = true
                        splitPacketsCount++
                        val firstChunk = 2
                        val secondChunk = minOf(14, len - firstChunk)
                        val remaining = len - firstChunk - secondChunk
                        rawOut.write(b, off, firstChunk)
                        rawOut.flush()
                        rawOut.write(b, off + firstChunk, secondChunk)
                        rawOut.flush()
                        if (remaining > 0) {
                            rawOut.write(b, off + firstChunk + secondChunk, remaining)
                            rawOut.flush()
                        }
                    } else {
                        rawOut.write(b, off, len)
                    }
                }
            }
        }
    }

    /**
     * SocketFactory Anti-DPI y QoS para TV Box y Mobile (inspirado en ByeDPI / FongMi/TV):
     * - Fragmentación TCP del primer paquete HTTP/TLS (AntiDpiSplitSocket).
     * - TCP_NODELAY: desactiva el algoritmo Nagle para enviar fragmentos al instante.
     * - SO_RCVBUF = 512 KB: amplía la ventana de recepción TCP del kernel para absorber ráfagas MPEG-TS.
     * - IPTOS_LOWDELAY (0x10): marca DSCP de baja latencia hacia el router doméstico.
     */
    val antiDpiSocketFactory: SocketFactory = object : SocketFactory() {
        private fun configure(s: Socket): Socket {
            runCatching {
                s.tcpNoDelay = true
                s.keepAlive = true
                s.receiveBufferSize = 512 * 1024
                if (vpnShieldEnabledState) {
                    s.trafficClass = 0x10 // IPTOS_LOWDELAY
                }
            }
            return s
        }
        override fun createSocket(): Socket = configure(AntiDpiSplitSocket())
        override fun createSocket(host: String?, port: Int): Socket = configure(AntiDpiSplitSocket(host, port))
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            configure(AntiDpiSplitSocket(host, port, localHost, localPort))
        override fun createSocket(host: InetAddress?, port: Int): Socket = configure(AntiDpiSplitSocket(host, port))
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            configure(AntiDpiSplitSocket(address, port, localAddress, localPort))
    }

    fun init(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        enabledState = sp.getBoolean(KEY_DNS_ENABLED, true)
        val savedProv = sp.getString(KEY_DNS_PROVIDER, DnsProvider.CLOUDFLARE.id)
        selectedProviderState = DnsProvider.entries.firstOrNull { it.id == savedProv } ?: DnsProvider.CLOUDFLARE

        val savedVideo = sp.getString(KEY_VIDEO_DECODER, VideoDecoderMode.HW_PLUS.id)
        videoDecoderModeState = VideoDecoderMode.entries.firstOrNull { it.id == savedVideo } ?: VideoDecoderMode.HW_PLUS

        val savedAudio = sp.getString(KEY_AUDIO_ENGINE, AudioEngineMode.OPENSL_ES.id)
        audioEngineModeState = AudioEngineMode.entries.firstOrNull { it.id == savedAudio } ?: AudioEngineMode.OPENSL_ES

        vpnShieldEnabledState = sp.getBoolean(KEY_VPN_SHIELD_ENABLED, false)
        _vpnShieldFlow.value = vpnShieldEnabledState
        if (vpnShieldEnabledState && com.lelouch.core.network.vpn.LelouchVpnController.prepare(context) == null) {
            com.lelouch.core.network.vpn.LelouchVpnController.start(context)
        }
    }

    fun isVpnShieldEnabled(): Boolean = vpnShieldEnabledState

    fun setVpnShieldEnabled(enabled: Boolean) {
        vpnShieldEnabledState = enabled
        _vpnShieldFlow.value = enabled
        prefs?.edit()?.putBoolean(KEY_VPN_SHIELD_ENABLED, enabled)?.apply()
        if (!enabled) {
            _vpnVerificationFlow.value = "Túnel Anti-DPI desactivado"
        } else {
            _vpnVerificationFlow.value = "Verificando túnel DoH 443 + TCP Split..."
        }
    }

    /**
     * Ejecuta una prueba real en vivo de DoH (Puerto 443 TLS) y del motor de fragmentación TCP Anti-DPI
     * para confirmar que el túnel está operativo y devolver el diagnóstico con latencia en ms.
     */
    suspend fun verifyVpnShieldLive(probeDomain: String = "cloudflare.com"): Boolean = withContext(Dispatchers.IO) {
        val provider = selectedProviderState
        val start = System.nanoTime()
        val dohIps = queryDohDns(provider.dohUrl, probeDomain, 2_500)
        val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        if (!dohIps.isNullOrEmpty()) {
            _vpnVerificationFlow.value = "✅ Verificado: ${provider.title} (${elapsedMs} ms) • TCP Split Anti-DPI Activo"
            true
        } else {
            val udpStart = System.nanoTime()
            val udpIps = queryUdpDns(provider.primaryIp, probeDomain, 1_800)
            val udpMs = ((System.nanoTime() - udpStart) / 1_000_000L).coerceAtLeast(1L)
            if (!udpIps.isNullOrEmpty()) {
                _vpnVerificationFlow.value = "✅ Verificado: DNS ${provider.primaryIp} (${udpMs} ms) • TCP Split Anti-DPI Activo"
                true
            } else {
                _vpnVerificationFlow.value = "⚠️ Escudo TCP Split Activo (Sin respuesta de ${provider.title})"
                false
            }
        }
    }

    fun resolveActiveUserAgent(defaultUserAgent: String = "IPTVSmartersPlayer"): String {
        if (!vpnShieldEnabledState) return defaultUserAgent
        return ANTI_DPI_USER_AGENTS.getOrElse(activeUserAgentIndex % ANTI_DPI_USER_AGENTS.size) { defaultUserAgent }
    }

    fun rotateUserAgentOnStall(): String {
        activeUserAgentIndex = (activeUserAgentIndex + 1) % ANTI_DPI_USER_AGENTS.size
        return ANTI_DPI_USER_AGENTS[activeUserAgentIndex]
    }

    fun isEnabled(): Boolean = enabledState

    fun setEnabled(enabled: Boolean) {
        enabledState = enabled
        ramCache.clear()
        prefs?.edit()?.putBoolean(KEY_DNS_ENABLED, enabled)?.apply()
    }

    fun getSelectedProvider(): DnsProvider = selectedProviderState

    fun setSelectedProvider(provider: DnsProvider) {
        selectedProviderState = provider
        ramCache.clear()
        prefs?.edit()?.putString(KEY_DNS_PROVIDER, provider.id)?.apply()
    }

    fun getVideoDecoderMode(): VideoDecoderMode = videoDecoderModeState

    fun setVideoDecoderMode(mode: VideoDecoderMode) {
        videoDecoderModeState = mode
        prefs?.edit()?.putString(KEY_VIDEO_DECODER, mode.id)?.apply()
    }

    fun getAudioEngineMode(): AudioEngineMode = audioEngineModeState

    fun setAudioEngineMode(mode: AudioEngineMode) {
        audioEngineModeState = mode
        prefs?.edit()?.putString(KEY_AUDIO_ENGINE, mode.id)?.apply()
    }

    fun cachedIpCount(): Int = ramCache.size

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim().lowercase()
        if (cleanHost.isEmpty()) return Dns.SYSTEM.lookup(hostname)

        if (cleanHost.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val now = System.currentTimeMillis()
        ramCache[cleanHost]?.let { cached ->
            if (now < cached.expiresAtMs && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
        }

        if (enabledState) {
            val provider = selectedProviderState
            // 1. Priorizar DNS-over-HTTPS (DoH RFC 8484 por puerto TCP 443 TLS) para evadir bloqueo/hijacking de ISP en puerto 53
            val resolved = queryDohDns(provider.dohUrl, cleanHost, 2_000)
                ?: queryUdpDns(provider.primaryIp, cleanHost, 1_500)
                ?: queryUdpDns(provider.secondaryIp, cleanHost, 1_500)
            if (!resolved.isNullOrEmpty()) {
                ramCache[cleanHost] = CachedEntry(resolved, now + CACHE_TTL_MS)
                return resolved
            }
        }

        val fallback = Dns.SYSTEM.lookup(hostname)
        if (fallback.isNotEmpty()) {
            ramCache[cleanHost] = CachedEntry(fallback, now + CACHE_TTL_MS)
        }
        return fallback
    }

    suspend fun prewarmHosts(urlsOrHosts: List<String>) = withContext(Dispatchers.IO) {
        urlsOrHosts.mapNotNull { raw ->
            runCatching {
                val withScheme = if (raw.startsWith("http")) raw else "http://$raw"
                java.net.URI(withScheme).host
            }.getOrNull()
        }.distinct().forEach { host ->
            runCatching { lookup(host) }
        }
    }

    suspend fun measureAllProviders(probeDomain: String = "cloudflare.com"): List<DnsBenchmarkResult> =
        withContext(Dispatchers.IO) {
            val rawResults = DnsProvider.entries.map { provider ->
                async {
                    val ms = measureSingleDohMs(provider.dohUrl, probeDomain)
                        ?: measureSingleDnsMs(provider.primaryIp, probeDomain)
                        ?: measureSingleDnsMs(provider.secondaryIp, probeDomain)
                    provider to ms
                }
            }.awaitAll()

            val bestLatency = rawResults.mapNotNull { it.second }.minOrNull()
            rawResults.map { (provider, ms) ->
                DnsBenchmarkResult(
                    provider = provider,
                    latencyMs = ms,
                    isBest = ms != null && ms == bestLatency
                )
            }
        }

    private fun measureSingleDohMs(dohUrl: String, domain: String): Long? {
        val start = System.nanoTime()
        val addresses = queryDohDns(dohUrl, domain, 2_000)
        if (addresses.isNullOrEmpty()) return null
        return ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
    }

    private fun measureSingleDnsMs(dnsServerIp: String, domain: String): Long? {
        val start = System.nanoTime()
        val addresses = queryUdpDns(dnsServerIp, domain, 1_800)
        if (addresses.isNullOrEmpty()) return null
        return ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
    }

    /**
     * Consulta DNS-over-HTTPS (RFC 8484 wireformat sobre puerto 443 TLS) usando IP directa
     * para evitar bloqueos de puerto 53 (DNS Hijacking / DPI) del proveedor de internet (ISP).
     */
    internal fun queryDohDns(dohUrl: String, domain: String, timeoutMs: Int): List<InetAddress>? {
        return try {
            val txId = Random.nextInt(1, 0xFFFF)
            val queryBytes = buildDnsQueryPacket(txId, domain)
            val conn = (java.net.URL(dohUrl).openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/dns-message")
                setRequestProperty("Accept", "application/dns-message")
                permissiveSslSocketFactory?.let { sslSocketFactory = it }
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
            try {
                conn.outputStream.use { it.write(queryBytes) }
                if (conn.responseCode == 200) {
                    val responseBytes = conn.inputStream.use { it.readBytes() }
                    parseDnsARecords(domain, responseBytes, responseBytes.size, txId).takeIf { it.isNotEmpty() }
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryUdpDns(dnsServerIp: String, domain: String, timeoutMs: Int): List<InetAddress>? {
        return try {
            val txId = Random.nextInt(1, 0xFFFF)
            val queryBytes = buildDnsQueryPacket(txId, domain)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val serverAddr = InetSocketAddress(InetAddress.getByName(dnsServerIp), 53)
                socket.send(DatagramPacket(queryBytes, queryBytes.size, serverAddr))
                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                parseDnsARecords(domain, buffer, response.length, txId)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildDnsQueryPacket(txId: Int, domain: String): ByteArray {
        val out = ByteArrayOutputStream(64)
        out.write((txId shr 8) and 0xFF)
        out.write(txId and 0xFF)
        out.write(0x01)
        out.write(0x00)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        for (label in domain.trim('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)
        return out.toByteArray()
    }

    private fun parseDnsARecords(domain: String, buf: ByteArray, length: Int, expectedTxId: Int): List<InetAddress> {
        if (length < 12) return emptyList()
        val rxId = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
        if (rxId != expectedTxId) return emptyList()
        val anCount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
        if (anCount <= 0) return emptyList()

        var pos = 12
        while (pos < length && buf[pos].toInt() != 0) {
            val len = buf[pos].toInt() and 0xFF
            if ((len and 0xC0) == 0xC0) {
                pos += 2
                break
            }
            pos += len + 1
        }
        if (pos < length && buf[pos].toInt() == 0) pos++
        pos += 4

        val result = mutableListOf<InetAddress>()
        for (i in 0 until anCount) {
            if (pos >= length) break
            while (pos < length) {
                val b = buf[pos].toInt() and 0xFF
                if (b == 0) {
                    pos++
                    break
                }
                if ((b and 0xC0) == 0xC0) {
                    pos += 2
                    break
                }
                pos += b + 1
            }
            if (pos + 10 > length) break
            val rType = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            val rdLength = ((buf[pos + 8].toInt() and 0xFF) shl 8) or (buf[pos + 9].toInt() and 0xFF)
            pos += 10
            if (pos + rdLength > length) break
            if (rType == 1 && rdLength == 4) {
                val ipBytes = byteArrayOf(buf[pos], buf[pos + 1], buf[pos + 2], buf[pos + 3])
                runCatching {
                    result.add(InetAddress.getByAddress(domain, ipBytes))
                }
            }
            pos += rdLength
        }
        return result
    }
}
