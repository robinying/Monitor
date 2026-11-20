package com.robin.monitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robin.monitor.data.model.OverlayMetric
import com.robin.monitor.data.model.OverlayMode
import com.robin.monitor.data.prefs.MonitorPreferences
import com.robin.monitor.data.prefs.OverlaySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel：悬浮窗形态、透明度、指标可见性与位置重置。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: MonitorPreferences,
) : ViewModel() {

    val settings: StateFlow<OverlaySettings> = preferences.overlaySettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = OverlaySettings(
            mode = OverlayMode.BUBBLE,
            x = OverlaySettings.POSITION_UNSET,
            y = OverlaySettings.POSITION_UNSET,
        ),
    )

    fun setMode(mode: OverlayMode) {
        viewModelScope.launch { preferences.setOverlayMode(mode) }
    }

    fun setOpacity(percent: Int) {
        viewModelScope.launch { preferences.setOverlayOpacity(percent) }
    }

    fun setMetricVisible(metric: OverlayMetric, visible: Boolean) {
        viewModelScope.launch { preferences.setOverlayMetricVisible(metric, visible) }
    }

    fun resetPosition() {
        viewModelScope.launch { preferences.resetOverlayPosition() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
