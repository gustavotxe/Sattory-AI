package org.sattory.app.rag.domain

object RagAnswerFormatter {
    fun format(answer: String): String {
        val match = TRAILING_REFUSAL.find(answer) ?: return answer
        val precedingAnswer = answer.substring(0, match.range.first).trimEnd()
        return precedingAnswer.ifBlank { answer }
    }

    private val TRAILING_REFUSAL = Regex(
        "(?:\\s*O material enviado n[aã]o informa isso\\.\\s*)+$",
        RegexOption.IGNORE_CASE,
    )
}
