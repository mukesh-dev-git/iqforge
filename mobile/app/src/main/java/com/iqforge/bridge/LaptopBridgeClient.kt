package com.iqforge.bridge

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** HTTP client for the laptop bridge. The JSON fields mirror CONTRACT.md exactly. */
class LaptopBridgeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun escalate(
        laptopUrl: String,
        task: BridgeTask,
        context: String,
        instruction: String
    ): String = request(
        laptopUrl = laptopUrl,
        endpoint = "escalate",
        body = json.encodeToString(
            EscalateRequest.serializer(),
            EscalateRequest(task.wireName, context, instruction)
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

    private fun <T> request(
        laptopUrl: String,
        endpoint: String,
        body: String,
        parse: (String) -> T
    ): T {
        val normalized = laptopUrl.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "Laptop URL must start with http:// or https://"
        }
        val request = Request.Builder()
            .url("$normalized/$endpoint")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Laptop bridge returned HTTP ${response.code}: ${responseBody.take(240)}")
            }
            return parse(responseBody)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

enum class BridgeTask(val wireName: String) {
    WRITE("write"), REVIEW("review"), DEBUG("debug"), EXPLAIN("explain")
}

data class ExecResult(val stdout: String, val stderr: String, val exitCode: Int)

@Serializable
private data class EscalateRequest(val task: String, val context: String, val instruction: String)

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
