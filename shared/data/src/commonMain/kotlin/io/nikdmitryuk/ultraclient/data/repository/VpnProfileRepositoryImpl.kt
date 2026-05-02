package io.nikdmitryuk.ultraclient.data.repository

import io.nikdmitryuk.ultraclient.data.local.VpnProfileLocalDataSource
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.repository.VpnProfileRepository
import kotlinx.coroutines.flow.Flow

class VpnProfileRepositoryImpl(
    private val dataSource: VpnProfileLocalDataSource,
) : VpnProfileRepository {
    override fun observeAll(): Flow<List<VpnProfile>> = dataSource.observeAll()

    override suspend fun getById(id: String): VpnProfile? = dataSource.getById(id)

    override suspend fun getActive(): VpnProfile? = dataSource.getActive()

    override suspend fun insert(profile: VpnProfile) = dataSource.insert(profile)

    override suspend fun setActive(id: String) = dataSource.setActive(id)

    override suspend fun delete(id: String) = dataSource.delete(id)

    override suspend fun deleteAll() = dataSource.deleteAll()
}
