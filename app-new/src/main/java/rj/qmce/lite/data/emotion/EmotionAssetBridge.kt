package rj.qmce.lite.data.emotion

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream

/**
 * Installs the small system-emotion resource set that NT expects under filesDir.
 *
 * The official resource manager does not read these files from APK assets.  It only looks at
 * filesDir/qq_emoticon_res, so the bridge materializes the bundled config and lottie/static files
 * before the manager is initialized.  Existing downloaded resources are preserved.
 */
object EmotionAssetBridge {
    private const val TAG = "QMCE-EmotionAssets"
    private const val VERSION = 6
    private const val RESOURCE_DIR = "qq_emoticon_res"
    private const val CONFIG_ASSET = "face_config.json"
    private const val ZIP_ASSET = "bigface.zip"
    private const val EMOJI_ZIP_ASSET = "emoji_res.zip"
    private const val INDEX_ASSET = "bigface_index.json"
    private const val MARKER = ".qmce_emotion_assets_v$VERSION"

    @Volatile
    private var resourceRoot: File? = null

    @Volatile
    private var applicationContext: Context? = null

    fun ensure(context: Context) {
        synchronized(this) {
            applicationContext = context.applicationContext
            runCatching {
                ensureLocked(context)
            }.onFailure {
                Log.e(TAG, "emotion resource initialization failed", it)
            }
        }
    }

    private fun ensureLocked(context: Context) {
        val resourceDir = File(context.filesDir, RESOURCE_DIR)
        resourceRoot = resourceDir
        if (!resourceDir.exists() && !resourceDir.mkdirs()) {
            error("cannot create ${resourceDir.absolutePath}")
        }

        val lockFile = File(resourceDir, ".qmce-emotion.lock")
        RandomAccessFile(lockFile, "rw").use { lockHandle ->
            lockHandle.channel.lock().use {
                ensureFiles(context, resourceDir)
            }
        }
    }

    private fun ensureFiles(context: Context, resourceDir: File) {
        val bundledConfig = context.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() }
        val bundledIndex = context.assets.open(INDEX_ASSET).bufferedReader().use { it.readText() }
        val indexFile = File(resourceDir, INDEX_ASSET)
        if (!indexFile.isFile || indexFile.readText() != bundledIndex) {
            writeAtomically(indexFile, bundledIndex)
        }
        val mergedConfig = mergeConfig(
            existing = File(resourceDir, CONFIG_ASSET).takeIf { it.isFile }?.readText(),
            bundled = bundledConfig,
            animatedIds = readAnimatedIds(bundledIndex),
        )
        val configFile = File(resourceDir, CONFIG_ASSET)
        if (!configFile.isFile || configFile.readText() != mergedConfig) {
            writeAtomically(configFile, mergedConfig)
        }

        val marker = File(resourceDir, MARKER)
        if (!marker.isFile || !hasBundledResource(resourceDir, bundledIndex, bundledConfig)) {
            extractZipAsset(context, resourceDir, ZIP_ASSET)
            extractZipAsset(context, resourceDir, EMOJI_ZIP_ASSET)
            writeAtomically(marker, "version=$VERSION\n")
        }
        Log.i(
            TAG,
            "emotion resources ready config=${configFile.length()} bytes " +
                "animated=${readAnimatedIds(bundledIndex).size} dir=${resourceDir.absolutePath}",
        )
    }

    fun resourceFile(relativePath: String): File? {
        val root = resourceRoot
            ?: applicationContext?.let { File(it.filesDir, RESOURCE_DIR) }
            ?: return null
        val output = File(root, relativePath)
        return runCatching {
            require(output.canonicalPath.startsWith(root.canonicalPath + File.separator))
            output
        }.getOrNull()
    }

    fun bundledAnimatedIds(): List<Int> = runCatching {
        val index = resourceFile(INDEX_ASSET)?.takeIf(File::isFile)?.readText() ?: return@runCatching emptyList()
        readAnimatedIds(index).mapNotNull(String::toIntOrNull).distinct()
    }.getOrDefault(emptyList())

    private fun mergeConfig(
        existing: String?,
        bundled: String,
        animatedIds: List<String>,
    ): String {
        val bundledJson = JSONObject(bundled)
        val base = existing
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject(bundled)

        val baseSysface = base.optJSONArray("sysface") ?: JSONArray().also {
            base.put("sysface", it)
        }
        val itemIndexById = HashMap<String, Int>()
        for (index in 0 until baseSysface.length()) {
            baseSysface.optJSONObject(index)
                ?.optString("QSid")
                ?.takeIf(String::isNotBlank)
                ?.let { itemIndexById[it] = index }
        }

        bundledJson.optJSONArray("sysface")?.let { bundledSysface ->
            for (index in 0 until bundledSysface.length()) {
                val item = bundledSysface.optJSONObject(index) ?: continue
                val id = item.optString("QSid")
                if (id.isBlank()) continue
                val existingIndex = itemIndexById[id]
                if (existingIndex == null) {
                    itemIndexById[id] = baseSysface.length()
                    baseSysface.put(JSONObject(item.toString()))
                } else {
                    val target = baseSysface.optJSONObject(existingIndex) ?: continue
                    mergeEmotionItem(target, item)
                }
            }
        }

        animatedIds.forEach { id ->
            if (!itemIndexById.containsKey(id)) {
                itemIndexById[id] = baseSysface.length()
                baseSysface.put(
                    JSONObject().apply {
                        put("QSid", id)
                        put("IQLid", id)
                        put("AQLid", id)
                        put("EMCode", "10$id")
                        put("QDes", "/大表情$id")
                        put("AniStickerType", 1)
                        put("AniStickerPackId", "1")
                        put("AniStickerId", id)
                    },
                )
            }
        }

        if (!base.has("emoji")) {
            bundledJson.optJSONArray("emoji")?.let { base.put("emoji", JSONArray(it.toString())) }
        }
        return base.toString()
    }

    private fun mergeEmotionItem(target: JSONObject, bundled: JSONObject) {
        val keys = bundled.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = bundled.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            val missing = !target.has(key) || target.isNull(key) ||
                (value is String && target.optString(key).isBlank())
            val missingAniType = key == "AniStickerType" && target.optInt(key, 0) == 0
            if (missing || missingAniType) target.put(key, value)
        }
    }

    private fun readAnimatedIds(indexJson: String): List<String> = runCatching {
        val ids = JSONObject(indexJson).optJSONArray("ids") ?: return@runCatching emptyList()
        buildList(ids.length()) {
            for (index in 0 until ids.length()) {
                ids.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
    }.getOrDefault(emptyList())

    private fun hasBundledResource(
        resourceDir: File,
        indexJson: String,
        bundledConfigJson: String,
    ): Boolean {
        val indexIds = readAnimatedIds(indexJson)
        val configIds = runCatching {
            val sysface = JSONObject(bundledConfigJson).optJSONArray("sysface") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until sysface.length()) {
                    val item = sysface.optJSONObject(index) ?: continue
                    if (item.optInt("AniStickerType", 0) <= 0) continue
                    item.optString("AniStickerId")
                        .takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
        val animatedIds = (indexIds + configIds).distinct()
        val animatedReady = animatedIds.isEmpty() || animatedIds.all { id ->
            File(resourceDir, "qlottie/1/$id/$id.json").isFile
        }
        val configReady = runCatching {
            JSONObject(bundledConfigJson).optJSONArray("sysface")?.length()?.let { it > 0 } == true &&
                File(resourceDir, CONFIG_ASSET).isFile
        }.getOrDefault(false)
        val emojiReady = (0..164).all { index ->
            File(resourceDir, "emoji_res/emoji_${index.toString().padStart(3, '0')}.png").isFile
        }
        return animatedReady && configReady && emojiReady
    }

    private fun extractZipAsset(context: Context, resourceDir: File, assetName: String) {
        val stage = File(
            resourceDir.parentFile,
            ".${resourceDir.name}.qmce-stage-${System.nanoTime()}",
        )
        deleteRecursively(stage)
        if (!stage.mkdirs()) error("cannot create ${stage.absolutePath}")

        var extracted = 0
        try {
            context.assets.open(assetName).buffered().use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val output = safeEntry(stage, entry.name)
                        if (entry.isDirectory) {
                            output.mkdirs()
                        } else {
                            output.parentFile?.mkdirs()
                            output.outputStream().buffered().use { zip.copyTo(it) }
                            extracted++
                        }
                        zip.closeEntry()
                    }
                }
            }

            stage.walkTopDown()
                .filter(File::isFile)
                .forEach { source ->
                    val relative = source.relativeTo(stage)
                    val target = File(resourceDir, relative.path)
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            Log.i(TAG, "extracted $extracted bundled resources from $assetName")
        } finally {
            deleteRecursively(stage)
        }
    }

    private fun safeEntry(root: File, entryName: String): File {
        require(entryName.isNotBlank()) { "empty zip entry" }
        val output = File(root, entryName)
        val rootPath = root.canonicalPath + File.separator
        require(output.canonicalPath.startsWith(rootPath)) { "invalid zip entry: $entryName" }
        return output
    }

    private fun writeAtomically(target: File, content: String) {
        val temporary = File(target.parentFile, ".${target.name}.qmce-tmp-${System.nanoTime()}")
        temporary.writeText(content)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        file.walkBottomUp().forEach { it.delete() }
    }
}
