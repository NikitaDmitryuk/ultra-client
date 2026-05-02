package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VpnProfileSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val sampleConfig =
        VlessConfig(
            uuid = "123e4567-e89b-12d3-a456-426614174000",
            address = "example.com",
            port = 443,
            encryption = "none",
            flow = "xtls-rprx-vision",
            security = "reality",
            network = "tcp",
            realityPublicKey = "ABC123pubkey",
            realityShortId = "deadbeef",
            sni = "www.example.com",
            fingerprint = "chrome",
        )

    private val sampleProfile =
        VpnProfile(
            id = "test-id-001",
            name = "My Server",
            rawUrl = "vless://test@example.com:443",
            config = sampleConfig,
            isActive = true,
            createdAt = 1700000000000L,
        )

    @Test
    fun vpnProfileRoundTrip() {
        val encoded = json.encodeToString(VpnProfile.serializer(), sampleProfile)
        val decoded = json.decodeFromString(VpnProfile.serializer(), encoded)
        assertEquals(sampleProfile, decoded)
    }

    @Test
    fun vlessConfigRoundTrip() {
        val encoded = json.encodeToString(VlessConfig.serializer(), sampleConfig)
        val decoded = json.decodeFromString(VlessConfig.serializer(), encoded)
        assertEquals(sampleConfig, decoded)
    }

    @Test
    fun vlessConfigDefaultsPreserved() {
        val minimal = VlessConfig(uuid = "uid", address = "host", port = 80, security = "none")
        val encoded = json.encodeToString(VlessConfig.serializer(), minimal)
        val decoded = json.decodeFromString(VlessConfig.serializer(), encoded)
        assertEquals("none", decoded.encryption)
        assertEquals("", decoded.flow)
        assertEquals("tcp", decoded.network)
        assertEquals("chrome", decoded.fingerprint)
    }

    @Test
    fun vpnProfileIsActiveDefaultsFalse() {
        val profileJson =
            """{"id":"x","name":"n","rawUrl":"r","config":""" +
            """{"uuid":"u","address":"a","port":80,"security":"none"},"createdAt":0}"""
        val decoded = json.decodeFromString(VpnProfile.serializer(), profileJson)
        assertEquals(false, decoded.isActive)
    }
}
