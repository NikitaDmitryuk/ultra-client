package io.nikdmitryuk.ultraclient.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val appDir =
            File(System.getProperty("user.home"), ".ultra-client")
                .apply { mkdirs() }
        val dbFile = File(appDir, "ultra-client.db")
        val shouldCreate = !dbFile.exists() || dbFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (shouldCreate) {
            UltraClientDatabase.Schema.create(driver)
        }
        return driver
    }
}
