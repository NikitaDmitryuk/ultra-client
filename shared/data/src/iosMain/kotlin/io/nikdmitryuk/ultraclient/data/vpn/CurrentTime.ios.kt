package io.nikdmitryuk.ultraclient.data.vpn

import platform.Foundation.NSDate

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()
