package rj.qmce.lite.data.reporting

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object OfficialReportBridge {
    object PageIds {
        const val SPLASH = "pg_watch_log_splash_screen"
        const val WELCOME = "pg_watch_welcome"
        const val LOGIN = "pg_watch_login"
        const val PROTOCOL_CONFIRMATION = "pg_watch_protocol_confirmation"
        const val MESSAGE = "pg_watch_message"
        const val CONTACTS = "pg_watch_contacts"
        const val AIO = "pg_watch_aio"
        const val RICH_MEDIA = "pg_watch_rich_media"
        const val EXPRESSION = "pg_watch_expression"
        const val LONG_PRESS_MENU = "pg_watch_long_press_menu"
        const val VOICE = "pg_watch_voice"
        const val DIAL_INTERFACE = "pg_watch_dial_interface"
        const val INVITED_INTERFACE = "ipg_watch_invited_interface"
        const val DYNAMIC_INFORMATION = "pg_watch_views_dynamic_information"
        const val MY_DYNAMIC = "pg_watch_my_dynamic"
        const val RELEASE_DYNAMIC = "pg_watch_release_dynamic"
        const val DYNAMIC_PUBLISH = "pg_watch_dynamic_publish"
        const val ALBUM = "pg_watch_album"
        const val ALBUM_SELECTION = "em_watch_album_selection"
        const val PHOTOS = "pg_watch_photos"
        const val SETTINGS = "pg_watch_settings"
        const val FRIEND_REQUEST = "pg_watch_friend_request"
        const val CLEARS_MESSAGES = "pg_watch_clears_messages"
        const val MODIFIES_NICKNAMES = "pg_watch_modifies_nicknames"
    }

    object ElementIds {
        const val LOGIN = "em_watch_login"
        const val AGREE = "em_watch_agree"
        const val MESSAGE_ENTRY = "em_watch_message_entry"
        const val AVATAR_MSGLIST = "em_avatar_msglist_pg"
        const val GROUP_ACCOUNT_CHANGE_NOTICE = "em_sgrp_account_change_notice"
        const val GROUP_PRIVATE_MESSAGE_NOTICE = "em_sgrp_private_msg_notice"
        const val GROUP_SYSTEM_NOTICE = "em_sgrp_system_notice"
        const val GROUP_INTERACTION_NOTICE = "em_sgrp_interaction_notice_entry"
        const val GROUP_MSGLIST_ASSISTANT = "em_sgrp_msglist_assitant"
        const val GROUP_SUBSCRIBE_CHANNEL = "em_sgrp_subscribe_channel"
        const val LIGHT_INTERACTIVE_ICON = "em_bas_message_list_light_interactive_icon"
        const val LITTLE_EAR = "em_bas_msglist_little_ear"
        const val SESSION_NODE_SLIDES_LEFT = "em_bas_session_node_slides_left"
        const val INTERACT_MSGLIST = "em_interact_msglist_pg"
        const val ASSOCIATE_ACCOUNT_SESSION = "em_bas_associate_account_message_session"
        const val SMALL_BAR = "em_bas_msglist_small_bar"
        const val CONTACT_ENTRY = "em_watch_contact_entry"
        const val FRIEND_NOTIFICATION = "em_watch_friend_notification"
        const val ADD_FRIEND = "em_watch_add_friend"
        const val ADD_FRIENDS = "em_watch_add_friends"
        const val GO_CHAT = "em_watch_go_chat"
        const val REJECT = "em_watch_reject"
        const val DYNAMIC_ENTRY = "em_watch_dynamic_entries"
        const val RELEASE_DYNAMIC = "em_watch_release_dynamic"
        const val PHOTOS = "em_watch_photos"
        const val PUBLISH = "em_watch_publish"
        const val CAMERA = "em_watch_camera"
        const val CANCEL = "em_watch_cancel"
        const val CONFIRM = "em_watch_confirm"
        const val STARTS_TAKING_PICTURES = "em_watch_starts_taking_pictures"
        const val NEXT = "em_watch_next"
        const val SEND = "em_watch_send"
        const val RESHOOT = "em_watch_reshoot"
        const val SHARE = "em_watch_share"
        const val REFRESH = "em_watch_refresh"
        const val CLICK_LIKE = "em_watch_click_like"
        const val COMMENTS = "em_watch_comments"
        const val RICH_MEDIA_ENTRIES = "em_watch_rich_media_entries"
        const val EXPRESSION_ENTRY = "em_watch_expression_entry"
        const val HOLD_SPEAK = "em_watch_hold_speak"
        const val EMOTICON_COLUMN = "em_watch_emoticon_column"
        const val EXPRESSION = "em_watch_expression"
        const val TO_TEXT = "em_watch_to_text"
        const val DELETED = "em_watch_deleted"
        const val REVOCATION = "em_watch_revocation"
        const val GO_TO_CHAT = "em_watch_go_to_chat"
        const val ANSWER_CALL = "em_watch_answer_call"
        const val MICROPHONE = "em_watch_microphone"
        const val HANG_UP = "em_watch_hang_up"
        const val FEATURE_ENTRY = "em_watch_feature_entry"
        const val EMPTY = "em_watch_empty"
        const val FUNCTION = "em_watch_function"
        const val REPORT = "em_watch_report"
        const val NUMBER_ADDITION = "em_watch_number_addition"
        const val TOUCH = "em_watch_touch"
        const val TRY_AGAIN = "em_watch_try_again"

        fun dynamicEntryReuse(identifier: String): String = "${DYNAMIC_ENTRY}_$identifier"

        fun commentsReuse(identifier: String): String = "${COMMENTS}_$identifier"
    }

    private const val EVENT_CLICK = "dt_clck"
    private const val EVENT_IMPRESSION = "dt_imp"
    private const val EVENT_CHAT_CLICK = "qq_clck"
    private const val EVENT_ITEM_IMPRESSION = "qq_imp"
    private const val EVENT_ITEM_IMPRESSION_END = "qq_imp_end"
    private const val CHAT_PAGE_ID = "pg_bas_msglist"
    private const val CHAT_REF_PAGE_ID = "vr_page_none"
    private const val TAG = "QMCE-OfficialReport"
    private const val INIT_DELAY_MILLIS = 3500L
    private const val VERIFY_DELAY_MILLIS = 1500L
    private const val MAX_VERIFY_ATTEMPTS = 2
    private const val FALLBACK_VERIFY_DELAY_MILLIS = 250L
    private const val MAX_FALLBACK_VERIFY_ATTEMPTS = 8
    private const val VIDEO_REPORT_INNER =
        "com.tencent.qqlive.module.videoreport.inner.VideoReportInner"
    private const val VIDEO_REPORT =
        "com.tencent.qqlive.module.videoreport.VideoReport"
    private const val EVENT_AGING_TYPE =
        "com.tencent.qqlive.module.videoreport.common.EventAgingType"
    private const val PAGE_PARAMS =
        "com.tencent.qqlive.module.videoreport.PageParams"
    private const val CLICK_POLICY =
        "com.tencent.qqlive.module.videoreport.constants.ClickPolicy"
    private const val DT_INIT_TASK = "com.tencent.qqnt.watch.startup.task.DtInitTask"
    private const val BEACON_INIT_TASK =
        "com.tencent.qqnt.watch.startup.task.BeaconSDKInitTask"
    private const val QQ_BEACON_REPORT = "com.tencent.mobileqq.statistics.QQBeaconReport"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val state = AtomicReference(State.NOT_STARTED)
    private val beaconState = AtomicReference(State.NOT_STARTED)

    enum class State {
        NOT_STARTED,
        WAITING_FOR_OFFICIAL_STARTUP,
        INITIALIZED,
        UNAVAILABLE,
        FAILED
    }

    fun initialize(application: Application) {
        if (!OfficialReportGuard.begin(application)) return

        state.set(State.WAITING_FOR_OFFICIAL_STARTUP)
        beaconState.set(State.WAITING_FOR_OFFICIAL_STARTUP)
        Log.d(TAG, "official reporter bridge scheduled")
        mainHandler.postDelayed(
            { verifyOrFallback(application, attempt = 0) },
            INIT_DELAY_MILLIS,
        )
    }

    fun currentState(): State = state.get()

    fun currentBeaconState(): State = beaconState.get()

    fun isReadyForEvents(): Boolean {
        return state.get() == State.INITIALIZED || isOfficialReporterInitialized()
    }

    fun reportEvent(
        eventKey: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        page: Any? = null,
    ): Boolean {
        if (eventKey.isBlank()) return false
        return runCatching {
            val videoReport = Class.forName(VIDEO_REPORT)
            val normalizedParams = linkedMapOf<String, String>()
            params.forEach { (key, value) ->
                if (!key.isNullOrBlank()) {
                    normalizedParams[key] = value?.toString().orEmpty()
                }
            }
            if (page == null) {
                videoReport.getMethod(
                    "reportEvent",
                    String::class.java,
                    Map::class.java,
                ).invoke(null, eventKey, normalizedParams)
            } else {
                videoReport.getMethod(
                    "reportEvent",
                    String::class.java,
                    Any::class.java,
                    Map::class.java,
                ).invoke(null, eventKey, page, normalizedParams)
            }
            Log.d(TAG, "event reported key=$eventKey")
            true
        }.onFailure { error ->
            Log.w(TAG, "official event failed key=$eventKey", unwrap(error))
        }.getOrDefault(false)
    }

    fun reportTypedEvent(
        eventKey: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        page: Any? = null,
    ): Boolean {
        if (eventKey.isBlank()) return false
        return runCatching {
            val videoReport = Class.forName(VIDEO_REPORT)
            if (page == null) {
                videoReport.getMethod(
                    "reportEvent",
                    String::class.java,
                    Map::class.java,
                ).invoke(null, eventKey, params)
            } else {
                videoReport.getMethod(
                    "reportEvent",
                    String::class.java,
                    Any::class.java,
                    Map::class.java,
                ).invoke(null, eventKey, page, params)
            }
            Log.d(TAG, "typed event reported key=$eventKey")
            true
        }.onFailure { error ->
            Log.w(TAG, "typed event failed key=$eventKey", unwrap(error))
        }.getOrDefault(false)
    }

    fun reportChatListItemExposure(
        contact: RecentContactInfo,
        homeUin: String,
        uid: String,
        firstExposure: Boolean,
    ): Boolean {
        return reportTypedEvent(
            eventKey = EVENT_ITEM_IMPRESSION,
            params = chatListItemParams(contact, homeUin, uid, firstExposure),
        )
    }

    fun reportChatListItemExposureEnd(
        contact: RecentContactInfo,
        homeUin: String,
        uid: String,
        exposureDurationMs: Long,
    ): Boolean {
        val params = chatListItemParams(contact, homeUin, uid, firstExposure = false)
        params["qq_element_lvtm"] = exposureDurationMs.coerceAtLeast(0L)
        return reportTypedEvent(EVENT_ITEM_IMPRESSION_END, params)
    }

    fun chatListItemElementParams(
        contact: RecentContactInfo,
        homeUin: String,
        uid: String,
    ): Map<String, Any?> {
        return chatListItemElementOnlyParams(contact)
    }

    fun reportChatListItemClick(
        target: Any?,
        contact: RecentContactInfo,
        homeUin: String,
        uid: String,
    ): Boolean {
        val reuseIdentifier = contact.contactId
            .takeIf { it > 0L }
            ?.toString()
            ?: contact.id?.takeIf { it.isNotBlank() }
            ?: contact.peerUid?.takeIf { it.isNotBlank() }
        val params = chatListItemParams(
            contact = contact,
            homeUin = homeUin,
            uid = uid,
            firstExposure = false,
        )
        params["click_method"] = "1"
        return if (target != null) {
            reportViewElementEvent(
                eventKey = EVENT_CHAT_CLICK,
                target = target,
                elementId = ElementIds.MESSAGE_ENTRY,
                params = params,
                elementParams = chatListItemElementOnlyParams(contact),
                reuseIdentifier = reuseIdentifier,
            )
        } else {
            reportTypedEvent(EVENT_CHAT_CLICK, params)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun reportElementClick(
        target: Any?,
        elementId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        reuseIdentifier: String? = null,
    ): Boolean {
        if (target is View) {
            return reportViewElementClick(target, elementId, params, reuseIdentifier)
        }
        return reportComposeElementClick(elementId, params, reuseIdentifier)
    }

    fun reportComposeElementClick(
        elementId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        reuseIdentifier: String? = null,
    ): Boolean {
        if (elementId.isBlank()) return false

        val eventParams = linkedMapOf<String, Any?>()
        eventParams["click_method"] = "1"
        eventParams.putAll(params)
        eventParams["qq_eid"] = elementId
        reuseIdentifier
            ?.takeIf(String::isNotBlank)
            ?.let { eventParams["dt_ele_reuse_id"] = it }
        return reportTypedEvent(EVENT_CLICK, eventParams)
    }

    fun reportViewElementClick(
        target: Any,
        elementId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        reuseIdentifier: String? = null,
    ): Boolean {
        return reportViewElementEvent(
            eventKey = EVENT_CLICK,
            target = target,
            elementId = elementId,
            params = params,
            reuseIdentifier = reuseIdentifier,
        )
    }

    fun reportViewElementImpression(
        target: Any,
        elementId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        reuseIdentifier: String? = null,
    ): Boolean {
        return reportViewElementEvent(
            eventKey = EVENT_IMPRESSION,
            target = target,
            elementId = elementId,
            params = params,
            reuseIdentifier = reuseIdentifier,
            includeClickMethod = false,
        )
    }

    fun reportViewElementEvent(
        eventKey: String,
        target: Any,
        elementId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        elementParams: Map<String, *> = params,
        reuseIdentifier: String? = null,
        includeClickMethod: Boolean = eventKey == EVENT_CLICK,
    ): Boolean {
        if (eventKey.isBlank()) return false
        if (elementId.isBlank()) return false
        if (!isOfficialReporterInitialized()) return false
        return runCatching {
            val eventParams = linkedMapOf<String, Any?>()
            if (includeClickMethod) eventParams["click_method"] = "1"
            eventParams.putAll(params)
            val videoReport = Class.forName(VIDEO_REPORT)
            configureViewElement(
                videoReport = videoReport,
                target = target,
                elementId = elementId,
                params = elementParams,
                reuseIdentifier = reuseIdentifier,
            )
            videoReport.getMethod(
                "reportEvent",
                String::class.java,
                Any::class.java,
                Map::class.java,
            ).invoke(null, eventKey, target, eventParams)
            Log.d(TAG, "element event reported key=$eventKey id=$elementId")
            true
        }.onFailure { error ->
            Log.w(TAG, "official element event failed key=$eventKey id=$elementId", unwrap(error))
        }.getOrDefault(false)
    }

    fun configureViewElement(
        target: Any,
        elementId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        reuseIdentifier: String? = null,
        reportAllExposures: Boolean = false,
    ): Boolean {
        if (elementId.isBlank() || !isOfficialReporterInitialized()) return false
        return runCatching {
            val videoReport = Class.forName(VIDEO_REPORT)
            configureViewElement(
                videoReport = videoReport,
                target = target,
                elementId = elementId,
                params = params,
                reuseIdentifier = reuseIdentifier,
                reportAllExposures = reportAllExposures,
            )
            true
        }.onFailure { error ->
            Log.w(TAG, "official element configuration failed id=$elementId", unwrap(error))
        }.getOrDefault(false)
    }

    private fun configureViewElement(
        videoReport: Class<*>,
        target: Any,
        elementId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
        reuseIdentifier: String? = null,
        reportAllExposures: Boolean = false,
    ) {
        videoReport.getMethod("setElementId", Any::class.java, String::class.java)
            .invoke(null, target, elementId)
        if (params.isNotEmpty()) {
            videoReport.getMethod("setElementParams", Any::class.java, Map::class.java)
                .invoke(null, target, params)
        }
        setManualClickPolicy(videoReport, target)
        if (!reuseIdentifier.isNullOrBlank()) {
            videoReport.getMethod(
                "setElementReuseIdentifier",
                Any::class.java,
                String::class.java,
            ).invoke(null, target, reuseIdentifier)
        }
        if (reportAllExposures) {
            runCatching {
                val exposurePolicy = Class.forName(
                    "com.tencent.qqlive.module.videoreport.constants.ExposurePolicy",
                )
                val reportAll = exposurePolicy.getField("REPORT_ALL").get(null)
                videoReport.getMethod(
                    "setElementExposePolicy",
                    Any::class.java,
                    exposurePolicy,
                ).invoke(null, target, reportAll)
            }.onFailure { error ->
                Log.w(TAG, "official exposure policy unavailable", unwrap(error))
            }
        }
    }

    private fun setManualClickPolicy(videoReport: Class<*>, target: Any) {
        runCatching {
            val clickPolicy = Class.forName(CLICK_POLICY)
            val reportNone = clickPolicy.getField("REPORT_NONE").get(null)
            videoReport.getMethod(
                "setElementClickPolicy",
                Any::class.java,
                clickPolicy,
            ).invoke(null, target, reportNone)
        }.onFailure { error ->
            Log.d(TAG, "official click policy unavailable", unwrap(error))
        }
    }

    fun reportPageIn(
        page: Any,
        pageId: String,
        params: Map<String, *> = emptyMap<String, Any?>(),
    ): Boolean {
        if (!isOfficialReporterInitialized()) return false
        return runCatching {
            val videoReport = Class.forName(VIDEO_REPORT)
            videoReport.getMethod("setPageId", Any::class.java, String::class.java)
                .invoke(null, page, pageId)
            setOfficialPageParams(videoReport, page, params)
            setOfficialPageEventType(videoReport, page)
            videoReport.getMethod("reportPgIn", Any::class.java).invoke(null, page)
            Log.d(TAG, "page in id=$pageId")
            true
        }.onFailure { error ->
            Log.w(TAG, "official page-in failed id=$pageId", unwrap(error))
        }.getOrDefault(false)
    }

    fun reportPageOut(page: Any): Boolean {
        if (!isOfficialReporterInitialized()) return false
        return runCatching {
            Class.forName(VIDEO_REPORT)
                .getMethod("reportPgOut", Any::class.java)
                .invoke(null, page)
            true
        }.onFailure { error ->
            Log.w(TAG, "official page-out failed", unwrap(error))
        }.getOrDefault(false)
    }

    fun destroyPage(page: Any): Boolean {
        if (!isOfficialReporterInitialized()) return false
        return runCatching {
            Class.forName(VIDEO_REPORT)
                .getMethod("pageLogicDestroy", Any::class.java)
                .invoke(null, page)
            true
        }.onFailure { error ->
            Log.w(TAG, "official page destroy failed", unwrap(error))
        }.getOrDefault(false)
    }

    private fun verifyOrFallback(application: Application, attempt: Int) {
        val dtReady = isOfficialReporterInitialized()
        val beaconReady = isOfficialBeaconInitialized()
        if (dtReady) {
            state.set(State.INITIALIZED)
        }
        if (beaconReady) beaconState.set(State.INITIALIZED)
        if (dtReady && beaconReady) {
            Log.d(TAG, "official DT and Beacon reporters initialized by startup tasks")
            return
        }

        if (attempt < MAX_VERIFY_ATTEMPTS) {
            mainHandler.postDelayed(
                { verifyOrFallback(application, attempt + 1) },
                VERIFY_DELAY_MILLIS,
            )
            return
        }

        if (!dtReady) {
            runCatching { invokeOfficialDtInitTask(application) }
                .onSuccess { Log.d(TAG, "official DtInitTask fallback invoked") }
                .onFailure { error ->
                    val root = unwrap(error)
                    state.set(failureState(root))
                    Log.w(TAG, "official DT reporter fallback failed", root)
                }
        }
        if (!beaconReady) {
            runCatching { invokeOfficialBeaconInitTask(application) }
                .onSuccess { Log.d(TAG, "official BeaconSDKInitTask fallback invoked") }
                .onFailure { error ->
                    val root = unwrap(error)
                    beaconState.set(failureState(root))
                    Log.w(TAG, "official Beacon reporter fallback failed", root)
                }
        }
        verifyFallbackResult(attempt = 0)
    }

    private fun verifyFallbackResult(attempt: Int) {
        val dtReady = isOfficialReporterInitialized()
        val beaconReady = isOfficialBeaconInitialized()
        if (dtReady) {
            state.set(State.INITIALIZED)
        }
        if (beaconReady) beaconState.set(State.INITIALIZED)
        if (dtReady && beaconReady) {
            Log.d(TAG, "official DT and Beacon reporters initialized after fallback")
            return
        }

        if (attempt < MAX_FALLBACK_VERIFY_ATTEMPTS) {
            mainHandler.postDelayed(
                { verifyFallbackResult(attempt + 1) },
                FALLBACK_VERIFY_DELAY_MILLIS,
            )
            return
        }

        if (!dtReady && state.get() == State.WAITING_FOR_OFFICIAL_STARTUP) {
            state.set(State.FAILED)
        }
        if (!beaconReady && beaconState.get() == State.WAITING_FOR_OFFICIAL_STARTUP) {
            beaconState.set(State.FAILED)
        }
        Log.w(
            TAG,
            "official reporter startup incomplete: dt=${state.get()}, beacon=${beaconState.get()}",
        )
    }

    private fun failureState(error: Throwable): State {
        return if (error is ClassNotFoundException || error is NoClassDefFoundError) {
            State.UNAVAILABLE
        } else {
            State.FAILED
        }
    }

    private fun isOfficialReporterInitialized(): Boolean {
        return runCatching {
            val innerClass = Class.forName(VIDEO_REPORT_INNER)
            val inner = innerClass.getMethod("getInstance").invoke(null)
            (innerClass.getMethod("isInit").invoke(inner) as? Boolean) == true
        }.getOrDefault(false)
    }

    private fun isOfficialBeaconInitialized(): Boolean {
        return runCatching {
            val reportClass = Class.forName(QQ_BEACON_REPORT)
            val initialized = reportClass.getDeclaredField("a").apply {
                isAccessible = true
            }.get(null) as? AtomicBoolean
            initialized?.get() == true
        }.getOrDefault(false)
    }

    private fun setOfficialPageParams(
        videoReport: Class<*>,
        page: Any,
        params: Map<String, *>,
    ) {
        if (params.isEmpty()) return
        val pageParams = Class.forName(PAGE_PARAMS)
            .getConstructor(Map::class.java)
            .newInstance(params)
        videoReport.getMethod("setPageParams", Any::class.java, pageParams.javaClass)
            .invoke(null, page, pageParams)
    }

    private fun setOfficialPageEventType(videoReport: Class<*>, page: Any) {
        runCatching {
            val eventType = Class.forName(EVENT_AGING_TYPE)
                .getField("d")
                .get(null)
            videoReport.getMethod(
                "setEventType",
                Any::class.java,
                Class.forName(EVENT_AGING_TYPE),
            ).invoke(null, page, eventType)
        }
    }

    private fun invokeOfficialDtInitTask(application: Application) {
        val taskClass = Class.forName(DT_INIT_TASK)
        val task = taskClass.getDeclaredConstructor().newInstance()
        taskClass.getMethod("a", Context::class.java).invoke(task, application)
    }

    private fun invokeOfficialBeaconInitTask(application: Application) {
        val taskClass = Class.forName(BEACON_INIT_TASK)
        val task = taskClass.getDeclaredConstructor().newInstance()
        taskClass.getMethod("a", Context::class.java).invoke(task, application)
    }

    private fun unwrap(error: Throwable): Throwable {
        return if (error is InvocationTargetException && error.targetException != null) {
            error.targetException
        } else {
            error
        }
    }

    private fun chatListItemParams(
        contact: RecentContactInfo,
        homeUin: String,
        uid: String,
        firstExposure: Boolean,
    ): LinkedHashMap<String, Any?> {
        val params = linkedMapOf<String, Any?>()
        params["qq_pgid"] = CHAT_PAGE_ID
        params["qq_pgstp"] = 1
        params["qq_ref_pgid"] = CHAT_REF_PAGE_ID
        params["qq_ele_is_first_imp"] = if (firstExposure) 1 else 0
        params["qq_pg_contentid"] = CHAT_PAGE_ID
        params["msglist_type"] = 0
        if (homeUin.isNotBlank()) params["home_uin"] = homeUin
        if (uid.isNotBlank()) params["uid"] = uid

        params["qq_eid"] = ElementIds.MESSAGE_ENTRY
        params.putAll(chatListItemElementOnlyParams(contact))
        return params
    }

    private fun chatListItemElementOnlyParams(
        contact: RecentContactInfo,
    ): LinkedHashMap<String, Any?> {
        val params = linkedMapOf<String, Any?>()
        params["message_session_type"] = when (contact.chatType) {
            2 -> "0"
            1 -> "1"
            else -> "-1"
        }
        params["session_subtype"] = "0"
        params["is_mark"] = if (contact.unreadCnt > 0L) "1" else "0"
        contact.peerUin.takeIf { it > 0L }?.let { params["touin"] = it.toString() }
        params["is_set_top"] = if (contact.topFlag.toInt() != 0) "1" else "0"
        params["red_word_type"] = "0"
        return params
    }
}
