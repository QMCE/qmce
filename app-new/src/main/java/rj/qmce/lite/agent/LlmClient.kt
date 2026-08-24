package rj.qmce.lite.agent

import android.util.Log
import com.tencent.qphone.base.util.BaseApplication
import org.json.JSONArray
import org.json.JSONObject
import rj.qmce.lite.viewmodel.SettingsViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Streaming OpenAI-compatible chat/completions client for the Agent.
 *
 * Reuses the existing AI endpoint config (SettingsViewModel.resolveAiEndpoint),
 * same SSE/HttpURLConnection pattern as MessageSummaryClient, plus `tools` /
 * `tool_calls` support.
 */
class LlmClient {

    interface Listener {
        /** Streamed delta of the assistant's text content. */
        fun onChunk(text: String)
        /** Fired once the full response (with tool calls) is available. */
        fun onComplete(toolCalls: List<AgentToolCall>)
        fun onError(message: String, retryable: Boolean)
    }

    private data class Endpoint(
        val url: String,
        val model: String,
        val apiKey: String?,
    )

    private companion object {
        const val TAG = "QMCE-Agent"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 180_000
        const val MAX_HISTORY_MESSAGES = 80
    }

    fun stream(
        messages: List<AgentMessage>,
        tools: List<Tool>,
        listener: Listener,
    ): Request {
        val request = Request()
        val worker = Thread({ execute(request, messages, tools, listener) }, "QMCE-Agent")
            .apply { isDaemon = true }
        request.worker = worker
        worker.start()
        return request
    }

    private fun resolveEndpoint(): Endpoint {
        val context = runCatching { BaseApplication.getContext() }.getOrNull()
        val resolved = if (context != null) {
            SettingsViewModel.resolveAiEndpoint(context)
        } else {
            SettingsViewModel.AiEndpoint(
                baseUrl = SettingsViewModel.BUILTIN_AI_BASE_URL,
                apiKey = SettingsViewModel.BUILTIN_AI_API_KEY,
                model = SettingsViewModel.BUILTIN_AI_MODEL,
                custom = false,
            )
        }
        return Endpoint(
            url = resolved.baseUrl,
            model = resolved.model,
            apiKey = resolved.apiKey,
        )
    }

    private fun execute(
        request: Request,
        messages: List<AgentMessage>,
        tools: List<Tool>,
        listener: Listener,
    ) {
        val endpoint = resolveEndpoint()
        var connection: HttpURLConnection? = null
        try {
            val body = buildRequestBody(messages, tools, endpoint.model)
            connection = (URL(endpoint.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream, application/json")
                setRequestProperty("User-Agent", "QMCE/1.0")
                endpoint.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                    setRequestProperty("Authorization", "Bearer $key")
                }
            }
            request.connection.set(connection)
            if (request.isCancelled()) return
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                handleError(connection, responseCode, listener)
                return
            }

            val toolCallBuilder = ToolCallBuilder()
            var emitted = false
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (!request.isCancelled()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    val payload = if (line.startsWith("data:")) {
                        line.substringAfter("data:").trim()
                    } else {
                        line.trim()
                    }
                    if (payload.isBlank()) continue
                    if (payload == "[DONE]") break
                    val consumed = consumePayload(payload, toolCallBuilder, listener)
                    emitted = emitted || consumed
                }
            }
            if (!request.isCancelled()) {
                if (emitted) {
                    listener.onComplete(toolCallBuilder.build())
                } else {
                    listener.onError("模型未返回内容", true)
                }
            }
        } catch (error: Exception) {
            if (request.isCancelled() || error is InterruptedException) return
            Log.w(TAG, "agent request failed", error)
            listener.onError("网络请求失败，请重试", true)
        } finally {
            request.connection.compareAndSet(connection, null)
            connection?.disconnect()
        }
    }

    private fun consumePayload(
        payload: String,
        toolCallBuilder: ToolCallBuilder,
        listener: Listener,
    ): Boolean {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return false
        val delta = choice.optJSONObject("delta") ?: return false
        var consumed = false

        // delta.content may be absent or JSON null during thinking / tool-call
        // only turns. optString() would turn JSON null into the literal "null",
        // so read the raw value and only emit genuine non-empty strings.
        val contentValue = delta.opt("content")
        val text = if (contentValue is String) contentValue else null
        if (!text.isNullOrBlank()) {
            listener.onChunk(text)
            consumed = true
        }

        val toolCallsJson = delta.optJSONArray("tool_calls")
        if (toolCallsJson != null) {
            for (i in 0 until toolCallsJson.length()) {
                val tc = toolCallsJson.optJSONObject(i) ?: continue
                toolCallBuilder.accumulate(tc)
            }
            consumed = true
        }
        return consumed
    }

    private fun buildRequestBody(
        messages: List<AgentMessage>,
        tools: List<Tool>,
        model: String,
    ): String {
        // Keep system messages pinned at the front; truncate only the rest.
        val systemMessages = messages.filter { it.role == "system" }
        val nonSystem = messages.filterNot { it.role == "system" }
        val history = systemMessages + nonSystem.takeLast(MAX_HISTORY_MESSAGES)
        val messagesJson = JSONArray()
        history.forEach { msg ->
            when (msg.role) {
                "system" -> {
                    messagesJson.put(JSONObject().apply {
                        put("role", "system")
                        put("content", msg.content)
                    })
                }

                "tool" -> {
                    val obj = JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", msg.toolCallId ?: "")
                        put("name", msg.name ?: "")
                        put("content", msg.content)
                    }
                    messagesJson.put(obj)
                }

                "assistant" -> {
                    val obj = JSONObject().apply {
                        put("role", "assistant")
                        put("content", msg.content)
                        if (msg.toolCalls.isNotEmpty()) {
                            put("tool_calls", JSONArray().apply {
                                msg.toolCalls.forEach { call ->
                                    put(JSONObject().apply {
                                        put("id", call.id)
                                        put("type", "function")
                                        put("function", JSONObject().apply {
                                            put("name", call.name)
                                            put("arguments", JSONObject(call.arguments).toString())
                                        })
                                    })
                                }
                            })
                        }
                    }
                    messagesJson.put(obj)
                }

                else -> {
                    messagesJson.put(JSONObject().apply {
                        put("role", "user")
                        put("content", msg.content)
                    })
                }
            }
        }

        val toolsJson = JSONArray()
        tools.forEach { tool ->
            toolsJson.put(JSONObject(tool.toOpenAiFunction()))
        }

        return JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", messagesJson)
            if (toolsJson.length() > 0) {
                put("tools", toolsJson)
                put("tool_choice", "auto")
            }
        }.toString()
    }

    private fun handleError(
        connection: HttpURLConnection,
        code: Int,
        listener: Listener,
    ) {
        val detail = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        Log.w(TAG, "agent response code=$code body=${detail.take(240)}")
        when (code) {
            401, 403 -> listener.onError("AI 服务拒绝了请求", false)
            408, 429 -> listener.onError("AI 服务繁忙，请稍后重试", true)
            in 500..599 -> listener.onError("AI 服务暂时不可用，请重试", true)
            else -> listener.onError("AI 请求失败（$code）", true)
        }
    }

    class Request internal constructor() {
        internal val connection = AtomicReference<HttpURLConnection?>(null)
        private val cancelled = AtomicBoolean(false)
        @Volatile
        internal var worker: Thread? = null

        fun cancel() {
            if (!cancelled.compareAndSet(false, true)) return
            connection.getAndSet(null)?.disconnect()
            worker?.interrupt()
        }

        internal fun isCancelled(): Boolean = cancelled.get()

        internal fun isWorkerAlive(): Boolean = worker?.isAlive == true
    }

    /**
     * Accumulates streaming `delta.tool_calls` fragments into complete calls.
     * OpenAI streams tool_calls in pieces: {index, id?, type?, function:{name?, arguments?}}.
     */
    private class ToolCallBuilder {
        private val parts = mutableMapOf<Int, MutableList<Pair<String, String>>>() // index -> (id, name)
        private val args = mutableMapOf<Int, StringBuilder>()
        private val ids = mutableMapOf<Int, String>()
        private val names = mutableMapOf<Int, String>()

        fun accumulate(tc: JSONObject) {
            val index = tc.optInt("index", 0)
            if (!args.containsKey(index)) args[index] = StringBuilder()
            // optString() turns JSON null into the literal "null"; use opt() and
            // only accept genuine non-empty strings.
            (tc.opt("id") as? String)?.takeIf { it.isNotBlank() }?.let { ids[index] = it }
            tc.optJSONObject("function")?.let { fn ->
                (fn.opt("name") as? String)?.takeIf { it.isNotBlank() }?.let { names[index] = it }
                (fn.opt("arguments") as? String)?.takeIf { it.isNotBlank() }?.let { args[index]!!.append(it) }
            }
        }

        fun build(): List<AgentToolCall> {
            val result = mutableListOf<AgentToolCall>()
            ids.keys.plus(names.keys).sorted().forEach { index ->
                val name = names[index] ?: return@forEach
                val id = ids[index] ?: "call_${index}"
                val arguments = runCatching {
                    JSONObject(args[index]?.toString().orEmpty())
                }.getOrNull()
                result.add(
                    AgentToolCall(
                        id = id,
                        name = name,
                        arguments = arguments?.let { jsonToMap(it) } ?: emptyMap(),
                    ),
                )
            }
            return result
        }

        private fun jsonToMap(obj: JSONObject): Map<String, Any> {
            val map = mutableMapOf<String, Any>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)
                map[key] = when (value) {
                    is JSONObject -> jsonToMap(value)
                    is JSONArray -> {
                        val list = mutableListOf<Any>()
                        for (i in 0 until value.length()) {
                            val item = value.opt(i)
                            list.add(
                                when (item) {
                                    is JSONObject -> jsonToMap(item)
                                    is JSONArray -> item.toString()
                                    else -> item
                                },
                            )
                        }
                        list
                    }

                    JSONObject.NULL -> ""
                    else -> value
                }
            }
            return map
        }
    }
}
