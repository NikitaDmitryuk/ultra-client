package io.nikdmitryuk.ultraclient.presentation.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

actual suspend fun measurePingMs(): Long? = withContext(Dispatchers.IO) {
    try {
        val start = System.currentTimeMillis()
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 3_000) }
        System.currentTimeMillis() - start
    } catch (_: Exception) {
        null
    }
}
