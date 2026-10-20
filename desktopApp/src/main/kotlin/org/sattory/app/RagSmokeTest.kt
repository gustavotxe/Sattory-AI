package org.sattory.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.sattory.app.llm.GenerationOptions
import org.sattory.app.llm.LocalLlm
import org.sattory.app.rag.RagHybridRanker
import org.sattory.app.rag.RagTextChunker
import org.sattory.app.rag.RagEmbeddingTextNormalizer
import org.sattory.app.rag.VectorCodec
import org.sattory.app.rag.local.RagChunkWithDocumentEntity
import org.sattory.app.rag.domain.RagPromptBuilder
import org.sattory.app.rag.domain.TextEmbedder

internal fun runRagSmokeTest(platformModule: Module) {
    val application = koinApplication { modules(platformModule) }
    val llm = application.koin.get<LocalLlm>()
    val embedder = application.koin.get<TextEmbedder>()
    try {
        runBlocking {
            val document = """
                Cila era um monstro de seis cabecas. Ela vivia em uma caverna entre rochedos.
                Durante a travessia, Cila capturou seis marinheiros de Ulisses.

                Odisseu, tambem chamado Ulisses, era casado com Penelope. Telemaco era o filho de Odisseu e Penelope.
                Alcino ouviu Ulisses contar a historia da travessia. Alcino nao participou da travessia por Cila.
            """.trimIndent() + "\n\n" + (1..100).joinToString("\n\n") {
                "Secao $it: O texto explica as plantas, a absorcao de luz e a fotossintese. A luz fornece energia para as plantas crescerem."
            }
            val chunks = RagTextChunker().chunkForEmbedding(document) { embedder.countDocumentTokens(it) }
                .mapIndexed { index, text ->
                    val vector = embedder.embedForDocument(RagEmbeddingTextNormalizer.normalize(text))
                   RagChunkWithDocumentEntity(
                        index.toLong() + 1,
                        1,
                        index,
                        "Documento de teste",
                        text,
                        VectorCodec.encode(
                            VectorCodec.normalized(vector)
                        )
                    )
                }
            val builder = RagPromptBuilder()
            val ranker = RagHybridRanker()
            val cases = listOf(
                "Quem era Cila?" to "um monstro de seis cabecas",
                "Quem era o filho de Odisseu?" to "telemaco",
                "Quem era o filho de Ulisses?" to "telemaco",
                "Alcino ajudou Ulisses a passar por Cila?" to "nao",
                "Qual era a idade de Cila?" to "nao informa",
            )
            for ((question, expected) in cases) {
                val query = VectorCodec.normalized(embedder.embedForQuery(
                    RagEmbeddingTextNormalizer.normalize(question)))
                val sources = ranker.rank(chunks, question, query, 5)
                check(sources == ranker.rank(chunks, question, query, 5)) { "Recuperacao instavel" }

                val prepared = builder.prepare(question, sources, llm::countPromptTokens)
                check(prepared.inputTokens + GenerationOptions.GroundedAnswering.maxTokens + 64 <= 4096)

                val first = llm.generate(prepared.prompt, GenerationOptions.GroundedAnswering).toList().joinToString("")
                llm.generate("Responda apenas banana.", GenerationOptions(maxTokens = 8)).toList()

                val second = llm.generate(prepared.prompt, GenerationOptions.GroundedAnswering).toList().joinToString("")
                check(first == second) { "Resposta RAG variou com o mesmo prompt: $question" }

                val answer = first.lowercase().replace("não", "nao")
                val supportedAnswer = answer.contains(expected) || (expected == "nao informa" && answer.contains("nenhuma fonte"))
                check(supportedAnswer) { "Resposta sem a evidencia esperada: $question -> $first" }
                println("RAG_SMOKE_QUERY=$question TOKENS=${prepared.inputTokens} CHUNKS=${prepared.sources.map { it.chunkIndex }} ANSWER=$first")
            }
            println("RAG_SMOKE_TEST_OK chunks=${chunks.size}")
        }
    } finally {
        llm.close()
        embedder.close()
        application.close()
    }
}
