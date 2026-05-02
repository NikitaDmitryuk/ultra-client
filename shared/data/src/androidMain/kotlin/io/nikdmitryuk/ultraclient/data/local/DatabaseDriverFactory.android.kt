package io.nikdmitryuk.ultraclient.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(UltraClientDatabase.Schema, context, "ultra_client.db")
}
