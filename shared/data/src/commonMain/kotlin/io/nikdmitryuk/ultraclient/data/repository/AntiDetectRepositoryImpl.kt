package io.nikdmitryuk.ultraclient.data.repository

import io.nikdmitryuk.ultraclient.data.local.AntiDetectLocalDataSource
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import kotlinx.coroutines.flow.Flow

class AntiDetectRepositoryImpl(
    private val dataSource: AntiDetectLocalDataSource
) : AntiDetectRepository {

    override fun observe(): Flow<AntiDetectConfig> = dataSource.observe()

    override suspend fun get(): AntiDetectConfig = dataSource.get()

    override suspend fun update(config: AntiDetectConfig) = dataSource.upsert(config)
}
