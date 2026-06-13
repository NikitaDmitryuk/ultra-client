package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingBoxConfigBuilderTest {
    private val builder = SingBoxConfigBuilder()

    @Test
    fun realityVlessConfigMapsToSingBoxOutbound() {
        val json =
            builder.build(
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
                ),
                AntiDetectConfig(),
            )

        assertContains(json, "\"type\":\"vless\"")
        assertContains(json, "\"tag\":\"proxy-out\"")
        assertContains(json, "\"server\":\"example.com\"")
        assertContains(json, "\"server_port\":443")
        assertContains(json, "\"flow\":\"xtls-rprx-vision\"")
        assertContains(json, "\"reality\"")
        assertContains(json, "\"public_key\":\"PUBKEY\"")
        assertContains(json, "\"short_id\":\"SHORTID\"")
        assertContains(json, "\"fingerprint\":\"chrome\"")
    }

    @Test
    fun tunInboundAndFakeIpDnsAreEnabledByDefault() {
        val json =
            builder.build(
                VlessConfig(
                    uuid = "123e4567-e89b-12d3-a456-426614174000",
                    address = "example.com",
                    port = 443,
                ),
                AntiDetectConfig(),
            )

        assertContains(json, "\"type\":\"tun\"")
        assertContains(json, "\"interface_name\":\"ultra0\"")
        assertContains(json, "\"auto_route\":true")
        assertContains(json, "\"strict_route\":true")
        assertFalse(json.contains("\"sniff\":true"))
        assertContains(json, "\"action\":\"sniff\"")
        assertContains(json, "\"action\":\"hijack-dns\"")
        assertContains(json, "\"default_domain_resolver\":\"remote\"")
        assertContains(json, "\"type\":\"fakeip\"")
        assertContains(json, "198.18.0.0/15")
        assertContains(json, "\"final\":\"proxy-out\"")
        assertFalse(json.contains("\"type\":\"dns\""))
        assertFalse(json.contains("\"rule_set\""))
        assertFalse(json.contains("raw.githubusercontent.com"))
    }

    @Test
    fun plainDnsOmitsFakeIpServer() {
        val json =
            builder.build(
                VlessConfig(
                    uuid = "123e4567-e89b-12d3-a456-426614174000",
                    address = "example.com",
                    port = 443,
                ),
                AntiDetectConfig(fakeDnsEnabled = false),
        )

        assertFalse(json.contains("\"type\":\"fakeip\""))
        assertContains(json, "\"type\":\"https\"")
        assertContains(json, "\"server\":\"1.1.1.1\"")
        assertContains(json, "\"path\":\"/dns-query\"")
    }

    @Test
    fun blankTunInterfaceNameIsOmittedForAutoAllocation() {
        val json =
            builder.build(
                VlessConfig(
                    uuid = "123e4567-e89b-12d3-a456-426614174000",
                    address = "example.com",
                    port = 443,
                ),
                AntiDetectConfig(),
                SingBoxOptions(interfaceName = ""),
            )

        assertFalse(json.contains("\"interface_name\""))
    }

    @Test
    fun cacheFilePathCanBePinnedForPrivilegedRuntimes() {
        val json =
            builder.build(
                VlessConfig(
                    uuid = "123e4567-e89b-12d3-a456-426614174000",
                    address = "example.com",
                    port = 443,
                ),
                AntiDetectConfig(),
                SingBoxOptions(cacheFilePath = "/Users/test/.ultra-client/cache.db"),
            )

        assertContains(json, "\"cache_file\"")
        assertContains(json, "\"path\":\"/Users/test/.ultra-client/cache.db\"")
    }

    @Test
    fun wsAndGrpcTransportsAreMapped() {
        val wsJson =
            builder.build(
                VlessConfig(
                    uuid = "123e4567-e89b-12d3-a456-426614174000",
                    address = "example.com",
                    port = 443,
                    network = "ws",
                    wsPath = "/ws",
                    wsHost = "host.example.com",
                ),
                AntiDetectConfig(),
            )
        assertContains(wsJson, "\"transport\"")
        assertContains(wsJson, "\"type\":\"ws\"")
        assertContains(wsJson, "\"path\":\"/ws\"")
        assertContains(wsJson, "\"Host\":\"host.example.com\"")

        val grpcJson =
            builder.build(
                VlessConfig(
                    uuid = "123e4567-e89b-12d3-a456-426614174000",
                    address = "example.com",
                    port = 443,
                    network = "grpc",
                    grpcServiceName = "svc",
                ),
                AntiDetectConfig(),
            )
        assertContains(grpcJson, "\"type\":\"grpc\"")
        assertContains(grpcJson, "\"service_name\":\"svc\"")
    }

    @Test
    fun androidPackageAllowListIsPreservedInTunConfig() {
        val json =
            builder.build(
                VlessConfig(
                    uuid = "123e4567-e89b-12d3-a456-426614174000",
                    address = "example.com",
                    port = 443,
                ),
                AntiDetectConfig(
                    vpnIncludedApps =
                        listOf(
                            VpnAppRouteRule("com.android.chrome", "Chrome", true),
                            VpnAppRouteRule("com.example.off", "Off", false),
                        ),
                ),
            )

        assertContains(json, "\"include_package\":[\"com.android.chrome\"]")
        assertFalse(json.contains("com.example.off"))
        assertTrue(json.trim().startsWith("{"))
    }
}
