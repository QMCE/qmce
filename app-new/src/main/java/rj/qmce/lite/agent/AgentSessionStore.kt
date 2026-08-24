package rj.qmce.lite.agent

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import rj.qmce.lite.util.QmceLog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persists Fluoxetine [AgentSession] history + UI messages per account uin.
 * Writes are debounced; JSON work runs off the main thread.
 */
object AgentSessionStore {

    private const val TAG = "QMCE-AgentPersist"
    private const val DEBOUNCE_MS = 300L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = AtomicBoolean(false)
    private var scheduled: Runnable? = null

    fun schedulePersist() {
        val ctx = AgentSubsystem.persistenceContext() ?: return
        val uin = AgentSubsystem.persistenceUin() ?: return
        if (!pending.compareAndSet(false, true) && scheduled != null) {
            // Already scheduled; keep single debounce window.
        }
        scheduled?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            pending.set(false)
            scheduled = null
            persistNow(ctx, uin)
        }
        scheduled = task
        mainHandler.postDelayed(task, DEBOUNCE_MS)
    }

    fun cancelPending() {
        scheduled?.let { mainHandler.removeCallbacks(it) }
        scheduled = null
        pending.set(false)
    }

    fun load(context: Context, uin: String) {
        val file = fileFor(context, uin)
        if (!file.exists()) return
        runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val history = parseHistory(json.optJSONArray("history") ?: JSONArray())
            val ui = parseUi(json.optJSONArray("uiMessages") ?: JSONArray())
            val lastText = json.optString("lastText", "")
            val lastActive = json.optLong("lastActiveMillis", 0L)
            val unread = json.optInt("unreadCount", 0)
            val markedRead = json.optBoolean("markedRead", true)
            AgentSession.restore(
                history = history,
                uiMessages = ui,
                lastText = lastText,
                lastActiveMillis = lastActive,
                unreadCount = unread,
                markedRead = markedRead,
            )
            QmceLog.d(TAG, "loaded session uin=$uin history=${history.size} ui=${ui.size}")
        }.onFailure {
            QmceLog.w(TAG, "load failed uin=$uin", it)
        }
    }

    fun clear(context: Context, uin: String) {
        cancelPending()
        runCatching { fileFor(context, uin).delete() }
        QmceLog.d(TAG, "cleared session uin=$uin")
    }

    private fun persistNow(context: Context, uin: String) {
        AgentSubsystem.scope().launch(Dispatchers.IO) {
            runCatching {
                val snapshot = AgentSession.snapshotForPersist()
                val root = JSONObject().apply {
                    put("history", historyToJson(snapshot.history))
                    put("uiMessages", uiToJson(snapshot.uiMessages))
                    put("lastText", snapshot.lastText)
                    put("lastActiveMillis", snapshot.lastActiveMillis)
                    put("unreadCount", snapshot.unreadCount)
                    put("markedRead", snapshot.markedRead)
                }
                val file = fileFor(context, uin)
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(root.toString(), Charsets.UTF_8)
                if (!tmp.renameTo(file)) {
                    file.writeText(root.toString(), Charsets.UTF_8)
                    tmp.delete()
                }
            }.onFailure {
                QmceLog.w(TAG, "persist failed uin=$uin", it)
            }
        }
    }

    private fun fileFor(context: Context, uin: String): File {
        val safe = uin.replace(Regex("[^0-9A-Za-z_-]"), "_")
        return File(context.filesDir, "agent_session_$safe.json")
    }

    private fun historyToJson(history: List<AgentMessage>): JSONArray {
        val arr = JSONArray()
        history.forEach { msg ->
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
                msg.toolCallId?.let { put("toolCallId", it) }
                msg.name?.let { put("name", it) }
                if (msg.toolCalls.isNotEmpty()) {
                    put("toolCalls", JSONArray().apply {
                        msg.toolCalls.forEach { call ->
                            put(JSONObject().apply {
                                put("id", call.id)
                                put("name", call.name)
                                put("arguments", anyToJson(call.arguments))
                            })
                        }
                    })
                }
            })
        }
        return arr
    }

    private fun uiToJson(ui: List<AgentUiMsg>): JSONArray {
        val arr = JSONArray()
        ui.filterNot { it.streaming }.forEach { msg ->
            arr.put(JSONObject().apply {
                put("stableKey", msg.stableKey)
                put("isSelf", msg.isSelf)
                put("text", msg.text)
                put("time", msg.time)
                put("isSystem", msg.isSystem)
            })
        }
        return arr
    }

    private fun parseHistory(arr: JSONArray): List<AgentMessage> {
        val out = mutableListOf<AgentMessage>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val toolCalls = mutableListOf<AgentToolCall>()
            val tcArr = obj.optJSONArray("toolCalls")
            if (tcArr != null) {
                for (j in 0 until tcArr.length()) {
                    val tc = tcArr.optJSONObject(j) ?: continue
                    val argsObj = tc.optJSONObject("arguments") ?: JSONObject()
                    toolCalls.add(
                        AgentToolCall(
                            id = tc.optString("id"),
                            name = tc.optString("name"),
                            arguments = jsonToMap(argsObj),
                        ),
                    )
                }
            }
            out.add(
                AgentMessage(
                    role = obj.optString("role"),
                    content = obj.optString("content"),
                    toolCallId = obj.optString("toolCallId").takeIf { it.isNotBlank() },
                    toolCalls = toolCalls,
                    name = obj.optString("name").takeIf { it.isNotBlank() },
                ),
            )
        }
        return out
    }

    private fun parseUi(arr: JSONArray): List<AgentUiMsg> {
        val out = mutableListOf<AgentUiMsg>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out.add(
                AgentUiMsg(
                    stableKey = obj.optString("stableKey"),
                    isSelf = obj.optBoolean("isSelf"),
                    text = obj.optString("text"),
                    time = obj.optLong("time"),
                    streaming = false,
                    isSystem = obj.optBoolean("isSystem"),
                ),
            )
        }
        return out
    }

    private fun anyToJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { obj ->
            value.forEach { (k, v) ->
                if (k != null) obj.put(k.toString(), anyToJson(v))
            }
        }
        is List<*> -> JSONArray().also { arr ->
            value.forEach { arr.put(anyToJson(it)) }
        }
        is Number, is Boolean, is String -> value
        else -> value.toString()
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
                                JSONObject.NULL -> ""
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
