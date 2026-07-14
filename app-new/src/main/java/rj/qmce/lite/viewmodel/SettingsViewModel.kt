package rj.qmce.lite.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    data class UiSettings(
        val showTimeText: Boolean = true,
        val showPageIndicator: Boolean = true,
        val showOnlineStatus: Boolean = false,
    )

    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        UiSettings(
            showTimeText = preferences.getBoolean(KEY_SHOW_TIME_TEXT, true),
            showPageIndicator = preferences.getBoolean(KEY_SHOW_PAGE_INDICATOR, true),
            showOnlineStatus = preferences.getBoolean(KEY_SHOW_ONLINE_STATUS, false),
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

    private fun update(transform: (UiSettings) -> UiSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        preferences.edit()
            .putBoolean(KEY_SHOW_TIME_TEXT, updated.showTimeText)
            .putBoolean(KEY_SHOW_PAGE_INDICATOR, updated.showPageIndicator)
            .putBoolean(KEY_SHOW_ONLINE_STATUS, updated.showOnlineStatus)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "qmce_settings"
        const val KEY_SHOW_TIME_TEXT = "show_time_text"
        const val KEY_SHOW_PAGE_INDICATOR = "show_page_indicator"
        const val KEY_SHOW_ONLINE_STATUS = "show_online_status"
    }
}
