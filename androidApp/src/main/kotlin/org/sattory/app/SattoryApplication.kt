package org.sattory.app

import android.app.Application
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.sattory.app.conversation.data.RoomSavedConversationRepository
import org.sattory.app.conversation.domain.SavedConversationRepository
import org.sattory.app.llm.AndroidLocalLlm
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.AndroidEmbeddingModelRepository
import org.sattory.app.model.AndroidModelRepository
import org.sattory.app.model.EmbeddingModelFileSpec
import org.sattory.app.model.ModelFileSpec
import org.sattory.app.model.ModelRepository
import org.sattory.app.rag.AndroidDatabaseBuilderFactory
import org.sattory.app.rag.data.AndroidPdfRepository
import org.sattory.app.rag.domain.PdfRepository
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
            single<org.sattory.app.rag.local.DatabaseBuilderFactory> { AndroidDatabaseBuilderFactory(applicationContext) }
            single { get<org.sattory.app.rag.local.DatabaseBuilderFactory>().create() }
            single { get<org.sattory.app.rag.local.SattoryDatabase>().ragDao() }
            single { get<org.sattory.app.rag.local.SattoryDatabase>().savedConversationDao() }
            single<TextEmbedder> {
                _root_ide_package_.org.sattory.app.rag.NativeTextEmbedder(
                    EmbeddingModelFileSpec.file(applicationContext).absolutePath
                )
            }
            single { _root_ide_package_.org.sattory.app.rag.RagTextChunker() }
            single<PdfRepository> {
                AndroidPdfRepository(
                    applicationContext
                )
            }
            single<RagRepository> {
                _root_ide_package_.org.sattory.app.rag.RoomRagRepository(
                    get<org.sattory.app.rag.local.RagDao>(),
                    get(),
                    get()
                )
            }
            single<SavedConversationRepository> {
                RoomSavedConversationRepository(get<org.sattory.app.rag.local.SavedConversationDao>())
            }
        }
    }
}
