package org.sattory.app.rag

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.sattory.app.rag.local.DatabaseBuilderFactory
import org.sattory.app.rag.local.SattoryDatabase
import org.sattory.app.rag.local.SattoryDatabaseMigrations

class AndroidDatabaseBuilderFactory(context: Context) :
    DatabaseBuilderFactory {
    private val appContext = context.applicationContext

    override fun create(): SattoryDatabase {
        val dbFile = appContext.getDatabasePath(DATABASE_NAME)
        return Room.databaseBuilder<SattoryDatabase>(
            context = appContext,
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*SattoryDatabaseMigrations)
            .build()
    }

    private companion object {
        const val DATABASE_NAME = "sattory.db"
    }
}
