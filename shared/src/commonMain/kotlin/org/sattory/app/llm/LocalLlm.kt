package org.sattory.app.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class GenerationOptions(
    val maxTokens: Int = 512,
    val contextSize: Int = 4096,
    val temperature: Float = 0.55f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.05f,
    val isolatedContext: Boolean = false,
) {
    companion object {
        val GroundedAnswering = GenerationOptions(
            temperature = 0f,
            topP = 0.9f,
            repeatPenalty = 1f,
            isolatedContext = true,
        )
    }
}

interface LocalLlm {
    suspend fun countPromptTokens(prompt: String): Int
    fun generate(prompt: String, options: GenerationOptions = GenerationOptions()): Flow<String>
    suspend fun wasLastGenerationTokenLimited(): Boolean
    suspend fun resetConversation()
    suspend fun truncateConversation(messageCount: Int)
    fun close()
}

class UnavailableLocalLlm(private val reason: String) : LocalLlm {
    override suspend fun countPromptTokens(prompt: String): Int = error(reason)
    override fun generate(prompt: String, options: GenerationOptions): Flow<String> = flow { error(reason) }
    override suspend fun wasLastGenerationTokenLimited(): Boolean = false
    override suspend fun resetConversation() = Unit
    override suspend fun truncateConversation(messageCount: Int) = Unit
    override fun close() = Unit
}
