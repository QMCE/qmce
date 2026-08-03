package rj.qmce.lite.data.emotion

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-demand QFace CDN store: index TTL + png/apng downloads with disk LRU.
 */
object QFaceRemoteStore {
    private const val TAG = "QMCE-QFace"
    private const val BASE = "https://koishi.js.org/QFace/"
    private const val INDEX_URL = BASE + "assets/qq_emoji/_index.json"
    private const val ROOT_DIR = "qface"
    private const val INDEX_FILE = "_index.json"
    private const val INDEX_META = "_index.meta"
    private const val TTL_MS = 24L * 60L * 60L * 1000L
    private const val MAX_BYTES = 48L * 1024L * 1024L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 20_000

    enum class Kind { Png, Apng }

    data class QFaceEntry(
        val emojiId: String,
        val describe: String,
        val emojiType: Int,
        val aniStickerPackId: Int,
        val aniStickerId: Int,
        val pngPath: String?,
        val apngPath: String?,
        val lottiePath: String?,
    )

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var byEmojiId: Map<String, QFaceEntry> = emptyMap()

    @Volatile
    private var byAniStickerId: Map<String, QFaceEntry> = emptyMap()

    @Volatile
    private var animatedEmojiIds: List<Int> = emptyList()

    private val indexReady = AtomicBoolean(false)
    private val indexLock = Any()
    private val downloadSemaphore = Semaphore(2)
    private val inFlight = ConcurrentHashMap<String, Any>()
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "qmce-qface-io").apply { isDaemon = true }
    }

    fun warmupIndex(context: Context) {
        applicationContext = context.applicationContext
        io.execute {
            runCatching { ensureIndexLocked(context.applicationContext) }
                .onFailure { Log.w(TAG, "warmupIndex failed", it) }
        }
    }

    /** Blocking index refresh for startup merge into face_config. */
    fun refreshIndexBlocking(context: Context) {
        applicationContext = context.applicationContext
        runCatching { ensureIndexLocked(context.applicationContext) }
            .onFailure { Log.w(TAG, "refreshIndexBlocking failed", it) }
    }

    fun animatedIds(): List<Int> = animatedEmojiIds

    fun lookup(emojiId: String): QFaceEntry? {
        ensureIndexIfNeeded()
        return byEmojiId[emojiId]
    }

    fun lookupByAniStickerId(id: String): QFaceEntry? {
        if (id.isBlank()) return null
        ensureIndexIfNeeded()
        return byAniStickerId[id]
    }

    fun resolveEmojiId(
        serverId: Int?,
        faceIndex: Int,
        stickerId: String?,
    ): String? {
        ensureIndexIfNeeded()
        serverId?.toString()?.takeIf { byEmojiId.containsKey(it) }?.let { return it }
        faceIndex.toString().takeIf { byEmojiId.containsKey(it) }?.let { return it }
        stickerId?.takeIf { it.isNotBlank() }?.let { sid ->
            byAniStickerId[sid]?.emojiId?.let { return it }
        }
        return serverId?.toString() ?: faceIndex.toString()
    }

    /** Blocking download for use on IO threads. */
    fun ensureAsset(emojiId: String, kind: Kind): File? {
        val context = applicationContext ?: return null
        ensureIndexLocked(context)
        val entry = byEmojiId[emojiId] ?: return null
        val remotePath = when (kind) {
            Kind.Png -> entry.pngPath
            Kind.Apng -> entry.apngPath
        } ?: return null
        val url = absoluteUrl(remotePath)
        val local = localFile(context, emojiId, kind, File(remotePath).name)
        if (local.isFile && local.length() > 0L) {
            local.setLastModified(System.currentTimeMillis())
            return local
        }
        return downloadSingleFlight(url, local)
    }

    private fun ensureIndexIfNeeded() {
        val context = applicationContext ?: return
        if (indexReady.get()) return
        ensureIndexLocked(context)
    }

    private fun ensureIndexLocked(context: Context) {
        synchronized(indexLock) {
            applicationContext = context.applicationContext
            val root = rootDir(context).also { it.mkdirs() }
            val indexFile = File(root, INDEX_FILE)
            val metaFile = File(root, INDEX_META)
            val fetchedAt = metaFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0L
            val fresh = indexFile.isFile &&
                indexFile.length() > 0L &&
                System.currentTimeMillis() - fetchedAt < TTL_MS
            if (!fresh) {
                runCatching {
                    downloadTo(URL(INDEX_URL), File(root, ".$INDEX_FILE.tmp")).also { tmp ->
                        if (!tmp.renameTo(indexFile)) {
                            tmp.copyTo(indexFile, overwrite = true)
                            tmp.delete()
                        }
                    }
                    writeAtomically(metaFile, System.currentTimeMillis().toString())
                }.onFailure {
                    Log.w(TAG, "index download failed; keeping stale cache", it)
                }
            }
            if (!indexFile.isFile) return
            parseIndex(indexFile.readText())
            indexReady.set(true)
        }
    }

    private fun parseIndex(json: String) {
        val array = JSONArray(json)
        val emojiMap = LinkedHashMap<String, QFaceEntry>(array.length())
        val stickerMap = LinkedHashMap<String, QFaceEntry>()
        val animated = ArrayList<Int>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val emojiId = obj.optString("emojiId").takeIf { it.isNotBlank() } ?: continue
            val assets = obj.optJSONArray("assets") ?: JSONArray()
            var pngPath: String? = null
            var preferredPng: String? = null
            var apngPath: String? = null
            var lottiePath: String? = null
            for (a in 0 until assets.length()) {
                val asset = assets.optJSONObject(a) ?: continue
                val type = asset.optInt("type", -1)
                val path = asset.optString("path").takeIf { it.isNotBlank() } ?: continue
                val name = asset.optString("name")
                when (type) {
                    0 -> {
                        if (pngPath == null) pngPath = path
                        if (name == "$emojiId.png") preferredPng = path
                    }
                    2 -> if (apngPath == null) apngPath = path
                    3 -> if (lottiePath == null) lottiePath = path
                }
            }
            val entry = QFaceEntry(
                emojiId = emojiId,
                describe = obj.optString("describe"),
                emojiType = obj.optInt("emojiType", 0),
                aniStickerPackId = obj.optInt("aniStickerPackId", 0),
                aniStickerId = obj.optInt("aniStickerId", 0),
                pngPath = preferredPng ?: pngPath,
                apngPath = apngPath,
                lottiePath = lottiePath,
            )
            emojiMap[emojiId] = entry
            if (entry.aniStickerId > 0) {
                stickerMap[entry.aniStickerId.toString()] = entry
            }
            val isAnimated = entry.emojiType == 1 || entry.apngPath != null || entry.lottiePath != null
            if (isAnimated) {
                emojiId.toIntOrNull()?.let(animated::add)
            }
        }
        byEmojiId = emojiMap
        byAniStickerId = stickerMap
        animatedEmojiIds = animated.distinct()
        Log.i(TAG, "index ready entries=${emojiMap.size} animated=${animatedEmojiIds.size}")
    }

    private fun downloadSingleFlight(url: String, target: File): File? {
        val gate = Any()
        val existing = inFlight.putIfAbsent(url, gate)
        if (existing != null) {
            synchronized(existing) {
                // waiter wakes when owner finishes
            }
            return target.takeIf { it.isFile && it.length() > 0L }
        }
        synchronized(gate) {
            try {
                if (target.isFile && target.length() > 0L) return target
                downloadSemaphore.acquire()
                try {
                    if (target.isFile && target.length() > 0L) return target
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, ".${target.name}.tmp-${System.nanoTime()}")
                    downloadTo(URL(url), tmp)
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                    trimToSize()
                    return target.takeIf { it.isFile && it.length() > 0L }
                } finally {
                    downloadSemaphore.release()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "download failed url=$url", t)
                return null
            } finally {
                inFlight.remove(url, gate)
            }
        }
    }

    private fun downloadTo(url: URL, target: File): File {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code for $url")
            connection.inputStream.use { input ->
                target.outputStream().buffered().use { input.copyTo(it) }
            }
            return target
        } finally {
            connection.disconnect()
        }
    }

    private fun trimToSize() {
        val context = applicationContext ?: return
        val root = rootDir(context)
        if (!root.isDirectory) return
        val files = root.walkTopDown()
            .filter { it.isFile }
            .filterNot { it.name.startsWith("_index") }
            .toList()
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        val ordered = files.sortedBy { it.lastModified() }
        for (file in ordered) {
            if (total <= MAX_BYTES) break
            val len = file.length()
            if (file.delete()) total -= len
        }
    }

    private fun absoluteUrl(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else BASE + path.removePrefix("/")

    private fun rootDir(context: Context): File = File(context.cacheDir, ROOT_DIR)

    private fun localFile(context: Context, emojiId: String, kind: Kind, fileName: String): File {
        val kindDir = when (kind) {
            Kind.Png -> "png"
            Kind.Apng -> "apng"
        }
        return File(rootDir(context), "$emojiId/$kindDir/$fileName")
    }

    private fun writeAtomically(target: File, content: String) {
        val temporary = File(target.parentFile, ".${target.name}.tmp-${System.nanoTime()}")
        temporary.writeText(content)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }
}
