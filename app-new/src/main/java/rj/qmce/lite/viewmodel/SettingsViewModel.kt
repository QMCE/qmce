package rj.qmce.lite.viewmodel

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    data class UiSettings(
        val showTimeText: Boolean = true,
        val showPageIndicator: Boolean = true,
        val showOnlineStatus: Boolean = false,
        val fullscreenDialogs: Boolean = true,
        val autoScale: Boolean = true,
        val manualScale: Float = DEFAULT_MANUAL_SCALE,
    )

    private val preferences =
        application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        UiSettings(
            showTimeText = preferences.getBoolean(KEY_SHOW_TIME_TEXT, true),
            showPageIndicator = preferences.getBoolean(KEY_SHOW_PAGE_INDICATOR, true),
            showOnlineStatus = preferences.getBoolean(KEY_SHOW_ONLINE_STATUS, false),
            fullscreenDialogs = preferences.getBoolean(KEY_FULLSCREEN_DIALOGS, true),
            autoScale = preferences.getBoolean(KEY_AUTO_SCALE, true),
            manualScale = preferences.getFloat(KEY_MANUAL_SCALE, DEFAULT_MANUAL_SCALE)
                .coerceIn(MIN_MANUAL_SCALE, MAX_MANUAL_SCALE),
        )
    )
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    fun setShowTimeText(show: Boolean) {
        update { it.copy(showTimeText = show) }
    }

    fun setShowPageIndicator(show: Boolean) {
        update { it.copy(showPageIndicator = show) }
    }

    fun setShowOnlineStatus(show: Boolean) {
        update { it.copy(showOnlineStatus = show) }
    }

    fun setFullscreenDialogs(fullscreen: Boolean) {
        update { it.copy(fullscreenDialogs = fullscreen) }
    }

    fun setAutoScale(enabled: Boolean) {
        update { it.copy(autoScale = enabled) }
    }

    fun setManualScale(scale: Float) {
        update { it.copy(manualScale = scale.coerceIn(MIN_MANUAL_SCALE, MAX_MANUAL_SCALE)) }
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
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "qmce_settings"
        const val KEY_SHOW_TIME_TEXT = "show_time_text"
        const val KEY_SHOW_PAGE_INDICATOR = "show_page_indicator"
        const val KEY_SHOW_ONLINE_STATUS = "show_online_status"
        const val KEY_FULLSCREEN_DIALOGS = "fullscreen_dialogs"
        const val KEY_AUTO_SCALE = "auto_scale"
        const val KEY_MANUAL_SCALE = "manual_scale"
        const val DEFAULT_MANUAL_SCALE = 1.1f
        const val MIN_MANUAL_SCALE = 0.75f
        const val MAX_MANUAL_SCALE = 2.0f
    }
}
