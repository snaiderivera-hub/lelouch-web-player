package com.lelouch.core.network.dns

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Dns
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
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
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutos en RAM

    enum class DnsProvider(
        val id: String,
        val title: String,
        val primaryIp: String,
        val secondaryIp: String
    ) {
        CLOUDFLARE("cloudflare", "Cloudflare", "1.1.1.1", "1.0.0.1"),
        GOOGLE("google", "Google", "8.8.8.8", "8.8.4.4"),
        CONTROL_D("controld", "Control D Uncensored", "76.76.2.5", "76.76.10.5"),
        ADGUARD("adguard", "AdGuard sans filtre", "94.140.14.140", "94.140.14.141")
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
            val resolved = queryUdpDns(provider.primaryIp, cleanHost, 1_500)
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
                    val ms = measureSingleDnsMs(provider.primaryIp, probeDomain)
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

    private fun measureSingleDnsMs(dnsServerIp: String, domain: String): Long? {
        val start = System.nanoTime()
        val addresses = queryUdpDns(dnsServerIp, domain, 1_800)
        if (addresses.isNullOrEmpty()) return null
        return ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
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
