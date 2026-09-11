package com.iqforge.bridge

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** HTTP client for the laptop bridge. The JSON fields mirror CONTRACT.md exactly. */
open class LaptopBridgeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    open suspend fun escalate(
        laptopUrl: String,
        task: BridgeTask,
        context: String,
        instruction: String
    ): String = escalateWithOptions(laptopUrl, task, context, instruction, "medium")

    open suspend fun escalateWithOptions(
        laptopUrl: String,
        task: BridgeTask,
        context: String,
        instruction: String,
        effort: String
    ): String = request(
        laptopUrl = laptopUrl,
        endpoint = "escalate",
        body = json.encodeToString(
            EscalateRequest.serializer(),
            EscalateRequest(task.wireName, context, instruction, effort)
        )
    ) { response -> json.decodeFromString(EscalateResponse.serializer(), response).result }

    suspend fun execute(laptopUrl: String, command: String, cwd: String): ExecResult = request(
        laptopUrl = laptopUrl,
        endpoint = "exec",
        body = json.encodeToString(ExecRequest.serializer(), ExecRequest(command, cwd))
    ) { response ->
        json.decodeFromString(ExecResponse.serializer(), response).let {
            ExecResult(it.stdout, it.stderr, it.exitCode)
        }
    }

    suspend fun planDispatch(laptopUrl: String, instruction: String, cwd: String): DispatchPlan = request(
        laptopUrl = laptopUrl,
        endpoint = "dispatch/plan",
        body = json.encodeToString(DispatchPlanRequest.serializer(), DispatchPlanRequest(instruction, cwd))
    ) { response -> json.decodeFromString(DispatchPlan.serializer(), response) }

    suspend fun dispatchWorkspaces(laptopUrl: String): List<String> =
        json.decodeFromString(DispatchWorkspacesResponse.serializer(), get(laptopUrl, "dispatch/workspaces")).workspaces

    suspend fun workspaceFiles(laptopUrl: String, cwd: String): List<String> =
        json.decodeFromString(
            WorkspaceFilesResponse.serializer(),
            get(laptopUrl, "workspace/files?cwd=${encode(cwd)}")
        ).files

    suspend fun workspaceFile(laptopUrl: String, cwd: String, path: String): String =
        json.decodeFromString(
            WorkspaceFileResponse.serializer(),
            get(laptopUrl, "workspace/file?cwd=${encode(cwd)}&path=${encode(path)}")
        ).content

    suspend fun writeWorkspaceFile(laptopUrl: String, cwd: String, path: String, content: String): Int = request(
        laptopUrl,
        "workspace/file",
        json.encodeToString(WorkspaceWriteRequest.serializer(), WorkspaceWriteRequest(cwd, path, content))
    ) { response -> json.decodeFromString(WorkspaceWriteResponse.serializer(), response).bytesWritten }

    open suspend fun search(
        laptopUrl: String,
        query: String,
        maxResults: Int = 5
    ): List<WebSearchResult> = request(
        laptopUrl = laptopUrl,
        endpoint = "search",
        body = json.encodeToString(
            WebSearchRequest.serializer(),
            WebSearchRequest(query = query, maxResults = maxResults.coerceIn(1, 8))
        )
    ) { response -> json.decodeFromString(WebSearchResponse.serializer(), response).results }

    open suspend fun health(laptopUrl: String): BridgeHealth = withContext(Dispatchers.IO) {
        val health = json.decodeFromString(HealthResponse.serializer(), get(laptopUrl, "health"))
        BridgeHealth(health.status, health.backend, health.model, health.modelReachable)
    }

    open suspend fun models(laptopUrl: String): List<BridgeModel> =
        json.decodeFromString(ModelsResponse.serializer(), get(laptopUrl, "models")).models

    open suspend fun selectModel(laptopUrl: String, model: String): BridgeModel = request(
        laptopUrl = laptopUrl,
        endpoint = "models/select",
        body = json.encodeToString(SelectModelRequest.serializer(), SelectModelRequest(model))
    ) { response -> json.decodeFromString(BridgeModel.serializer(), response) }

    open suspend fun connectors(laptopUrl: String): List<BridgeConnector> =
        json.decodeFromString(ConnectorsResponse.serializer(), get(laptopUrl, "connectors")).connectors

    private suspend fun get(laptopUrl: String, endpoint: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${normalizeUrl(laptopUrl)}/$endpoint").get().build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Laptop bridge returned HTTP ${response.code}: ${responseBody.take(240)}")
            }
            responseBody
        }
    }

    private suspend fun <T> request(
        laptopUrl: String,
        endpoint: String,
        body: String,
        parse: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(laptopUrl)
        val request = Request.Builder()
            .url("$normalized/$endpoint")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Laptop bridge returned HTTP ${response.code}: ${responseBody.take(240)}")
            }
            parse(responseBody)
        }
    }

    private fun normalizeUrl(laptopUrl: String): String {
        val normalized = laptopUrl.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "Laptop URL must start with http:// or https://"
        }
        return normalized
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

enum class BridgeTask(val wireName: String) {
    WRITE("write"), REVIEW("review"), DEBUG("debug"), EXPLAIN("explain")
}

data class ExecResult(val stdout: String, val stderr: String, val exitCode: Int)

@Serializable
data class DispatchPlan(
    val summary: String,
    val command: String? = null,
    val cwd: String,
    val executable: Boolean
)

@Serializable
data class WebSearchResult(val title: String, val url: String, val snippet: String)

data class BridgeHealth(
    val status: String,
    val backend: String,
    val model: String?,
    val modelReachable: Boolean = status == "ok"
)

@Serializable
data class BridgeModel(
    val id: String,
    @kotlinx.serialization.SerialName("parameter_size") val parameterSize: String? = null,
    val quantization: String? = null,
    val capabilities: List<String> = emptyList(),
    val selected: Boolean = false
)

@Serializable
data class BridgeConnector(
    val id: String,
    val name: String,
    val status: String,
    val detail: String,
    val connected: Boolean
)

@Serializable
private data class EscalateRequest(
    val task: String,
    val context: String,
    val instruction: String,
    val effort: String
)

@Serializable
private data class EscalateResponse(val result: String)

@Serializable
private data class ExecRequest(val command: String, val cwd: String)

@Serializable
private data class ExecResponse(
    val stdout: String,
    val stderr: String,
    @kotlinx.serialization.SerialName("exit_code") val exitCode: Int
)

@Serializable
private data class DispatchPlanRequest(val instruction: String, val cwd: String)

@Serializable
private data class DispatchWorkspacesResponse(val workspaces: List<String>)

@Serializable
private data class WorkspaceFilesResponse(val files: List<String>)

@Serializable
private data class WorkspaceFileResponse(val path: String, val content: String)

@Serializable
private data class WorkspaceWriteRequest(val cwd: String, val path: String, val content: String)

@Serializable
private data class WorkspaceWriteResponse(
    val path: String,
    @kotlinx.serialization.SerialName("bytes_written") val bytesWritten: Int
)

@Serializable
private data class WebSearchRequest(
    val query: String,
    @kotlinx.serialization.SerialName("max_results") val maxResults: Int
)

@Serializable
private data class WebSearchResponse(val query: String, val results: List<WebSearchResult>)

@Serializable
private data class HealthResponse(
    val status: String,
    val backend: String,
    val model: String? = null,
    @kotlinx.serialization.SerialName("model_reachable") val modelReachable: Boolean = status == "ok"
)

@Serializable
private data class ModelsResponse(val models: List<BridgeModel>)

@Serializable
private data class SelectModelRequest(val model: String)

@Serializable
private data class ConnectorsResponse(val connectors: List<BridgeConnector>)
