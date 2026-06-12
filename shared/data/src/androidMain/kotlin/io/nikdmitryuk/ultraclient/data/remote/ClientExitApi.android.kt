package io.nikdmitryuk.ultraclient.data.remote

import io.nikdmitryuk.ultraclient.domain.model.ExitLocation
import io.nikdmitryuk.ultraclient.domain.model.ExitSelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

actual class ClientExitApi actual constructor() {
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
            if (exitId == null) {
                """{"exit_id":null}"""
            } else {
                JSONObject().put("exit_id", exitId).toString()
            }
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
            val url = URL(apiBaseUrl.trimEnd('/') + "/" + path)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) {
                error(text.ifBlank { "Location API error $code" })
            }
            parseSelection(text)
        }

    private fun parseSelection(text: String): ExitSelectionState {
        val root = JSONObject(text)
        val exitsJson = root.optJSONArray("exits")
        val exits =
            buildList {
                if (exitsJson != null) {
                    for (i in 0 until exitsJson.length()) {
                        val item = exitsJson.getJSONObject(i)
                        add(
                            ExitLocation(
                                id = item.optString("id"),
                                displayName = item.optString("display_name", item.optString("id")),
                                countryCode = item.optString("country_code"),
                                countryName = item.optString("country_name"),
                                city = item.optString("city"),
                                reachable = item.optBoolean("reachable"),
                                latencyMs = item.optLongOrNull("latency_ms"),
                                selected = item.optBoolean("selected"),
                                effective = item.optBoolean("effective"),
                            ),
                        )
                    }
                }
            }
        return ExitSelectionState(
            selectedExitId = root.optNullableString("selected_exit_id"),
            effectiveExitId = root.optNullableString("effective_exit_id"),
            exits = exits,
        )
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null
