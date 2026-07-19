package rj.qmce.lite.data.emotion

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.util.Log
import com.tencent.mobileqq.app.AppConstants
import com.tencent.mobileqq.emoticonview.EmoticonInfo
import com.tencent.mobileqq.emoticonview.IPicEmoticonInfo
import com.tencent.mobileqq.text.style.EmoticonSpan
import com.tencent.mobileqq.data.AniStickerInfo
import com.tencent.mobileqq.emoticon.QQSysAndEmojiResInfo
import com.tencent.mobileqq.emoticon.QQSysAndEmojiResMgr
import com.tencent.mobileqq.utils.HexUtil
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.aio.anisticker.download.AniStickerLottieResDownloader
import com.tencent.qqnt.aio.anisticker.download.LoadListener
import com.tencent.qqnt.aio.anisticker.download.LottieResDownloadFactory
import com.tencent.qqnt.aio.anisticker.drawable.IAniStickerLottieDrawable
import com.tencent.qqnt.aio.anisticker.view.AniStickerLottie
import com.tencent.qqnt.aio.anisticker.view.AniStickerHelper
import com.tencent.qqnt.aio.anisticker.view.AniStickerLottieView
import com.tencent.qqnt.aio.anisticker.view.AniStickerSvgHelper
import com.tencent.qqnt.aio.adapter.api.IQQTextApi
import com.tencent.qqnt.emotion.adapter.api.IMarketFaceApi
import com.tencent.qqnt.emotion.api.IEmoticonManagerService
import com.tencent.qqnt.emotion.info.SystemAndEmojiEmotionInfo
import com.tencent.qqnt.emotion.text.style.api.IEmojiSpanService
import com.tencent.qqnt.emotion.utils.MarketFaceStorageUtil
import com.tencent.qqnt.emotion.utils.QQEmojiUtil
import com.tencent.qqnt.emotion.utils.QQSysFaceUtil
import com.tencent.qqnt.emoji.EmoJIConstant
import com.tencent.qqnt.kernel.nativeinterface.IFetchMarketEmoticonListCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetMarketEmoticonEncryptKeysCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MarketEmojiPathServiceType
import com.tencent.qqnt.kernel.nativeinterface.MarketEmoticonInfo
import com.tencent.qqnt.kernel.nativeinterface.MarketFaceElement
import com.tencent.qqnt.kernel.nativeinterface.MarketFaceSupportSize
import com.tencent.qqnt.kernel.nativeinterface.FaceElement
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.TabEmojiInfo
import com.tencent.qqnt.watch.emotion.popemo.EmoMsgUtils
import com.tencent.mobileqq.data.MarkFaceMessage
import com.tencent.qphone.base.util.BaseApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mqq.app.MobileQQ
import org.json.JSONArray
import org.json.JSONObject
import rj.qmce.lite.QmceApplication
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
    private const val TAG = "QMCE-Emotion"
    sealed interface Selection {
        val label: String

        data class SystemFace(
            val faceType: Int,
            val faceIndex: Int,
            override val label: String,
            val packId: String? = null,
            val imageType: Int? = null,
            val stickerId: String? = null,
            val stickerType: Int? = null,
            val isEmoji: Boolean = false,
            val serverId: Int? = null,
            val resultId: String? = null,
            val surpriseId: String? = null,
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
            val isSmallFace: Boolean = false,
            val imageType: Int = 1,
        ) : Selection
    }

    data class MarketPack(
        val epId: Int,
        val name: String,
        val tabType: Int,
        val isSmallFace: Boolean = false,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val marketJsonPaths = ConcurrentHashMap<Int, String>()
    private val marketPreviewPathCache = ConcurrentHashMap<String, String>()
    private val animatedDrawableCache = ConcurrentHashMap<String, IAniStickerLottieDrawable>()
    private val lottieInitialized = AtomicBoolean(false)
    private const val MARKET_FACE_RENDER_TIMEOUT_MS = 8_000L
    private const val MARKET_FACE_PREVIEW_POLL_MS = 150L
    private const val SYSTEM_FACE_CONFIG_TIMEOUT_MS = 4_000L
    private const val SYSTEM_FACE_CONFIG_POLL_MS = 150L
    private const val ANIMATED_FACE_RETRY_DELAY_MS = 350L
    private const val ANIMATED_FACE_MAX_RETRIES = 12

    private data class MarketFacePaths(
        val staticPath: String?,
        val dynamicPath: String?,
    )

    data class MarketFacePreview(
        val path: String?,
        val drawable: Drawable?,
    )

    private data class BundledSystemFace(
        val localId: Int,
        val serverId: Int,
        val description: String,
        val packId: String?,
        val stickerId: String?,
        val stickerType: Int,
    )

    private data class BundledFaceMapping(
        val byLocalId: Map<Int, BundledSystemFace>,
        val byServerId: Map<Int, BundledSystemFace>,
    )

    private val bundledFaceMapping by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val faces = bundledSystemFaces()
        BundledFaceMapping(
            byLocalId = faces.associateBy { it.localId },
            byServerId = faces.associateBy { it.serverId },
        )
    }

    fun loadSystemFaces(): List<Selection.SystemFace> {
        val result = ArrayList<Selection.SystemFace>()
        val systemRes = systemFaceRes()
        val bundledFaces = bundledSystemFaces()
        val bundledByLocalId = bundledFaces.associateBy { it.localId }
        val bundledAnimatedIds = EmotionAssetBridge.bundledAnimatedIds()
        val configuredIds = systemRes?.let { resourceFaceOrder(it) }.orEmpty()
        val systemIds = buildList {
            addAll(configuredIds)
            if (isEmpty()) {
                addAll(runCatching { QQSysFaceUtil.a.h() }.getOrDefault(emptyList()))
            }
            bundledFaces.forEach { add(it.localId) }
            bundledAnimatedIds.forEach { add(it) }
            if (isEmpty()) addAll(QQSysFaceUtil.e.toList())
        }.distinct()
        if (configuredIds.isEmpty()) {
            Log.w(TAG, "loadSystemFaces: system resource order unavailable; using static ids")
        }
        systemIds.forEach { localId ->
            val aniStickerType = systemRes?.invokeInt("getAniStickerType", localId) ?: 0
            val bundled = bundledByLocalId[localId]
            val resolvedStickerType = aniStickerType.takeIf { it > 0 } ?: bundled?.stickerType ?: 0
            val isSuperFace = resolvedStickerType > 0
            val aniInfo = if (isSuperFace) {
                systemRes?.invokeAniStickerInfo(
                    localId = localId,
                    resultId = null,
                    surpriseId = null,
                )
            } else {
                null
            }
            val label = runCatching { systemRes?.getDescription(localId) }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: runCatching { QQSysFaceUtil.a.c(localId) }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: bundled?.description?.takeIf(String::isNotBlank)
                ?: "表情 $localId"
            result += Selection.SystemFace(
                // The official system-emotion panel exposes both classic and AniSticker faces as
                // system faces (type 1).  AniStickerType is metadata for the send/render path,
                // not a replacement for the kernel face type used by the panel.
                faceType = 1,
                faceIndex = localId,
                label = label,
                packId = if (isSuperFace) {
                    aniInfo?.b?.takeIf(String::isNotBlank)
                        ?: systemRes?.invokeString("getAniStickerPackageId", localId)
                        ?.takeIf(String::isNotBlank)
                        ?: bundled?.packId
                } else {
                    null
                },
                stickerId = aniInfo?.c?.takeIf(String::isNotBlank) ?: bundled?.stickerId,
                stickerType = aniInfo?.a?.takeIf { it > 0 }
                    ?: resolvedStickerType.takeIf { it > 0 },
                serverId = bundled?.serverId ?: serverIdForLocal(localId),
            )
        }

        val emojiIds = runCatching {
            QQEmojiUtil.a.b().takeIf { it.isNotEmpty() }
                ?: (0 until 165).toList()
        }.getOrElse {
            Log.w(TAG, "loadSystemFaces: emoji resource order unavailable; using static ids", it)
            (0 until 165).toList()
        }
        emojiIds.forEach { emojiId ->
            result += Selection.SystemFace(
                faceType = 2,
                faceIndex = emojiId,
                label = emojiText(emojiId) ?: "Emoji $emojiId",
                isEmoji = true,
            )
        }
        return result
    }

    fun loadSystemFacesAsync(onResult: (List<Selection.SystemFace>) -> Unit) {
        val deadline = System.currentTimeMillis() + SYSTEM_FACE_CONFIG_TIMEOUT_MS
        fun poll() {
            mainHandler.post {
                val faces = loadSystemFaces()
                Log.i(
                    TAG,
                    "loadSystemFacesAsync: count=${faces.size}, " +
                        "animated=${faces.count { it.isAnimatedSticker() }}, " +
                        "labels=${faces.take(3).joinToString { it.label }}",
                )
                onResult(faces)
                if (System.currentTimeMillis() < deadline && !hasConfiguredSystemFaces(faces)) {
                    mainHandler.postDelayed({ poll() }, SYSTEM_FACE_CONFIG_POLL_MS)
                }
            }
        }
        poll()
    }

    fun systemFaceDrawable(face: Selection.SystemFace): Drawable? {
        if (face.isEmoji) {
            return runCatching { QQEmojiUtil.a.a(face.faceIndex) }.getOrNull()
                ?: localEmojiDrawable(face.faceIndex)
                ?: runCatching {
                    SystemAndEmojiEmotionInfo(face.faceType, face.faceIndex, face.label).r()
                }.getOrNull()
        }
        if (face.isAnimatedSticker()) {
            animatedDrawableCache[animatedFaceKey(face)]?.let {
                runCatching { it.drawable }.getOrNull()
            }?.takeUnless { it is ColorDrawable }?.let { return it }
            superFaceDrawable(face)?.let { return it }
            localSystemFaceDrawable(face)?.let { return it }
        } else {
            kernelRenderedFaceDrawable(face)
                ?.takeUnless { it is ColorDrawable }
                ?.let { return it }
            runCatching { QQSysFaceUtil.a.d(face.faceIndex) }.getOrNull()
                ?.takeUnless { it is ColorDrawable }
                ?.let { return it }
            runCatching { systemFaceRes()?.getDrawable(face.faceIndex) }.getOrNull()
                ?.takeUnless { it is ColorDrawable }
                ?.let { return it }
            localSystemFaceDrawable(face)?.let { return it }
        }
        kernelRenderedFaceDrawable(face)
            ?.takeUnless { it is ColorDrawable }
            ?.let { return it }
        runCatching {
            SystemAndEmojiEmotionInfo(face.faceType, face.faceIndex, face.label).r()
        }.getOrNull()
            ?.takeUnless { it is ColorDrawable }
            ?.let { return it }
        return runCatching { QQSysFaceUtil.a.e(face.faceIndex) }.getOrNull()
            ?.takeUnless { it is ColorDrawable }
    }

    private fun superFaceDrawable(face: Selection.SystemFace): Drawable? = runCatching {
        QQSysFaceUtil.a.f(face.faceIndex)
    }.getOrNull()?.takeUnless { it is ColorDrawable }
        ?: runCatching {
            systemFaceRes()?.invokeDrawable("getGifDrawable", face.faceIndex)
        }.getOrNull()?.takeUnless { it is ColorDrawable }
        ?: runCatching {
            systemFaceRes()?.invokeDrawable("getGifURLDrawable", face.faceIndex)
        }.getOrNull()?.takeUnless { it is ColorDrawable }

    private fun localSystemFaceDrawable(face: Selection.SystemFace): Drawable? = runCatching {
        val serverIndex = face.serverId ?: serverIdForLocal(face.faceIndex)
        val localIndex = face.faceIndex
        val animated = face.isAnimatedSticker()
        val file = buildList {
            if (animated) {
                add(EmotionAssetBridge.resourceFile("sysface_res/apng/s$serverIndex.png"))
                if (localIndex != serverIndex) {
                    add(EmotionAssetBridge.resourceFile("sysface_res/apng/s$localIndex.png"))
                }
            }
            add(EmotionAssetBridge.resourceFile("sysface_res/static/s$serverIndex.png"))
            if (localIndex != serverIndex) {
                add(EmotionAssetBridge.resourceFile("sysface_res/static/s$localIndex.png"))
            }
        }.filterNotNull()
            .firstOrNull(File::isFile)
            ?: return@runCatching null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
        BitmapDrawable(BaseApplication.getContext().resources, bitmap)
    }.onFailure {
        Log.d(TAG, "local system-face drawable unavailable face=${face.faceIndex}", it)
    }.getOrNull()

    private fun localEmojiDrawable(localIndex: Int): Drawable? = runCatching {
        val file = EmotionAssetBridge.resourceFile(
            "emoji_res/emoji_${localIndex.toString().padStart(3, '0')}.png",
        )?.takeIf(File::isFile) ?: return@runCatching null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
        BitmapDrawable(android.content.res.Resources.getSystem(), bitmap)
    }.onFailure {
        Log.d(TAG, "local emoji drawable unavailable local=$localIndex", it)
    }.getOrNull()

    /**
     * Official system-face drawables can be URLDrawable instances.  The first call may only create
     * the loading drawable, so notify the UI again from the official URLDrawable callback.
     */
    fun loadSystemFaceDrawable(
        face: Selection.SystemFace,
        onResult: (Drawable?) -> Unit,
    ) {
        Thread {
            val localFallback = if (face.isEmoji) {
                runCatching { localEmojiDrawable(face.faceIndex) }.getOrNull()
            } else {
                runCatching { localSystemFaceDrawable(face) }.getOrNull()
            }
            mainHandler.post {
                val hasDrawable = AtomicBoolean(localFallback != null)
                val deliver: (Drawable?) -> Unit = { value ->
                    mainHandler.post {
                        if (value != null) {
                            hasDrawable.set(true)
                            onResult(value)
                        } else if (!hasDrawable.get()) {
                            onResult(null)
                        }
                    }
                }
                localFallback?.let(onResult)
                if (face.isEmoji) {
                    if (localFallback == null) {
                        deliver(systemFaceDrawable(face))
                    }
                    return@post
                }
                if (face.isAnimatedSticker()) {
                    loadAnimatedSvgThumb(face, deliver)
                    val officialAnimated = superFaceDrawable(face)
                    if (officialAnimated != null) {
                        if (!isOfficialUrlDrawable(officialAnimated) ||
                            officialUrlDrawableStatus(officialAnimated) == 1
                        ) {
                            deliver(officialAnimated)
                        } else {
                            runCatching {
                                installOfficialUrlDrawableListener(officialAnimated, face, deliver)
                                startOfficialUrlDrawableDownload(officialAnimated) {
                                    deliver(officialAnimated)
                                }
                            }.onFailure {
                                Log.d(TAG, "animated official drawable unavailable face=${face.faceIndex}", it)
                            }
                        }
                    }
                    loadAnimatedSystemFaceDrawable(face, deliver)
                    return@post
                }
                val currentDrawable = systemFaceDrawable(face)
                if (currentDrawable == null) {
                    deliver(null)
                    return@post
                }
                if (!isOfficialUrlDrawable(currentDrawable)) {
                    deliver(currentDrawable)
                    return@post
                }

                if (officialUrlDrawableStatus(currentDrawable) == 1) {
                    deliver(currentDrawable)
                    return@post
                }

                runCatching {
                    installOfficialUrlDrawableListener(currentDrawable, face, deliver)
                    startOfficialUrlDrawableDownload(currentDrawable) {
                        deliver(currentDrawable)
                    }
                }.onFailure {
                    Log.d(TAG, "system face drawable listener unavailable face=${face.faceIndex}", it)
                    deliver(null)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun loadAnimatedSvgThumb(
        face: Selection.SystemFace,
        onResult: (Drawable?) -> Unit,
    ) {
        Thread {
            val drawable = runCatching {
                val info = animatedStickerInfo(face) ?: return@runCatching null
                val svgPath = QQSysAndEmojiResMgr.getSvgResSavePath(info.b, info.c)
                    .takeIf(::isReadableFile)
                    ?: return@runCatching null
                val context = BaseApplication.getContext()
                val size = (64f * context.resources.displayMetrics.density)
                    .toInt()
                    .coerceAtLeast(1)
                AniStickerSvgHelper.a.d(svgPath, size, size)
            }.onFailure {
                Log.d(TAG, "animated face svg thumb unavailable face=${face.faceIndex}", it)
            }.getOrNull()
            if (drawable != null) onResult(drawable)
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun loadAnimatedSystemFaceDrawable(
        face: Selection.SystemFace,
        onResult: (Drawable?) -> Unit,
        attempt: Int = 0,
    ) {
        val cacheKey = animatedFaceKey(face)
        animatedDrawableCache[cacheKey]?.let { cached ->
            mainHandler.post { onResult(runCatching { cached.drawable }.getOrNull()) }
            return
        }
        if (attempt > ANIMATED_FACE_MAX_RETRIES) {
            Log.w(TAG, "animated face load exhausted local=${face.faceIndex} server=${face.serverId}")
            mainHandler.post { onResult(null) }
            return
        }
        val context = runCatching { com.tencent.qphone.base.util.BaseApplication.getContext() }
            .getOrNull()
            ?: run {
                mainHandler.post { onResult(null) }
                return
            }
        val info = animatedStickerInfo(face)
        if (info == null) {
            Log.w(
                TAG,
                "animated face metadata unavailable local=${face.faceIndex} " +
                    "server=${face.serverId} pack=${face.packId} sticker=${face.stickerId}",
            )
            mainHandler.post { onResult(null) }
            return
        }
        if (!ensureLottieRuntime()) {
            Log.w(TAG, "animated face lottie runtime unavailable local=${face.faceIndex}")
            mainHandler.post { onResult(null) }
            return
        }
        mainHandler.post {
            val downloader: AniStickerLottieResDownloader? = runCatching {
                LottieResDownloadFactory.a.a<AniStickerLottieResDownloader>(1)
            }.getOrNull()
            if (downloader == null) {
                Log.w(TAG, "animated face downloader unavailable local=${face.faceIndex}")
                onResult(null)
                return@post
            }
            val helperView = runCatching { AniStickerLottieView(context) }.getOrNull()
            if (helperView == null) {
                Log.w(TAG, "animated face helper view unavailable local=${face.faceIndex}")
                onResult(null)
                return@post
            }
            val jsonPath = runCatching { info.b() }.getOrNull()
            val svgPath = runCatching {
                QQSysAndEmojiResMgr.getSvgResSavePath(info.b, info.c)
                    .takeIf(::isReadableFile)
            }.getOrNull()
            val builder = AniStickerHelper.Builder(helperView).apply {
                val size = (64f * context.resources.displayMetrics.density).toInt()
                b = size
                c = size
                d = svgPath
                e = info
                f = info.e
                g = android.R.drawable.progress_indeterminate_horizontal
                j = info.f.orEmpty()
                m = !info.f.isNullOrBlank() || !info.g.isNullOrBlank()
                n = info.g.orEmpty()
            }
            val callbackDelivered = AtomicBoolean(false)
            val listener = object : LoadListener {
                override fun a(drawable: IAniStickerLottieDrawable) {
                    if (!callbackDelivered.compareAndSet(false, true)) return
                    runCatching {
                        drawable.d(if (builder.m) 1 else -1)
                        drawable.setAllowDecodeSingleFrame(true)
                        drawable.start()
                        animatedDrawableCache[cacheKey] = drawable
                        onResult(drawable.drawable)
                    }.onFailure {
                        Log.w(TAG, "animated face drawable failed local=${face.faceIndex}", it)
                        onResult(null)
                    }
                }

                override fun b(key: String) {
                    if (attempt >= ANIMATED_FACE_MAX_RETRIES) {
                        Log.w(TAG, "animated face resource unavailable key=$key local=${face.faceIndex}")
                        if (callbackDelivered.compareAndSet(false, true)) onResult(null)
                        return
                    }
                    mainHandler.postDelayed(
                        { loadAnimatedSystemFaceDrawable(face, onResult, attempt + 1) },
                        ANIMATED_FACE_RETRY_DELAY_MS,
                    )
                }

                override fun onFail(error: Throwable?) {
                    Log.w(
                        TAG,
                        "animated face load failed local=${face.faceIndex} attempt=$attempt",
                        error,
                    )
                    if (attempt < ANIMATED_FACE_MAX_RETRIES) {
                        mainHandler.postDelayed(
                            { loadAnimatedSystemFaceDrawable(face, onResult, attempt + 1) },
                            ANIMATED_FACE_RETRY_DELAY_MS,
                        )
                    } else if (callbackDelivered.compareAndSet(false, true)) {
                        onResult(null)
                    }
                }
            }
            runCatching {
                downloader.h(info)
                Log.d(
                    TAG,
                        "animated face load local=${face.faceIndex} server=${face.serverId} " +
                        "path=$jsonPath cache=${info.a()} exists=${jsonPath?.let(::File)?.isFile}",
                    )
                downloader.f(jsonPath, builder, listener)
            }.onFailure {
                Log.w(TAG, "animated face load invocation failed local=${face.faceIndex}", it)
                if (attempt < ANIMATED_FACE_MAX_RETRIES) {
                    mainHandler.postDelayed(
                        { loadAnimatedSystemFaceDrawable(face, onResult, attempt + 1) },
                        ANIMATED_FACE_RETRY_DELAY_MS,
                    )
                } else if (callbackDelivered.compareAndSet(false, true)) {
                    onResult(null)
                }
            }
        }
    }

    private fun ensureLottieRuntime(): Boolean {
        if (lottieInitialized.get()) return true
        return runCatching {
            AniStickerLottie.a.a()
            if (!AniStickerLottie.b) error("rlottie native runtime was not loaded")
            lottieInitialized.set(true)
            true
        }.getOrElse {
            lottieInitialized.set(false)
            Log.w(TAG, "init animated-face lottie runtime failed", it)
            false
        }
    }

    private fun animatedStickerInfo(face: Selection.SystemFace): AniStickerInfo? {
        val localId = face.faceIndex
        val bundled = bundledFaceMapping.byLocalId[localId]
            ?: face.serverId?.let(bundledFaceMapping.byServerId::get)
        val official = systemFaceRes()?.invokeAniStickerInfo(
            localId = localId,
            resultId = face.resultId,
            surpriseId = face.surpriseId,
        )
        val packId = official?.b?.takeIf(String::isNotBlank)
            ?: face.packId?.takeIf(String::isNotBlank)
            ?: bundled?.packId
        val stickerId = official?.c?.takeIf(String::isNotBlank)
            ?: face.stickerId?.takeIf(String::isNotBlank)
            ?: bundled?.stickerId
        val stickerType = official?.a?.takeIf { it > 0 }
            ?: face.stickerType
            ?: bundled?.stickerType
        if (packId.isNullOrBlank() || stickerId.isNullOrBlank() || stickerType == null || stickerType <= 0) {
            return null
        }
        return (official ?: AniStickerInfo()).apply {
            a = stickerType
            b = packId
            c = stickerId
            d = face.serverId ?: bundled?.serverId ?: serverIdForLocal(localId)
            e = localId
            if (h.isNullOrBlank()) h = face.label
            face.resultId?.takeIf { it.isNotBlank() }?.let { f = it }
            face.surpriseId?.takeIf { it.isNotBlank() }?.let { g = it }
        }
    }

    private fun animatedFaceKey(face: Selection.SystemFace): String =
        "${face.serverId ?: serverIdForLocal(face.faceIndex)}:${face.faceIndex}:" +
            "${face.packId.orEmpty()}:${face.stickerId.orEmpty()}:" +
            "${face.resultId.orEmpty()}:${face.surpriseId.orEmpty()}"

    private fun isOfficialUrlDrawable(drawable: Drawable): Boolean {
        var type: Class<*>? = drawable.javaClass
        while (type != null) {
            if (type.name == "com.tencent.image.URLDrawable") return true
            type = type.superclass
        }
        return false
    }

    private fun officialUrlDrawableStatus(drawable: Drawable): Int = runCatching {
        (drawable.javaClass.methods.firstOrNull {
            it.name == "getStatus" && it.parameterTypes.isEmpty()
        }?.invoke(drawable) as? Number)?.toInt() ?: -1
    }.getOrDefault(-1)

    private fun installOfficialUrlDrawableListener(
        drawable: Drawable,
        face: Selection.SystemFace,
        deliver: (Drawable?) -> Unit,
    ) {
        val loader = drawable.javaClass.classLoader ?: EmotionRepository::class.java.classLoader
        val listenerClass = Class.forName(
            "com.tencent.image.URLDrawable\$URLDrawableListener",
            false,
            loader,
        )
        val retried = AtomicBoolean(false)
        val listener = java.lang.reflect.Proxy.newProxyInstance(
            loader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            when (method.name) {
                "onLoadSuccessed" -> deliver(drawable)
                "onLoadCanceled", "onLoadFialed" -> {
                    Log.d(TAG, "system face drawable load failed face=${face.faceIndex}")
                    if (!retried.compareAndSet(false, true) || !restartOfficialUrlDrawable(drawable)) {
                        deliver(localSystemFaceDrawable(face))
                    }
                }
            }
            null
        }
        drawable.javaClass.getMethod("setURLDrawableListener", listenerClass)
            .invoke(drawable, listener)
    }

    private fun startOfficialUrlDrawableDownload(
        drawable: Drawable,
        onSuccess: (() -> Unit)? = null,
    ) {
        when (officialUrlDrawableStatus(drawable)) {
            2, 3 -> {
                if (!restartOfficialUrlDrawable(drawable)) {
                    invokeOfficialUrlDrawableMethod(drawable, "downloadImediatly")
                }
            }

            1 -> {
                onSuccess?.invoke()
                return
            }

            else -> invokeOfficialUrlDrawableMethod(drawable, "downloadImediatly")
        }

        // URLDrawable may finish synchronously while the download method is invoked.  In that
        // case the listener can miss the only success callback, so close the state transition by
        // checking the status once more after starting the request.
        if (officialUrlDrawableStatus(drawable) == 1) {
            onSuccess?.invoke()
        }
    }

    private fun restartOfficialUrlDrawable(drawable: Drawable): Boolean =
        invokeOfficialUrlDrawableMethod(drawable, "restartDownload")

    private fun invokeOfficialUrlDrawableMethod(
        drawable: Drawable,
        methodName: String,
    ): Boolean = runCatching {
        val method = drawable.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty()
        } ?: return@runCatching false
        method.invoke(drawable)
        true
    }.getOrDefault(false)

    private fun Selection.SystemFace.isAnimatedSticker(): Boolean =
        faceType == 3 || (
            (stickerType ?: 0) > 0 &&
                !packId.isNullOrBlank() &&
                !stickerId.isNullOrBlank()
            )

    /**
     * Use the same span-producing path as the official AIO renderer. The span owns the drawable
     * selected by QQ's emotion/runtime resources; this avoids treating a face as an arbitrary app
     * resource and also handles the current server/local id mapping.
     */
    private fun kernelRenderedFaceDrawable(face: Selection.SystemFace): Drawable? = runCatching {
        val rawIndex = if (face.isEmoji) {
            face.faceIndex
        } else {
            face.serverId ?: serverIdForLocal(face.faceIndex)
        }
        val spanService = QRoute.api(IEmojiSpanService::class.java)
        val rawText = spanService.createSysAndEmojiSpanText(
            face.faceType,
            rawIndex,
            face.imageType ?: 0,
            true,
            0,
        )
        val text = if (face.faceType == 4 && rawText !is Spanned) {
            QRoute.api(IQQTextApi::class.java).getQQText(
                rawText,
                5,
                face.imageType ?: 0,
                null,
                null,
            )
        } else {
            rawText
        }
        val spanned = text as? Spanned ?: return@runCatching null
        spanned.getSpans(0, spanned.length, Any::class.java)
            .asSequence()
            .mapNotNull(::spanDrawable)
            .firstOrNull()
    }.onFailure {
        Log.d(
            TAG,
            "kernel face drawable unavailable type=${face.faceType} index=${face.faceIndex}",
            it,
        )
    }.getOrNull()

    private fun spanDrawable(span: Any): Drawable? = runCatching {
        when (span) {
            is EmoticonSpan -> span.getDrawable() ?: span.e()
            else -> span.javaClass.methods
                .firstOrNull { method ->
                    method.parameterTypes.isEmpty() &&
                        (method.name == "getDrawable" || method.name == "e") &&
                        Drawable::class.java.isAssignableFrom(method.returnType)
                }
                ?.invoke(span) as? Drawable
        }
    }.getOrNull()

    /** Text that remains visible when a QQ resource drawable is not available in this process. */
    fun systemFaceText(face: Selection.SystemFace): String =
        if (face.isEmoji) emojiText(face.faceIndex) ?: face.label else face.label

    private fun emojiText(index: Int): String? = runCatching {
        val unicode = EmoJIConstant.b.getOrNull(index) ?: return@runCatching null
        String(Character.toChars(unicode)).takeIf(String::isNotBlank)
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
        return buildList {
            face.staticFacePath.takeIf(String::isNotBlank)?.let(::add)
            face.dynamicFacePath.takeIf(String::isNotBlank)?.let(::add)
            addAll(marketFaceStorageCandidates(context, epId, eId))
        }.distinct()
    }

    private fun systemFaceRes(): QQSysAndEmojiResInfo? = runCatching {
        QQSysAndEmojiResMgr.getInstance().apply { checkInitConfig() }.getResImpl(1)
    }.onFailure {
        Log.d(TAG, "system face resource manager unavailable", it)
    }.getOrNull()

    private fun resourceFaceOrder(resource: QQSysAndEmojiResInfo): List<Int> = buildList {
        addAll(resource.getOrderList().orEmpty())
        addAll(resource.getAniStickerOrderList().orEmpty())
        addAll(resource.getExtAniStickerOrderList().orEmpty())
    }.distinct().filterNot {
        runCatching { resource.checkEmoticonShouldHide(it) }.getOrDefault(false)
    }

    private fun bundledSystemFaces(): List<BundledSystemFace> = runCatching {
        val config = EmotionAssetBridge.resourceFile("face_config.json")
            ?.takeIf(File::isFile)
            ?.readText()
            ?: return@runCatching emptyList()
        val items = JSONObject(config).optJSONArray("sysface") ?: JSONArray()
        val faces = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                if (item.optString("QHide") == "1") continue
                val localId = item.optString("AQLid").toIntOrNull() ?: continue
                val serverId = item.optString("QSid").toIntOrNull() ?: localId
                add(
                    BundledSystemFace(
                        localId = localId,
                        serverId = serverId,
                        description = item.optString("QDes"),
                        packId = item.optString("AniStickerPackId")
                            .takeIf(String::isNotBlank),
                        stickerId = item.optString("AniStickerId")
                            .takeIf(String::isNotBlank),
                        stickerType = item.optInt("AniStickerType", 0),
                    ),
                )
            }
        }
        val knownServerIds = faces.mapTo(HashSet()) { it.serverId }
        buildList {
            addAll(faces)
            EmotionAssetBridge.bundledAnimatedIds()
                .filterNot(knownServerIds::contains)
                .forEach { id ->
                    add(
                        BundledSystemFace(
                            localId = id,
                            serverId = id,
                            description = "/超表情$id",
                            packId = "1",
                            stickerId = id.toString(),
                            stickerType = 1,
                        ),
                    )
                }
        }
    }.onFailure {
        Log.d(TAG, "bundled system-face config unavailable", it)
    }.getOrDefault(emptyList())

    private fun hasConfiguredSystemFaces(faces: List<Selection.SystemFace>): Boolean =
        if (EmotionAssetBridge.bundledAnimatedIds().isNotEmpty()) {
            faces.count { it.isAnimatedSticker() } >= EmotionAssetBridge.bundledAnimatedIds().size
        } else {
            resourceFaceOrder(systemFaceRes() ?: return false).isNotEmpty()
        }

    private fun serverIdForLocal(localId: Int): Int =
        runCatching { systemFaceRes()?.getServerId(localId) }
            .getOrNull()
            ?.takeIf { it >= 0 }
            ?: bundledFaceMapping.byLocalId[localId]?.serverId
            ?: runCatching { QQSysFaceUtil.a.b(localId) }.getOrDefault(localId)

    private fun localIdForServer(serverId: Int, fallback: Int = serverId): Int =
        runCatching { systemFaceRes()?.getLocalId(serverId) }
            .getOrNull()
            ?.takeIf { it >= 0 }
            ?: bundledFaceMapping.byServerId[serverId]?.localId
            ?: runCatching { QQSysFaceUtil.a.a(serverId) }
                .getOrDefault(fallback)

    private fun QQSysAndEmojiResInfo.invokeInt(methodName: String, localId: Int): Int? = runCatching {
        (javaClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(this, localId) as Number).toInt()
    }.getOrNull()

    private fun QQSysAndEmojiResInfo.invokeString(methodName: String, localId: Int): String? =
        runCatching {
            javaClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(this, localId) as? String
        }.getOrNull()

    private fun QQSysAndEmojiResInfo.invokeDrawable(
        methodName: String,
        localId: Int,
    ): Drawable? = runCatching {
        javaClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(this, localId) as? Drawable
    }.getOrNull()

    private fun QQSysAndEmojiResInfo.invokeAniStickerInfo(
        localId: Int,
        resultId: String?,
        surpriseId: String?,
    ): AniStickerInfo? = runCatching {
        javaClass.getMethod(
            "getAniStickerInfo",
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
        ).invoke(this, localId, resultId, surpriseId) as? AniStickerInfo
    }.getOrNull()

    /** Resolve a new market-face preview through the official emotion manager data path. */
    fun loadMarketFaceDrawable(
        selection: Selection.MarketFace,
        element: MarketFaceElement,
        onResult: (Drawable?) -> Unit,
    ) {
        Thread {
            val info = resolveMarketFaceInfo(element)
            deliverMarketFaceDrawable(info, selection, onResult)
        }.apply {
            isDaemon = true
            start()
        }
    }

    /** Resolve a market face already received from NT; its element contains the render metadata. */
    fun loadMarketFaceDrawable(
        element: MarketFaceElement,
        onResult: (Drawable?) -> Unit,
    ) {
        Thread {
            val info = resolveMarketFaceInfo(element)
            deliverMarketFaceDrawable(info, null, onResult)
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun resolveMarketFaceInfo(element: MarketFaceElement): IPicEmoticonInfo? = runCatching {
        val result = runBlocking {
            withTimeoutOrNull(MARKET_FACE_RENDER_TIMEOUT_MS) {
                QRoute.api(IMarketFaceApi::class.java)
                    .fetchMarketFaceInfoSuspend(element)
            }
        }
        Log.d(
            TAG,
            "resolve market face result=" +
                "code=${result?.b ?: -1} message=${result?.c.orEmpty()} " +
                "package=${element.emojiPackageId} eId=${element.emojiId}",
        )
        if (result?.b == 0) {
            result.a?.a
        } else {
            null
        } ?: queryMarketFaceInfoFromRuntime(element)
    }.onFailure {
        Log.w(
            TAG,
            "resolve market face failed package=${element.emojiPackageId} " +
                "eId=${element.emojiId}",
            it,
        )
    }.getOrNull()

    private fun deliverMarketFaceDrawable(
        info: IPicEmoticonInfo?,
        selection: Selection.MarketFace?,
        onResult: (Drawable?) -> Unit,
    ) {
        mainHandler.post {
            val drawable = runCatching { info?.i("fromAIO", true) }
                .onFailure {
                    Log.w(
                        TAG,
                        "create market-face drawable failed " +
                            "epId=${selection?.epId ?: -1} eId=${selection?.eId ?: "received"}",
                        it,
                    )
                }
                .getOrNull()
            if (drawable == null) {
                onResult(null)
                return@post
            }

            if (isOfficialUrlDrawable(drawable)) {
                installMarketFaceUrlDrawableListener(drawable, onResult)
            }
            onResult(drawable)
            if (isOfficialUrlDrawable(drawable)) {
                startOfficialUrlDrawableDownload(drawable) {
                    mainHandler.post { onResult(drawable) }
                }
            } else {
                requestMarketFaceDownload(drawable)
            }
        }
    }

    private fun installMarketFaceUrlDrawableListener(
        drawable: Drawable,
        onResult: (Drawable?) -> Unit,
    ) {
        runCatching {
            val loader = drawable.javaClass.classLoader ?: EmotionRepository::class.java.classLoader
            val listenerClass = Class.forName(
                "com.tencent.image.URLDrawable\$URLDrawableListener",
                false,
                loader,
            )
            val retried = AtomicBoolean(false)
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                loader,
                arrayOf(listenerClass),
            ) { _, method, _ ->
                when (method.name) {
                    "onLoadSuccessed", "onFileDownloaded" -> mainHandler.post {
                        onResult(drawable)
                    }
                    "onLoadCanceled", "onLoadFialed" -> mainHandler.post {
                        if (!retried.compareAndSet(false, true) || !restartOfficialUrlDrawable(drawable)) {
                            onResult(null)
                        }
                    }
                }
                null
            }
            drawable.javaClass.getMethod("setURLDrawableListener", listenerClass)
                .invoke(drawable, listener)
        }.onFailure {
            Log.d(TAG, "market-face URLDrawable listener unavailable", it)
        }
    }

    private fun queryMarketFaceInfoFromRuntime(
        element: MarketFaceElement,
    ): IPicEmoticonInfo? = runCatching {
        val message = marketFaceMessageFromElement(element) ?: return@runCatching null
        val runtime = runCatching { MobileQQ.sMobileQQ?.waitAppRuntime() }.getOrNull()
            ?: QmceApplication.sAppRuntime
            ?: return@runCatching null
        runtime.getRuntimeService(IEmoticonManagerService::class.java, "")
            .syncGetEmoticonInfo<EmoticonInfo>(message)
            as? IPicEmoticonInfo
    }.onFailure {
        Log.w(
            TAG,
            "query market face info failed package=${element.emojiPackageId} " +
                "eId=${element.emojiId}",
            it,
        )
    }.getOrNull()

    private fun marketFaceMessageFromElement(element: MarketFaceElement): MarkFaceMessage? {
        val emojiId = element.emojiId.takeIf(String::isNotBlank) ?: return null
        val key = element.key.takeIf(String::isNotBlank) ?: return null
        val sbufId = runCatching { HexUtil.c(emojiId) }.getOrNull() ?: return null
        return MarkFaceMessage().apply {
            c = element.itemType
            d = element.faceInfo
            f = element.emojiPackageId
            g = element.subType
            i = element.mediaType
            j = element.imageWidth
            k = element.imageHeight
            b = element.faceName
            e = sbufId
            h = key.toByteArray(Charsets.UTF_8)
            l = element.mobileParam ?: ByteArray(0)
            n = element.sourceType ?: 0
            r = element.startTime?.toLong() ?: 0L
            s = element.endTime?.toLong() ?: 0L
            u = element.emojiType == 2
            v = element.hasIpProduct == 1
            x = element.voiceItemHeightArr ?: arrayListOf()
            o = element.sourceName.orEmpty()
            p = element.sourceJumpUrl.orEmpty()
            y = element.backColor.orEmpty()
            z = element.volumeColor.orEmpty()
            A = element.supportSize ?: arrayListOf()
            B = element.apngSupportSize ?: arrayListOf()
        }
    }

    fun createSystemFaceElement(face: Selection.SystemFace): MsgElement? = runCatching {
        if (face.isEmoji) {
            return@runCatching EmoMsgUtils.a.b(face.faceIndex, 2)
        }
        val serverIndex = face.serverId ?: serverIdForLocal(face.faceIndex)
        if (face.isAnimatedSticker()) {
            return@runCatching buildFallbackSystemFaceElement(face, serverIndex)
        }
        runCatching { systemFaceRes() }
        runCatching { EmoMsgUtils.a.a(serverIndex) }
            .getOrNull()
            ?.takeIf { it.faceElement != null }
            ?: buildFallbackSystemFaceElement(face, serverIndex)
    }.onFailure {
        Log.w(
            TAG,
            "createSystemFaceElement failed type=${face.faceType} local=${face.faceIndex}",
            it,
        )
    }.getOrNull()

    private fun buildFallbackSystemFaceElement(
        face: Selection.SystemFace,
        serverIndex: Int,
    ): MsgElement = MsgElement().apply {
        elementType = 6
        val localIndex = localIdForServer(serverIndex, face.faceIndex)
        val animated = face.isAnimatedSticker()
        val aniInfo: AniStickerInfo? = if (animated) {
            animatedStickerInfo(face.copy(faceIndex = localIndex, serverId = serverIndex))
        } else null
        val resolvedStickerType = face.stickerType
            ?: aniInfo?.a
            ?: systemFaceRes()?.invokeInt("getAniStickerType", localIndex)
        val resolvedStickerId = face.stickerId
            ?: aniInfo?.c?.takeIf(String::isNotBlank)
        val resolvedPackId = face.packId
            ?: aniInfo?.b?.takeIf(String::isNotBlank)

        if (animated && (
                resolvedStickerType == null ||
                    resolvedStickerType <= 0 ||
                    resolvedStickerId.isNullOrBlank() ||
                    resolvedPackId.isNullOrBlank()
            )
        ) {
            error("super face metadata unavailable local=${face.faceIndex} server=$serverIndex")
        }
        faceElement = FaceElement().apply {
                faceType = when {
                    animated -> 3
                    face.faceType > 0 -> face.faceType
                    else -> 1
                }
            faceIndex = serverIndex
            face.imageType?.let { imageType = it }
            face.resultId?.takeIf { it.isNotBlank() }?.let { resultId = it }
            face.surpriseId?.takeIf { it.isNotBlank() }?.let { surpriseId = it }
            faceText = aniInfo?.h?.takeIf(String::isNotBlank)
                ?: runCatching {
                    QRoute.api(IEmojiSpanService::class.java)
                        .getFaceDescription(serverIndex, 1)
                }.getOrNull()?.takeIf(String::isNotBlank)
                ?: face.label
            if (animated) {
                packId = resolvedPackId.orEmpty()
                stickerType = resolvedStickerType
                stickerId = resolvedStickerId.orEmpty()
                sourceType = 1
                randomType = 1
            }
        }
    }

    fun systemFaceForMessage(
        faceType: Int,
        ntFaceIndex: Int,
        label: String,
        packId: String?,
        imageType: Int?,
        stickerId: String? = null,
        stickerType: Int? = null,
        resultId: String? = null,
        surpriseId: String? = null,
    ): Selection.SystemFace {
        val resolvedFaceType = faceType.takeIf { it > 0 } ?: 1
        val localIndex = when (resolvedFaceType) {
            1, 3 -> localIdForServer(ntFaceIndex)
            2 -> ntFaceIndex
            else -> ntFaceIndex
        }
        return Selection.SystemFace(
            faceType = resolvedFaceType,
            faceIndex = localIndex,
            label = label,
            packId = packId,
            imageType = imageType,
            stickerId = stickerId,
            stickerType = stickerType,
            isEmoji = resolvedFaceType == 2,
            serverId = ntFaceIndex,
            resultId = resultId,
            surpriseId = surpriseId,
        )
    }

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
                            tab?.smallTabinfoList.orEmpty().forEach {
                                add(it.toMarketPack(isSmallFace = true))
                            }
                            tab?.magicTabinfoList.orEmpty().forEach { add(it.toMarketPack()) }
                        }.distinctBy { it.epId to it.isSmallFace }
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
        isSmallFace: Boolean = false,
        callback: (List<Selection.MarketFace>) -> Unit,
    ) {
        val service = KernelBridge.getMsgService()
        if (service == null) {
            Log.w(TAG, "loadMarketFaces: kernel msg service unavailable epId=$epId")
            post(callback, emptyList())
            return
        }
        Log.d(TAG, "loadMarketFaces: fetch epId=$epId service=$service")
        runCatching {
            service.fetchMarketEmotionJsonFile(epId, object : IOperateCallback {
                override fun onResult(code: Int, jsonPath: String?) {
                    Log.d(
                        TAG,
                        "loadMarketFaces: fetch result epId=$epId code=$code " +
                            "path=${jsonPath?.trim()} isFile=${jsonPath?.trim()?.let { File(it).isFile }}",
                    )
                    if (code != 0 || jsonPath.isNullOrBlank()) {
                        post(callback, emptyList())
                        return
                    }
                    marketJsonPaths[epId] = jsonPath
                    Thread {
                            val faces = runCatching {
                                parseMarketFaces(context, epId, jsonPath, isSmallFace)
                            }
                            .onFailure { Log.w(TAG, "loadMarketFaces: parse failed epId=$epId", it) }
                            .getOrDefault(emptyList())
                        Log.d(TAG, "loadMarketFaces: parsed epId=$epId count=${faces.size}")
                        post(callback, faces)
                    }.apply {
                        isDaemon = true
                        start()
                    }
                }
            })
        }.onFailure {
            Log.w(TAG, "loadMarketFaces: fetch invocation failed epId=$epId", it)
            post(callback, emptyList())
        }
    }

    /**
     * Resolve a complete NT market-face element. A failure is reported as null and never falls
     * through to a text or picture element.
     */
    fun createMarketFaceElement(
        selection: Selection.MarketFace,
        onResult: (MsgElement?) -> Unit,
    ) {
        if (selection.isSmallFace) {
            val element = runCatching {
                val faceId = selection.eId.toIntOrNull()
                    ?: error("small-face id is not numeric: ${selection.eId}")
                val packedIndex = QRoute.api(IEmojiSpanService::class.java)
                    .combineEmoIndex(selection.epId, faceId)
                MsgElement().apply {
                    elementType = 6
                    faceElement = FaceElement().apply {
                        faceType = 4
                        faceIndex = packedIndex
                        imageType = selection.imageType
                        faceText = selection.label
                    }
                }
            }.onFailure {
                Log.w(
                    TAG,
                    "create: small-face build failed epId=${selection.epId} eId=${selection.eId}",
                    it,
                )
            }.getOrNull()
            post(onResult, element)
            return
        }
        val service = KernelBridge.getMsgService()
        if (service == null) {
            Log.w(
                TAG,
                "create: kernel msg service unavailable epId=${selection.epId} eId=${selection.eId}",
            )
            post(onResult, null)
            return
        }
        Log.d(
            TAG,
            "create: begin epId=${selection.epId} eId=${selection.eId} " +
                "json=${selection.jsonPath} service=$service",
        )
        val delivered = AtomicBoolean(false)
        val timeout = Runnable {
            if (delivered.compareAndSet(false, true)) {
                Log.w(TAG, "create: timeout epId=${selection.epId} eId=${selection.eId}")
                onResult(null)
            }
        }
        mainHandler.postDelayed(timeout, 15_000L)
        fun finish(element: MsgElement?) {
            if (delivered.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                Log.d(
                    TAG,
                    "create: finish epId=${selection.epId} eId=${selection.eId} " +
                        "success=${element != null}",
                )
                onResult(element)
            }
        }

        fun build(jsonPath: String) {
            val message = runCatching {
                parseMarketFaceMessage(selection.epId, selection.eId, jsonPath)
            }.onFailure {
                Log.w(
                    TAG,
                    "create: local market-face parse failed epId=${selection.epId} " +
                        "eId=${selection.eId} path=$jsonPath",
                    it,
                )
            }.getOrNull()
            if (message == null) {
                Log.w(TAG, "create: MarkFaceMessage null epId=${selection.epId} eId=${selection.eId}")
                finish(null)
                return
            }
            Log.d(
                TAG,
                "create: message epId=${message.f} eId=${selection.eId} " +
                    "type=${message.g} size=${message.j}x${message.k} name=${message.b}",
            )
            runCatching {
                service.getMarketEmoticonEncryptKeys(
                    selection.epId,
                    arrayListOf(selection.eId),
                    object : IGetMarketEmoticonEncryptKeysCallback {
                        override fun onGetMarketEmoticonEncryptKeysCallback(
                            code: Int,
                            messageText: String?,
                            keys: HashMap<String, String>?,
                        ) {
                            Log.d(
                                TAG,
                                "create: keys callback epId=${selection.epId} eId=${selection.eId} " +
                                    "code=$code size=${keys?.size ?: -1} " +
                                    "names=${keys?.keys?.joinToString()}",
                            )
                            if (code != 0 || keys.isNullOrEmpty()) {
                                Log.w(
                                    TAG,
                                    "create: key request rejected epId=${selection.epId} " +
                                        "eId=${selection.eId} code=$code message=$messageText",
                                )
                                finish(null)
                                return
                            }
                            val encryptKey = keys.entries
                                .firstOrNull { it.key.equals(selection.eId, ignoreCase = true) }
                                ?.value
                                ?.takeIf(String::isNotBlank)
                                ?: keys.values.firstOrNull { it.isNotBlank() }
                            if (encryptKey == null) {
                                Log.w(
                                    TAG,
                                    "create: exact market-face key missing epId=${selection.epId} " +
                                        "eId=${selection.eId}",
                                )
                                finish(null)
                                return
                            }
                            val element = runCatching {
                                message.h = encryptKey.toByteArray(Charsets.UTF_8)
                                val candidate = QRoute.api(IMarketFaceApi::class.java)
                                    .createMarketFaceElement(message)
                                Log.d(
                                    TAG,
                                    "create: local candidate epId=${candidate.emojiPackageId} " +
                                        "eId=${candidate.emojiId} keyLength=${candidate.key.length} " +
                                        "itemType=${candidate.itemType} faceInfo=${candidate.faceInfo} " +
                                        "subType=${candidate.subType} mediaType=${candidate.mediaType} " +
                                        "size=${candidate.imageWidth}x${candidate.imageHeight}",
                                )
                                if (!candidate.isValidMarketFace(selection)) {
                                    error("本地系列表情元素字段不完整")
                                }
                                MsgElement().apply {
                                    elementType = 11
                                    elementId = 0
                                    marketFaceElement = candidate
                                }
                            }.onFailure {
                                Log.w(
                                    TAG,
                                    "create: local candidate failed epId=${selection.epId} " +
                                        "eId=${selection.eId}",
                                    it,
                                )
                            }.getOrNull()
                            finish(element)
                        }
                    },
                )
            }.onFailure {
                Log.w(TAG, "create: key request failed epId=${selection.epId} eId=${selection.eId}", it)
                finish(null)
            }
        }

        fun buildCachedPathOrFinish() {
            val cachedPath = sequenceOf(
                selection.jsonPath,
                marketJsonPaths[selection.epId],
            ).filterNotNull()
                .map(String::trim)
                .firstOrNull { path -> File(path).isFile }
            if (cachedPath != null) {
                Log.d(TAG, "create: using cached json epId=${selection.epId} path=$cachedPath")
                build(cachedPath)
            } else {
                Log.w(TAG, "create: no json path epId=${selection.epId} eId=${selection.eId}")
                finish(null)
            }
        }

        runCatching {
            service.fetchMarketEmotionJsonFile(selection.epId, object : IOperateCallback {
                override fun onResult(code: Int, jsonPath: String?) {
                    val path = jsonPath?.trim().orEmpty()
                    Log.d(
                        TAG,
                        "create: fetch result epId=${selection.epId} eId=${selection.eId} " +
                            "code=$code path=$path isFile=${path.isNotBlank() && File(path).isFile}",
                    )
                    if (code == 0 && path.isNotBlank() && File(path).isFile) {
                        marketJsonPaths[selection.epId] = path
                        build(path)
                    } else {
                        buildCachedPathOrFinish()
                    }
                }
            })
        }.onFailure {
            Log.w(
                TAG,
                "create: fetch invocation failed epId=${selection.epId} eId=${selection.eId}",
                it,
            )
            buildCachedPathOrFinish()
        }
    }

    fun loadMarketFacePreview(
        selection: Selection.MarketFace,
        onResult: (MarketFacePreview) -> Unit,
    ) {
        if (selection.isSmallFace) {
            val face = runCatching {
                val faceId = selection.eId.toIntOrNull()
                    ?: error("small-face id is not numeric: ${selection.eId}")
                val packedIndex = QRoute.api(IEmojiSpanService::class.java)
                    .combineEmoIndex(selection.epId, faceId)
                Selection.SystemFace(
                    faceType = 4,
                    faceIndex = packedIndex,
                    label = selection.label,
                    imageType = selection.imageType,
                    serverId = packedIndex,
                )
            }.getOrNull()
            if (face == null) {
                post(onResult, MarketFacePreview(path = null, drawable = null))
            } else {
                loadSystemFaceDrawable(face) { drawable ->
                    onResult(MarketFacePreview(path = null, drawable = drawable))
                }
            }
            return
        }
        val cacheKey = marketFaceCacheKey(selection.epId, selection.eId)
        findMarketFacePreviewPath(selection)?.let { path ->
            if (isBitmapPreviewFile(path)) {
                marketPreviewPathCache[cacheKey] = path
                post(onResult, MarketFacePreview(path = path, drawable = null))
                return
            }
        }

        createMarketFaceElement(selection) { element ->
            val marketFace = element?.marketFaceElement
            if (marketFace == null) {
                post(onResult, MarketFacePreview(path = null, drawable = null))
                return@createMarketFaceElement
            }

            val latestDrawable = java.util.concurrent.atomic.AtomicReference<Drawable?>(null)
            Thread {
                val path = waitForMarketFacePreviewPath(selection)
                if (path != null) marketPreviewPathCache[cacheKey] = path
                post(
                    onResult,
                    MarketFacePreview(path = path, drawable = latestDrawable.get()),
                )
            }.apply {
                isDaemon = true
                start()
            }
            loadMarketFaceDrawable(selection, marketFace) { drawable ->
                latestDrawable.set(drawable)
                post(
                    onResult,
                    MarketFacePreview(
                        path = marketPreviewPathCache[cacheKey],
                        drawable = drawable,
                    ),
                )
            }
        }
    }

    fun resolveMarketFacePreview(
        selection: Selection.MarketFace,
        onResult: (String?) -> Unit,
    ) {
        loadMarketFacePreview(selection) { preview ->
            if (preview.path != null || preview.drawable == null) {
                onResult(preview.path)
            }
        }
    }

    private fun parseMarketFaces(
        context: Context,
        epId: Int,
        jsonPath: String,
        isSmallFace: Boolean,
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
                        isSmallFace = isSmallFace,
                        imageType = if (isSmallFace) {
                            image.optInt("imageType", image.optInt("type", 1)).coerceAtLeast(1)
                        } else {
                            1
                        },
                    ),
                )
            }
        }
    }

    private fun parseMarketFaceMessage(
        epId: Int,
        eId: String,
        jsonPath: String,
    ): MarkFaceMessage? {
        val raw = File(jsonPath).takeIf(File::isFile)?.readText() ?: return null
        val root = JSONObject(raw)
        val sbufId = runCatching { HexUtil.c(eId) }.getOrNull() ?: return null
        val packageType = root.optInt("type", 0)
        val image = root.optJSONArray("imgs")?.let { images ->
            (0 until images.length())
                .asSequence()
                .mapNotNull(images::optJSONObject)
                .firstOrNull { it.optString("id") == eId }
        }
        val packageName = root.optString("name").takeIf(String::isNotBlank)
        val faceName = image?.optString("name")?.takeIf(String::isNotBlank)
            ?: packageName
            ?: "大表情 $eId"
        val supportSizes = parseMarketFaceSupportSizes(root.optJSONArray("supportSize"))
        return MarkFaceMessage().apply {
            e = sbufId
            f = epId
            g = if (packageType == 1 || packageType == 4) 3 else packageType
            b = faceName
            i = if (root.optString("ringtype") == "1") 1 else 0
            j = image?.optInt("wWidthInPhone", 200)?.takeIf { it > 0 } ?: 200
            k = image?.optInt("wHeightInPhone", 200)?.takeIf { it > 0 } ?: 200
            u = root.optInt("isApng", 0) == 2
            A = supportSizes
            B = ArrayList(supportSizes)
            l = ByteArray(0)
            m = ByteArray(0)
            x = parseMarketFaceVoiceHeights(root.optJSONArray("voiceItemHeightArr"))
            o = ""
            p = ""
            y = ""
            z = ""
        }
    }

    private fun parseMarketFaceSupportSizes(
        array: org.json.JSONArray?,
    ): ArrayList<MarketFaceSupportSize> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val width = item.optInt("width", item.optInt("w", 0))
            val height = item.optInt("height", item.optInt("h", 0))
            if (width > 0 && height > 0) {
                add(MarketFaceSupportSize(width, height))
            }
        }
    }.let { ArrayList(it) }

    private fun parseMarketFaceVoiceHeights(
        array: org.json.JSONArray?,
    ): ArrayList<Int> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val height = array.optInt(index, 0)
            if (height > 0) add(height)
        }
    }.let { ArrayList(it) }

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
        val roots = marketFaceRoots(context, epId)
        val staticCandidates = buildList<String?> {
            add(safeStoragePath { MarketFaceStorageUtil.a(epIdText, eId) })
            add(safeStoragePath { MarketFaceStorageUtil.e(epIdText, eId) })
            roots.forEach { root ->
                add(File(root, "${eId}_aio.png").absolutePath)
                add(File(root, "${eId}_thu.png").absolutePath)
            }
        }
        val dynamicCandidates = buildList<String?> {
            add(safeStoragePath { MarketFaceStorageUtil.c(epIdText, eId) })
            add(safeStoragePath { MarketFaceStorageUtil.b(epIdText, eId) })
            roots.forEach { root ->
                add(File(root, eId).absolutePath)
                add(File(root, "${eId}_apng").absolutePath)
            }
        }
        return MarketFacePaths(
            staticPath = preferredPath(*staticCandidates.toTypedArray()),
            dynamicPath = preferredPath(*dynamicCandidates.toTypedArray()),
        )
    }

    private fun marketFaceStorageCandidates(
        context: Context?,
        epId: Int,
        eId: String,
    ): List<String> {
        val epIdText = epId.toString()
        val roots = marketFaceRoots(context, epId)
        return buildList {
            safeStoragePath { MarketFaceStorageUtil.a(epIdText, eId) }?.let(::add)
            safeStoragePath { MarketFaceStorageUtil.e(epIdText, eId) }?.let(::add)
            roots.forEach { root ->
                add(File(root, "${eId}_aio.png").absolutePath)
                add(File(root, "${eId}_thu.png").absolutePath)
            }
            safeStoragePath { MarketFaceStorageUtil.c(epIdText, eId) }?.let(::add)
            safeStoragePath { MarketFaceStorageUtil.b(epIdText, eId) }?.let(::add)
            roots.forEach { root ->
                add(File(root, eId).absolutePath)
                add(File(root, "${eId}_apng").absolutePath)
            }
        }.filter(String::isNotBlank).distinct()
    }

    private fun marketFaceRoots(
        context: Context?,
        epId: Int,
    ): List<File> {
        val officialRoot = runCatching { AppConstants.q }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
        val explicitOfficialRoot = File(
            "/sdcard/Android/data/com.tencent.mobileqq/Tencent/MobileQQ/.emotionsm",
        )
        val packageRoot = context?.getExternalFilesDir(null)?.parentFile?.let {
            File(it, "Tencent/MobileQQ/.emotionsm")
        }
        return listOfNotNull(officialRoot, explicitOfficialRoot, packageRoot)
            .map { File(it, epId.toString()) }
            .distinctBy(File::getAbsolutePath)
    }

    private inline fun safeStoragePath(block: () -> String): String? = runCatching {
        block().takeIf(String::isNotBlank)
    }.getOrNull()

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
        return buildList {
            add(selection.staticPath)
            add(selection.dynamicPath)
            addAll(marketFaceStorageCandidates(null, selection.epId, selection.eId))
        }.filterNotNull().filter(String::isNotBlank).distinct()
    }

    private fun preferredPath(vararg paths: String?): String? {
        val candidates = paths.filterNotNull().filter(String::isNotBlank).distinct()
        return candidates.firstOrNull(::isReadableFile) ?: candidates.firstOrNull()
    }

    private fun isReadableFile(path: String): Boolean = runCatching {
        File(path).isFile && File(path).length() > 0L
    }.getOrDefault(false)

    private fun isBitmapPreviewFile(path: String): Boolean = runCatching {
        if (!isReadableFile(path)) return@runCatching false
        BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, this)
            outWidth > 0 && outHeight > 0
        }
    }.getOrDefault(false)

    private fun marketFaceCacheKey(epId: Int, eId: String): String = "$epId:$eId"

    private fun MarketFaceElement.isValidMarketFace(selection: Selection.MarketFace): Boolean {
        val face = this
        return face.emojiPackageId == selection.epId &&
            face.emojiId.equals(selection.eId, ignoreCase = true) &&
            face.key.isNotBlank()
    }

    private fun requestMarketFaceDownload(drawable: Drawable?) {
        runCatching {
            val target = drawable ?: return@runCatching
            target.javaClass.methods.firstOrNull {
                it.parameterTypes.isEmpty() && it.name == "downloadImediatly"
            }?.invoke(target)
        }
    }

    private fun TabEmojiInfo.toMarketPack(isSmallFace: Boolean = false): MarketPack = MarketPack(
        epId = epId,
        name = tabName?.takeIf(String::isNotBlank) ?: "表情包 $epId",
        tabType = tabType,
        isSmallFace = isSmallFace,
    )

    private fun <T> post(callback: (T) -> Unit, value: T) {
        mainHandler.post { callback(value) }
    }

}
