package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.parser.VpnUrlParser
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository

class ImportProfileUseCase(
    private val parser: VpnUrlParser,
    private val repository: VpnProfileRepository,
) {
    suspend operator fun invoke(rawUrl: String): Result<VpnProfile> =
        runCatching {
            val profile = parser.parse(rawUrl)
            repository.insert(profile)
            profile
        }
}
