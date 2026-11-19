package com.t2m.monitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.OverlayMetric
import com.t2m.monitor.data.model.OverlayMode
import com.t2m.monitor.data.prefs.MonitorPreferences
import com.t2m.monitor.data.prefs.OverlaySettings
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
    private val logger: MonitorLogger,
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
        viewModelScope.launch {
            if (!preferences.setOverlayMode(mode)) {
                logger.warn("persist overlay mode failed")
            }
        }
    }

    fun setOpacity(percent: Int) {
        viewModelScope.launch {
            if (!preferences.setOverlayOpacity(percent)) {
                logger.warn("persist overlay opacity failed")
            }
        }
    }

    fun setMetricVisible(metric: OverlayMetric, visible: Boolean) {
        viewModelScope.launch {
            if (!preferences.setOverlayMetricVisible(metric, visible)) {
                logger.warn("persist overlay metric visibility failed")
            }
        }
    }

    fun resetPosition() {
        viewModelScope.launch {
            if (!preferences.resetOverlayPosition()) {
                logger.warn("reset overlay position failed")
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
