package rj.qmce.lite.data.qzone

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.watch.qzone_impl.event.Event
import com.tencent.watch.qzone_impl.event.EventCenter
import com.tencent.watch.qzone_impl.event.EventSource
import com.tencent.watch.qzone_impl.event.IObserver
import com.tencent.watch.qzone_impl.feed.BaseResponseWrapper
import com.tencent.watch.qzone_impl.feed.IFeedManager
import com.tencent.watch.qzone_impl.feed.QZoneFeedService
import com.tencent.watch.qzone_impl.feed.ResultWrapper
import com.tencent.watch.qzone_impl.feed.ServiceCallbackWrapper
import com.tencent.watch.qzone_impl.feed.TaskWrapper
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.utils.UinUtils
import kotlinx.coroutines.delay
import mqq.app.MobileQQ
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class QZoneFeedRepository {

    sealed interface RefreshResult {
        data object Success : RefreshResult

        data class Unavailable(val reason: String) : RefreshResult

        data object Cancelled : RefreshResult
    }

    private var feedObserver: IObserver? = null
    private var observedFeedService: QZoneFeedService? = null
    private var feedService: QZoneFeedService? = null
    private val lastRequestFailure = AtomicReference<String?>(null)
    private val lastNetworkSucceeded = AtomicBoolean(false)

    suspend fun refresh(
        isCurrent: () -> Boolean,
        onFeeds: (List<BusinessFeedData>, finishLoading: Boolean) -> Unit,
    ): RefreshResult {
        when (val readiness = awaitQZoneReady(isCurrent)) {
            RefreshResult.Cancelled -> return RefreshResult.Cancelled
            is RefreshResult.Unavailable -> return readiness
            RefreshResult.Success -> Unit
        }

        val service = awaitFeedService(isCurrent)
            ?: return if (isCurrent()) {
                RefreshResult.Unavailable("QZoneFeedService 不可用")
            } else {
                RefreshResult.Cancelled
            }
        feedService = service

        val uin = runCatching { UinUtils.b() }.getOrDefault(0L)
        if (uin <= 0L) {
            return RefreshResult.Unavailable("账号未就绪")
        }
        service.m(uin, uin)
        Log.d(TAG, "feed service initialized, uin=$uin")
        attachRequestCallback(service)
        registerFeedObserver(service, onFeeds)

        val feedManager = service.i ?: return RefreshResult.Unavailable("FeedManager 不可用")
        lastRequestFailure.set(null)
        lastNetworkSucceeded.set(false)
        val cached = feedManager.n()
        if (!cached.isNullOrEmpty()) {
            Log.d(TAG, "loaded ${cached.size} cached feeds")
            onFeeds(cached, false)
        }

        val cacheEmpty = cached.isNullOrEmpty()
        requestRefresh(feedManager, force = cacheEmpty)
        Log.d(TAG, "requested network feed refresh force=$cacheEmpty")

        var lastFingerprint = feedFingerprint(cached.orEmpty())
        var sawNonEmpty = !cached.isNullOrEmpty()
        var forced = cacheEmpty
        repeat(POLL_COUNT) { round ->
            delay(POLL_INTERVAL_MILLIS)
            if (!isCurrent()) return RefreshResult.Cancelled
            if (!forced && round + 1 >= FORCE_REFRESH_AFTER_ROUND) {
                val mid = feedManager.n()
                if (mid.isNullOrEmpty()) {
                    requestRefresh(feedManager, force = true)
                    forced = true
                    Log.d(TAG, "requested network feed refresh force=true after round=${round + 1}")
                }
            }
            val failure = lastRequestFailure.get()
            if (failure != null && !sawNonEmpty) {
                Log.w(TAG, "feed refresh failed: $failure")
                return RefreshResult.Unavailable(failure)
            }
            val fresh = feedManager.n()
            if (!fresh.isNullOrEmpty()) {
                sawNonEmpty = true
                val fingerprint = feedFingerprint(fresh)
                if (fingerprint != lastFingerprint) {
                    onFeeds(fresh, true)
                    lastFingerprint = fingerprint
                    Log.d(TAG, "feed poll round=${round + 1} size=${fresh.size}")
                }
            }
        }
        val finalSize = feedManager.n()?.size ?: 0
        Log.d(
            TAG,
            "feed refresh settled size=$finalSize forced=$forced " +
                "networkOk=${lastNetworkSucceeded.get()} failure=${lastRequestFailure.get()}",
        )
        val failure = lastRequestFailure.get()
        if (finalSize == 0 && failure != null) {
            return RefreshResult.Unavailable(failure)
        }
        if (finalSize == 0 && !lastNetworkSucceeded.get()) {
            return RefreshResult.Unavailable("空间动态加载超时，请重试")
        }
        return RefreshResult.Success
    }

    private fun requestRefresh(feedManager: IFeedManager, force: Boolean) {
        val callback = ServiceCallbackWrapper().apply {
            a = WeakReference(Handler(Looper.getMainLooper()))
        }
        feedManager.j(0, callback, force)
    }

    private fun attachRequestCallback(service: QZoneFeedService) {
        service.j = object : IFeedManager.RequestCallbackListener {
            override fun k(
                task: TaskWrapper?,
                result: ResultWrapper?,
                response: BaseResponseWrapper?,
                code: Int,
            ) {
                val respCode = runCatching { response?.a() }.getOrNull()
                val respMsg = runCatching { response?.c() }.getOrNull().orEmpty()
                Log.d(
                    TAG,
                    "feed callback k code=$code respCode=$respCode msg=$respMsg " +
                        "size=${runCatching { response?.b()?.size }.getOrNull()}",
                )
                if (code != 0) {
                    lastRequestFailure.set(
                        if (respMsg.isNotBlank()) {
                            "空间请求失败：$respMsg"
                        } else {
                            "空间请求失败 code=$code"
                        },
                    )
                } else if (respCode != null && respCode != 0) {
                    lastRequestFailure.set(
                        if (respMsg.isNotBlank()) {
                            "空间请求失败：$respMsg"
                        } else {
                            "空间请求失败 resp=$respCode"
                        },
                    )
                } else {
                    lastRequestFailure.set(null)
                    lastNetworkSucceeded.set(true)
                }
            }

            override fun o(task: TaskWrapper?, result: ResultWrapper?) {
                Log.d(TAG, "feed callback o result=$result")
            }
        }
    }

    private suspend fun awaitQZoneReady(isCurrent: () -> Boolean): RefreshResult {
        repeat(READY_POLL_COUNT) {
            if (!isCurrent()) return RefreshResult.Cancelled
            val runtime = runCatching { MobileQQ.sMobileQQ?.peekAppRuntime() }.getOrNull()
            val uin = runCatching { UinUtils.b() }.getOrDefault(0L)
            val a2 = runCatching { UinUtils.a() }.getOrNull()
            if (runtime != null && uin > 0L && a2 != null && a2.isNotEmpty()) {
                Log.d(TAG, "qzone ready: uin=$uin, a2Bytes=${a2.size}")
                return RefreshResult.Success
            }
            delay(READY_POLL_INTERVAL_MILLIS)
        }
        val runtime = runCatching { MobileQQ.sMobileQQ?.peekAppRuntime() }.getOrNull()
        val uin = runCatching { UinUtils.b() }.getOrDefault(0L)
        val a2 = runCatching { UinUtils.a() }.getOrNull()
        return when {
            runtime == null -> RefreshResult.Unavailable("Runtime 未就绪")
            uin <= 0L -> RefreshResult.Unavailable("账号未就绪")
            a2 == null || a2.isEmpty() -> RefreshResult.Unavailable("A2 票据未就绪")
            else -> RefreshResult.Success
        }
    }

    private suspend fun awaitFeedService(isCurrent: () -> Boolean): QZoneFeedService? {
        ensureFeedService()
        repeat(50) {
            if (!isCurrent()) return null
            QZoneFeedService.h()?.let { return it }
            ensureFeedService()
            delay(300)
        }
        return QZoneFeedService.h() ?: run {
            Log.w(TAG, "QZoneFeedService still unavailable after wait")
            null
        }
    }

    private fun ensureFeedService() {
        if (QZoneFeedService.h() != null) return
        runCatching {
            val clazz = QZoneFeedService::class.java
            clazz.declaredConstructors
                .filter { it.parameterCount == 0 }
                .minByOrNull { it.parameterCount }
                ?.let { constructor ->
                    constructor.isAccessible = true
                    constructor.newInstance()
                }
        }.onFailure { error ->
            Log.w(TAG, "failed to construct QZoneFeedService", error)
        }
    }

    suspend fun loadMore(
        previousSize: Int,
        onFeeds: (List<BusinessFeedData>, finishLoading: Boolean) -> Unit,
    ): Int? {
        val service = feedService ?: return null
        service.n(Handler(Looper.getMainLooper()))

        var fresh: List<BusinessFeedData>? = null
        var lastFingerprint = feedFingerprint(service.i?.n().orEmpty())
        repeat(POLL_COUNT) {
            delay(POLL_INTERVAL_MILLIS)
            val candidate = service.i?.n()
            if (!candidate.isNullOrEmpty()) {
                fresh = candidate
                val fingerprint = feedFingerprint(candidate)
                if (fingerprint != lastFingerprint || candidate.size > previousSize) {
                    onFeeds(candidate, true)
                    lastFingerprint = fingerprint
                    if (candidate.size > previousSize) return candidate.size
                }
            }
        }
        return fresh?.size ?: 0
    }

    fun close() {
        feedObserver?.let { observer ->
            runCatching { EventCenter.b().g(observer) }
        }
        feedObserver = null
        observedFeedService = null
        feedService?.j = null
        feedService = null
        lastRequestFailure.set(null)
    }

    private fun registerFeedObserver(
        service: QZoneFeedService,
        onFeeds: (List<BusinessFeedData>, finishLoading: Boolean) -> Unit,
    ) {
        if (feedObserver != null && observedFeedService === service) return
        runCatching {
            feedObserver?.let { observer ->
                runCatching { EventCenter.b().g(observer) }
            }
            val observer = object : IObserver.main {
                override fun n(event: Event) {
                    Log.d(TAG, "feed event: type=${event.a}")
                    if (event.a != FEED_EVENT_UPDATED && event.a != FEED_EVENT_REFRESHED) return
                    val feeds = service.i?.n()
                    Log.d(TAG, "feed event type=${event.a} size=${feeds?.size ?: 0}")
                    if (!feeds.isNullOrEmpty()) onFeeds(feeds, true)
                }
            }
            feedObserver = observer
            observedFeedService = service
            EventCenter.b()
                .a(observer, 0, EventSource("Feed", null), FEED_EVENT_UPDATED, FEED_EVENT_REFRESHED)
            Log.d(TAG, "registered feed observer")
        }.onFailure { error ->
            Log.e(TAG, "failed to register feed observer", error)
        }
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

    private companion object {
        private const val TAG = "QMCE-QZoneFeed"
        private const val FEED_EVENT_UPDATED = 1
        private const val FEED_EVENT_REFRESHED = 4
        private const val POLL_COUNT = 60
        private const val POLL_INTERVAL_MILLIS = 500L
        private const val FORCE_REFRESH_AFTER_ROUND = 30
        private const val READY_POLL_COUNT = 80
        private const val READY_POLL_INTERVAL_MILLIS = 250L
    }
}
