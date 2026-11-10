package org.sattory.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.sattory.app.conversation.data.UnavailableSavedConversationRepository
import org.sattory.app.conversation.domain.DeleteSavedConversationUseCase
import org.sattory.app.conversation.domain.GetSavedConversationUseCase
import org.sattory.app.conversation.domain.ObserveSavedConversationsUseCase
import org.sattory.app.conversation.domain.SaveConversationUseCase
import org.sattory.app.conversation.domain.SavedConversationRepository
import org.sattory.app.llm.LocalLlm
import org.sattory.app.llm.UnavailableLocalLlm
import org.sattory.app.model.EmbeddingModelPreparationUseCase
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.model.ModelRepository
import org.sattory.app.model.ReadyModelRepository
import org.sattory.app.presentation.ChatScreen
import org.sattory.app.presentation.ChatStoreFactory
import org.sattory.app.rag.UnavailableRagRepository
import org.sattory.app.rag.UnavailableTextEmbedder
import org.sattory.app.rag.domain.CreateCollectionUseCase
import org.sattory.app.rag.domain.DeleteCollectionUseCase
import org.sattory.app.rag.domain.GetCollectionDraftUseCase
import org.sattory.app.rag.domain.ImportPdfUseCase
import org.sattory.app.rag.domain.ObserveCollectionsUseCase
import org.sattory.app.rag.domain.PdfDocument
import org.sattory.app.rag.domain.PdfRepository
import org.sattory.app.rag.domain.RagPromptBuilder
import org.sattory.app.rag.domain.RagRepository
import org.sattory.app.rag.domain.RetrieveContextUseCase
import org.sattory.app.rag.domain.TextEmbedder
import org.sattory.app.rag.domain.UpdateCollectionUseCase

@Composable
fun App(
    localLlm: LocalLlm,
    modelRepository: ModelRepository = ReadyModelRepository(),
) {
    App(listOf(sattoryModule(localLlm, modelRepository)))
}

@Composable
fun App(module: Module) {
    App(listOf(module))
}

@Composable
fun App(modules: List<Module> = listOf(defaultSattoryModule())) {
    KoinApplication(application = { modules(modules + commonSattoryModule()) }) {
        CloseLocalLlmOnDispose()
        CloseTextEmbedderOnDispose()
        ChatScreen(factory = koinInject())
    }
}

fun sattoryModule(
    localLlm: LocalLlm,
    modelRepository: ModelRepository = ReadyModelRepository(),
): Module = module {
    single { localLlm }
    single { modelRepository }
    single(named(EMBEDDING_MODEL_REPOSITORY)) { ReadyModelRepository() }
    single<PdfRepository> {
        object : PdfRepository {
            override suspend fun extract(reference: String): PdfDocument = error("Importacao de PDF nao configurada")
        }
    }
    single<RagRepository> { UnavailableRagRepository() }
    single<TextEmbedder> { UnavailableTextEmbedder() }
    single<SavedConversationRepository> { UnavailableSavedConversationRepository() }
}

private fun commonSattoryModule(): Module = module {
    single { ModelPreparationUseCase(get()) }
    single { EmbeddingModelPreparationUseCase(get(named(EMBEDDING_MODEL_REPOSITORY))) }
    single { ObserveCollectionsUseCase(get()) }
    single { CreateCollectionUseCase(get()) }
    single { GetCollectionDraftUseCase(get()) }
    single { UpdateCollectionUseCase(get()) }
    single { DeleteCollectionUseCase(get()) }
    single { RetrieveContextUseCase(get()) }
    single { RagPromptBuilder() }
    single { ImportPdfUseCase(get()) }
    single { ObserveSavedConversationsUseCase(get()) }
    single { SaveConversationUseCase(get()) }
    single { GetSavedConversationUseCase(get()) }
    single { DeleteSavedConversationUseCase(get()) }
    factory {
        ChatStoreFactory(
            localLlm = get(),
            modelPreparationUseCase = get(),
            embeddingModelPreparationUseCase = get(),
            observeCollectionsUseCase = get(),
            createCollectionUseCase = get(),
            getCollectionDraftUseCase = get(),
            updateCollectionUseCase = get(),
            deleteCollectionUseCase = get(),
            retrieveContextUseCase = get(),
            ragPromptBuilder = get(),
            observeSavedConversationsUseCase = get(),
            saveConversationUseCase = get(),
            getSavedConversationUseCase = get(),
            deleteSavedConversationUseCase = get(),
            importPdfUseCase = get(),
        )
    }
}

private fun defaultSattoryModule(): Module = sattoryModule(
    localLlm = UnavailableLocalLlm("Modelo local nao configurado"),
)

@Composable
private fun CloseLocalLlmOnDispose(localLlm: LocalLlm = koinInject()) {
    DisposableEffect(localLlm) {
        onDispose {
            localLlm.close()
        }
    }
}

@Composable
private fun CloseTextEmbedderOnDispose(textEmbedder: TextEmbedder = koinInject()) {
    DisposableEffect(textEmbedder) {
        onDispose {
            textEmbedder.close()
        }
    }
}

const val EMBEDDING_MODEL_REPOSITORY = "embeddingModelRepository"
