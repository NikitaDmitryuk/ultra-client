package io.nikdmitryuk.ultraclient.domain.parser

import io.nikdmitryuk.ultraclient.domain.model.VpnProfile

interface VpnUrlParser {
    fun canHandle(url: String): Boolean

    fun parse(rawUrl: String): VpnProfile
}
