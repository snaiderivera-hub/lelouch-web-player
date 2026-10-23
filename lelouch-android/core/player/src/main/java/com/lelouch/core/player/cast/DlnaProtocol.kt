package com.lelouch.core.player.cast

import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/**
 * FASE 38 · Protocolo DLNA/UPnP mínimo, sin dependencias externas.
 *
 * Esta parte es **pura** (sin Android, sin red): construye y analiza los mensajes. Así se
 * puede probar con JUnit y el [DlnaCastManager] queda reducido a transporte.
 *
 * Alcance deliberado: descubrimiento SSDP de renderers + AVTransport (SetAVTransportURI,
 * Play, Pause, Stop). No implementa control de volumen, posición ni ContentDirectory.
 */
internal object DlnaProtocol {

    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"

    private const val SSDP_ADDRESS = "239.255.255.250"
    const val SSDP_PORT = 1900

    /** Dirección del grupo SSDP; el manager la usa para el envío multicast. */
    fun ssdpAddress(): String = SSDP_ADDRESS

    /**
     * M-SEARCH dirigido a renderers multimedia. Un solo datagrama basta para la mayoría de
     * equipos; el manager repite el envío porque UDP no garantiza entrega.
     */
    fun searchMessage(): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 3\r\n")
        append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n")
        append("USER-AGENT: Lelouch/1.0 UPnP/1.1\r\n")
        append("\r\n")
    }

    /**
     * Extrae la URL de descripción de una respuesta SSDP.
     * Devuelve `null` si no es un 200 OK con `LOCATION` utilizable.
     */
    fun parseSsdpLocation(raw: String): String? {
        val lines = raw.lineSequence().map { it.trim() }.toList()
        if (lines.isEmpty()) return null
        val statusOk = lines.first().uppercase().startsWith("HTTP/1.1 200") ||
            lines.first().uppercase().startsWith("HTTP/1.0 200")
        if (!statusOk) return null
        val location = lines
            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.trimEnd('\r')
            ?: return null
        if (location.isEmpty()) return null
        return runCatching { URI(location) }.getOrNull()?.takeIf { it.scheme == "http" }?.toString()
    }

    /**
     * Analiza la descripción XML del dispositivo y devuelve su servicio AVTransport.
     *
     * [location] es la URL desde la que se descargó el XML: las rutas relativas de
     * `controlURL` se resuelven contra ella, como exige UPnP.
     */
    fun parseDeviceDescription(xml: String, location: String): ParsedDevice? {
        val document = parseXml(xml) ?: return null
        val device = document.getElementsByTagName("device").item(0) ?: return null

        fun childText(parent: org.w3c.dom.Node, tag: String): String? =
            (parent as? org.w3c.dom.Element)
                ?.getElementsByTagName(tag)
                ?.item(0)
                ?.textContent
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        val friendlyName = childText(device, "friendlyName") ?: return null
        val udn = childText(device, "UDN") ?: return null
        val services = device.let { d ->
            (d as org.w3c.dom.Element).getElementsByTagName("service")
        }
        var controlPath: String? = null
        for (i in 0 until services.length) {
            val service = services.item(i)
            val type = childText(service, "serviceType") ?: continue
            if (!type.startsWith("urn:schemas-upnp-org:service:AVTransport:")) continue
            controlPath = childText(service, "controlURL")
            if (!controlPath.isNullOrBlank()) break
        }
        val controlUrl = controlPath?.let { resolveUrl(location, it) } ?: return null

        return ParsedDevice(
            friendlyName = friendlyName,
            udn = udn,
            controlUrl = controlUrl,
            modelName = childText(device, "modelName"),
            location = location
        )
    }

    /** Resuelve `controlURL` (absoluta o relativa) contra la URL de descripción. */
    fun resolveUrl(base: String, reference: String): String? = runCatching {
        URI(base).resolve(reference).toString()
    }.getOrNull()

    /** Metadatos DIDL-Lite que muchos renderers necesitan para aceptar el recurso. */
    fun buildMetadata(url: String, title: String, mimeType: String = mimeTypeFor(url)): String =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"" +
            " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
            " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escape(title)}</dc:title>" +
            "<upnp:class>object.item.videoItem</upnp:class>" +
            // FLAGS declara modo streaming; sin ellas varios televisores aceptan la orden
            // pero no abren el recurso.
            "<res protocolInfo=\"http-get:*:${escape(mimeType)}:DLNA.ORG_OP=01;DLNA.ORG_CI=0;" +
            "DLNA.ORG_FLAGS=01700000000000000000000000000000\">" +
            "${escape(url)}</res>" +
            "</item></DIDL-Lite>"

    fun setAvTransportUriBody(url: String, metadata: String): String = envelope(
        action = "SetAVTransportURI",
        arguments = "<InstanceID>0</InstanceID>" +
            "<CurrentURI>${escape(url)}</CurrentURI>" +
            "<CurrentURIMetaData>${escape(metadata)}</CurrentURIMetaData>"
    )

    fun playBody(): String = envelope("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")

    fun pauseBody(): String = envelope("Pause", "<InstanceID>0</InstanceID>")

    fun stopBody(): String = envelope("Stop", "<InstanceID>0</InstanceID>")

    /** Consulta el estado real del renderer para no dar por buena una orden ignorada. */
    fun getTransportInfoBody(): String = envelope("GetTransportInfo", "<InstanceID>0</InstanceID>")

    /**
     * Extrae el estado de transporte de la respuesta de `GetTransportInfo`.
     * Devuelve `null` si la respuesta no contiene `CurrentTransportState` (renderer sin
     * soporte para esta acción).
     */
    fun parseTransportInfo(xml: String): TransportInfo? {
        val document = parseXml(xml) ?: return null
        val state = firstElementText(document, "CurrentTransportState") ?: return null
        return TransportInfo(
            state = state,
            status = firstElementText(document, "CurrentTransportStatus").orEmpty(),
            speed = firstElementText(document, "CurrentSpeed").orEmpty()
        )
    }

    /** Busca por nombre local: algunas respuestas usan prefijo (`u:CurrentTransportState`). */
    private fun firstElementText(document: org.w3c.dom.Document, tag: String): String? {
        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? org.w3c.dom.Element ?: continue
            if (element.nodeName.substringAfter(':') != tag) continue
            return element.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun envelope(action: String, arguments: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$AV_TRANSPORT\">$arguments</u:$action></s:Body>" +
            "</s:Envelope>"

    fun soapActionHeader(action: String): String = "\"$AV_TRANSPORT#$action\""

    /**
     * Tipo MIME aproximado por extensión; el renderer decide si puede reproducirlo.
     *
     * `protocolInfo` no admite comodines de tipo, así que cuando la URL no trae
     * extensión se usa el contenedor más probable: MPEG-TS en directo y MP4 en VOD.
     */
    fun mimeTypeFor(url: String, isLive: Boolean = false): String {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
        return when (path.substringAfterLast('.', "").lowercase()) {
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "ts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            else -> if (isLive) "video/mp2t" else "video/mp4"
        }
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun parseXml(xml: String): org.w3c.dom.Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        // Endurecido: la descripción viene de la red local y no debe resolver entidades externas.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.isExpandEntityReferences = false
        factory.isNamespaceAware = false
        factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    }.getOrNull()

    data class ParsedDevice(
        val friendlyName: String,
        val udn: String,
        val controlUrl: String,
        val modelName: String?,
        val location: String
    )

    /** Estado devuelto por `GetTransportInfo` (valores UPnP sin normalizar). */
    data class TransportInfo(
        val state: String,
        val status: String,
        val speed: String
    )
}

/** Renderer DLNA descubierto en la red local. */
data class DlnaDevice(
    val name: String,
    val udn: String,
    val controlUrl: String,
    val location: String,
    val modelName: String? = null
)
