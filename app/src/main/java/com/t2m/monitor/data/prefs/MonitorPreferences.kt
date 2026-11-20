package com.t2m.monitor.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.OverlayMetric
import com.t2m.monitor.data.model.OverlayMode
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 悬浮窗位置与用户偏好持久化。
 *
 * 读取失败回退默认值；写入失败在本类记录日志，不把异常抛到界面。
 */
@Singleton
class MonitorPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val logger: MonitorLogger,
) {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error ->
            logger.warn("read monitor preferences failed", error)
            emit(emptyPreferences())
        }

    /** 用户是否希望显示悬浮窗（服务启动时据此自动恢复）。 */
    val overlayEnabled: Flow<Boolean> = preferences
        .map { values -> values[KEY_OVERLAY_ENABLED] ?: false }

    /** 权限引导是否已读，用于首次启动自动跳转。 */
    val guideSeen: Flow<Boolean> = preferences
        .map { values -> values[KEY_GUIDE_SEEN] ?: false }

    /** 悬浮窗样式与指标可见性；设置页与悬浮窗共用。 */
    val overlaySettings: Flow<OverlaySettings> = preferences.map(::toOverlaySettings)

    /** 一次性读取悬浮窗初始状态，供 attach 时使用。 */
    suspend fun readOverlaySettings(): OverlaySettings = toOverlaySettings(preferences.first())

    private fun toOverlaySettings(preferences: Preferences): OverlaySettings {
        val storedMode = preferences[KEY_OVERLAY_MODE]
        return OverlaySettings(
            mode = OverlayMode.entries.firstOrNull { mode -> mode.name == storedMode }
                ?: OverlayMode.BUBBLE,
            x = preferences[KEY_OVERLAY_X] ?: OverlaySettings.POSITION_UNSET,
            y = preferences[KEY_OVERLAY_Y] ?: OverlaySettings.POSITION_UNSET,
            opacityPercent = (preferences[KEY_OVERLAY_OPACITY]
                ?: OverlaySettings.DEFAULT_OPACITY_PERCENT)
                .coerceIn(OverlaySettings.MIN_OPACITY_PERCENT, 100),
            visibleMetrics = OverlayMetric.entries.filter { metric ->
                preferences[metric.preferenceKey()] ?: true
            }.toSet(),
        )
    }

    suspend fun setOverlayEnabled(enabled: Boolean) = update {
        it[KEY_OVERLAY_ENABLED] = enabled
    }

    suspend fun setOverlayMode(mode: OverlayMode) = update {
        it[KEY_OVERLAY_MODE] = mode.name
    }

    suspend fun setOverlayPosition(x: Int, y: Int) = update {
        it[KEY_OVERLAY_X] = x
        it[KEY_OVERLAY_Y] = y
    }

    suspend fun resetOverlayPosition() = update {
        it.remove(KEY_OVERLAY_X)
        it.remove(KEY_OVERLAY_Y)
    }

    suspend fun setOverlayOpacity(percent: Int) = update {
        it[KEY_OVERLAY_OPACITY] = percent
    }

    suspend fun setOverlayMetricVisible(metric: OverlayMetric, visible: Boolean) = update {
        it[metric.preferenceKey()] = visible
    }

    private fun OverlayMetric.preferenceKey() = when (this) {
        OverlayMetric.BATTERY -> KEY_OVERLAY_BATTERY
        OverlayMetric.TEMPERATURE -> KEY_OVERLAY_TEMPERATURE
        OverlayMetric.CPU -> KEY_OVERLAY_CPU
        OverlayMetric.MEMORY -> KEY_OVERLAY_MEMORY
        OverlayMetric.STORAGE -> KEY_OVERLAY_STORAGE
        OverlayMetric.FPS -> KEY_OVERLAY_FPS
    }

    suspend fun setGuideSeen(seen: Boolean) = update {
        it[KEY_GUIDE_SEEN] = seen
    }

    private suspend fun update(block: suspend (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // DataStore 写入失败不影响运行时功能，记录日志即可。
            logger.warn("write monitor preferences failed", error)
        }
    }

    private companion object {
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_OVERLAY_MODE = stringPreferencesKey("overlay_mode")
        val KEY_OVERLAY_X = intPreferencesKey("overlay_x")
        val KEY_OVERLAY_Y = intPreferencesKey("overlay_y")
        val KEY_OVERLAY_OPACITY = intPreferencesKey("overlay_opacity")
        val KEY_OVERLAY_BATTERY = booleanPreferencesKey("overlay_show_battery")
        val KEY_OVERLAY_TEMPERATURE = booleanPreferencesKey("overlay_show_temperature")
        val KEY_OVERLAY_CPU = booleanPreferencesKey("overlay_show_cpu")
        val KEY_OVERLAY_MEMORY = booleanPreferencesKey("overlay_show_memory")
        val KEY_OVERLAY_STORAGE = booleanPreferencesKey("overlay_show_storage")
        val KEY_OVERLAY_FPS = booleanPreferencesKey("overlay_show_fps")
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
    val mode: OverlayMode,
    val x: Int,
    val y: Int,
    val opacityPercent: Int = DEFAULT_OPACITY_PERCENT,
    val visibleMetrics: Set<OverlayMetric> = OverlayMetric.entries.toSet(),
) {
    fun isVisible(metric: OverlayMetric): Boolean = metric in visibleMetrics

    companion object {
        const val POSITION_UNSET = -1
        const val DEFAULT_OPACITY_PERCENT = 100
        const val MIN_OPACITY_PERCENT = 20
    }
}
