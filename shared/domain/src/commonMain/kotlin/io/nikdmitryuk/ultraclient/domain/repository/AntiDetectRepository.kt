package io.nikdmitryuk.ultraclient.domain.repository

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import kotlinx.coroutines.flow.Flow

interface AntiDetectRepository {
    fun observe(): Flow<AntiDetectConfig>

    suspend fun get(): AntiDetectConfig

    suspend fun update(config: AntiDetectConfig)
}
