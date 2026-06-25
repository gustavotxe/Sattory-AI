package org.sattory.app.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class ThinkingContentFilter {
    private enum class State { Detecting, Suppressing, Passing }

    private var state = State.Detecting
    private var pending = ""

    fun accept(chunk: String): String {
        if (chunk.isEmpty()) return ""
        if (state == State.Passing) return chunk
        pending += chunk

        if (state == State.Detecting) {
            val candidate = pending.trimStart()
            if (candidate.isEmpty() || START_TAG.startsWith(candidate)) return ""
            if (candidate.startsWith(START_TAG)) {
                pending = candidate.removePrefix(START_TAG)
                state = State.Suppressing
            } else {
                state = State.Passing
                return pending.trimStart().also { pending = "" }
            }
        }

        val end = pending.indexOf(END_TAG)
        if (end >= 0) {
            val visible = pending.substring(end + END_TAG.length).trimStart()
            pending = ""
            state = State.Passing
            return visible
        }

        pending = pending.takeLast((END_TAG.length - 1).coerceAtMost(pending.length))
        return ""
    }

    fun finish(): String = when (state) {
        State.Detecting, State.Passing -> pending
        State.Suppressing -> ""
    }.also { pending = "" }

    private companion object {
        const val START_TAG = "<think>"
        const val END_TAG = "</think>"
    }
}

internal fun Flow<String>.withoutThinkingContent(): Flow<String> = flow {
    val filter = ThinkingContentFilter()
    var started = false
    collect { chunk ->
        val value = filter.accept(chunk)
        val visible = if (started) value else value.trimStart()
        if (visible.isNotEmpty()) {
            started = true
            emit(visible)
        }
    }
    val remainder = filter.finish()
    val visibleRemainder = if (started) remainder else remainder.trimStart()
    if (visibleRemainder.isNotEmpty()) emit(visibleRemainder)
}
