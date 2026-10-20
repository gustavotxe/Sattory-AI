package org.sattory.app.rag

import kotlin.math.ln
import org.sattory.app.rag.domain.RagSource
import org.sattory.app.rag.local.RagChunkWithDocumentEntity

class RagHybridRanker {
    fun rank(
        indexedChunks: List<RagChunkWithDocumentEntity>,
        queryText: String,
        queryEmbedding: FloatArray,
        resultLimit: Int,
    ): List<RagSource> {
        if (indexedChunks.isEmpty() || resultLimit <= 0) return emptyList()
        val queryTerms = tokenize(queryText).filterNot { it in STOP_WORDS }.toSet()
        val tokenizedDocuments = indexedChunks.map { tokenize(it.text) }
        val averageDocumentLength = tokenizedDocuments.map { it.size }.average().coerceAtLeast(1.0)
        val documentFrequencyByTerm = queryTerms.associateWith { term -> tokenizedDocuments.count { term in it } }
        val scoredCandidates = indexedChunks.mapIndexed { chunkPosition, chunk ->
            val chunkTokens = tokenizedDocuments[chunkPosition]
            val tokenFrequencies = chunkTokens.groupingBy { it }.eachCount()
            val lexicalScore = queryTerms.sumOf { term ->
                val termFrequency = (tokenFrequencies[term] ?: 0).toDouble()
                val inverseDocumentFrequency = ln(
                    1.0 + (indexedChunks.size - documentFrequencyByTerm.getValue(term) + 0.5) /
                        (documentFrequencyByTerm.getValue(term) + 0.5),
                )
                inverseDocumentFrequency * termFrequency * 2.2 /
                    (termFrequency + 1.2 * (0.25 + 0.75 * chunkTokens.size / averageDocumentLength))
            }
            val semanticSimilarity = VectorCodec.cosine(queryEmbedding, VectorCodec.decode(chunk.embedding))
            val queryTermCoverage = if (queryTerms.isEmpty()) {
                0.0
            } else {
                queryTerms.count { it in tokenFrequencies }.toDouble() / queryTerms.size
            }
            RankedChunkCandidate(chunk, lexicalScore, semanticSimilarity, queryTermCoverage)
        }
        val semanticRankedCandidates = scoredCandidates
            .sortedWith(compareByDescending<RankedChunkCandidate> { it.semanticSimilarity }.thenBy { it.chunk.chunkId })
        val lexicalRankedCandidates = scoredCandidates
            .filter { it.lexicalScore > 0 }
            .sortedWith(compareByDescending<RankedChunkCandidate> { it.lexicalScore }.thenBy { it.chunk.chunkId })
        val semanticRankByChunkId = semanticRankedCandidates
            .mapIndexed { rankPosition, candidate -> candidate.chunk.chunkId to rankPosition }
            .toMap()
        val lexicalRankByChunkId = lexicalRankedCandidates
            .mapIndexed { rankPosition, candidate -> candidate.chunk.chunkId to rankPosition }
            .toMap()
        val bestSemanticSimilarity = scoredCandidates.maxOf { it.semanticSimilarity }

        val candidatePool = (semanticRankedCandidates.take(24) + lexicalRankedCandidates.take(24))
            .distinctBy { it.chunk.chunkId }
        val scoreRankedCandidates = candidatePool
            .filter {
                it.lexicalScore > 0 ||
                    (it.semanticSimilarity >= 0.45f && it.semanticSimilarity >= bestSemanticSimilarity - 0.06f)
            }
            .map { candidate ->
                val semanticRankPosition = semanticRankByChunkId.getValue(candidate.chunk.chunkId)
                val lexicalRankPosition = lexicalRankByChunkId[candidate.chunk.chunkId]
                val fusionScore = 0.45 * 20.0 / (20 + semanticRankPosition) +
                    (lexicalRankPosition?.let { 0.45 * 20.0 / (20 + it) } ?: 0.0) +
                    0.10 * candidate.queryTermCoverage
                candidate to fusionScore
            }
            .sortedWith(
                compareByDescending<Pair<RankedChunkCandidate, Double>> { it.second }
                    .thenBy { it.first.chunk.chunkId },
            )
        val minimumAcceptedScore = (scoreRankedCandidates.firstOrNull()?.second ?: 0.0) * 0.65
        val selectedSources = mutableListOf<RagSource>()
        for ((candidate, fusionScore) in scoreRankedCandidates) {
            if (fusionScore < minimumAcceptedScore) continue
            var sourceText = candidate.chunk.text
            for (selectedSource in selectedSources) {
                if (selectedSource.documentId != candidate.chunk.documentId) continue
                sourceText = removeBoundaryOverlap(sourceText, selectedSource.text)
            }
            if (sourceText.isBlank()) continue
            selectedSources += RagSource(
                candidate.chunk.documentTitle,
                sourceText.trim(),
                fusionScore.toFloat(),
                candidate.chunk.chunkIndex,
                candidate.chunk.documentId,
            )
            if (selectedSources.size >= resultLimit) break
        }
        return selectedSources
    }

    private fun removeBoundaryOverlap(sourceText: String, previousSourceText: String): String {
        if (previousSourceText.contains(sourceText)) return ""
        for (length in minOf(sourceText.length, previousSourceText.length) downTo 40) {
            if (sourceText.startsWith(previousSourceText.takeLast(length))) return sourceText.drop(length)
            if (sourceText.endsWith(previousSourceText.take(length))) return sourceText.dropLast(length)
        }
        return sourceText
    }

    private fun tokenize(sourceText: String): List<String> = RagEmbeddingTextNormalizer.normalize(sourceText).map { character ->
        when (character) {
            'á', 'à', 'â', 'ã', 'ä' -> 'a'
            'é', 'è', 'ê', 'ë' -> 'e'
            'í', 'ì', 'î', 'ï' -> 'i'
            'ó', 'ò', 'ô', 'õ', 'ö' -> 'o'
            'ú', 'ù', 'û', 'ü' -> 'u'
            'ç' -> 'c'
            else -> character
        }
    }.joinToString("").split(WORD_SEPARATOR).filter { it.isNotBlank() }

    private data class RankedChunkCandidate(
        val chunk: RagChunkWithDocumentEntity,
        val lexicalScore: Double,
        val semanticSimilarity: Float,
        val queryTermCoverage: Double,
    )

    private companion object {
        val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
        val STOP_WORDS = setOf(
            "o", "a", "os", "as", "de", "da", "do", "das", "dos", "e", "em", "um", "uma", "que",
            "qual", "quais", "quem", "como", "era", "foi", "ser", "para", "por", "com", "sobre",
            "contexto", "pergunta", "atual", "the", "is", "was", "who", "what", "how", "of", "and", "to", "in",
        )
    }
}
