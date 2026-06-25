package org.sattory.app

import android.app.Application
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.sattory.app.EMBEDDING_MODEL_REPOSITORY
import org.sattory.app.llm.AndroidLocalLlm
import org.sattory.app.llm.LocalLlm
import org.sattory.app.conversation.data.RoomSavedConversationRepository
import org.sattory.app.conversation.domain.SavedConversationRepository
import org.sattory.app.model.AndroidEmbeddingModelRepository
import org.sattory.app.model.AndroidModelRepository
import org.sattory.app.model.EmbeddingModelFileSpec
import org.sattory.app.model.ModelFileSpec
import org.sattory.app.model.ModelRepository
import org.sattory.app.rag.AndroidDatabaseBuilderFactory
import org.sattory.app.rag.data.NativeTextEmbedder
import org.sattory.app.rag.data.RagTextChunker
import org.sattory.app.rag.data.RoomRagRepository
import org.sattory.app.rag.data.local.DatabaseBuilderFactory
import org.sattory.app.rag.domain.RagRepository
import org.sattory.app.rag.domain.TextEmbedder

class SattoryApplication : Application() {
    val appModule: Module by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        module {
            single<LocalLlm> {
                AndroidLocalLlm(ModelFileSpec.file(applicationContext).absolutePath)
            }
            single<ModelRepository> {
                AndroidModelRepository(applicationContext)
            }
            single<ModelRepository>(named(EMBEDDING_MODEL_REPOSITORY)) {
                AndroidEmbeddingModelRepository(applicationContext)
            }
            single<DatabaseBuilderFactory> { AndroidDatabaseBuilderFactory(applicationContext) }
            single { get<DatabaseBuilderFactory>().create() }
            single { get<org.sattory.app.rag.data.local.SattoryDatabase>().ragDao() }
            single { get<org.sattory.app.rag.data.local.SattoryDatabase>().savedConversationDao() }
            single<TextEmbedder> { NativeTextEmbedder(EmbeddingModelFileSpec.file(applicationContext).absolutePath) }
            single { RagTextChunker() }
            single<RagRepository> { RoomRagRepository(get(), get(), get()) }
            single<SavedConversationRepository> { RoomSavedConversationRepository(get()) }
        }
    }
}
