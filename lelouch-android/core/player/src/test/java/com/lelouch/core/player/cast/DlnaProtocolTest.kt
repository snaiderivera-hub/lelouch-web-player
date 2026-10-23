package com.lelouch.core.player.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 38 · Pruebas del protocolo DLNA puro.
 *
 * Cubren el análisis de respuestas reales de SSDP, la resolución de rutas relativas del
 * `controlURL` y el escapado de metadatos. El transporte de red no se prueba aquí: requiere
 * un renderer en la LAN.
 */
class DlnaProtocolTest {

    @Test
    fun `m-search pide renderers multimedia`() {
        val message = DlnaProtocol.searchMessage()
        assertTrue(message.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(message.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(message.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(message.contains("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"))
        assertTrue(message.endsWith("\r\n\r\n"))
    }

    @Test
    fun `extrae la ubicacion de una respuesta ssdp valida`() {
        val response = """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=1800
            LOCATION: http://192.168.1.42:49152/description.xml
            SERVER: Linux/3.14 UPnP/1.0
            ST: urn:schemas-upnp-org:device:MediaRenderer:1
            USN: uuid:abcd::urn:schemas-upnp-org:device:MediaRenderer:1
        """.trimIndent().replace("\n", "\r\n")
        assertEquals(
            "http://192.168.1.42:49152/description.xml",
            DlnaProtocol.parseSsdpLocation(response)
        )
    }

    @Test
    fun `ignora respuestas que no son 200 o no traen location`() {
        assertNull(DlnaProtocol.parseSsdpLocation("NOTIFY * HTTP/1.1\r\nLOCATION: http://x/y.xml\r\n"))
        assertNull(DlnaProtocol.parseSsdpLocation("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n"))
        assertNull(DlnaProtocol.parseSsdpLocation(""))
    }

    @Test
    fun `resuelve controlURL relativa contra la ubicacion`() {
        val xml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>TV del salón</friendlyName>
                <modelName>SmartTV 2020</modelName>
                <UDN>uuid:1111-2222</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/control/volume</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/AVTransport1</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val parsed = DlnaProtocol.parseDeviceDescription(
            xml,
            "http://192.168.1.42:49152/description.xml"
        )
        assertEquals("TV del salón", parsed?.friendlyName)
        assertEquals("uuid:1111-2222", parsed?.udn)
        assertEquals("SmartTV 2020", parsed?.modelName)
        assertEquals(
            "http://192.168.1.42:49152/upnp/control/AVTransport1",
            parsed?.controlUrl
        )
    }

    @Test
    fun `rechaza dispositivos sin AVTransport`() {
        val xml = """
            <root>
              <device>
                <friendlyName>Solo volumen</friendlyName>
                <UDN>uuid:3333</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/volume</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()
        assertNull(
            DlnaProtocol.parseDeviceDescription(xml, "http://192.168.1.42/description.xml")
        )
    }

    @Test
    fun `construye metadatos DIDL escapando el titulo y la url`() {
        val metadata = DlnaProtocol.buildMetadata(
            "http://host/a.m3u8?x=1&y=2",
            "Película & <Serie> \"X\""
        )
        assertTrue(metadata.contains("<dc:title>Película &amp; &lt;Serie&gt; &quot;X&quot;</dc:title>"))
        assertTrue(metadata.contains("application/vnd.apple.mpegurl"))
        assertTrue(metadata.contains("x=1&amp;y=2"))
        assertTrue(metadata.contains("object.item.videoItem"))
        // Sin FLAGS varios televisores aceptan la orden pero no abren el recurso.
        assertTrue(metadata.contains("DLNA.ORG_FLAGS=01700000000000000000000000000000"))
    }

    @Test
    fun `el cuerpo SOAP escapa los metadatos anidados`() {
        val body = DlnaProtocol.setAvTransportUriBody(
            "http://host/v.mp4",
            DlnaProtocol.buildMetadata("http://host/v.mp4", "A & B")
        )
        assertTrue(body.contains("<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"))
        assertTrue(body.contains("<CurrentURI>http://host/v.mp4</CurrentURI>"))
        // El DIDL viaja como texto dentro de CurrentURIMetaData: se escapa una segunda vez,
        // de modo que el receptor recupera el XML original al deserializar el sobre SOAP.
        assertTrue(body.contains("&lt;DIDL-Lite"))
        assertTrue(body.contains("&lt;dc:title&gt;A &amp;amp; B"))
    }

    @Test
    fun `deduce el tipo mime por extension`() {
        assertEquals("video/mp4", DlnaProtocol.mimeTypeFor("http://h/pelicula.MP4"))
        assertEquals("application/vnd.apple.mpegurl", DlnaProtocol.mimeTypeFor("http://h/live.m3u8"))
        assertEquals("video/x-matroska", DlnaProtocol.mimeTypeFor("http://h/capitulo.mkv"))
        // Sin extensión no se puede usar `video/*`: protocolInfo exige un MIME concreto.
        assertEquals("video/mp4", DlnaProtocol.mimeTypeFor("http://h/pelicula"))
        assertEquals("video/mp2t", DlnaProtocol.mimeTypeFor("http://h/stream", isLive = true))
    }

    @Test
    fun `el cuerpo de GetTransportInfo pide la instancia cero`() {
        val body = DlnaProtocol.getTransportInfoBody()
        assertTrue(body.contains("<u:GetTransportInfo xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"))
        assertTrue(body.contains("<InstanceID>0</InstanceID>"))
    }

    @Test
    fun `analiza el estado de transporte de una respuesta real`() {
        val xml = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <CurrentTransportState>PLAYING</CurrentTransportState>
                  <CurrentTransportStatus>OK</CurrentTransportStatus>
                  <CurrentSpeed>1</CurrentSpeed>
                </u:GetTransportInfoResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val info = DlnaProtocol.parseTransportInfo(xml)
        assertEquals("PLAYING", info?.state)
        assertEquals("OK", info?.status)
        assertEquals("1", info?.speed)
    }

    @Test
    fun `reconoce el estado sin medio tras el aviso del televisor`() {
        val xml = """
            <Envelope><Body><GetTransportInfoResponse>
              <CurrentTransportState>NO_MEDIA_PRESENT</CurrentTransportState>
              <CurrentTransportStatus>OK</CurrentTransportStatus>
            </GetTransportInfoResponse></Body></Envelope>
        """.trimIndent()

        assertEquals("NO_MEDIA_PRESENT", DlnaProtocol.parseTransportInfo(xml)?.state)
        assertNull(DlnaProtocol.parseTransportInfo("<Envelope/>"))
    }

    @Test
    fun `la cabecera SOAP usa la accion solicitada`() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            DlnaProtocol.soapActionHeader("Play")
        )
    }
}
