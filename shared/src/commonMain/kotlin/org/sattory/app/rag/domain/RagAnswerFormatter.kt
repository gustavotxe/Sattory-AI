package org.sattory.app.rag.domain

object RagAnswerFormatter {
    fun format(answer: String): String {
        var formatted = answer
        while (true) {
            val suffix = TRAILING_SUFFIX.find(formatted) ?: break
            val precedingAnswer = formatted.substring(0, suffix.range.first).trimEnd()
            if (precedingAnswer.isBlank()) break
            formatted = precedingAnswer
        }
        return formatted
    }

    private val TRAILING_SUFFIX = Regex(
        "(?:\\s*(?:,?\\s*(?:conforme|de acordo com)\\s+(?:o que\\s+)?(?:foi\\s+)?(?:informado|apresentado|descrito)\\s+(?:em|por)\\s*)?\\[Fonte\\s+\\d+](?:\\s*,?\\s*\\[Fonte\\s+\\d+])*\\.?|\\s*O material enviado n[aã]o informa isso\\.\\s*)+$",
        RegexOption.IGNORE_CASE,
    )
}
