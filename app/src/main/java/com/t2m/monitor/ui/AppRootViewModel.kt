package com.t2m.monitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.prefs.MonitorPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用根 ViewModel：决定首次启动是否进入权限引导。
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val preferences: MonitorPreferences,
    private val logger: MonitorLogger,
) : ViewModel() {

    /** 读取完成前为 null，避免引导页闪烁。 */
    val guideSeen: StateFlow<Boolean?> = preferences.guideSeen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun markGuideSeen() {
        viewModelScope.launch {
            if (!preferences.setGuideSeen(true)) {
                logger.warn("persist guide seen failed")
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
