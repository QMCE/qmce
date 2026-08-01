package rj.qmce.lite.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.Flag
import rj.qmce.lite.data.update.OtaDownloadMode
import rj.qmce.lite.data.update.OtaSourceMode
import rj.qmce.lite.util.QmceLog

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    data class UiSettings(
        val showTimeText: Boolean = true,
        val showPageIndicator: Boolean = true,
        val showOnlineStatus: Boolean = false,
        val fullscreenDialogs: Boolean = true,
        val autoScale: Boolean = true,
        val manualScale: Float = DEFAULT_MANUAL_SCALE,
        val edgeSafeAreaEnabled: Boolean = true,
        val edgeSafeAreaScale: Float = DEFAULT_EDGE_SAFE_SCALE,
        val aiCustomEnabled: Boolean = false,
        val aiBaseUrl: String = "",
        val aiApiKey: String = "",
        val aiModel: String = "",
        val contactsSortMode: String = DEFAULT_CONTACTS_SORT_MODE,
        val notifyEnabled: Boolean = true,
        val notifyC2c: Boolean = true,
        val notifyGroup: Boolean = true,
        val notifyContact: Boolean = true,
        val keepAlive: Boolean = true,
        val messageRefreshMode: String = REFRESH_PUSH_ONLY,
        val liveUpdates: Boolean = true,
        val voiceBackground: Boolean = true,
        val voiceOngoingSurface: Boolean = true,
        val videoStrictForeground: Boolean = true,
        val callBlockBack: Boolean = true,
        val wearComplicationsEnabled: Boolean = true,
        val wearTilesEnabled: Boolean = true,
        val otaRequireWifi: Boolean = true,
        val otaSourceMode: String = OtaSourceMode.Auto.pref,
        val otaLastDownloadMode: String = OtaDownloadMode.WatchBrowser.pref,
        val appCenterReportingEnabled: Boolean = true,
        val qmceVerboseLog: Boolean = BuildConfig.DEBUG,
        val qlogLocalWriteEnabled: Boolean = false,
    )

    data class AiEndpoint(
        val baseUrl: String,
        val apiKey: String?,
        val model: String,
        val custom: Boolean,
    )

    private val preferences =
        application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    private fun loadSettings(): UiSettings = UiSettings(
        showTimeText = preferences.getBoolean(KEY_SHOW_TIME_TEXT, true),
        showPageIndicator = preferences.getBoolean(KEY_SHOW_PAGE_INDICATOR, true),
        showOnlineStatus = preferences.getBoolean(KEY_SHOW_ONLINE_STATUS, false),
        fullscreenDialogs = preferences.getBoolean(KEY_FULLSCREEN_DIALOGS, true),
        autoScale = preferences.getBoolean(KEY_AUTO_SCALE, true),
        manualScale = preferences.getFloat(KEY_MANUAL_SCALE, DEFAULT_MANUAL_SCALE)
            .coerceIn(MIN_MANUAL_SCALE, MAX_MANUAL_SCALE),
        edgeSafeAreaEnabled = preferences.getBoolean(KEY_EDGE_SAFE_AREA_ENABLED, true),
        edgeSafeAreaScale = preferences.getFloat(KEY_EDGE_SAFE_AREA_SCALE, DEFAULT_EDGE_SAFE_SCALE)
            .coerceIn(MIN_EDGE_SAFE_SCALE, MAX_EDGE_SAFE_SCALE),
        aiCustomEnabled = preferences.getBoolean(KEY_AI_CUSTOM_ENABLED, false),
        aiBaseUrl = preferences.getString(KEY_AI_BASE_URL, "").orEmpty(),
        aiApiKey = preferences.getString(KEY_AI_API_KEY, "").orEmpty(),
        aiModel = preferences.getString(KEY_AI_MODEL, "").orEmpty(),
        contactsSortMode = preferences.getString(KEY_CONTACTS_SORT_MODE, DEFAULT_CONTACTS_SORT_MODE)
            .orEmpty()
            .ifBlank { DEFAULT_CONTACTS_SORT_MODE },
        notifyEnabled = preferences.getBoolean(KEY_NOTIFY_ENABLED, true),
        notifyC2c = preferences.getBoolean(KEY_NOTIFY_C2C, true),
        notifyGroup = preferences.getBoolean(KEY_NOTIFY_GROUP, true),
        notifyContact = preferences.getBoolean(KEY_NOTIFY_CONTACT, true),
        keepAlive = preferences.getBoolean(KEY_KEEP_ALIVE, true),
        messageRefreshMode = preferences.getString(KEY_MESSAGE_REFRESH_MODE, REFRESH_PUSH_ONLY)
            .orEmpty()
            .ifBlank { REFRESH_PUSH_ONLY },
        liveUpdates = preferences.getBoolean(KEY_LIVE_UPDATES, true),
        voiceBackground = preferences.getBoolean(KEY_VOICE_BACKGROUND, true),
        voiceOngoingSurface = preferences.getBoolean(KEY_VOICE_ONGOING_SURFACE, true),
        videoStrictForeground = preferences.getBoolean(KEY_VIDEO_STRICT_FOREGROUND, true),
        callBlockBack = preferences.getBoolean(KEY_CALL_BLOCK_BACK, true),
        wearComplicationsEnabled = preferences.getBoolean(KEY_WEAR_COMPLICATIONS, true),
        wearTilesEnabled = preferences.getBoolean(KEY_WEAR_TILES, true),
        otaRequireWifi = preferences.getBoolean(KEY_OTA_REQUIRE_WIFI, true),
        otaSourceMode = preferences.getString(KEY_OTA_SOURCE_MODE, OtaSourceMode.Auto.pref)
            .orEmpty()
            .ifBlank { OtaSourceMode.Auto.pref },
        otaLastDownloadMode = preferences
            .getString(KEY_OTA_LAST_DOWNLOAD_MODE, OtaDownloadMode.WatchBrowser.pref)
            .orEmpty()
            .ifBlank { OtaDownloadMode.WatchBrowser.pref },
        appCenterReportingEnabled = preferences.getBoolean(KEY_APP_CENTER_REPORTING, true),
        qmceVerboseLog = preferences.getBoolean(KEY_QMCE_VERBOSE_LOG, BuildConfig.DEBUG),
        qlogLocalWriteEnabled = preferences.getBoolean(KEY_QLOG_LOCAL_WRITE, false),
    )

    fun setShowTimeText(show: Boolean) = update { it.copy(showTimeText = show) }
    fun setShowPageIndicator(show: Boolean) = update { it.copy(showPageIndicator = show) }
    fun setShowOnlineStatus(show: Boolean) = update { it.copy(showOnlineStatus = show) }
    fun setFullscreenDialogs(fullscreen: Boolean) = update { it.copy(fullscreenDialogs = fullscreen) }
    fun setAutoScale(enabled: Boolean) = update { it.copy(autoScale = enabled) }
    fun setManualScale(scale: Float) =
        update { it.copy(manualScale = scale.coerceIn(MIN_MANUAL_SCALE, MAX_MANUAL_SCALE)) }
    fun setEdgeSafeAreaEnabled(enabled: Boolean) = update { it.copy(edgeSafeAreaEnabled = enabled) }
    fun setEdgeSafeAreaScale(scale: Float) =
        update { it.copy(edgeSafeAreaScale = scale.coerceIn(MIN_EDGE_SAFE_SCALE, MAX_EDGE_SAFE_SCALE)) }
    fun setAiCustomEnabled(enabled: Boolean) = update { it.copy(aiCustomEnabled = enabled) }
    fun setAiBaseUrl(value: String) = update { it.copy(aiBaseUrl = value) }
    fun setAiApiKey(value: String) = update { it.copy(aiApiKey = value) }
    fun setAiModel(value: String) = update { it.copy(aiModel = value) }
    fun setContactsSortMode(mode: String) =
        update { it.copy(contactsSortMode = mode.ifBlank { DEFAULT_CONTACTS_SORT_MODE }) }

    fun setNotifyEnabled(enabled: Boolean) = update { it.copy(notifyEnabled = enabled) }
    fun setNotifyC2c(enabled: Boolean) = update { it.copy(notifyC2c = enabled) }
    fun setNotifyGroup(enabled: Boolean) = update { it.copy(notifyGroup = enabled) }
    fun setNotifyContact(enabled: Boolean) = update { it.copy(notifyContact = enabled) }
    fun setKeepAlive(enabled: Boolean) = update { it.copy(keepAlive = enabled) }
    fun setMessageRefreshMode(mode: String) =
        update { it.copy(messageRefreshMode = mode.ifBlank { REFRESH_PUSH_ONLY }) }
    fun setLiveUpdates(enabled: Boolean) = update { it.copy(liveUpdates = enabled) }
    fun setVoiceBackground(enabled: Boolean) = update { it.copy(voiceBackground = enabled) }
    fun setVoiceOngoingSurface(enabled: Boolean) = update { it.copy(voiceOngoingSurface = enabled) }
    fun setVideoStrictForeground(enabled: Boolean) =
        update { it.copy(videoStrictForeground = enabled) }
    fun setCallBlockBack(enabled: Boolean) = update { it.copy(callBlockBack = enabled) }
    fun setWearComplicationsEnabled(enabled: Boolean) =
        update { it.copy(wearComplicationsEnabled = enabled) }
    fun setWearTilesEnabled(enabled: Boolean) = update { it.copy(wearTilesEnabled = enabled) }
    fun setOtaRequireWifi(enabled: Boolean) = update { it.copy(otaRequireWifi = enabled) }
    fun setOtaSourceMode(mode: OtaSourceMode) = update { it.copy(otaSourceMode = mode.pref) }
    fun setOtaLastDownloadMode(mode: OtaDownloadMode) =
        update { it.copy(otaLastDownloadMode = mode.pref) }

    fun setAppCenterReportingEnabled(enabled: Boolean) {
        val effective = if (BuildConfig.DEBUG) true else enabled
        update { it.copy(appCenterReportingEnabled = effective) }
        applyAppCenterEnabled(effective)
    }

    fun setQmceVerboseLog(enabled: Boolean) {
        update { it.copy(qmceVerboseLog = enabled) }
        QmceLog.setVerboseEnabled(enabled)
    }

    fun setQlogLocalWriteEnabled(enabled: Boolean) {
        update { it.copy(qlogLocalWriteEnabled = enabled) }
        applyQlogLocalWriteEnabled(enabled)
    }

    private fun update(transform: (UiSettings) -> UiSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        preferences.edit {
            putBoolean(KEY_SHOW_TIME_TEXT, updated.showTimeText)
                .putBoolean(KEY_SHOW_PAGE_INDICATOR, updated.showPageIndicator)
                .putBoolean(KEY_SHOW_ONLINE_STATUS, updated.showOnlineStatus)
                .putBoolean(KEY_FULLSCREEN_DIALOGS, updated.fullscreenDialogs)
                .putBoolean(KEY_AUTO_SCALE, updated.autoScale)
                .putFloat(KEY_MANUAL_SCALE, updated.manualScale)
                .putBoolean(KEY_EDGE_SAFE_AREA_ENABLED, updated.edgeSafeAreaEnabled)
                .putFloat(KEY_EDGE_SAFE_AREA_SCALE, updated.edgeSafeAreaScale)
                .putBoolean(KEY_AI_CUSTOM_ENABLED, updated.aiCustomEnabled)
                .putString(KEY_AI_BASE_URL, updated.aiBaseUrl)
                .putString(KEY_AI_API_KEY, updated.aiApiKey)
                .putString(KEY_AI_MODEL, updated.aiModel)
                .putString(KEY_CONTACTS_SORT_MODE, updated.contactsSortMode)
                .putBoolean(KEY_NOTIFY_ENABLED, updated.notifyEnabled)
                .putBoolean(KEY_NOTIFY_C2C, updated.notifyC2c)
                .putBoolean(KEY_NOTIFY_GROUP, updated.notifyGroup)
                .putBoolean(KEY_NOTIFY_CONTACT, updated.notifyContact)
                .putBoolean(KEY_KEEP_ALIVE, updated.keepAlive)
                .putString(KEY_MESSAGE_REFRESH_MODE, updated.messageRefreshMode)
                .putBoolean(KEY_LIVE_UPDATES, updated.liveUpdates)
                .putBoolean(KEY_VOICE_BACKGROUND, updated.voiceBackground)
                .putBoolean(KEY_VOICE_ONGOING_SURFACE, updated.voiceOngoingSurface)
                .putBoolean(KEY_VIDEO_STRICT_FOREGROUND, updated.videoStrictForeground)
                .putBoolean(KEY_CALL_BLOCK_BACK, updated.callBlockBack)
                .putBoolean(KEY_WEAR_COMPLICATIONS, updated.wearComplicationsEnabled)
                .putBoolean(KEY_WEAR_TILES, updated.wearTilesEnabled)
                .putBoolean(KEY_OTA_REQUIRE_WIFI, updated.otaRequireWifi)
                .putString(KEY_OTA_SOURCE_MODE, updated.otaSourceMode)
                .putString(KEY_OTA_LAST_DOWNLOAD_MODE, updated.otaLastDownloadMode)
                .putBoolean(KEY_APP_CENTER_REPORTING, updated.appCenterReportingEnabled)
                .putBoolean(KEY_QMCE_VERBOSE_LOG, updated.qmceVerboseLog)
                .putBoolean(KEY_QLOG_LOCAL_WRITE, updated.qlogLocalWriteEnabled)
        }
    }

    companion object {
        const val PREFERENCES_NAME = "qmce_settings"
        private const val KEY_SHOW_TIME_TEXT = "show_time_text"
        private const val KEY_SHOW_PAGE_INDICATOR = "show_page_indicator"
        private const val KEY_SHOW_ONLINE_STATUS = "show_online_status"
        private const val KEY_FULLSCREEN_DIALOGS = "fullscreen_dialogs"
        private const val KEY_AUTO_SCALE = "auto_scale"
        private const val KEY_MANUAL_SCALE = "manual_scale"
        private const val KEY_EDGE_SAFE_AREA_ENABLED = "edge_safe_area_enabled"
        private const val KEY_EDGE_SAFE_AREA_SCALE = "edge_safe_area_scale"
        private const val KEY_AI_CUSTOM_ENABLED = "ai_custom_enabled"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        const val KEY_CONTACTS_SORT_MODE = "contacts_sort_mode"
        const val DEFAULT_CONTACTS_SORT_MODE = "category"
        private const val DEFAULT_MANUAL_SCALE = 1.0f
        private const val MIN_MANUAL_SCALE = 0.75f
        private const val MAX_MANUAL_SCALE = 1.75f
        private const val DEFAULT_EDGE_SAFE_SCALE = 1.0f
        private const val MIN_EDGE_SAFE_SCALE = 0.25f
        private const val MAX_EDGE_SAFE_SCALE = 1.5f

        const val KEY_NOTIFY_ENABLED = "notify_enabled"
        const val KEY_NOTIFY_C2C = "notify_c2c"
        const val KEY_NOTIFY_GROUP = "notify_group"
        const val KEY_NOTIFY_CONTACT = "notify_contact"
        const val KEY_KEEP_ALIVE = "keep_alive"
        const val KEY_MESSAGE_REFRESH_MODE = "message_refresh_mode"
        const val KEY_LIVE_UPDATES = "live_updates"
        const val KEY_VOICE_BACKGROUND = "voice_background"
        const val KEY_VOICE_ONGOING_SURFACE = "voice_ongoing_surface"
        const val KEY_VIDEO_STRICT_FOREGROUND = "video_strict_foreground"
        const val KEY_CALL_BLOCK_BACK = "call_block_back"
        const val KEY_WEAR_COMPLICATIONS = "wear_complications"
        const val KEY_WEAR_TILES = "wear_tiles"
        const val KEY_WATCHLIST_JSON = "tile_watchlist_json"
        const val KEY_OTA_REQUIRE_WIFI = "ota_require_wifi"
        const val KEY_OTA_SOURCE_MODE = "ota_source_mode"
        const val KEY_OTA_LAST_DOWNLOAD_MODE = "ota_last_download_mode"
        const val KEY_APP_CENTER_REPORTING = "app_center_reporting_enabled"
        const val KEY_QMCE_VERBOSE_LOG = "qmce_verbose_log"
        const val KEY_QLOG_LOCAL_WRITE = "qlog_local_write_enabled"

        const val REFRESH_PUSH_ONLY = "push_only"
        const val REFRESH_15S = "15s"
        const val REFRESH_30S = "30s"
        const val REFRESH_1M = "1m"
        const val REFRESH_5M = "5m"

        const val BUILTIN_AI_BASE_URL = "https://opencode.ai/zen/v1/chat/completions"
        const val BUILTIN_AI_MODEL = "big-pickle"

        fun resolveAiEndpoint(context: Context): AiEndpoint {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_AI_CUSTOM_ENABLED, false)
            val baseUrl = prefs.getString(KEY_AI_BASE_URL, "").orEmpty().trim()
            val apiKey = prefs.getString(KEY_AI_API_KEY, "").orEmpty().trim()
            val model = prefs.getString(KEY_AI_MODEL, "").orEmpty().trim()
            return if (enabled && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
                AiEndpoint(
                    baseUrl = normalizeCompletionsUrl(baseUrl),
                    apiKey = apiKey,
                    model = model,
                    custom = true,
                )
            } else {
                AiEndpoint(
                    baseUrl = BUILTIN_AI_BASE_URL,
                    apiKey = null,
                    model = BUILTIN_AI_MODEL,
                    custom = false,
                )
            }
        }

        fun isAppCenterReportingEnabled(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_APP_CENTER_REPORTING, true)

        fun applyAppCenterEnabled(enabled: Boolean) {
            runCatching {
                Analytics.setEnabled(enabled)
                Crashes.setEnabled(enabled)
            }
        }

        fun applyQlogLocalWriteEnabled(enabled: Boolean) {
            Flag.DISABLE_QLOG_LOCAL_WRITE = !enabled
        }

        /** Apply QmceLog verbose + QQ QLog local-write flags from prefs at process start. */
        fun applyDiagnosticFlags(context: Context) {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val verbose = prefs.getBoolean(KEY_QMCE_VERBOSE_LOG, BuildConfig.DEBUG)
            val qlogWrite = prefs.getBoolean(KEY_QLOG_LOCAL_WRITE, false)
            QmceLog.setVerboseEnabled(verbose)
            applyQlogLocalWriteEnabled(qlogWrite)
        }

        fun setOtaLastDownloadMode(context: Context, mode: OtaDownloadMode) {
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_OTA_LAST_DOWNLOAD_MODE, mode.pref)
                }
        }

        fun otaLastDownloadMode(context: Context): OtaDownloadMode =
            OtaDownloadMode.fromPref(
                context.applicationContext
                    .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_OTA_LAST_DOWNLOAD_MODE, OtaDownloadMode.WatchBrowser.pref),
            )

        private fun normalizeCompletionsUrl(raw: String): String {
            val trimmed = raw.trimEnd('/')
            return when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                else -> trimmed
            }
        }
    }
}
