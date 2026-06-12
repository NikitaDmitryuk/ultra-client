package io.nikdmitryuk.ultraclient.data.vpn

import kotlin.time.Clock

actual fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
