package org.sattory.app.conversation.domain

class ObserveSavedConversationsUseCase(
    private val repository: SavedConversationRepository,
) {
    operator fun invoke() = repository.observeSummaries()
}

class SaveConversationUseCase(
    private val repository: SavedConversationRepository,
) {
    suspend operator fun invoke(input: SaveConversationInput): Long = repository.save(input)
}

class DeleteSavedConversationUseCase(
    private val repository: SavedConversationRepository,
) {
    suspend operator fun invoke(conversationId: Long) = repository.delete(conversationId)
}
