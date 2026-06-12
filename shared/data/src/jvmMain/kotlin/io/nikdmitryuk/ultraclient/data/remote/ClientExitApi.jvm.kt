package io.nikdmitryuk.ultraclient.data.remote

import io.nikdmitryuk.ultraclient.domain.model.ExitLocation
import io.nikdmitryuk.ultraclient.domain.model.ExitSelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

actual class ClientExitApi actual constructor() {
    private val client = HttpClient.newBuilder().build()
    private val json = Json { ignoreUnknownKeys = true }

    actual suspend fun listExits(
        apiBaseUrl: String,
        token: String,
    ): ExitSelectionState = request(apiBaseUrl, token, "GET", null)

    actual suspend fun setExitSelection(
        apiBaseUrl: String,
        token: String,
        exitId: String?,
    ): ExitSelectionState {
        val body =
            buildJsonObject {
                if (exitId == null) {
                    put("exit_id", JsonNull)
                } else {
                    put("exit_id", exitId)
                }
            }.toString()
        return request(apiBaseUrl, token, "PUT", body)
    }

    private suspend fun request(
        apiBaseUrl: String,
        token: String,
        method: String,
        body: String?,
    ): ExitSelectionState =
        withContext(Dispatchers.IO) {
            val path = if (method == "GET") "exits" else "exit-selection"
            val uri = URI.create(apiBaseUrl.trimEnd('/') + "/" + path)
            val builder =
                HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
            val request =
                if (body == null) {
                    builder.GET().build()
                } else {
                    builder
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build()
                }
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                error(response.body().ifBlank { "Location API error ${response.statusCode()}" })
            }
            parseSelection(response.body())
        }

    private fun parseSelection(text: String): ExitSelectionState {
        val root = json.parseToJsonElement(text).jsonObject
        val exits =
            root["exits"]
                ?.jsonArray
                ?.map { item ->
                    val obj = item.jsonObject
                    ExitLocation(
                        id = obj.string("id"),
                        displayName = obj.string("display_name").ifBlank { obj.string("id") },
                        countryCode = obj.string("country_code"),
                        countryName = obj.string("country_name"),
                        city = obj.string("city"),
                        reachable = obj.boolean("reachable"),
                        latencyMs = obj.longOrNull("latency_ms"),
                        selected = obj.boolean("selected"),
                        effective = obj.boolean("effective"),
                    )
                }
                .orEmpty()
        return ExitSelectionState(
            selectedExitId = root.stringOrNull("selected_exit_id"),
            effectiveExitId = root.stringOrNull("effective_exit_id"),
            exits = exits,
        )
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.stringOrNull(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.boolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: false

    private fun JsonObject.longOrNull(name: String): Long? =
        this[name]?.jsonPrimitive?.longOrNull
}
