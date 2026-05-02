package io.nikdmitryuk.ultraclient.data.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlessUrlParserTest {
    private val parser = VlessUrlParser()

    // vless://uuid@host:port?security=reality&pbk=KEY&sid=ID&fp=chrome&sni=sni.example.com&flow=xtls-rprx-vision#My%20Server
    private val realityUrl =
        "vless://123e4567-e89b-12d3-a456-426614174000@example.com:443" +
            "?security=reality&pbk=ABC123pubkey&sid=deadbeef&fp=chrome" +
            "&sni=www.example.com&flow=xtls-rprx-vision&type=tcp#My%20Server"

    private val tlsUrl =
        "vless://aaaabbbb-cccc-dddd-eeee-ffffffffffff@tls.example.com:8443" +
            "?security=tls&sni=tls.example.com&fp=firefox&type=tcp"

    private val wsUrl =
        "vless://11112222-3333-4444-5555-666677778888@ws.example.com:80" +
            "?security=none&type=ws&path=%2Fapi&host=ws.example.com"

    @Test
    fun canHandleReturnsTrueForVlessScheme() {
        assertTrue(parser.canHandle("vless://uuid@host:443"))
    }

    @Test
    fun canHandleReturnsFalseForOtherScheme() {
        assertFalse(parser.canHandle("vmess://something"))
        assertFalse(parser.canHandle("https://example.com"))
        assertFalse(parser.canHandle(""))
    }

    @Test
    fun parseRealityUrlFields() {
        val profile = parser.parse(realityUrl)
        val cfg = profile.config
        assertEquals("123e4567-e89b-12d3-a456-426614174000", cfg.uuid)
        assertEquals("example.com", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("reality", cfg.security)
        assertEquals("ABC123pubkey", cfg.realityPublicKey)
        assertEquals("deadbeef", cfg.realityShortId)
        assertEquals("chrome", cfg.fingerprint)
        assertEquals("www.example.com", cfg.sni)
        assertEquals("xtls-rprx-vision", cfg.flow)
        assertEquals("tcp", cfg.network)
    }

    @Test
    fun parseRealityUrlFragmentDecoded() {
        val profile = parser.parse(realityUrl)
        assertEquals("My Server", profile.name)
    }

    @Test
    fun parseTlsUrl() {
        val cfg = parser.parse(tlsUrl).config
        assertEquals("tls", cfg.security)
        assertEquals("tls.example.com", cfg.sni)
        assertEquals("firefox", cfg.fingerprint)
    }

    @Test
    fun parseWsUrl() {
        val cfg = parser.parse(wsUrl).config
        assertEquals("ws", cfg.network)
        assertEquals("/api", cfg.wsPath)
        assertEquals("ws.example.com", cfg.wsHost)
    }

    @Test
    fun missingAtSignThrows() {
        assertFailsWith<VlessParseException> {
            parser.parse("vless://nouserinfo:443?security=none")
        }
    }

    @Test
    fun invalidPortThrows() {
        assertFailsWith<VlessParseException> {
            parser.parse("vless://uuid@host:notaport?security=none")
        }
    }

    @Test
    fun missingPortThrows() {
        assertFailsWith<VlessParseException> {
            parser.parse("vless://uuid@hostonly?security=none")
        }
    }

    @Test
    fun fallbackNameUsesHostPort() {
        // URL with no fragment → name should be host:port
        val url = "vless://uuid@fallback.example.com:8080?security=none"
        val profile = parser.parse(url)
        assertEquals("fallback.example.com:8080", profile.name)
    }
}
