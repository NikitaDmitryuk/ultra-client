package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VlessConfig(
    val uuid: String,
    val address: String,
    val port: Int,
    val encryption: String = "none",
    val flow: String = "",
    val security: String = "none",
    val network: String = "tcp",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "/",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val alpn: String = "",
    val wsPath: String = "",
    val wsHost: String = "",
    val grpcServiceName: String = ""
)
