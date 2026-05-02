package io.nikdmitryuk.ultraclient.data.antidetect

import kotlin.random.Random

class PortRandomizer {
    fun randomSocksPort(): Int = randomAvailablePort()
    fun randomDnsPort(): Int = randomAvailablePort()

    private fun randomAvailablePort(): Int {
        repeat(20) {
            val port = Random.nextInt(10000, 60001)
            if (isPortAvailable(port)) return port
        }
        return Random.nextInt(10000, 60001)
    }

    private fun isPortAvailable(port: Int): Boolean = port !in RESERVED_PORTS

    companion object {
        private val RESERVED_PORTS = setOf(
            10808, 1080, 8080, 8443, 7890, 7891, 7892
        )
    }
}
