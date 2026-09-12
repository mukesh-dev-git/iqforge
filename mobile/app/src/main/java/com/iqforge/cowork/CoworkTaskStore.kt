package com.iqforge.cowork

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CoworkTask(
    val id: String,
    val title: String,
    val instruction: String,
    val repository: String? = null,
    val status: CoworkStatus = CoworkStatus.RUNNING,
    val result: String? = null,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
enum class CoworkStatus { RUNNING, COMPLETED, FAILED, INTERRUPTED }

/** Durable task ledger. Every entry is created by a real model request. */
class CoworkTaskStore(
    private val preferences: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): List<CoworkTask> = runCatching {
        json.decodeFromString<List<CoworkTask>>(preferences.getString(KEY, "[]").orEmpty())
    }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }

    fun create(title: String, instruction: String, repository: String?): CoworkTask {
        val now = System.currentTimeMillis()
        val task = CoworkTask(
            id = "task-$now",
            title = title.trim(),
            instruction = instruction.trim(),
            repository = repository,
            createdAt = now,
            updatedAt = now
        )
        save(listOf(task) + load())
        return task
    }

    fun finish(id: String, result: String): List<CoworkTask> = update(id) {
        it.copy(status = CoworkStatus.COMPLETED, result = result, error = null, updatedAt = System.currentTimeMillis())
    }

    fun fail(id: String, message: String): List<CoworkTask> = update(id) {
        it.copy(status = CoworkStatus.FAILED, error = message, updatedAt = System.currentTimeMillis())
    }

    fun remove(id: String): List<CoworkTask> = load().filterNot { it.id == id }.also(::save)

    fun markInterrupted(): List<CoworkTask> = load().map {
        if (it.status == CoworkStatus.RUNNING) it.copy(
            status = CoworkStatus.INTERRUPTED,
            error = "The app stopped before this task returned. Run it again to continue.",
            updatedAt = System.currentTimeMillis()
        ) else it
    }.also(::save)

    private fun update(id: String, transform: (CoworkTask) -> CoworkTask): List<CoworkTask> =
        load().map { if (it.id == id) transform(it) else it }.sortedByDescending { it.updatedAt }.also(::save)

    private fun save(tasks: List<CoworkTask>) {
        preferences.edit().putString(KEY, json.encodeToString(tasks.take(MAX_TASKS))).apply()
    }

    private companion object {
        const val KEY = "cowork_tasks_v1"
        const val MAX_TASKS = 50
    }
}
