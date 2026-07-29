package rj.qmce.lite.data.qzone

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.watch.qzone_impl.event.Event
import com.tencent.watch.qzone_impl.event.EventCenter
import com.tencent.watch.qzone_impl.event.EventSource
import com.tencent.watch.qzone_impl.event.IObserver
import com.tencent.watch.qzone_impl.feed.QZoneFeedService
import com.tencent.watch.qzone_impl.feed.ServiceCallbackWrapper
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.utils.UinUtils
import kotlinx.coroutines.delay
import mqq.app.MobileQQ
import java.lang.ref.WeakReference

class QZoneFeedRepository {

    sealed interface RefreshResult {
        data object Success : RefreshResult

        data class Unavailable(val reason: String) : RefreshResult

        data object Cancelled : RefreshResult
    }

    private var feedObserver: IObserver? = null
    private var observedFeedService: QZoneFeedService? = null
    private var feedService: QZoneFeedService? = null

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
        registerFeedObserver(service, onFeeds)

        val feedManager = service.i ?: return RefreshResult.Unavailable("FeedManager 不可用")
        val cached = feedManager.n()
        if (!cached.isNullOrEmpty()) {
            Log.d(TAG, "loaded ${cached.size} cached feeds")
            onFeeds(cached, false)
        }

        val callback = ServiceCallbackWrapper().apply {
            a = WeakReference(Handler(Looper.getMainLooper()))
        }
        feedManager.j(0, callback, false)
        Log.d(TAG, "requested network feed refresh")

        var lastFingerprint = feedFingerprint(cached.orEmpty())
        repeat(POLL_COUNT) {
            delay(POLL_INTERVAL_MILLIS)
            if (!isCurrent()) return RefreshResult.Cancelled
            val fresh = feedManager.n()
            if (!fresh.isNullOrEmpty()) {
                val fingerprint = feedFingerprint(fresh)
                if (fingerprint != lastFingerprint) {
                    onFeeds(fresh, true)
                    lastFingerprint = fingerprint
                }
            }
        }
        return RefreshResult.Success
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
        repeat(50) {
            if (!isCurrent()) return null
            QZoneFeedService.h()?.let { return it }
            delay(300)
        }
        return QZoneFeedService.h()
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
        feedService = null
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
        private const val POLL_COUNT = 30
        private const val POLL_INTERVAL_MILLIS = 400L
        private const val READY_POLL_COUNT = 40
        private const val READY_POLL_INTERVAL_MILLIS = 250L
    }
}
