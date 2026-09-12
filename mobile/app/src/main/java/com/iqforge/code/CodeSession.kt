package com.iqforge.code

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One turn in a code session's conversation. `role` is "user", "assistant" (answered by the
 * on-device model — the default path), or "laptop" (answered via the laptop bridge, for an
 * explicit "/" command, an explicit "/escalate", or a detected bigger task like a test run).
 */
@Serializable
data class CodeSessionMessage(
    val role: String,
    val text: String,
    val at: Long = System.currentTimeMillis(),
)

/** A coding-agent session bound to one cloned/created repo working directory. */
@Serializable
data class CodeSession(
    val id: String,
    val title: String,
    val repository: String,
    val workspace: String,
    val messages: List<CodeSessionMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Durable log of code sessions — one per repo working directory, persisted the same way as
 * CoworkTaskStore/DispatchStore (SharedPreferences + JSON). Reconstructed after this file was
 * missing from the repo (referenced everywhere in MainActivity.kt, never committed) — its
 * shape is inferred exactly from those call sites: preferences-based constructor, load(),
 * create(workspace), append(id, role, text).
 */
class CodeSessionStore(
    private val preferences: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun load(): List<CodeSession> = runCatching {
        json.decodeFromString<List<CodeSession>>(preferences.getString(KEY, "[]").orEmpty())
    }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }

    fun create(workspace: String): CodeSession {
        val name = File(workspace).name.ifBlank { workspace }
        val now = System.currentTimeMillis()
        val session = CodeSession(
            id = "session-$now",
            title = name,
            repository = name,
            workspace = workspace,
            createdAt = now,
            updatedAt = now,
        )
        save(listOf(session) + load())
        return session
    }

    fun append(id: String, role: String, text: String): List<CodeSession> =
        load().map {
            if (it.id == id) {
                it.copy(messages = it.messages + CodeSessionMessage(role, text), updatedAt = System.currentTimeMillis())
            } else it
        }.sortedByDescending { it.updatedAt }.also(::save)

    private fun save(sessions: List<CodeSession>) {
        preferences.edit().putString(KEY, json.encodeToString(sessions.take(MAX_SESSIONS))).apply()
    }

    private companion object {
        const val KEY = "code_sessions_v1"
        const val MAX_SESSIONS = 30
    }
}
