package org.sattory.app.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class GenerationOptions(
    val maxTokens: Int = 512,
    val contextSize: Int = 4096,
    val temperature: Float = 0.7f,
)

interface LocalLlm {
    fun generate(prompt: String, options: GenerationOptions = GenerationOptions()): Flow<String>
    suspend fun resetConversation()
    suspend fun truncateConversation(messageCount: Int)
    fun close()
}

class UnavailableLocalLlm(private val reason: String) : LocalLlm {
    override fun generate(prompt: String, options: GenerationOptions): Flow<String> = flow { error(reason) }
    override suspend fun resetConversation() = Unit
    override suspend fun truncateConversation(messageCount: Int) = Unit
    override fun close() = Unit
}
