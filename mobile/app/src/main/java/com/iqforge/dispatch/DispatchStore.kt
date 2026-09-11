package com.iqforge.dispatch

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DispatchRecord(
    val id: String,
    val instruction: String,
    val summary: String,
    val command: String? = null,
    val cwd: String,
    val executable: Boolean,
    val status: DispatchStatus = DispatchStatus.PLANNED,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class DispatchStatus { PLANNED, RUNNING, COMPLETED, FAILED }

class DispatchStore(private val preferences: SharedPreferences, private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun load(): List<DispatchRecord> = runCatching {
        json.decodeFromString<List<DispatchRecord>>(preferences.getString(KEY, "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun add(record: DispatchRecord): List<DispatchRecord> = (listOf(record) + load()).take(40).also(::save)

    fun update(id: String, transform: (DispatchRecord) -> DispatchRecord): List<DispatchRecord> =
        load().map { if (it.id == id) transform(it) else it }.also(::save)

    private fun save(records: List<DispatchRecord>) {
        preferences.edit().putString(KEY, json.encodeToString(records)).apply()
    }

    private companion object { const val KEY = "dispatch_records_v1" }
}
