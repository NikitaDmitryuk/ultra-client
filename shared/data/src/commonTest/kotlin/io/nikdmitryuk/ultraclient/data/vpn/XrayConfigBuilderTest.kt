package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayConfigBuilderTest {
    private val builder = XrayConfigBuilder()

    private val realityConfig =
        VlessConfig(
            uuid = "123e4567-e89b-12d3-a456-426614174000",
            address = "example.com",
            port = 443,
            security = "reality",
            realityPublicKey = "PUBKEY",
            realityShortId = "SHORTID",
            sni = "www.example.com",
            fingerprint = "chrome",
            flow = "xtls-rprx-vision",
        )

    private val tlsConfig =
        VlessConfig(
            uuid = "aaaabbbb-cccc-dddd-eeee-ffffffffffff",
            address = "tls.example.com",
            port = 8443,
            security = "tls",
            sni = "tls.example.com",
        )

    private val antiDetectWithFakeDns = AntiDetectConfig(fakeDnsEnabled = true)
    private val antiDetectPlainDns = AntiDetectConfig(fakeDnsEnabled = false)

    @Test
    fun outputContainsAllTopLevelKeys() {
        val json = builder.build(realityConfig, antiDetectWithFakeDns, 18492, 5353)
        assertContains(json, "\"log\"")
        assertContains(json, "\"dns\"")
        assertContains(json, "\"inbounds\"")
        assertContains(json, "\"outbounds\"")
        assertContains(json, "\"routing\"")
    }

    @Test
    fun fakeDnsEnabledInjectsFakeDnsBlock() {
        val json = builder.build(realityConfig, antiDetectWithFakeDns, 18492, 5353)
        assertContains(json, "fakedns")
        assertContains(json, "dns-in")
        assertContains(json, "dns-out")
        assertContains(json, "198.18.0")
    }

    @Test
    fun fakeDnsDisabledOmitsFakeDnsBlock() {
        val json = builder.build(realityConfig, antiDetectPlainDns, 18492, 5353)
        assertFalse(json.contains("fakedns"), "Should not contain fakedns when disabled")
        assertFalse(json.contains("dns-in"), "Should not contain dns-in inbound when disabled")
    }

    @Test
    fun socksPortAppearsInOutput() {
        val port = 23456
        val json = builder.build(realityConfig, antiDetectPlainDns, port, 5353)
        assertContains(json, "\"port\": $port")
    }

    @Test
    fun realitySecurityUsesRealitySettings() {
        val json = builder.build(realityConfig, antiDetectPlainDns, 18492, 5353)
        assertContains(json, "realitySettings")
        assertContains(json, "\"security\": \"reality\"")
        assertContains(json, "PUBKEY")
        assertContains(json, "SHORTID")
    }

    @Test
    fun tlsSecurityUsesTlsSettings() {
        val json = builder.build(tlsConfig, antiDetectPlainDns, 18492, 5353)
        assertContains(json, "tlsSettings")
        assertContains(json, "\"security\": \"tls\"")
    }

    @Test
    fun vlessOutboundContainsUuidAndAddress() {
        val json = builder.build(realityConfig, antiDetectPlainDns, 18492, 5353)
        assertContains(json, realityConfig.uuid)
        assertContains(json, realityConfig.address)
    }

    @Test
    fun outputIsNonEmpty() {
        val json = builder.build(realityConfig, antiDetectPlainDns, 18492, 5353)
        assertTrue(json.trim().startsWith("{"))
        assertTrue(json.trim().endsWith("}"))
    }
}
