package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig

/**
 * Генерация JSON для Xray. Диагностика allow-list / DNS:
 * - **Fake DNS** — runtime Android VPN path keeps it enabled by default for TUN DNS handling.
 * - **initDns** — в `androidApp/build.gradle.kts` поле `LIBXRAY_SKIP_INIT_DNS` (пропуск вызова `libXray.initDns`).
 */
class XrayConfigBuilder {
    /**
     * @param _localSocksPort legacy parameter kept for call-site compatibility; Android VPN mode uses Xray's TUN inbound.
     * @param _localDnsPort reserved for a future dedicated DNS inbound; currently unused in generated JSON (TUN + LibXray handle DNS).
     * @param xrayErrorLogPath when non-null (e.g. debug), Xray writes internal errors to this file and loglevel is raised to debug.
     */
    fun build(
        vlessConfig: VlessConfig,
        antiDetect: AntiDetectConfig,
        @Suppress("UNUSED_PARAMETER") _localSocksPort: Int,
        @Suppress("UNUSED_PARAMETER") _localDnsPort: Int,
        xrayErrorLogPath: String? = null,
    ): String =
        buildString {
            appendLine("{")
            appendLine(buildLogBlock(xrayErrorLogPath))

            if (antiDetect.fakeDnsEnabled) {
                appendLine(buildFakeDns())
            } else {
                appendLine(buildPlainDns())
            }

            append("""  "inbounds": [""")
            appendLine()
            appendLine(buildTunInbound(antiDetect.fakeDnsEnabled))
            appendLine("  ],")

            append("""  "outbounds": [""")
            appendLine()
            appendLine(buildVlessOutbound(vlessConfig))
            appendLine("  ,")
            appendLine("""    { "tag": "direct", "protocol": "freedom", "settings": {} }""")
            appendLine("  ,")
            appendLine("""    { "tag": "block", "protocol": "blackhole", "settings": {} }""")
            if (antiDetect.fakeDnsEnabled) {
                appendLine("  ,")
                appendLine("""    { "tag": "dns-out", "protocol": "dns" }""")
            }
            appendLine("  ],")

            appendLine(buildRouting(antiDetect.fakeDnsEnabled))
            append("}")
        }

    private fun buildLogBlock(errorLogPath: String?): String {
        val level = if (errorLogPath != null) "debug" else "warning"
        val errorValue =
            errorLogPath?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: ""
        return """  "log": { "loglevel": "$level", "access": "", "error": "$errorValue" },"""
    }

    private fun buildFakeDns() =
        """
  "dns": {
    "servers": [
      { "address": "fakedns", "domains": ["geosite:geolocation-!cn"] },
      "1.1.1.1"
    ],
    "fakedns": { "ipPool": "198.18.0.0/15", "poolSize": 65535 }
  },"""

    private fun buildPlainDns() =
        """
  "dns": {
    "servers": ["1.1.1.1", "8.8.8.8"]
  },"""

    private fun buildTunInbound(fakeDns: Boolean): String {
        val destOverride = if (fakeDns) """["http","tls","fakedns"]""" else """["http","tls"]"""
        return """    {
      "tag": "tun-in",
      "protocol": "tun",
      "settings": { "name": "ultra-client", "MTU": 1500, "userLevel": 0 },
      "sniffing": { "enabled": true, "destOverride": $destOverride }
    }"""
    }

    private fun buildVlessOutbound(cfg: VlessConfig) =
        """    {
      "tag": "proxy-out",
      "protocol": "vless",
      "settings": {
        "vnext": [{
          "address": "${cfg.address}",
          "port": ${cfg.port},
          "users": [{
            "id": "${cfg.uuid}",
            "encryption": "${cfg.encryption}"${if (cfg.flow.isNotBlank()) ""","flow": "${cfg.flow}"""" else ""}
          }]
        }]
      },
      "streamSettings": ${buildStreamSettings(cfg)}
    }"""

    private fun buildStreamSettings(cfg: VlessConfig): String =
        when (cfg.security) {
            "reality" -> """{
        "network": "${cfg.network}",
        "security": "reality",
        "realitySettings": {
          "fingerprint": "${cfg.fingerprint}",
          "serverName": "${cfg.sni}",
          "publicKey": "${cfg.realityPublicKey}",
          "shortId": "${cfg.realityShortId}",
          "spiderX": "${cfg.realitySpiderX}"
        }${buildNetworkSettings(cfg)}
      }"""
            "tls" -> """{
        "network": "${cfg.network}",
        "security": "tls",
        "tlsSettings": {
          "serverName": "${cfg.sni}",
          "fingerprint": "${cfg.fingerprint}"${if (cfg.alpn.isNotBlank()) ""","alpn": ["${cfg.alpn}"]""" else ""}
        }${buildNetworkSettings(cfg)}
      }"""
            else -> """{
        "network": "${cfg.network}"${buildNetworkSettings(cfg)}
      }"""
        }

    private fun buildNetworkSettings(cfg: VlessConfig): String =
        when (cfg.network) {
            "ws" ->
                """,
        "wsSettings": {
          "path": "${cfg.wsPath}",
          "headers": { "Host": "${cfg.wsHost}" }
        }"""
            "grpc" ->
                """,
        "grpcSettings": { "serviceName": "${cfg.grpcServiceName}" }"""
            else -> ""
        }

    private fun buildRouting(fakeDns: Boolean): String {
        val dnsRule =
            if (fakeDns) {
                """
      { "type": "field", "port": 53, "outboundTag": "dns-out" },"""
            } else {
                ""
            }
        return """  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [$dnsRule
      { "type": "field", "ip": ["geoip:private"], "outboundTag": "direct" },
      { "type": "field", "domain": ["geosite:category-ads-all"], "outboundTag": "block" },
      { "type": "field", "network": "tcp,udp", "outboundTag": "proxy-out" }
    ]
  }"""
    }
}
