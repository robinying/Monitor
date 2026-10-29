package com.t2m.monitor.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.t2m.monitor.data.model.OverlayMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 悬浮窗位置与用户偏好持久化。
 *
 * 所有写操作返回是否成功，失败由调用方记录日志；不吞掉异常也不抛出到界面。
 */
@Singleton
class MonitorPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** 用户是否希望显示悬浮窗（服务启动时据此自动恢复）。 */
    val overlayEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_OVERLAY_ENABLED] ?: false }

    /** 权限引导是否已读，用于首次启动自动跳转。 */
    val guideSeen: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_GUIDE_SEEN] ?: false }

    /** 一次性读取悬浮窗初始状态，供 attach 时使用。 */
    suspend fun readOverlaySettings(): OverlaySettings {
        val preferences = dataStore.data.first()
        val storedMode = preferences[KEY_OVERLAY_MODE]
        return OverlaySettings(
            enabled = preferences[KEY_OVERLAY_ENABLED] ?: false,
            mode = OverlayMode.entries.firstOrNull { mode -> mode.name == storedMode }
                ?: OverlayMode.BUBBLE,
            x = preferences[KEY_OVERLAY_X] ?: OverlaySettings.POSITION_UNSET,
            y = preferences[KEY_OVERLAY_Y] ?: OverlaySettings.POSITION_UNSET,
        )
    }

    suspend fun setOverlayEnabled(enabled: Boolean): Boolean = update {
        it[KEY_OVERLAY_ENABLED] = enabled
    }

    suspend fun setOverlayMode(mode: OverlayMode): Boolean = update {
        it[KEY_OVERLAY_MODE] = mode.name
    }

    suspend fun setOverlayPosition(x: Int, y: Int): Boolean = update {
        it[KEY_OVERLAY_X] = x
        it[KEY_OVERLAY_Y] = y
    }

    suspend fun setGuideSeen(seen: Boolean): Boolean = update {
        it[KEY_GUIDE_SEEN] = seen
    }

    private suspend fun update(block: suspend (MutablePreferences) -> Unit): Boolean =
        try {
            dataStore.edit(block)
            true
        } catch (ioError: IOException) {
            // DataStore 写入失败不影响运行时功能，由调用方记录日志。
            false
        }

    private companion object {
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_OVERLAY_MODE = stringPreferencesKey("overlay_mode")
        val KEY_OVERLAY_X = intPreferencesKey("overlay_x")
        val KEY_OVERLAY_Y = intPreferencesKey("overlay_y")
        val KEY_GUIDE_SEEN = booleanPreferencesKey("guide_seen")
    }
}

/**
 * 悬浮窗持久化状态。
 *
 * @param x 上次窗口左上角 x；[POSITION_UNSET] 表示尚未设置
 * @param y 上次窗口左上角 y
 */
data class OverlaySettings(
    val enabled: Boolean,
    val mode: OverlayMode,
    val x: Int,
    val y: Int,
) {
    companion object {
        const val POSITION_UNSET = -1
    }
}
