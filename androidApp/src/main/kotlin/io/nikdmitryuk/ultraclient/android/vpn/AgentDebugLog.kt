package io.nikdmitryuk.ultraclient.android.vpn

import android.util.Log
import org.json.JSONObject

/**
 * Debug NDJSON lines в logcat (фильтр по тегу [TAG]). Сессия Cursor debug mode: `5aebf7`.
 */
internal object AgentDebugLog {
    const val TAG = "UltraAgent"
    private const val SESSION_ID = "5aebf7"

    // #region agent log
    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
    ) {
        try {
            val dataJson = JSONObject()
            for ((k, v) in data) {
                dataJson.put(k, v ?: JSONObject.NULL)
            }
            val payload =
                JSONObject().apply {
                    put("sessionId", SESSION_ID)
                    put("hypothesisId", hypothesisId)
                    put("location", location)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                    put("data", dataJson)
                }
            Log.w(TAG, payload.toString())
        } catch (_: Exception) {
            // не ломаем VPN
        }
    }
    // #endregion
}
