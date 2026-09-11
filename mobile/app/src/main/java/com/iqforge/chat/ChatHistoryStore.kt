package com.iqforge.chat

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedChat(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<SavedMessage> = emptyList()
)

@Serializable
data class SavedMessage(val role: String, val text: String)

/** Durable local chat history. Incognito sessions never call this store. */
class ChatHistoryStore(
    private val preferences: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): List<SavedChat> = runCatching {
        json.decodeFromString<List<SavedChat>>(preferences.getString(KEY, "[]").orEmpty())
    }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }

    fun append(chatId: String, role: String, text: String): List<SavedChat> {
        val now = System.currentTimeMillis()
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == chatId }
        val existing = current.getOrNull(index)
        val title = existing?.title ?: text.lineSequence().firstOrNull().orEmpty().trim().take(52)
            .ifBlank { "New chat" }
        val updated = SavedChat(
            id = chatId,
            title = title,
            updatedAt = now,
            messages = existing?.messages.orEmpty().plus(SavedMessage(role, text.take(MAX_MESSAGE_CHARS)))
                .takeLast(MAX_MESSAGES)
        )
        if (index >= 0) current[index] = updated else current += updated
        val result = current.sortedByDescending { it.updatedAt }.take(MAX_CHATS)
        preferences.edit().putString(KEY, json.encodeToString(result)).apply()
        return result
    }

    private companion object {
        const val KEY = "saved_chats_v1"
        const val MAX_CHATS = 30
        const val MAX_MESSAGES = 80
        const val MAX_MESSAGE_CHARS = 24_000
    }
}
