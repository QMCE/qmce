package rj.qmce.lite.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.watch.qzone_impl.event.Event
import com.tencent.watch.qzone_impl.event.EventCenter
import com.tencent.watch.qzone_impl.event.EventSource
import com.tencent.watch.qzone_impl.event.IObserver
import com.tencent.watch.qzone_impl.common.QZoneBusinessLooper
import com.tencent.watch.qzone_impl.common.task.QZoneTask
import com.tencent.watch.qzone_impl.feed.ServiceCallbackWrapper
import com.tencent.watch.qzone_impl.feed.QZoneFeedService
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.protocol.request.QZoneAddCommentRequest
import com.tencent.watch.qzone_impl.publish.business.model.ImageInfo
import com.tencent.watch.qzone_impl.publish.business.model.MediaWrapper
import com.tencent.watch.qzone_impl.publish.business.model.QzoneShuoShuoParams
import com.tencent.watch.qzone_impl.publish.business.publishqueue.QZonePublishQueue
import com.tencent.watch.qzone_impl.publish.business.task.QZoneLikeFeedTask
import com.tencent.watch.qzone_impl.publish.business.task.QZoneUploadShuoShuoTask
import com.tencent.watch.qzone_impl.service.QZoneWriteOperationService
import com.tencent.watch.qzone_impl.utils.UinUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import com.tencent.mobileqq.activity.photo.LocalMediaInfo
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

class QZoneViewModel : ViewModel() {

    companion object {
        private const val TAG = "QMCE-QZone"
        private const val AVATAR_BASE = "https://thirdqq.qlogo.cn/headimg_dl?spec=100&dst_uin="
    }

    data class FeedItem(
        val feedId: String,
        val uin: String,
        val nick: String,
        val content: String,
        val forward: ForwardInfo? = null,
        val time: Long,
        val displayTime: String = "",
        val picUrls: List<String>,
        val videoUrl: String? = null,
        val likeCount: Int,
        val commentCount: Int,
        val isLiked: Boolean,
        val comments: List<FeedComment> = emptyList(),
    )

    data class ForwardInfo(
        val author: String,
        val content: String,
        val isUnavailable: Boolean,
    )

    data class FeedComment(
        val id: String,
        val author: String,
        val text: String,
        val replies: List<FeedReply> = emptyList(),
    )

    data class FeedReply(
        val author: String,
        val text: String,
    )

    private val _feeds = MutableStateFlow<List<FeedItem>>(emptyList())
    val feeds: StateFlow<List<FeedItem>> = _feeds

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _operationStatus = MutableStateFlow("")
    val operationStatus: StateFlow<String> = _operationStatus

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private var runtime: AppRuntime? = null
    private var loaded = false
    private val feedLoadLock = Any()
    private var feedLoadGeneration = 0L
    private var activeFeedLoad: Job? = null
    private var feedObserver: IObserver? = null
    private var observedFeedService: QZoneFeedService? = null
    private var svcRef: QZoneFeedService? = null
    private var loadingMore = false
    private var lastLoadMoreTime = 0L
    private var noMoreData = false
    private val _loadingMore = MutableStateFlow(false)
    val loadingMoreFlow: StateFlow<Boolean> = _loadingMore
    private var loadMoreStartTime = 0L
    private val feedDataById = HashMap<String, BusinessFeedData>()
    private var lastSubmittedFingerprint = ""

    fun init(rt: AppRuntime?) {
        runtime = rt
    }

    fun isLoadingMore(): Boolean = loadingMore

    fun hasMoreData(): Boolean = !noMoreData

    fun resetNoMoreData() { noMoreData = false }

    fun publishText(text: String) {
        val content = text.trim()
        if (content.isBlank()) {
            _operationStatus.value = "动态内容不能为空"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在发表动态…"
            runCatching {
                val params = QzoneShuoShuoParams().apply { a = content }
                QZoneWriteOperationService.h().i(params)
            }.onSuccess {
                _operationStatus.value = "动态已发送"
                delay(1200)
                loadFeeds(forceRefresh = true)
            }.onFailure { error ->
                Log.e(TAG, "publishText failed", error)
                _operationStatus.value = "发表失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun publishImages(context: Context, text: String, uris: List<Uri>) {
        val content = text.trim()
        if (content.isBlank() && uris.isEmpty()) {
            _operationStatus.value = "动态内容不能为空"
            return
        }
        if (uris.isEmpty()) {
            publishText(content)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在准备图片…"
            runCatching {
                val files = uris.mapIndexed { index, uri -> copyUriToQZoneFile(context, uri, index) }
                val params = QzoneShuoShuoParams().apply {
                    a = content
                    b = files.map { it.absolutePath }
                    c = ArrayList(files.map { toLocalMediaInfo(it) })
                    e = ArrayList(files.map {
                        MediaWrapper(ImageInfo(it.absolutePath).apply { mDescription = content })
                    })
                }
                val task = QZoneUploadShuoShuoTask(6, 1, params).apply {
                    uploadEntrance = 0
                    refer = null
                }
                QZonePublishQueue.e().b(task)
            }.onSuccess {
                _operationStatus.value = "动态已发送"
                delay(2500)
                loadFeeds(forceRefresh = true)
            }.onFailure { error ->
                Log.e(TAG, "publishImages failed", error)
                _operationStatus.value = "图片动态失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun comment(feedId: String, text: String) {
        val content = text.trim()
        if (content.isBlank()) {
            _operationStatus.value = "评论内容不能为空"
            return
        }
        val data = synchronized(feedDataById) { feedDataById[feedId] }
        if (data == null) {
            _operationStatus.value = "动态数据尚未准备好，请稍后重试"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在发送评论…"
            runCatching {
                val feedCommInfo = data.feedCommInfo
                val ownerUin = runCatching { data.user?.uin }.getOrNull() ?: data.owner_uin
                val cellId = runCatching { data.idInfo?.cellId }.getOrNull().orEmpty()
                val busiParam = runCatching { data.operationInfo?.busiParam }.getOrNull()
                val request = QZoneAddCommentRequest(
                    feedCommInfo.appid,
                    ownerUin,
                    cellId,
                    content,
                    null,
                    false,
                    busiParam,
                )
                val task = QZoneTask(request, null, QZoneWriteOperationService.h(), 0)
                task.addParameter("ugckey", feedCommInfo.ugckey)
                task.addParameter("feedkey", feedCommInfo.feedskey)
                task.addParameter("uniKey", UUID.randomUUID().toString())
                task.addParameter("clickScene", 0)
                QZoneBusinessLooper.a().c(task)
            }.onSuccess {
                _feeds.value = _feeds.value.map { item ->
                    if (item.feedId == feedId) {
                        item.copy(
                            commentCount = item.commentCount + 1,
                            comments = item.comments + FeedComment(
                                id = "local:${UUID.randomUUID()}",
                                author = "我",
                                text = content,
                            ),
                        )
                    } else item
                }
                _operationStatus.value = "评论已发送"
                delay(1200)
                loadFeeds(forceRefresh = true)
            }.onFailure { error ->
                Log.e(TAG, "comment failed", error)
                _operationStatus.value = "评论失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun toggleLike(feedId: String) {
        val data = synchronized(feedDataById) { feedDataById[feedId] }
        val like = data?.likeInfo
        if (data == null || like == null) {
            _operationStatus.value = "动态数据尚未准备好，请稍后重试"
            return
        }
        val oldLiked = like.isLiked
        val newLiked = !oldLiked
        val oldCount = like.likeNum
        like.isLiked = newLiked
        like.likeNum = (oldCount + if (newLiked) 1 else -1).coerceAtLeast(0)
        _feeds.value = _feeds.value.map { item ->
            if (item.feedId == feedId) item.copy(isLiked = newLiked, likeCount = like.likeNum) else item
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = if (newLiked) "正在点赞…" else "正在取消点赞…"
            runCatching {
                val feedCommInfo = data.feedCommInfo
                val params = QZoneWriteOperationService.LikeParams().apply {
                    a = feedCommInfo.ugckey
                    b = feedCommInfo.curlikekey
                    c = feedCommInfo.orglikekey
                    d = newLiked
                    e = feedCommInfo.appid
                    f = data.operationInfo?.busiParam?.let { HashMap(it) }
                    g = -1
                    h = data
                    i = data.feedType
                }
                val task = QZoneLikeFeedTask(null, params, 1)
                runCatching { task.javaClass.getField("clientKey").set(task, feedCommInfo.clientkey) }
                QZonePublishQueue.e().b(task)
            }.onSuccess {
                _operationStatus.value = if (newLiked) "已点赞" else "已取消点赞"
            }.onFailure { error ->
                like.isLiked = oldLiked
                like.likeNum = oldCount
                _feeds.value = _feeds.value.map { item ->
                    if (item.feedId == feedId) item.copy(isLiked = oldLiked, likeCount = oldCount) else item
                }
                Log.e(TAG, "toggleLike failed", error)
                _operationStatus.value = "点赞失败：${error.message ?: "未知错误"}"
            }
        }
    }

    private fun copyUriToQZoneFile(context: Context, uri: Uri, index: Int): File {
        val directory = File(
            context.getExternalFilesDir("qzone_photo") ?: context.cacheDir,
            "publish",
        ).apply { mkdirs() }
        val target = File(directory, "qzone_${System.currentTimeMillis()}_${index}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取图片")
        if (!target.isFile || target.length() == 0L) error("图片为空")
        return target
    }

    private fun toLocalMediaInfo(file: File): LocalMediaInfo = LocalMediaInfo().apply {
        c = file.absolutePath
        C = 0
        runCatching {
            BitmapFactory.Options().also { options ->
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.absolutePath, options)
                E = options.outWidth
                F = options.outHeight
            }
        }
    }

    fun loadMore() {
        if (loadingMore || noMoreData) return
        if (System.currentTimeMillis() - lastLoadMoreTime < 3000) return
        val svc = svcRef ?: return
        loadingMore = true
        _loadingMore.value = true
        lastLoadMoreTime = System.currentTimeMillis()
        loadMoreStartTime = System.currentTimeMillis()
        Log.d(TAG, "loadMore via svc.n()")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prevSize = _feeds.value.size
                svc.n(Handler(Looper.getMainLooper()))
                var fresh: List<BusinessFeedData>? = null
                var lastFingerprint = feedFingerprint(svc.h?.n().orEmpty())
                for (attempt in 0 until 30) {
                    delay(400)
                    val candidate = svc.h?.n()
                    if (!candidate.isNullOrEmpty()) {
                        fresh = candidate
                        val fingerprint = feedFingerprint(candidate)
                        if (fingerprint != lastFingerprint || candidate.size > prevSize) {
                            processFeeds(candidate)
                            lastFingerprint = fingerprint
                            if (candidate.size > prevSize) break
                        }
                    }
                }
                val newSize = fresh?.size ?: 0
                val elapsed = System.currentTimeMillis() - loadMoreStartTime
                if (elapsed < 500) Thread.sleep(500 - elapsed)
                loadingMore = false
                _loadingMore.value = false
                Log.d(TAG, "loadMore done: prev=$prevSize, now=$newSize")
                if (!fresh.isNullOrEmpty() && newSize > prevSize) {
                    noMoreData = false
                    Log.d(TAG, "loadMore received more feeds: prev=$prevSize, now=$newSize")
                } else {
                    noMoreData = false
                    Log.d(TAG, "loadMore produced no new page; keeping pagination retryable")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "loadMore error", e)
            } finally {
                loadingMore = false
                _loadingMore.value = false
            }
        }
    }

    fun loadFeeds(forceRefresh: Boolean = false) {
        val generation: Long
        synchronized(feedLoadLock) {
            if (loaded && !forceRefresh) return
            activeFeedLoad?.cancel()
            generation = ++feedLoadGeneration
            loaded = true
            activeFeedLoad = null
        }
        noMoreData = false
        _loading.value = true
        _statusText.value = "加载空间动态..."

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val svc = QZoneFeedService.h()
                svcRef = svc
                if (svc == null) {
                    _statusText.value = "QZoneFeedService 不可用"
                    finishFeedLoad(generation, false)
                    return@launch
                }

                // 初始化 UIN
                val uin = runCatching { UinUtils.b() }.getOrDefault(0L)
                svc.m(uin, uin)
                Log.d(TAG, "QZoneFeedService init OK, uin=$uin")

                // 注册事件监听
                registerFeedObserver(svc)

                // 先检查缓存
                val feedMgr = svc.h
                if (feedMgr != null) {
                    val cached = feedMgr.n()
                    if (!cached.isNullOrEmpty()) {
                        Log.d(TAG, "got ${cached.size} cached feeds")
                        processFeeds(cached, finishLoading = false)
                    }

                    // 触发网络加载
                    val wrapper = ServiceCallbackWrapper()
                    wrapper.a = WeakReference(Handler(Looper.getMainLooper()))
                    feedMgr.b(0, wrapper, false)
                    Log.d(TAG, "triggered feed load")

                    var lastFingerprint = feedFingerprint(cached.orEmpty())
                    repeat(30) {
                        delay(400)
                        if (!isCurrentFeedLoad(generation)) return@launch
                        val fresh = feedMgr.n()
                        if (!fresh.isNullOrEmpty()) {
                            val fingerprint = feedFingerprint(fresh)
                            if (fingerprint != lastFingerprint) {
                                processFeeds(fresh)
                                lastFingerprint = fingerprint
                            }
                        }
                    }
                    finishFeedLoad(generation, true)
                } else {
                    Log.w(TAG, "IFeedManager is null")
                    _statusText.value = "FeedManager 不可用"
                    finishFeedLoad(generation, false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "loadFeeds error", e)
                _statusText.value = "加载失败: ${e.message}"
                finishFeedLoad(generation, false)
            } finally {
                synchronized(feedLoadLock) {
                    if (feedLoadGeneration == generation) activeFeedLoad = null
                }
            }
        }
        synchronized(feedLoadLock) {
            if (feedLoadGeneration == generation) activeFeedLoad = job
        }
    }

    private fun registerFeedObserver(svc: QZoneFeedService) {
        if (feedObserver != null && observedFeedService === svc) return
        try {
            feedObserver?.let { oldObserver ->
                runCatching { EventCenter.b().g(oldObserver) }
            }
            val observer = object : IObserver.main {
                override fun s(event: Event) {
                    Log.d(TAG, "feed event: type=${event.a}")
                    if (event.a == 1 || event.a == 4) {
                        val feedMgr = svc.h ?: return
                        val list = feedMgr.n()
                        if (!list.isNullOrEmpty()) processFeeds(list)
                    }
                }
            }
            feedObserver = observer
            observedFeedService = svc
            val ec = EventCenter.b()
            val src = EventSource("Feed", null)
            ec.a(observer, 0, src, 1, 4)
            Log.d(TAG, "registered feed observer")
        } catch (e: Exception) {
            Log.e(TAG, "registerFeedObserver error", e)
        }
    }

    private fun processFeeds(list: List<BusinessFeedData>, finishLoading: Boolean = true) {
        if (list.isEmpty()) return
        val currentFingerprint = feedFingerprint(list)
        if (currentFingerprint == lastSubmittedFingerprint) {
            if (finishLoading) _loading.value = false
            return
        }
        synchronized(feedDataById) {
            list.forEach { data ->
                val id = runCatching { data.cellIdInfo?.cellId }.getOrNull().orEmpty()
                if (id.isNotBlank()) feedDataById[id] = data
            }
        }
        val items = list.mapNotNull { data ->
            try {
                val user = data.cellUserInfo?.user
                val uin = user?.uin?.toString()
                    ?: data.cellFeedCommInfo?.ugckey?.split("_")?.firstOrNull()
                    ?: "0"
                val nick = user?.nickName ?: "QQ用户"
                val summaryObj = runCatching { data.getCellSummaryV2() }.getOrNull()
                val summary = summaryObj?.summary ?: ""
                val title = runCatching { data.cellTitleInfo?.title }.getOrNull() ?: ""
                val content = summary.ifEmpty { title }
                val original = runCatching { data.originalInfo }.getOrNull()
                val forward = if (original != null || data.isForwardFeedData) {
                    val originalSummary = runCatching { original?.cellSummaryV2?.summary }.getOrNull().orEmpty()
                    val originalTitle = runCatching { original?.cellTitleInfo?.title }.getOrNull().orEmpty()
                    ForwardInfo(
                        author = runCatching { original?.user?.nickName }.getOrNull().orEmpty(),
                        content = originalSummary.ifEmpty { originalTitle },
                        isUnavailable = original == null || data.isOriginalEmpty,
                    )
                } else {
                    null
                }
                // The Watch client renders a repost's media from originalInfo, not the reposter's shell.
                val mediaSource = original ?: data
                val picsRaw = mediaSource.cellPictureInfo?.pics ?: emptyList()
                val pics = picsRaw.mapNotNull { pic ->
                    pic.currentUrl?.url
                        ?: pic.bigUrl?.url
                        ?: pic.originUrl?.url
                }
                val videoUrl = mediaSource.cellVideoInfo?.videoUrl?.url?.takeIf { it.isNotBlank() }
                // debug: dump all fields for ALL feeds
                if (list.indexOf(data) < list.size) {
                    Log.d(TAG, "feed[${list.indexOf(data)}] feedType=${data.feedType} owner_uin=${data.owner_uin}")
                    Log.d(TAG, "  nick=$nick, content='$content', summary='$summary', title='$title'")
                    Log.d(TAG, "  pics=${pics.size}")
                    if (data.feedType != 4097 || content.isNotBlank()) {
                        Log.d(TAG, "  DEEP PROBE: ${probeStrings(data)}")
                    }
                }
                val likes = data.cellLikeInfo
                val comments = data.cellCommentInfo
                FeedItem(
                    feedId = data.cellIdInfo?.cellId ?: "",
                    uin = uin,
                    nick = nick,
                    content = content,
                    forward = forward,
                    time = data.cellFeedCommInfo?.time ?: 0L,
                    displayTime = data.cellFeedCommInfo?.cacheTimeString ?: "",
                    picUrls = pics,
                    videoUrl = videoUrl,
                    likeCount = likes?.likeNum ?: 0,
                    commentCount = comments?.c?.size ?: 0,
                    isLiked = likes?.isLiked ?: false,
                    comments = comments?.c.orEmpty().map { comment ->
                        FeedComment(
                            id = comment.commentid.orEmpty(),
                            author = comment.user?.nickName?.takeIf { it.isNotBlank() } ?: "QQ用户",
                            text = comment.comment.orEmpty(),
                            replies = comment.replies.orEmpty().map { reply ->
                                FeedReply(
                                    author = reply.user?.nickName?.takeIf { it.isNotBlank() } ?: "QQ用户",
                                    text = reply.content.orEmpty(),
                                )
                            },
                        )
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "processFeed error", e)
                null
            }
        }
        val distinctItems = items.distinctBy { it.feedId.ifBlank { "${it.uin}:${it.time}:${it.content}" } }
        if (distinctItems.isNotEmpty()) {
            lastSubmittedFingerprint = currentFingerprint
            _feeds.value = distinctItems
        }
        _statusText.value = if (distinctItems.isEmpty()) "暂无动态" else ""
        if (finishLoading) _loading.value = false
        Log.d(TAG, "processed ${distinctItems.size} feeds")
    }

    private fun finishFeedLoad(generation: Long, success: Boolean) {
        if (!isCurrentFeedLoad(generation)) return
        _loading.value = false
        synchronized(feedLoadLock) {
            if (feedLoadGeneration == generation) loaded = success
        }
        if (!success && _feeds.value.isEmpty()) _statusText.value = "加载失败，请重试"
    }

    private fun isCurrentFeedLoad(generation: Long): Boolean = synchronized(feedLoadLock) {
        feedLoadGeneration == generation
    }

    private fun feedFingerprint(list: List<BusinessFeedData>): String = list.joinToString("|") { data ->
        val id = runCatching { data.cellIdInfo?.cellId }.getOrNull().orEmpty()
        val time = runCatching { data.cellFeedCommInfo?.time }.getOrNull() ?: 0L
        val summary = runCatching { data.getCellSummaryV2()?.summary }.getOrNull().orEmpty()
        val title = runCatching { data.cellTitleInfo?.title }.getOrNull().orEmpty()
        val nick = runCatching { data.cellUserInfo?.user?.nickName }.getOrNull().orEmpty()
        val likes = runCatching { data.cellLikeInfo?.likeNum }.getOrNull() ?: 0
        val comments = runCatching {
            data.cellCommentInfo?.c.orEmpty().joinToString(",") { comment ->
                "${comment.commentid}:${comment.user?.uin}:${comment.comment}:${comment.replies?.size ?: 0}"
            }
        }.getOrNull().orEmpty()
        val pictures = runCatching {
            data.cellPictureInfo?.pics.orEmpty().joinToString(",") {
                it.currentUrl?.url ?: it.bigUrl?.url ?: it.originUrl?.url.orEmpty()
            }
        }.getOrNull().orEmpty()
        val video = runCatching { data.cellVideoInfo?.videoUrl?.url }.getOrNull().orEmpty()
        val original = runCatching {
            data.originalInfo?.let { originalData ->
                "${originalData.cellUserInfo?.user?.nickName}:${originalData.getCellSummaryV2()?.summary}:${originalData.cellTitleInfo?.title}"
            }
        }.getOrNull().orEmpty()
        "$id:$time:$nick:$summary:$title:$likes:$comments:$pictures:$video:$original"
    }

    private fun probeStrings(obj: Any?, depth: Int = 0): String {
        if (obj == null || depth > 2) return ""
        val results = mutableListOf<String>()
        try {
            for (field in obj.javaClass.declaredFields) {
                if (field.name == "Companion" || field.name == "INSTANCE" || field.name.contains("$")) continue
                field.isAccessible = true
                val v = runCatching { field.get(obj) }.getOrNull() ?: continue
                when (v) {
                    is String -> if (v.isNotBlank() && v.length > 2) results.add("${field.name}='${v.take(100)}'")
                    is Number -> if (v.toLong() != 0L) results.add("${field.name}=$v")
                    is Collection<*> -> {
                        if (v.isNotEmpty()) {
                            results.add("${field.name}[${v.size}]")
                            v.forEachIndexed { i, item ->
                                if (item != null && i < 2) {
                                    val sub = probeStrings(item, depth + 1)
                                    if (sub.isNotBlank()) results.add("  [$i]: $sub")
                                }
                            }
                        }
                    }
                    else -> {
                        if (depth < 2) {
                            val sub = probeStrings(v, depth + 1)
                            if (sub.isNotBlank()) results.add("${field.name}{$sub}")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return results.joinToString("; ")
    }

    fun avatarUrl(uin: String): String = "$AVATAR_BASE$uin"

    override fun onCleared() {
        super.onCleared()
        feedObserver?.let {
            runCatching { EventCenter.b().g(it) }
        }
    }
}
