package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntiDetectUseCaseTest {
    @Test
    fun updateAntiDetectNormalizesDeprecatedFlags() =
        runBlocking {
            val repository = FakeAntiDetectRepository()
            val useCase = UpdateAntiDetectUseCase(repository)

            useCase(
                AntiDetectConfig(
                    killSwitchEnabled = true,
                    fakeDnsEnabled = false,
                    randomPortEnabled = true,
                ),
            ).getOrThrow()

            assertFalse(repository.current.killSwitchEnabled)
            assertTrue(repository.current.fakeDnsEnabled)
            assertFalse(repository.current.randomPortEnabled)
        }

    @Test
    fun updateVpnIncludedAppsNormalizesDeprecatedFlagsAndKeepsIncludedApps() =
        runBlocking {
            val repository =
                FakeAntiDetectRepository(
                    AntiDetectConfig(
                        killSwitchEnabled = true,
                        fakeDnsEnabled = false,
                        randomPortEnabled = true,
                        legacyBypassAppIds = listOf("legacy.app"),
                    ),
                )
            val useCase = UpdateVpnIncludedAppsUseCase(repository)
            val chrome = VpnAppRouteRule("com.android.chrome", "Chrome", throughVpn = true)

            useCase(listOf(chrome)).getOrThrow()

            assertFalse(repository.current.killSwitchEnabled)
            assertTrue(repository.current.fakeDnsEnabled)
            assertFalse(repository.current.randomPortEnabled)
            assertEquals(listOf(chrome), repository.current.vpnIncludedApps)
            assertEquals(emptyList(), repository.current.legacyBypassAppIds)
        }

    private class FakeAntiDetectRepository(
        initial: AntiDetectConfig = AntiDetectConfig(),
    ) : AntiDetectRepository {
        private val state = MutableStateFlow(initial)
        var current: AntiDetectConfig = initial
            private set

        override fun observe(): Flow<AntiDetectConfig> = state

        override suspend fun get(): AntiDetectConfig = current

        override suspend fun update(config: AntiDetectConfig) {
            current = config
            state.value = config
        }
    }
}
