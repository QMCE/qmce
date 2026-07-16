package rj.qmce.lite.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import rj.qmce.lite.data.qzone.QZoneFeedRepository
import rj.qmce.lite.data.qzone.QZoneWriteRepository
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
    private var loadingMore = false
    private var lastLoadMoreTime = 0L
    private var noMoreData = false
    private val _loadingMore = MutableStateFlow(false)
    val loadingMoreFlow: StateFlow<Boolean> = _loadingMore
    private var loadMoreStartTime = 0L
    private val feedDataById = HashMap<String, BusinessFeedData>()
    private var lastSubmittedFingerprint = ""
    private val qZoneFeedRepository = QZoneFeedRepository()
    private val qZoneWriteRepository = QZoneWriteRepository()

    fun init(rt: AppRuntime?) {
        runtime = rt
    }

    fun isLoadingMore(): Boolean = loadingMore

    fun hasMoreData(): Boolean = !noMoreData

    fun resetNoMoreData() {
        noMoreData = false
    }

    fun publishText(text: String) {
        val content = text.trim()
        if (content.isBlank()) {
            _operationStatus.value = "动态内容不能为空"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在发表动态…"
            qZoneWriteRepository.publishText(content).onSuccess {
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
            qZoneWriteRepository.publishImages(context, content, uris).onSuccess {
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
            qZoneWriteRepository.comment(data, content).onSuccess {
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
            if (item.feedId == feedId) item.copy(
                isLiked = newLiked,
                likeCount = like.likeNum
            ) else item
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = if (newLiked) "正在点赞…" else "正在取消点赞…"
            qZoneWriteRepository.updateLike(data, newLiked).onSuccess {
                _operationStatus.value = if (newLiked) "已点赞" else "已取消点赞"
            }.onFailure { error ->
                like.isLiked = oldLiked
                like.likeNum = oldCount
                _feeds.value = _feeds.value.map { item ->
                    if (item.feedId == feedId) item.copy(
                        isLiked = oldLiked,
                        likeCount = oldCount
                    ) else item
                }
                Log.e(TAG, "toggleLike failed", error)
                _operationStatus.value = "点赞失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun loadMore() {
        if (loadingMore || noMoreData) return
        if (System.currentTimeMillis() - lastLoadMoreTime < 3000) return
        loadingMore = true
        _loadingMore.value = true
        lastLoadMoreTime = System.currentTimeMillis()
        loadMoreStartTime = System.currentTimeMillis()
        Log.d(TAG, "loadMore via svc.n()")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prevSize = _feeds.value.size
                val newSize = qZoneFeedRepository.loadMore(prevSize, ::processFeeds) ?: run {
                    Log.w(TAG, "loadMore ignored: feed service unavailable")
                    return@launch
                }
                val elapsed = System.currentTimeMillis() - loadMoreStartTime
                if (elapsed < 500) Thread.sleep(500 - elapsed)
                loadingMore = false
                _loadingMore.value = false
                Log.d(TAG, "loadMore done: prev=$prevSize, now=$newSize")
                if (newSize > prevSize) {
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
                when (val result = qZoneFeedRepository.refresh(
                    isCurrent = { isCurrentFeedLoad(generation) },
                    onFeeds = ::processFeeds,
                )) {
                    QZoneFeedRepository.RefreshResult.Success -> finishFeedLoad(generation, true)
                    QZoneFeedRepository.RefreshResult.Cancelled -> Unit
                    is QZoneFeedRepository.RefreshResult.Unavailable -> {
                        _statusText.value = result.reason
                        finishFeedLoad(generation, false)
                    }
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
                    val originalSummary =
                        runCatching { original?.cellSummaryV2?.summary }.getOrNull().orEmpty()
                    val originalTitle =
                        runCatching { original?.cellTitleInfo?.title }.getOrNull().orEmpty()
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
                    Log.d(
                        TAG,
                        "feed[${list.indexOf(data)}] feedType=${data.feedType} owner_uin=${data.owner_uin}"
                    )
                    Log.d(
                        TAG,
                        "  nick=$nick, content='$content', summary='$summary', title='$title'"
                    )
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
                                    author = reply.user?.nickName?.takeIf { it.isNotBlank() }
                                        ?: "QQ用户",
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
        val distinctItems =
            items.distinctBy { it.feedId.ifBlank { "${it.uin}:${it.time}:${it.content}" } }
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

    private fun feedFingerprint(list: List<BusinessFeedData>): String =
        list.joinToString("|") { data ->
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
                    is String -> if (v.isNotBlank() && v.length > 2) results.add(
                        "${field.name}='${
                            v.take(
                                100
                            )
                        }'"
                    )

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
        } catch (_: Exception) {
        }
        return results.joinToString("; ")
    }

    fun avatarUrl(uin: String): String = "$AVATAR_BASE$uin"

    override fun onCleared() {
        qZoneFeedRepository.close()
        super.onCleared()
    }
}
