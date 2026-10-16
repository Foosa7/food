package com.food.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One AI analysis of the diet, as the model answered it plus when and by whom. */
@Serializable
data class Analysis(
    /** 2–3 sentences: how things are going over time. */
    val progress: String = "",
    val good: List<String> = emptyList(),
    val improve: List<String> = emptyList(),
    /** Concrete things to try, short. */
    val suggestions: List<String> = emptyList(),
    /** The model's memory for next time; not shown up front. */
    val notes: String = "",
    /** ISO date-time it was made. */
    val at: String = "",
    val model: String = "",
    val effort: String = "",
    val seconds: Int = 0,
    /** Logged days it looked at. */
    val days: Int = 0,
)

object Analyses {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * The model's reply, which should be one JSON object. Models sometimes wrap it in a code
     * fence or a sentence, so the outermost braces are taken. Null if there's no usable object.
     */
    fun parse(reply: String): Analysis? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val a = runCatching { json.decodeFromString<Analysis>(reply.substring(start, end + 1)) }.getOrNull() ?: return null
        return a.takeIf { it.progress.isNotBlank() || it.suggestions.isNotEmpty() || it.improve.isNotEmpty() }
    }

    fun line(a: Analysis): String = json.encodeToString(a)

    /** One analysis per line, oldest first; a broken line is skipped. */
    fun lines(text: String): List<Analysis> = text.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { json.decodeFromString<Analysis>(it) }.getOrNull() }
        .toList()
}
