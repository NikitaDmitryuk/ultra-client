package io.nikdmitryuk.ultraclient.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.nikdmitryuk.ultraclient.data.local.db.Anti_detect_config
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnAppRouteRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AntiDetectLocalDataSource(
    private val db: UltraClientDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(): Flow<AntiDetectConfig> =
        db.antiDetectQueries
            .select()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.toDomain(json) ?: AntiDetectConfig() }

    suspend fun get(): AntiDetectConfig =
        withContext(Dispatchers.Default) {
            db.antiDetectQueries
                .select()
                .executeAsOneOrNull()
                ?.toDomain(json) ?: AntiDetectConfig()
        }

    suspend fun upsert(config: AntiDetectConfig): Unit =
        withContext(Dispatchers.Default) {
            val existing = db.antiDetectQueries.select().executeAsOneOrNull()
            val stored =
                config.vpnIncludedApps
                    .filter { it.throughVpn }
            val tunnelJson =
                when {
                    stored.isNotEmpty() ->
                        json.encodeToString(
                            ListSerializer(VpnAppRouteRule.serializer()),
                            stored,
                        )
                    config.legacyBypassAppIds.isNotEmpty() && existing != null ->
                        existing.split_tunnel_json
                    else ->
                        json.encodeToString(
                            ListSerializer(VpnAppRouteRule.serializer()),
                            emptyList(),
                        )
                }
            db.antiDetectQueries.upsert(
                kill_switch_enabled = if (config.killSwitchEnabled) 1L else 0L,
                fake_dns_enabled = if (config.fakeDnsEnabled) 1L else 0L,
                random_port_enabled = if (config.randomPortEnabled) 1L else 0L,
                split_tunnel_json = tunnelJson,
            )
        }

    private fun Anti_detect_config.toDomain(json: Json): AntiDetectConfig {
        val (included, legacyBypass) = parseStoredVpnApps(split_tunnel_json, json)
        return AntiDetectConfig(
            killSwitchEnabled = kill_switch_enabled == 1L,
            fakeDnsEnabled = fake_dns_enabled == 1L,
            randomPortEnabled = random_port_enabled == 1L,
            vpnIncludedApps = included,
            legacyBypassAppIds = legacyBypass.toList(),
        )
    }

    private companion object {
        /**
         * Older builds stored only apps that bypassed the tunnel (`isExcluded` / bypassVpn).
         * Current builds store only apps with `throughVpn == true`.
         */
        @Serializable
        private data class LegacyVpnRow(
            val appId: String,
            val appName: String,
            val bypassVpn: Boolean? = null,
            val isExcluded: Boolean? = null,
        ) {
            fun bypassLegacy(): Boolean = bypassVpn == true || isExcluded == true
        }

        private fun parseStoredVpnApps(
            raw: String,
            json: Json,
        ): Pair<List<VpnAppRouteRule>, Set<String>> {
            if (raw.isBlank() || raw == "[]") return Pair(emptyList(), emptySet())
            return try {
                when {
                    raw.contains("\"isExcluded\"") || raw.contains("\"bypassVpn\"") -> {
                        val legacy =
                            json.decodeFromString(
                                ListSerializer(LegacyVpnRow.serializer()),
                                raw,
                            )
                        val bypassIds =
                            legacy
                                .filter { it.bypassLegacy() }
                                .map { it.appId }
                                .toSet()
                        Pair(emptyList(), bypassIds)
                    }
                    else -> {
                        val list =
                            json.decodeFromString(
                                ListSerializer(VpnAppRouteRule.serializer()),
                                raw,
                            )
                        Pair(list.filter { it.throughVpn }, emptySet())
                    }
                }
            } catch (_: Exception) {
                Pair(emptyList(), emptySet())
            }
        }
    }
}
