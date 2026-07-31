package rj.qmce.lite.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.utils.UinUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import rj.qmce.lite.data.qzone.QZoneFeedRepository
import rj.qmce.lite.data.qzone.QZoneMediaRepository
import rj.qmce.lite.data.qzone.QZoneWriteRepository
import java.util.UUID

class QZoneViewModel : ViewModel() {

    companion object {
        private const val TAG = "QMCE-QZone"
        private const val AVATAR_BASE = "https://thirdqq.qlogo.cn/headimg_dl?spec=100&dst_uin="

        // A refresh can return "success" while the first page has not arrived yet (slow
        // backend). Retry a couple of times before settling on an empty space.
        private const val MAX_EMPTY_REFRESH_RETRIES = 2
        private const val EMPTY_REFRESH_RETRY_DELAY_MS = 2_500L
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
        val authorUin: String,
        val author: String,
        val text: String,
        val replies: List<FeedReply> = emptyList(),
    )

    data class CommentReplyTarget(
        val feedId: String,
        val commentId: String,
        val targetUin: String,
        val targetName: String,
    )

    sealed interface CommentSendState {
        data object Idle : CommentSendState
        data object Sending : CommentSendState
        data object Succeeded : CommentSendState
        data class Failed(val message: String) : CommentSendState
    }

    sealed interface PublishState {
        data object Idle : PublishState
        data object Publishing : PublishState
        data object Succeeded : PublishState
        data class Failed(val message: String) : PublishState
    }

    sealed interface DeleteState {
        data object Idle : DeleteState
        data object Submitting : DeleteState
        data object Refreshing : DeleteState
        data object Confirmed : DeleteState
        data object Unconfirmed : DeleteState
        data class Failed(val message: String) : DeleteState
    }

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
    private val _commentReplyTarget = MutableStateFlow<CommentReplyTarget?>(null)
    val commentReplyTarget: StateFlow<CommentReplyTarget?> = _commentReplyTarget
    private val _commentSendState = MutableStateFlow<CommentSendState>(CommentSendState.Idle)
    val commentSendState: StateFlow<CommentSendState> = _commentSendState
    private val _publishState = MutableStateFlow<PublishState>(PublishState.Idle)
    val publishState: StateFlow<PublishState> = _publishState
    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError

    private val _loadMoreError = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = _loadMoreError

    private val _noMoreData = MutableStateFlow(false)
    val noMoreData: StateFlow<Boolean> = _noMoreData

    private var runtime: AppRuntime? = null
    private var loaded = false
    private val feedLoadLock = Any()
    private var feedLoadGeneration = 0L
    private var activeFeedLoad: Job? = null
    private var loadingMore = false
    private var lastLoadMoreTime = 0L
    private val _loadingMore = MutableStateFlow(false)
    val loadingMoreFlow: StateFlow<Boolean> = _loadingMore
    private var loadMoreStartTime = 0L
    private val feedRetryLock = Any()
    private var feedRetryJob: Job? = null
    private val emptyRetryLock = Any()
    private var emptyRetryJob: Job? = null
    private var emptyRefreshAttempts = 0
    private val feedDataById = HashMap<String, BusinessFeedData>()
    private var lastSubmittedFingerprint = ""
    private val qZoneFeedRepository = QZoneFeedRepository()
    private val qZoneWriteRepository = QZoneWriteRepository()
    private val qZoneMediaRepository = QZoneMediaRepository()

    fun init(rt: AppRuntime?) {
        runtime = rt
    }

    private fun scheduleFeedRetry(reason: String) {
        synchronized(feedRetryLock) {
            if (feedRetryJob?.isActive == true) return
            feedRetryJob = viewModelScope.launch(Dispatchers.IO) {
                delay(2_000)
                synchronized(feedRetryLock) { feedRetryJob = null }
                if (!_loading.value && !loaded && _feeds.value.isEmpty()) {
                    Log.d(TAG, "retrying qzone feed load, reason=$reason")
                    loadFeeds(forceRefresh = true)
                }
            }
        }
    }

    private fun cancelFeedRetry() {
        synchronized(feedRetryLock) {
            feedRetryJob?.cancel()
            feedRetryJob = null
        }
    }

    /**
     * A feed refresh can report success before the first page has actually landed (slow
     * backend / cold cache). Rather than permanently showing 暂无动态, re-run the refresh a
     * bounded number of times. The counter is reset to 0 in [processFeeds] the moment real
     * feeds arrive, so this never loops once data exists.
     */
    private fun scheduleEmptyRefreshRetry() {
        synchronized(emptyRetryLock) {
            if (emptyRefreshAttempts >= MAX_EMPTY_REFRESH_RETRIES) {
                Log.d(TAG, "empty feed refresh retry budget exhausted; keeping empty state")
                return
            }
            if (emptyRetryJob?.isActive == true) return
            emptyRefreshAttempts++
            val attempt = emptyRefreshAttempts
            emptyRetryJob = viewModelScope.launch(Dispatchers.IO) {
                delay(EMPTY_REFRESH_RETRY_DELAY_MS)
                synchronized(emptyRetryLock) { emptyRetryJob = null }
                if (!_loading.value && _feeds.value.isEmpty()) {
                    Log.d(TAG, "retrying empty feed refresh, attempt=$attempt")
                    loadFeeds(forceRefresh = true)
                }
            }
        }
    }

    private fun cancelEmptyRefreshRetry() {
        synchronized(emptyRetryLock) {
            emptyRetryJob?.cancel()
            emptyRetryJob = null
        }
    }

    fun isLoadingMore(): Boolean = loadingMore

    fun hasMoreData(): Boolean = !_noMoreData.value

    fun resetNoMoreData() {
        _noMoreData.value = false
    }

    fun publishText(text: String) {
        if (_publishState.value is PublishState.Publishing) return
        val content = text.trim()
        if (content.isBlank()) {
            _operationStatus.value = "动态内容不能为空"
            _publishState.value = PublishState.Failed("动态内容不能为空")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在发表动态…"
            _publishState.value = PublishState.Publishing
            qZoneWriteRepository.publishText(content).onSuccess {
                _operationStatus.value = "动态已发送"
                _publishState.value = PublishState.Succeeded
                delay(1200)
                loadFeeds(forceRefresh = true)
            }.onFailure { error ->
                Log.e(TAG, "publishText failed", error)
                _operationStatus.value = "发表失败：${error.message ?: "未知错误"}"
                _publishState.value = PublishState.Failed(error.message ?: "未知错误")
            }
        }
    }

    fun publishImages(context: Context, text: String, uris: List<Uri>) {
        if (_publishState.value is PublishState.Publishing) return
        val content = text.trim()
        if (content.isBlank() && uris.isEmpty()) {
            _operationStatus.value = "动态内容不能为空"
            _publishState.value = PublishState.Failed("动态内容不能为空")
            return
        }
        if (uris.isEmpty()) {
            publishText(content)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在准备图片…"
            _publishState.value = PublishState.Publishing
            qZoneWriteRepository.publishImages(context, content, uris).onSuccess {
                _operationStatus.value = "动态已发送"
                _publishState.value = PublishState.Succeeded
                delay(2500)
                loadFeeds(forceRefresh = true)
            }.onFailure { error ->
                Log.e(TAG, "publishImages failed", error)
                _operationStatus.value = "图片动态失败：${error.message ?: "未知错误"}"
                _publishState.value = PublishState.Failed(error.message ?: "未知错误")
            }
        }
    }

    fun clearPublishState() {
        _publishState.value = PublishState.Idle
    }

    fun canDeleteFeed(feed: FeedItem): Boolean =
        feed.uin.toLongOrNull() == UinUtils.b()

    fun clearDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

    fun deleteFeed(feedId: String) {
        if (_deleteState.value is DeleteState.Submitting || _deleteState.value is DeleteState.Refreshing) {
            return
        }
        val data = synchronized(feedDataById) { feedDataById[feedId] }
        if (data == null) {
            _operationStatus.value = "动态数据尚未准备好，请稍后重试"
            _deleteState.value = DeleteState.Failed("动态数据尚未准备好，请稍后重试")
            return
        }
        val authorUin = runCatching { data.cellUserInfo?.user?.uin?.toString() }
            .getOrNull()
            ?.toLongOrNull()
            ?: runCatching { data.owner_uin.toString().toLongOrNull() }.getOrNull()
        if (authorUin == null || authorUin != UinUtils.b()) {
            _operationStatus.value = "只能删除自己发表的动态"
            _deleteState.value = DeleteState.Failed("当前动态不属于此账号")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在提交删除请求…"
            _deleteState.value = DeleteState.Submitting
            qZoneWriteRepository.deleteFeed(data).onSuccess {
                _operationStatus.value = "删除请求已提交，正在刷新确认…"
                _deleteState.value = DeleteState.Refreshing
                delay(1500)
                loadFeeds(forceRefresh = true)
                var remainingChecks = 24
                while (_loading.value && remainingChecks-- > 0) {
                    delay(250)
                }
                if (_feeds.value.none { it.feedId == feedId }) {
                    _operationStatus.value = "动态已删除"
                    _deleteState.value = DeleteState.Confirmed
                } else {
                    _operationStatus.value = "删除请求已提交，但暂未从动态流确认"
                    _deleteState.value = DeleteState.Unconfirmed
                }
            }.onFailure { error ->
                Log.e(TAG, "deleteFeed failed", error)
                val message = error.message ?: "未知错误"
                _operationStatus.value = "删除失败：$message"
                _deleteState.value = DeleteState.Failed(message)
            }
        }
    }

    fun saveImage(context: Context, sourceUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _operationStatus.value = "正在保存图片…"
            qZoneMediaRepository.saveImage(context, sourceUrl).onSuccess {
                _operationStatus.value = "图片已保存到图库"
            }.onFailure { error ->
                _operationStatus.value = "保存失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun prepareCommentReply(feedId: String, comment: FeedComment): Boolean {
        if (comment.id.isBlank() || comment.authorUin.toLongOrNull() == null) {
            _operationStatus.value = "这条评论暂时无法回复"
            return false
        }
        _commentReplyTarget.value = CommentReplyTarget(
            feedId = feedId,
            commentId = comment.id,
            targetUin = comment.authorUin,
            targetName = comment.author,
        )
        return true
    }

    fun clearCommentReplyTarget() {
        _commentReplyTarget.value = null
    }

    fun clearCommentSendState() {
        _commentSendState.value = CommentSendState.Idle
    }

    fun comment(feedId: String, text: String, replyTarget: CommentReplyTarget? = null) {
        if (_commentSendState.value is CommentSendState.Sending) return
        val content = text.trim()
        if (content.isBlank()) {
            _operationStatus.value = "评论内容不能为空"
            _commentSendState.value = CommentSendState.Failed("评论内容不能为空")
            return
        }
        val data = synchronized(feedDataById) { feedDataById[feedId] }
        if (data == null) {
            _operationStatus.value = "动态数据尚未准备好，请稍后重试"
            _commentSendState.value = CommentSendState.Failed("动态数据尚未准备好，请稍后重试")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = replyTarget?.takeIf { it.feedId == feedId }
            _operationStatus.value = if (target == null) "正在发送评论…" else "正在回复 ${target.targetName}…"
            _commentSendState.value = CommentSendState.Sending
            val result = if (target == null) {
                qZoneWriteRepository.comment(data, content)
            } else {
                qZoneWriteRepository.reply(
                    data = data,
                    commentId = target.commentId,
                    targetUin = target.targetUin.toLongOrNull() ?: 0L,
                    targetNick = target.targetName,
                    content = content,
                )
            }
            result.onSuccess {
                _feeds.value = _feeds.value.map { item ->
                    if (item.feedId == feedId) {
                        if (target == null) item.copy(
                            commentCount = item.commentCount + 1,
                            comments = item.comments + FeedComment(
                                id = "local:${UUID.randomUUID()}",
                                authorUin = "",
                                author = "我",
                                text = content,
                            ),
                        ) else item.copy(
                            commentCount = item.commentCount + 1,
                            comments = item.comments.map { comment ->
                                if (comment.id == target.commentId) {
                                    comment.copy(replies = comment.replies + FeedReply("我", content))
                                } else comment
                            },
                        )
                    } else item
                }
                _commentReplyTarget.value = null
                _operationStatus.value = if (target == null) "评论已发送" else "回复已发送"
                _commentSendState.value = CommentSendState.Succeeded
                delay(1200)
                loadFeeds(forceRefresh = true)
            }.onFailure { error ->
                Log.e(TAG, "comment failed", error)
                _operationStatus.value = "评论失败：${error.message ?: "未知错误"}"
                _commentSendState.value = CommentSendState.Failed(error.message ?: "未知错误")
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
        if (loadingMore || _noMoreData.value) return
        if (System.currentTimeMillis() - lastLoadMoreTime < 3000) return
        loadingMore = true
        _loadingMore.value = true
        _loadMoreError.value = null
        lastLoadMoreTime = System.currentTimeMillis()
        loadMoreStartTime = System.currentTimeMillis()
        Log.d(TAG, "loadMore via svc.n()")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prevSize = _feeds.value.size
                val newSize = qZoneFeedRepository.loadMore(prevSize, ::processFeeds) ?: run {
                    // Feed service not ready yet — a normal cold-start race. The original
                    // implementation ignored this silently; surfacing it as an error made
                    // ordinary launches show a spurious "加载更多失败".
                    Log.w(TAG, "loadMore ignored: feed service unavailable")
                    return@launch
                }
                val elapsed = System.currentTimeMillis() - loadMoreStartTime
                if (elapsed < 500) Thread.sleep(500 - elapsed)
                Log.d(TAG, "loadMore done: prev=$prevSize, now=$newSize")
                if (newSize > prevSize) {
                    Log.d(TAG, "loadMore received more feeds: prev=$prevSize, now=$newSize")
                } else {
                    // The feed backend exposes no terminal "end of list" signal: a page
                    // with no new items only means nothing arrived within the poll window.
                    // Keep pagination retryable (do NOT flip _noMoreData) so a slow page
                    // does not permanently stop further loading.
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
            loaded = false
            activeFeedLoad = null
        }
        if (forceRefresh) cancelFeedRetry()
        cancelEmptyRefreshRetry()
        _noMoreData.value = false
        _feedError.value = null
        _loading.value = true
        _statusText.value = "加载空间动态..."

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = qZoneFeedRepository.refresh(
                    isCurrent = { isCurrentFeedLoad(generation) },
                    onFeeds = ::processFeeds,
                )) {
                    QZoneFeedRepository.RefreshResult.Success -> {
                        val hasFeeds = _feeds.value.isNotEmpty()
                        if (hasFeeds) {
                            finishFeedLoad(generation, success = true)
                            cancelFeedRetry()
                        } else {
                            // Empty space is a valid success — do not retry as failure. It may
                            // also be a slow backend; give it a bounded number of extra tries
                            // before settling on the empty state.
                            if (_statusText.value.isBlank() ||
                                _statusText.value == "加载空间动态..."
                            ) {
                                _statusText.value = "暂无动态"
                            }
                            finishFeedLoad(generation, success = true, preserveStatus = true)
                            cancelFeedRetry()
                            scheduleEmptyRefreshRetry()
                        }
                    }
                    QZoneFeedRepository.RefreshResult.Cancelled -> Unit
                    is QZoneFeedRepository.RefreshResult.Unavailable -> {
                        _statusText.value = result.reason
                        _feedError.value = result.reason
                        finishFeedLoad(generation, success = false, preserveStatus = true)
                        scheduleFeedRetry("unavailable-${result.reason}")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "loadFeeds error", e)
                _statusText.value = "加载失败: ${e.message}"
                _feedError.value = _statusText.value
                finishFeedLoad(generation, success = false, preserveStatus = true)
                scheduleFeedRetry("exception-${e.javaClass.simpleName}")
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
                            authorUin = comment.user?.uin?.toString().orEmpty(),
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
            // Real feeds arrived (initial load, retry, or late observer event) — the
            // empty-refresh retry budget no longer applies.
            synchronized(emptyRetryLock) { emptyRefreshAttempts = 0 }
        }
        _statusText.value = if (distinctItems.isEmpty()) "暂无动态" else ""
        if (finishLoading) _loading.value = false
        Log.d(TAG, "processed ${distinctItems.size} feeds")
    }

    private fun finishFeedLoad(
        generation: Long,
        success: Boolean,
        preserveStatus: Boolean = false,
    ) {
        if (!isCurrentFeedLoad(generation)) return
        _loading.value = false
        synchronized(feedLoadLock) {
            if (feedLoadGeneration == generation) loaded = success
        }
        if (!success && _feeds.value.isEmpty() && !preserveStatus && _statusText.value.isBlank()) {
            _statusText.value = "加载失败，请重试"
        }
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
        cancelFeedRetry()
        cancelEmptyRefreshRetry()
        qZoneFeedRepository.close()
        super.onCleared()
    }
}
