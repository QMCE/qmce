package rj.qmce.lite.viewmodel

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.watch.qzone_impl.event.Event
import com.tencent.watch.qzone_impl.event.EventCenter
import com.tencent.watch.qzone_impl.event.EventSource
import com.tencent.watch.qzone_impl.event.IObserver
import com.tencent.watch.qzone_impl.feed.ServiceCallbackWrapper
import com.tencent.watch.qzone_impl.feed.QZoneFeedService
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.utils.UinUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import java.lang.ref.WeakReference

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
        val likeCount: Int,
        val commentCount: Int,
        val isLiked: Boolean,
    )

    data class ForwardInfo(
        val author: String,
        val content: String,
        val isUnavailable: Boolean,
    )

    private val _feeds = MutableStateFlow<List<FeedItem>>(emptyList())
    val feeds: StateFlow<List<FeedItem>> = _feeds

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private var runtime: AppRuntime? = null
    private var loaded = false
    private var feedObserver: IObserver? = null
    private var svcRef: QZoneFeedService? = null
    private var loadingMore = false
    private var lastLoadMoreTime = 0L
    private var noMoreData = false
    private val _loadingMore = MutableStateFlow(false)
    val loadingMoreFlow: StateFlow<Boolean> = _loadingMore
    private var loadMoreStartTime = 0L

    fun init(rt: AppRuntime?) {
        runtime = rt
    }

    fun isLoadingMore(): Boolean = loadingMore

    fun hasMoreData(): Boolean = !noMoreData

    fun resetNoMoreData() { noMoreData = false }

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
                Thread.sleep(3000)
                val fresh = svc.h?.n()
                val newSize = fresh?.size ?: 0
                // 保证加载指示器至少显示 500ms，避免快速滑动时闪烁
                val elapsed = System.currentTimeMillis() - loadMoreStartTime
                if (elapsed < 500) Thread.sleep(500 - elapsed)
                loadingMore = false
                _loadingMore.value = false
                Log.d(TAG, "loadMore done: prev=$prevSize, now=$newSize")
                if (!fresh.isNullOrEmpty() && newSize > prevSize) {
                    processFeeds(fresh)
                } else {
                    noMoreData = true
                    Log.d(TAG, "no more data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore error", e)
            } finally {
                loadingMore = false
                _loadingMore.value = false
            }
        }
    }

    fun loadFeeds(forceRefresh: Boolean = false) {
        if (loaded && !forceRefresh) return
        loaded = true
        noMoreData = false
        _loading.value = true
        _statusText.value = "加载空间动态..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val svc = QZoneFeedService.h()
                svcRef = svc
                if (svc == null) {
                    _statusText.value = "QZoneFeedService 不可用"
                    _loading.value = false
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
                        processFeeds(cached)
                    }

                    // 触发网络加载
                    val wrapper = ServiceCallbackWrapper()
                    wrapper.a = WeakReference(Handler(Looper.getMainLooper()))
                    feedMgr.b(0, wrapper, false)
                    Log.d(TAG, "triggered feed load")

                    // 等待回调到达后再次读取
                    Thread.sleep(3000)
                    val fresh = feedMgr.n()
                    if (!fresh.isNullOrEmpty() && fresh.size != (cached?.size ?: 0)) {
                        processFeeds(fresh)
                    }
                } else {
                    Log.w(TAG, "IFeedManager is null")
                    _statusText.value = "FeedManager 不可用"
                    _loading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadFeeds error", e)
                _statusText.value = "加载失败: ${e.message}"
                _loading.value = false
            }
        }
    }

    private fun registerFeedObserver(svc: QZoneFeedService) {
        if (feedObserver != null) return
        try {
            val observer = object : IObserver.main {
                override fun s(event: Event) {
                    Log.d(TAG, "feed event: type=${event.a}")
                    if (event.a == 1 || event.a == 4) {
                        val feedMgr = svc.h ?: return
                        val list = feedMgr.n()
                        if (!list.isNullOrEmpty()) {
                            processFeeds(list)
                        }
                    }
                }
            }
            feedObserver = observer
            val ec = EventCenter.b()
            val src = EventSource("Feed", null)
            ec.a(observer, 0, src, 1, 4)
            Log.d(TAG, "registered feed observer")
        } catch (e: Exception) {
            Log.e(TAG, "registerFeedObserver error", e)
        }
    }

    private fun processFeeds(list: List<BusinessFeedData>) {
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
                    likeCount = likes?.likeNum ?: 0,
                    commentCount = comments?.c?.size ?: 0,
                    isLiked = likes?.isLiked ?: false,
                )
            } catch (e: Exception) {
                Log.e(TAG, "processFeed error", e)
                null
            }
        }
        _feeds.value = items
        _statusText.value = if (items.isEmpty()) "暂无动态" else ""
        _loading.value = false
        Log.d(TAG, "processed ${items.size} feeds")
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
