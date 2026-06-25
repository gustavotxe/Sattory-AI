package org.sattory.app.rag

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.sattory.app.platform.DesktopAppDirectories
import org.sattory.app.rag.data.local.DatabaseBuilderFactory
import org.sattory.app.rag.data.local.SattoryDatabase
import org.sattory.app.rag.data.local.SattoryDatabaseMigrations
import java.io.File

class DesktopDatabaseBuilderFactory(
    private val directories: DesktopAppDirectories,
) : DatabaseBuilderFactory {
    override fun create(): SattoryDatabase {
        val databaseFile = File(directories.dataRoot, DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        return Room.databaseBuilder<SattoryDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*SattoryDatabaseMigrations)
            .build()
    }

    private companion object {
        const val DATABASE_NAME = "sattory.db"
    }
}
