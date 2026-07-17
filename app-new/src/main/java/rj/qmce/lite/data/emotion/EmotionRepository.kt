package rj.qmce.lite.data.emotion

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.emotion.adapter.api.IMarketFaceApi
import com.tencent.qqnt.emotion.info.SystemAndEmojiEmotionInfo
import com.tencent.qqnt.emotion.utils.MarketFaceStorageUtil
import com.tencent.qqnt.emotion.utils.QQEmojiUtil
import com.tencent.qqnt.emotion.utils.QQSysFaceUtil
import com.tencent.qqnt.kernel.nativeinterface.IFetchMarketEmoticonListCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MarketEmojiPathServiceType
import com.tencent.qqnt.kernel.nativeinterface.MarketEmoticonInfo
import com.tencent.qqnt.kernel.nativeinterface.MarketFaceElement
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.TabEmojiInfo
import com.tencent.qqnt.msg.api.IMsgUtilApi
import com.tencent.qqnt.watch.emotion.popemo.EmoMsgUtils
import com.tencent.qqnt.watch.emotion.util.EmosmUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import rj.qmce.lite.kernel.KernelBridge
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The data and NT bridge for system faces and market-face stickers.
 *
 * This deliberately exposes data models instead of official emotion Views.  The UI owns its
 * presentation while this class owns the exact element type and the asynchronous NT preparation
 * required by a market face.
 */
object EmotionRepository {
    sealed interface Selection {
        val label: String

        data class SystemFace(
            val faceType: Int,
            val faceIndex: Int,
            override val label: String,
            val packId: String? = null,
            val imageType: Int? = null,
        ) : Selection

        data class MarketFace(
            val epId: Int,
            val eId: String,
            override val label: String,
            val width: Int,
            val height: Int,
            val jsonPath: String? = null,
            val staticPath: String? = null,
            val dynamicPath: String? = null,
        ) : Selection
    }

    data class MarketPack(
        val epId: Int,
        val name: String,
        val tabType: Int,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val marketJsonPaths = ConcurrentHashMap<Int, String>()
    private val marketPreviewPathCache = ConcurrentHashMap<String, String>()
    private const val MARKET_FACE_RENDER_TIMEOUT_MS = 8_000L
    private const val MARKET_FACE_PREVIEW_POLL_MS = 150L

    private data class MarketFacePaths(
        val staticPath: String?,
        val dynamicPath: String?,
    )

    fun loadSystemFaces(): List<Selection.SystemFace> = runCatching {
        val result = ArrayList<Selection.SystemFace>()
        val systemIds = QQSysFaceUtil.a.h()
        systemIds.forEach { localId ->
            val label = runCatching { QQSysFaceUtil.a.c(localId) }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: "表情 $localId"
            result += Selection.SystemFace(
                faceType = 1,
                faceIndex = localId,
                label = label,
            )
        }
        QQEmojiUtil.a.b().forEach { emojiId ->
            result += Selection.SystemFace(
                faceType = 2,
                faceIndex = emojiId,
                label = "Emoji $emojiId",
            )
        }
        result
    }.getOrDefault(emptyList())

    fun systemFaceDrawable(face: Selection.SystemFace): Drawable? = runCatching {
        // NT uses faceType=3 for animated system stickers. The local resource manager exposes
        // their drawable through the ordinary system-face resource by local face id.
        val drawableType = if (face.faceType == 3) 1 else face.faceType
        SystemAndEmojiEmotionInfo(drawableType, face.faceIndex, face.label).r()
    }.getOrNull()

    fun cachedMarketFacePaths(
        context: Context,
        element: MarketFaceElement?,
    ): List<String> {
        val face = element ?: return emptyList()
        val eId = face.emojiId?.takeIf {
            it.isNotBlank() && !it.contains('/') && !it.contains('\\')
        } ?: return emptyList()
        val epId = face.emojiPackageId
        val root = File(
            context.getExternalFilesDir(null)?.parentFile,
            "Tencent/MobileQQ/.emotionsm/$epId",
        )
        return listOf(
            MarketFaceStorageUtil.a(epId.toString(), eId),
            MarketFaceStorageUtil.e(epId.toString(), eId),
            File(root, "${eId}_aio.png").absolutePath,
            File(root, "${eId}_thu.png").absolutePath,
            MarketFaceStorageUtil.c(epId.toString(), eId),
            MarketFaceStorageUtil.b(epId.toString(), eId),
            File(root, eId).absolutePath,
        ).distinct()
    }

    /**
     * Ask the same high-level NT emotion API used by the official AIO cell for a renderable
     * drawable. The returned object is normally a URLDrawable; the caller owns only the Compose
     * container, not the official View hierarchy.
     */
    fun loadMarketFaceDrawable(
        element: MarketFaceElement,
        onResult: (Drawable?) -> Unit,
    ) {
        Thread {
            val drawable = runCatching {
                val result = runBlocking {
                    withTimeoutOrNull(MARKET_FACE_RENDER_TIMEOUT_MS) {
                        QRoute.api(IMarketFaceApi::class.java)
                            .fetchMarketFaceInfoSuspend(element)
                    }
                }
                if (result == null || result.b != 0) {
                    null
                } else {
                    val emoticonInfo = result.a?.a
                    val drawable = emoticonInfo
                        ?.javaClass
                        ?.methods
                        ?.firstOrNull { method ->
                            method.name == "i" && method.parameterTypes.size == 2
                        }
                        ?.invoke(emoticonInfo, "fromAIO", true) as? Drawable
                    requestMarketFaceDownload(drawable)
                    drawable
                }
            }.getOrNull()
            mainHandler.post { onResult(drawable) }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun createSystemFaceElement(face: Selection.SystemFace): MsgElement? = runCatching {
        if (face.faceType == 2 && face.packId.isNullOrBlank()) {
            // QQ Emoji is represented by the official text element, not a synthetic face element.
            return@runCatching EmoMsgUtils.a.b(face.faceIndex, 2)
        }
        val serverIndex = if (face.faceType == 1 || face.faceType == 3) {
            QQSysFaceUtil.a.b(face.faceIndex)
        } else {
            face.faceIndex
        }
        if (face.faceType == 1 || face.faceType == 3) {
            runCatching { EmoMsgUtils.a.a(serverIndex) }.getOrNull()
                ?: if (face.faceType == 3 && !face.packId.isNullOrBlank()) {
                    QRoute.api(IMsgUtilApi::class.java).createFaceElement(
                        face.faceType,
                        serverIndex,
                        face.packId,
                        face.imageType ?: 0,
                        face.label,
                    )
                } else {
                    QRoute.api(IMsgUtilApi::class.java).createFaceElement(
                        face.faceType,
                        serverIndex,
                        face.label,
                    )
                }
        } else if (face.packId.isNullOrBlank()) {
            QRoute.api(IMsgUtilApi::class.java)
                .createFaceElement(face.faceType, serverIndex, face.label)
        } else {
            QRoute.api(IMsgUtilApi::class.java).createFaceElement(
                face.faceType,
                serverIndex,
                face.packId,
                face.imageType ?: 0,
                face.label,
            )
        }
    }.getOrNull()

    fun systemFaceForMessage(
        faceType: Int,
        ntFaceIndex: Int,
        label: String,
        packId: String?,
        imageType: Int?,
    ): Selection.SystemFace = Selection.SystemFace(
        faceType = faceType,
        faceIndex = if (faceType == 1 || faceType == 3) {
            runCatching { QQSysFaceUtil.a.a(ntFaceIndex) }.getOrDefault(ntFaceIndex)
        } else {
            ntFaceIndex
        },
        label = label,
        packId = packId,
        imageType = imageType,
    )

    fun loadMarketPacks(onResult: (List<MarketPack>) -> Unit) {
        val service = KernelBridge.getKernelMsgService()
        if (service == null) {
            post(onResult, emptyList())
            return
        }
        runCatching {
            service.fetchMarketEmoticonList(
                0,
                0,
                object : IFetchMarketEmoticonListCallback {
                    override fun onFetchMarketEmoticonListCallback(
                        code: Int,
                        msg: String?,
                        info: MarketEmoticonInfo?,
                    ) {
                        val tab = info?.roamEmojiTab
                        val packs = buildList {
                            tab?.ordinaryTabinfoList.orEmpty().forEach { add(it.toMarketPack()) }
                            tab?.smallTabinfoList.orEmpty().forEach { add(it.toMarketPack()) }
                            tab?.magicTabinfoList.orEmpty().forEach { add(it.toMarketPack()) }
                        }.distinctBy { it.epId }
                        post(onResult, if (code == 0) packs else emptyList())
                    }
                },
            )
        }.onFailure {
            post(onResult, emptyList())
        }
    }

    fun loadMarketFaces(
        context: Context,
        epId: Int,
        callback: (List<Selection.MarketFace>) -> Unit,
    ) {
        val service = KernelBridge.getMsgService()
        if (service == null) {
            post(callback, emptyList())
            return
        }
        runCatching {
            service.fetchMarketEmotionJsonFile(epId, object : IOperateCallback {
                override fun onResult(code: Int, jsonPath: String?) {
                    if (code != 0 || jsonPath.isNullOrBlank()) {
                        post(callback, emptyList())
                        return
                    }
                    marketJsonPaths[epId] = jsonPath
                    Thread {
                        val faces = runCatching {
                            parseMarketFaces(context, epId, jsonPath)
                        }.getOrDefault(emptyList())
                        post(callback, faces)
                    }.apply {
                        isDaemon = true
                        start()
                    }
                }
            })
        }.onFailure {
            post(callback, emptyList())
        }
    }

    /**
     * Resolve a complete NT market-face element. A failure is reported as null and never falls
     * through to a picture element; callers can then send a readable text fallback.
     */
    fun createMarketFaceElement(
        selection: Selection.MarketFace,
        onResult: (MsgElement?) -> Unit,
    ) {
        val service = KernelBridge.getMsgService()
        if (service == null) {
            post(onResult, null)
            return
        }
        val delivered = AtomicBoolean(false)
        val timeout = Runnable {
            if (delivered.compareAndSet(false, true)) onResult(null)
        }
        mainHandler.postDelayed(timeout, 15_000L)
        fun finish(element: MsgElement?) {
            if (delivered.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                onResult(element)
            }
        }

        fun build(jsonPath: String) {
            val message = runCatching {
                EmosmUtils.a.a(selection.epId, selection.eId, jsonPath)
            }.getOrNull()
            if (message == null) {
                finish(null)
                return
            }
            runCatching {
                EmosmUtils.a.b(
                    service,
                    selection.epId,
                    selection.eId,
                    { keys ->
                        val element = runCatching {
                            val encryptKey = keys
                                ?.get(selection.eId)
                                ?.takeIf(String::isNotBlank)
                                ?: keys?.values?.firstOrNull(String::isNotBlank)
                                ?: error("系列表情加密密钥不可用")
                            message.h = encryptKey.toByteArray(Charsets.UTF_8)
                            if (message.l == null) message.l = ByteArray(0)
                            if (message.m == null) message.m = ByteArray(0)
                            val marketFace = QRoute.api(IMarketFaceApi::class.java)
                                .createMarketFaceElement(message)
                            MsgElement().apply {
                                elementType = 11
                                elementId = 0
                                marketFaceElement = marketFace
                            }
                        }.getOrNull()
                        finish(element)
                    },
                    {
                        finish(null)
                    },
                )
            }.onFailure {
                finish(null)
            }
        }

        selection.jsonPath?.takeIf(String::isNotBlank)?.let {
            build(it)
            return
        }
        marketJsonPaths[selection.epId]?.let {
            build(it)
            return
        }
        runCatching {
            service.fetchMarketEmotionJsonFile(selection.epId, object : IOperateCallback {
                override fun onResult(code: Int, jsonPath: String?) {
                    if (code == 0 && !jsonPath.isNullOrBlank()) {
                        marketJsonPaths[selection.epId] = jsonPath
                        build(jsonPath)
                    } else {
                        finish(null)
                    }
                }
            })
        }.onFailure {
            finish(null)
        }
    }

    fun resolveMarketFacePreview(
        selection: Selection.MarketFace,
        onResult: (String?) -> Unit,
    ) {
        val cacheKey = marketFaceCacheKey(selection.epId, selection.eId)
        findMarketFacePreviewPath(selection)?.let { path ->
            marketPreviewPathCache[cacheKey] = path
            post(onResult, path)
            return
        }

        createMarketFaceElement(selection) { element ->
            val marketFace = element?.marketFaceElement
            if (marketFace == null) {
                post(onResult, null)
                return@createMarketFaceElement
            }
            loadMarketFaceDrawable(marketFace) {
                Thread {
                    val path = waitForMarketFacePreviewPath(selection)
                    if (path != null) marketPreviewPathCache[cacheKey] = path
                    post(onResult, path)
                }.apply {
                    isDaemon = true
                    start()
                }
            }
        }
    }

    private fun parseMarketFaces(
        context: Context,
        epId: Int,
        jsonPath: String,
    ): List<Selection.MarketFace> {
        val raw = File(jsonPath).takeIf(File::isFile)?.readText() ?: jsonPath
        val images = JSONObject(raw).optJSONArray("imgs") ?: return emptyList()
        val definitions = buildList {
            for (index in 0 until images.length()) {
                val image = images.optJSONObject(index) ?: continue
                val eId = image.optString("id").takeIf(String::isNotBlank) ?: continue
                add(image to eId)
            }
        }
        val resolvedPaths = resolveMarketFacePaths(
            context = context,
            epId = epId,
            eIds = definitions.map { it.second },
        )
        return buildList {
            definitions.forEach { (image, eId) ->
                val label = image.optString("name").takeIf(String::isNotBlank)
                    ?: "大表情 $eId"
                val paths = resolvedPaths[eId]
                add(
                    Selection.MarketFace(
                        epId = epId,
                        eId = eId,
                        label = label,
                        width = image.optInt("wWidthInPhone", 200).coerceAtLeast(1),
                        height = image.optInt("wHeightInPhone", 200).coerceAtLeast(1),
                        jsonPath = jsonPath,
                        staticPath = paths?.staticPath,
                        dynamicPath = paths?.dynamicPath,
                    ),
                )
            }
        }
    }

    private fun resolveMarketFacePaths(
        context: Context,
        epId: Int,
        eIds: List<String>,
    ): Map<String, MarketFacePaths> {
        val ids = eIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyMap()
        val service = KernelBridge.getKernelMsgService()
        val previewPaths = service?.let {
            queryMarketFacePaths(it, epId, ids, MarketEmojiPathServiceType.KMSGPNGEIDEMOJI)
        }.orEmpty()
        val rawPaths = service?.let {
            queryMarketFacePaths(it, epId, ids, MarketEmojiPathServiceType.KEIDEMOJI)
        }.orEmpty()
        val packPaths = service?.let {
            queryMarketFacePaths(it, epId, ids, MarketEmojiPathServiceType.KEPIDEMOJI)
        }.orEmpty()
        return ids.associateWith { eId ->
            val localPaths = marketFaceStoragePaths(context, epId, eId)
            MarketFacePaths(
                staticPath = preferredPath(previewPaths[eId], localPaths.staticPath),
                dynamicPath = preferredPath(rawPaths[eId], packPaths[eId], localPaths.dynamicPath),
            )
        }
    }

    private fun queryMarketFacePaths(
        service: IKernelMsgService,
        epId: Int,
        eIds: List<String>,
        type: MarketEmojiPathServiceType,
    ): Map<String, String> = runCatching {
        val paths = service.getMarketEmoticonPathBySync(epId, ArrayList(eIds), type)
        buildMap {
            eIds.forEach { eId ->
                paths[eId]?.path
                    ?.takeIf(String::isNotBlank)
                    ?.let { put(eId, it) }
            }
        }
    }.getOrDefault(emptyMap())

    private fun marketFaceStoragePaths(
        context: Context,
        epId: Int,
        eId: String,
    ): MarketFacePaths {
        val epIdText = epId.toString()
        val root = File(
            context.getExternalFilesDir(null)?.parentFile,
            "Tencent/MobileQQ/.emotionsm/$epId",
        )
        return MarketFacePaths(
            staticPath = preferredPath(
                MarketFaceStorageUtil.a(epIdText, eId),
                MarketFaceStorageUtil.e(epIdText, eId),
                File(root, "${eId}_aio.png").absolutePath,
                File(root, "${eId}_thu.png").absolutePath,
            ),
            dynamicPath = preferredPath(
                MarketFaceStorageUtil.c(epIdText, eId),
                MarketFaceStorageUtil.b(epIdText, eId),
                File(root, eId).absolutePath,
                File(root, "${eId}_apng").absolutePath,
            ),
        )
    }

    private fun findMarketFacePreviewPath(selection: Selection.MarketFace): String? {
        val cacheKey = marketFaceCacheKey(selection.epId, selection.eId)
        marketPreviewPathCache[cacheKey]?.takeIf(::isReadableFile)?.let { return it }
        marketFacePreviewPathCandidates(selection)
            .firstOrNull(::isReadableFile)
            ?.let { return it }
        return null
    }

    private fun waitForMarketFacePreviewPath(selection: Selection.MarketFace): String? {
        val deadline = System.currentTimeMillis() + MARKET_FACE_RENDER_TIMEOUT_MS
        do {
            findMarketFacePreviewPath(selection)?.let { return it }
            Thread.sleep(MARKET_FACE_PREVIEW_POLL_MS)
        } while (System.currentTimeMillis() < deadline)
        return null
    }

    private fun marketFacePreviewPathCandidates(selection: Selection.MarketFace): List<String> {
        val epId = selection.epId.toString()
        return listOf(
            selection.staticPath,
            MarketFaceStorageUtil.a(epId, selection.eId),
            MarketFaceStorageUtil.e(epId, selection.eId),
        ).filterNotNull().distinct()
    }

    private fun preferredPath(vararg paths: String?): String? {
        val candidates = paths.filterNotNull().filter(String::isNotBlank).distinct()
        return candidates.firstOrNull(::isReadableFile) ?: candidates.firstOrNull()
    }

    private fun isReadableFile(path: String): Boolean = runCatching {
        File(path).isFile && File(path).length() > 0L
    }.getOrDefault(false)

    private fun marketFaceCacheKey(epId: Int, eId: String): String = "$epId:$eId"

    private fun requestMarketFaceDownload(drawable: Drawable?) {
        runCatching {
            val target = drawable ?: return@runCatching
            target.javaClass.methods.firstOrNull {
                it.parameterTypes.isEmpty() && it.name == "downloadImediatly"
            }?.invoke(target)
        }
    }

    private fun TabEmojiInfo.toMarketPack(): MarketPack = MarketPack(
        epId = epId,
        name = tabName?.takeIf(String::isNotBlank) ?: "表情包 $epId",
        tabType = tabType,
    )

    private fun <T> post(callback: (T) -> Unit, value: T) {
        mainHandler.post { callback(value) }
    }
}
