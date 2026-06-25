package org.sattory.app.conversation.domain

data class ConversationMessageDraft(
    val text: String,
    val isFromUser: Boolean,
)

data class SaveConversationInput(
    val conversationId: Long? = null,
    val title: String,
    val collectionId: Long?,
    val collectionTitle: String?,
    val messages: List<ConversationMessageDraft>,
)

data class SavedConversationSummary(
    val id: Long,
    val title: String,
    val collectionTitle: String?,
    val savedAtMillis: Long,
    val messageCount: Int,
)

data class SavedConversationDetail(
    val id: Long,
    val title: String,
    val collectionId: Long?,
    val collectionTitle: String?,
    val messages: List<ConversationMessageDraft>,
)
