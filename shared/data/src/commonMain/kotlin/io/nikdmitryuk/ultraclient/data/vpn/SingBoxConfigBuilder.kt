package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SingBoxConfigBuilder {
    fun build(
        vlessConfig: VlessConfig,
        antiDetect: AntiDetectConfig,
        options: SingBoxOptions = SingBoxOptions(),
    ): String =
        buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("level", options.logLevel)
                    options.logPath?.takeIf { it.isNotBlank() }?.let { put("output", it) }
                },
            )
            put("dns", buildDns(antiDetect.fakeDnsEnabled))
            put(
                "inbounds",
                buildJsonArray {
                    add(buildTunInbound(antiDetect, options))
                },
            )
            put(
                "outbounds",
                buildJsonArray {
                    add(buildVlessOutbound(vlessConfig))
                    add(buildJsonObject { put("type", "direct"); put("tag", "direct") })
                    add(buildJsonObject { put("type", "block"); put("tag", "block") })
                },
            )
            put("route", buildRoute(antiDetect.fakeDnsEnabled, options))
            put(
                "experimental",
                buildJsonObject {
                    put(
                        "cache_file",
                        buildJsonObject {
                            put("enabled", true)
                            options.cacheFilePath?.takeIf { it.isNotBlank() }?.let { put("path", it) }
                        },
                    )
                },
            )
        }.toString()

    private fun buildDns(fakeDns: Boolean): JsonObject =
        buildJsonObject {
            put(
                "servers",
                buildJsonArray {
                    if (fakeDns) {
                        add(
                            buildJsonObject {
                                put("tag", "fakeip")
                                put("type", "fakeip")
                                put("inet4_range", "198.18.0.0/15")
                            },
                        )
                    }
                    add(
                        buildJsonObject {
                            put("tag", "remote")
                            put("type", "https")
                            put("server", "1.1.1.1")
                            put("server_port", 443)
                            put("path", "/dns-query")
                            put("detour", "proxy-out")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("tag", "local")
                            put("type", "local")
                            put("detour", "direct")
                        },
                    )
                },
            )
            put(
                "rules",
                buildJsonArray {
                    if (fakeDns) {
                        add(
                            buildJsonObject {
                                put("query_type", buildStringArray("A", "AAAA"))
                                put("server", "fakeip")
                            },
                        )
                    }
                },
            )
            put("final", "remote")
        }

    private fun buildTunInbound(
        antiDetect: AntiDetectConfig,
        options: SingBoxOptions,
    ): JsonObject =
        buildJsonObject {
            put("type", "tun")
            put("tag", "tun-in")
            options.interfaceName.takeIf { it.isNotBlank() }?.let { put("interface_name", it) }
            put("mtu", options.mtu)
            put("address", buildStringArray(options.ipv4Address, options.ipv6Address))
            put("auto_route", options.autoRoute)
            put("strict_route", options.strictRoute)
            put("stack", options.stack)
            if (antiDetect.vpnIncludedApps.any { it.throughVpn }) {
                put(
                    "include_package",
                    JsonArray(
                        antiDetect.vpnIncludedApps
                            .filter { it.throughVpn }
                            .map { JsonPrimitive(it.appId) },
                    ),
                )
            }
        }

    private fun buildVlessOutbound(cfg: VlessConfig): JsonObject =
        buildJsonObject {
            put("type", "vless")
            put("tag", "proxy-out")
            put("server", cfg.address)
            put("server_port", cfg.port)
            put("uuid", cfg.uuid)
            cfg.flow.takeIf { it.isNotBlank() }?.let { put("flow", it) }
            put("packet_encoding", "xudp")
            if (cfg.security == "tls" || cfg.security == "reality") {
                put("tls", buildTls(cfg))
            }
            buildTransport(cfg)?.let { put("transport", it) }
        }

    private fun buildTls(cfg: VlessConfig): JsonObject =
        buildJsonObject {
            put("enabled", true)
            cfg.sni.takeIf { it.isNotBlank() }?.let { put("server_name", it) }
            cfg.alpn.takeIf { it.isNotBlank() }?.let { put("alpn", buildStringArray(it)) }
            cfg.fingerprint.takeIf { it.isNotBlank() }?.let { put("utls", buildJsonObject { put("enabled", true); put("fingerprint", it) }) }
            if (cfg.security == "reality") {
                put(
                    "reality",
                    buildJsonObject {
                        put("enabled", true)
                        put("public_key", cfg.realityPublicKey)
                        cfg.realityShortId.takeIf { it.isNotBlank() }?.let { put("short_id", it) }
                    },
                )
            }
        }

    private fun buildTransport(cfg: VlessConfig): JsonObject? =
        when (cfg.network) {
            "ws" ->
                buildJsonObject {
                    put("type", "ws")
                    cfg.wsPath.takeIf { it.isNotBlank() }?.let { put("path", it) }
                    cfg.wsHost.takeIf { it.isNotBlank() }?.let {
                        put("headers", buildJsonObject { put("Host", it) })
                    }
                }
            "grpc" ->
                buildJsonObject {
                    put("type", "grpc")
                    cfg.grpcServiceName.takeIf { it.isNotBlank() }?.let { put("service_name", it) }
                }
            else -> null
        }

    private fun buildRoute(
        fakeDns: Boolean,
        options: SingBoxOptions,
    ): JsonObject =
        buildJsonObject {
            put("auto_detect_interface", options.autoDetectInterface)
            put("default_domain_resolver", "remote")
            put(
                "rules",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("inbound", "tun-in")
                            put("action", "sniff")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("protocol", "dns")
                            put("action", "hijack-dns")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("ip_is_private", true)
                            put("outbound", "direct")
                        },
                    )
                    if (fakeDns) {
                        add(
                            buildJsonObject {
                                put("ip_cidr", buildStringArray("198.18.0.0/15"))
                                put("outbound", "proxy-out")
                            },
                        )
                    }
                },
            )
            put("final", "proxy-out")
        }

    private fun buildStringArray(vararg values: String): JsonArray =
        JsonArray(values.filter { it.isNotBlank() }.map { JsonPrimitive(it) })
}

data class SingBoxOptions(
    val interfaceName: String = "ultra0",
    val ipv4Address: String = "172.19.0.1/30",
    val ipv6Address: String = "fdfe:dcba:9876::1/126",
    val mtu: Int = 1500,
    val autoRoute: Boolean = true,
    val strictRoute: Boolean = true,
    val stack: String = "mixed",
    val logLevel: String = "warn",
    val logPath: String? = null,
    val cacheFilePath: String? = null,
    val autoDetectInterface: Boolean = true,
)
