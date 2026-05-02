package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig

class XrayConfigBuilder {

    fun build(
        vlessConfig: VlessConfig,
        antiDetect: AntiDetectConfig,
        localSocksPort: Int,
        localDnsPort: Int
    ): String = buildString {
        appendLine("{")
        appendLine("""  "log": { "loglevel": "warning", "access": "", "error": "" },""")

        if (antiDetect.fakeDnsEnabled) {
            appendLine(buildFakeDns())
        } else {
            appendLine(buildPlainDns())
        }

        append("""  "inbounds": [""")
        appendLine()
        appendLine(buildSocksInbound(localSocksPort, antiDetect.fakeDnsEnabled))
        if (antiDetect.fakeDnsEnabled) {
            appendLine("  ,")
            appendLine(buildDnsInbound())
        }
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

    private fun buildFakeDns() = """
  "dns": {
    "servers": [
      { "address": "fakedns", "domains": ["geosite:geolocation-!cn"] },
      "1.1.1.1"
    ],
    "fakedns": { "ipPool": "198.18.0.0/15", "poolSize": 65535 }
  },"""

    private fun buildPlainDns() = """
  "dns": {
    "servers": ["1.1.1.1", "8.8.8.8"]
  },"""

    private fun buildSocksInbound(port: Int, fakeDns: Boolean): String {
        val destOverride = if (fakeDns) """["http","tls","fakedns"]""" else """["http","tls"]"""
        return """    {
      "tag": "socks-in",
      "listen": "127.0.0.1",
      "port": $port,
      "protocol": "socks",
      "settings": { "auth": "noauth", "udp": true, "ip": "127.0.0.1" },
      "sniffing": { "enabled": true, "destOverride": $destOverride }
    }"""
    }

    private fun buildDnsInbound() = """    {
      "tag": "dns-in",
      "listen": "198.18.0.3",
      "port": 53,
      "protocol": "dokodemo-door",
      "settings": { "address": "1.1.1.1", "port": 53, "network": "udp" }
    }"""

    private fun buildVlessOutbound(cfg: VlessConfig) = """    {
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

    private fun buildStreamSettings(cfg: VlessConfig): String = when (cfg.security) {
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

    private fun buildNetworkSettings(cfg: VlessConfig): String = when (cfg.network) {
        "ws" -> """,
        "wsSettings": {
          "path": "${cfg.wsPath}",
          "headers": { "Host": "${cfg.wsHost}" }
        }"""
        "grpc" -> """,
        "grpcSettings": { "serviceName": "${cfg.grpcServiceName}" }"""
        else -> ""
    }

    private fun buildRouting(fakeDns: Boolean): String {
        val dnsRule = if (fakeDns) """
      { "type": "field", "inboundTag": ["dns-in"], "outboundTag": "dns-out" },""" else ""
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
