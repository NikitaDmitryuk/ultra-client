package io.nikdmitryuk.ultraclient.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(UltraClientDatabase.Schema, "ultra_client.db")
}
