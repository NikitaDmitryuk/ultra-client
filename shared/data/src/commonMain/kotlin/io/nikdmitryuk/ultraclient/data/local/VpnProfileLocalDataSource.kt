package io.nikdmitryuk.ultraclient.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase
import io.nikdmitryuk.ultraclient.data.local.db.Vpn_profiles
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class VpnProfileLocalDataSource(private val db: UltraClientDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<VpnProfile>> =
        db.vpnProfilesQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain(json) } }

    suspend fun getById(id: String): VpnProfile? = withContext(Dispatchers.Default) {
        db.vpnProfilesQueries.selectById(id).executeAsOneOrNull()?.toDomain(json)
    }

    suspend fun getActive(): VpnProfile? = withContext(Dispatchers.Default) {
        db.vpnProfilesQueries.selectActive().executeAsOneOrNull()?.toDomain(json)
    }

    suspend fun insert(profile: VpnProfile): Unit = withContext(Dispatchers.Default) {
        db.vpnProfilesQueries.insert(
            id = profile.id,
            name = profile.name,
            raw_url = profile.rawUrl,
            config_json = json.encodeToString(VlessConfig.serializer(), profile.config),
            is_active = if (profile.isActive) 1L else 0L,
            created_at = profile.createdAt
        )
        Unit
    }

    suspend fun setActive(id: String): Unit = withContext(Dispatchers.Default) {
        db.vpnProfilesQueries.setActiveById(id)
        Unit
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.Default) {
        db.vpnProfilesQueries.deleteById(id)
        Unit
    }

    suspend fun deleteAll(): Unit = withContext(Dispatchers.Default) {
        db.vpnProfilesQueries.deleteAll()
        Unit
    }

    private fun Vpn_profiles.toDomain(json: Json): VpnProfile = VpnProfile(
        id = id,
        name = name,
        rawUrl = raw_url,
        config = json.decodeFromString(VlessConfig.serializer(), config_json),
        isActive = is_active == 1L,
        createdAt = created_at
    )
}
