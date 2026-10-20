package org.sattory.app.rag.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        DocumentCollectionEntity::class,
        RagDocumentEntity::class,
        RagChunkEntity::class,
        SavedConversationEntity::class,
        SavedConversationMessageEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(SattoryDatabaseConstructor::class)
abstract class SattoryDatabase : RoomDatabase() {
    abstract fun ragDao(): RagDao
    abstract fun savedConversationDao(): SavedConversationDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SattoryDatabaseConstructor : RoomDatabaseConstructor<SattoryDatabase> {
    override fun initialize(): SattoryDatabase
}

val SattoryDatabaseMigrations = arrayOf(
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_conversations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    collectionId INTEGER,
                    collectionTitle TEXT,
                    savedAtMillis INTEGER NOT NULL,
                    FOREIGN KEY(collectionId) REFERENCES document_collections(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_saved_conversations_collectionId ON saved_conversations(collectionId)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_saved_conversations_savedAtMillis ON saved_conversations(savedAtMillis)")
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_conversation_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    conversationId INTEGER NOT NULL,
                    messageIndex INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    isFromUser INTEGER NOT NULL,
                    FOREIGN KEY(conversationId) REFERENCES saved_conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_saved_conversation_messages_conversationId ON saved_conversation_messages(conversationId)")
        }
    },
)
