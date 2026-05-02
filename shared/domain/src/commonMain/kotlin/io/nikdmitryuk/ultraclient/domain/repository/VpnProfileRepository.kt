package io.nikdmitryuk.ultraclient.domain.repository

import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import kotlinx.coroutines.flow.Flow

interface VpnProfileRepository {
    fun observeAll(): Flow<List<VpnProfile>>
    suspend fun getById(id: String): VpnProfile?
    suspend fun getActive(): VpnProfile?
    suspend fun insert(profile: VpnProfile)
    suspend fun setActive(id: String)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
