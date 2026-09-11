package com.iqforge.workspace

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProjectMetadata(
    val name: String,
    val description: String = "",
    val instructions: String = "",
    val archived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

class ProjectMetadataStore(
    private val preferences: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): Map<String, ProjectMetadata> = runCatching {
        json.decodeFromString<List<ProjectMetadata>>(preferences.getString(KEY, "[]").orEmpty())
            .associateBy { it.name }
    }.getOrDefault(emptyMap())

    fun save(metadata: ProjectMetadata): Map<String, ProjectMetadata> =
        load().toMutableMap().apply { put(metadata.name, metadata.copy(updatedAt = System.currentTimeMillis())) }
            .also(::persist)

    fun remove(name: String): Map<String, ProjectMetadata> =
        load().toMutableMap().apply { remove(name) }.also(::persist)

    private fun persist(values: Map<String, ProjectMetadata>) {
        preferences.edit().putString(KEY, json.encodeToString(values.values.sortedBy { it.name })).apply()
    }

    private companion object { const val KEY = "project_metadata_v1" }
}
