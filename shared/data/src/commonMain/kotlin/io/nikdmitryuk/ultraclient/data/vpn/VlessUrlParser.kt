package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.parser.VpnUrlParser
import kotlin.random.Random

class VlessParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

class VlessUrlParser : VpnUrlParser {

    override fun canHandle(url: String): Boolean = url.startsWith("vless://")

    override fun parse(rawUrl: String): VpnProfile {
        val config = parseConfig(rawUrl)
        val name = extractName(rawUrl).ifBlank { "${config.address}:${config.port}" }
        return VpnProfile(
            id = generateUuid(),
            name = name,
            rawUrl = rawUrl,
            config = config,
            isActive = false,
            createdAt = currentTimeMillis()
        )
    }

    private fun parseConfig(rawUrl: String): VlessConfig {
        if (!rawUrl.startsWith("vless://")) {
            throw VlessParseException("Not a vless:// URL")
        }

        val withoutScheme = rawUrl.removePrefix("vless://")
        val atIndex = withoutScheme.indexOf('@')
        if (atIndex < 0) throw VlessParseException("Missing @ separator in VLESS URL")

        val uuid = withoutScheme.substring(0, atIndex)
        if (uuid.isBlank()) throw VlessParseException("Empty UUID in VLESS URL")

        val rest = withoutScheme.substring(atIndex + 1)
        val hashIndex = rest.indexOf('#')
        val withoutFragment = if (hashIndex >= 0) rest.substring(0, hashIndex) else rest

        val qIndex = withoutFragment.indexOf('?')
        val hostPort = if (qIndex >= 0) withoutFragment.substring(0, qIndex) else withoutFragment
        val query = if (qIndex >= 0) withoutFragment.substring(qIndex + 1) else ""

        val colonIdx = hostPort.lastIndexOf(':')
        if (colonIdx < 0) throw VlessParseException("Missing port in VLESS URL")

        val host = hostPort.substring(0, colonIdx)
        val port = hostPort.substring(colonIdx + 1).toIntOrNull()
            ?: throw VlessParseException("Invalid port value in VLESS URL")

        val params = parseQueryParams(query)

        return VlessConfig(
            uuid = uuid,
            address = host,
            port = port,
            encryption = params["encryption"] ?: "none",
            flow = params["flow"] ?: "",
            security = params["security"] ?: "none",
            network = params["type"] ?: "tcp",
            sni = params["sni"] ?: "",
            fingerprint = params["fp"] ?: "chrome",
            realityPublicKey = params["pbk"] ?: "",
            realityShortId = params["sid"] ?: "",
            realitySpiderX = params["spx"]?.let { urlDecode(it) } ?: "/",
            alpn = params["alpn"] ?: "",
            wsPath = params["path"]?.let { urlDecode(it) } ?: "",
            wsHost = params["host"] ?: "",
            grpcServiceName = params["serviceName"] ?: ""
        )
    }

    private fun extractName(rawUrl: String): String {
        val hashIndex = rawUrl.indexOf('#')
        return if (hashIndex >= 0) urlDecode(rawUrl.substring(hashIndex + 1)) else ""
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&")
            .filter { it.contains("=") }
            .associate { param ->
                val eq = param.indexOf('=')
                param.substring(0, eq) to param.substring(eq + 1)
            }
    }

    private fun urlDecode(encoded: String): String =
        encoded
            .replace("+", " ")
            .replace(Regex("%([0-9A-Fa-f]{2})")) { mr ->
                mr.groupValues[1].toInt(16).toChar().toString()
            }

    private fun generateUuid(): String {
        val chars = "abcdef0123456789"
        fun seg(len: Int) = (1..len).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "${seg(8)}-${seg(4)}-${seg(4)}-${seg(4)}-${seg(12)}"
    }
}

expect fun currentTimeMillis(): Long
