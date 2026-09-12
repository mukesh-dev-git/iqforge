package com.iqforge.code

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CodeSessionMessage(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

@Serializable
data class CodeSession(
    val id: String,
    val title: String,
    val workspace: String,
    val repository: String,
    val messages: List<CodeSessionMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

class CodeSessionStore(
    private val preferences: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): List<CodeSession> = runCatching {
        json.decodeFromString<List<CodeSession>>(preferences.getString(KEY, "[]").orEmpty())
    }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }

    fun create(workspace: String, title: String = "New Session"): CodeSession {
        val session = CodeSession("code-${System.currentTimeMillis()}", title, workspace, workspace.substringAfterLast('\\'))
        save(listOf(session) + load())
        return session
    }

    fun append(id: String, role: String, text: String): List<CodeSession> {
        val updated = load().map {
            if (it.id == id) it.copy(
                title = if (it.messages.isEmpty() && role == "user") text.take(42) else it.title,
                messages = it.messages + CodeSessionMessage(role, text),
                updatedAt = System.currentTimeMillis()
            ) else it
        }.sortedByDescending { it.updatedAt }
        save(updated)
        return updated
    }

    private fun save(sessions: List<CodeSession>) = preferences.edit().putString(KEY, json.encodeToString(sessions)).apply()

    companion object { private const val KEY = "code_sessions_v1" }
}
