package io.nikdmitryuk.ultraclient.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.nikdmitryuk.ultraclient.data.local.db.Anti_detect_config
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.SplitTunnelRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AntiDetectLocalDataSource(private val db: UltraClientDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observe(): Flow<AntiDetectConfig> =
        db.antiDetectConfigQueries.select()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.toDomain(json) ?: AntiDetectConfig() }

    suspend fun get(): AntiDetectConfig = withContext(Dispatchers.Default) {
        db.antiDetectConfigQueries.select().executeAsOneOrNull()?.toDomain(json) ?: AntiDetectConfig()
    }

    suspend fun upsert(config: AntiDetectConfig) = withContext(Dispatchers.Default) {
        db.antiDetectConfigQueries.upsert(
            kill_switch_enabled = if (config.killSwitchEnabled) 1L else 0L,
            fake_dns_enabled = if (config.fakeDnsEnabled) 1L else 0L,
            random_port_enabled = if (config.randomPortEnabled) 1L else 0L,
            split_tunnel_json = json.encodeToString(
                ListSerializer(SplitTunnelRule.serializer()),
                config.splitTunnelRules
            )
        )
    }

    private fun Anti_detect_config.toDomain(json: Json): AntiDetectConfig = AntiDetectConfig(
        killSwitchEnabled = kill_switch_enabled == 1L,
        fakeDnsEnabled = fake_dns_enabled == 1L,
        randomPortEnabled = random_port_enabled == 1L,
        splitTunnelRules = json.decodeFromString(
            ListSerializer(SplitTunnelRule.serializer()),
            split_tunnel_json
        )
    )
}
