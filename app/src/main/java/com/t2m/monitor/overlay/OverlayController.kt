package com.t2m.monitor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.t2m.monitor.R
import com.t2m.monitor.core.FormatUtils
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.core.MonitorRuntime
import com.t2m.monitor.data.model.MonitorSnapshot
import com.t2m.monitor.data.model.OverlayMode
import com.t2m.monitor.data.prefs.MonitorPreferences
import com.t2m.monitor.data.prefs.OverlaySettings
import com.t2m.monitor.databinding.OverlayBubbleBinding
import com.t2m.monitor.databinding.OverlayMenuBinding
import com.t2m.monitor.databinding.OverlayMiniBarBinding
import com.t2m.monitor.databinding.OverlayPanelBinding
import com.t2m.monitor.di.ApplicationScope
import com.t2m.monitor.domain.MonitorEngine
import com.t2m.monitor.service.MonitorService
import com.t2m.monitor.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 悬浮窗控制器。
 *
 * 负责窗口的添加/移除、三态切换、拖拽后的位置持久化与菜单展示。
 * 所有 WindowManager 操作都在主线程执行（[Dispatchers.Main.immediate]）。
 */
@Singleton
class OverlayController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: MonitorPreferences,
    private val engine: MonitorEngine,
    private val runtime: MonitorRuntime,
    private val logger: MonitorLogger,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)

    private var container: FrameLayout? = null

    private var params: WindowManager.LayoutParams? = null

    private var mode: OverlayMode = OverlayMode.BUBBLE

    private var bubbleBinding: OverlayBubbleBinding? = null

    private var miniBarBinding: OverlayMiniBarBinding? = null

    private var panelBinding: OverlayPanelBinding? = null

    private var updateJob: Job? = null

    private var menuVisible = false

    private var showRequested = false

    fun isVisible(): Boolean = container != null

    /** 显示悬浮窗；缺少权限时给出提示且不创建窗口。 */
    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            logger.warn("overlay permission missing")
            runtime.setOverlayVisible(false)
            notifyPermissionMissing()
            return
        }
        if (container != null || showRequested) {
            return
        }
        showRequested = true
        scope.launch(Dispatchers.Main.immediate) {
            val settings = try {
                preferences.readOverlaySettings()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logger.warn("read overlay settings failed", error)
                showRequested = false
                return@launch
            }
            attach(settings)
        }
    }

    /** 隐藏悬浮窗并记录用户意图，下次服务启动不再自动显示。 */
    fun hide() {
        showRequested = false
        scope.launch(Dispatchers.Main.immediate) {
            detach()
            scope.launch(Dispatchers.IO) {
                if (!preferences.setOverlayEnabled(false)) {
                    logger.warn("persist overlay disabled failed")
                }
            }
        }
    }

    /** 服务销毁时移除窗口，但不改变用户的开关意图。 */
    fun release() {
        showRequested = false
        scope.launch(Dispatchers.Main.immediate) {
            detach()
        }
    }

    fun toggle() {
        if (container != null) {
            hide()
        } else {
            show()
        }
    }

    /** 屏幕旋转/折叠形态变化后重新夹取窗口。 */
    fun onConfigurationChanged() {
        scope.launch(Dispatchers.Main.immediate) {
            clampAndUpdate(persist = false)
        }
    }

    // 触摸与无障碍事件由 OverlayTouchHandler 统一处理（内部调用 performClick）。
    @SuppressLint("ClickableViewAccessibility")
    private fun attach(settings: OverlaySettings) {
        if (container != null || !showRequested) {
            return
        }
        val windowManager = windowManager ?: return
        val bounds = safeBounds()
        val initialX = if (settings.x != OverlaySettings.POSITION_UNSET) {
            settings.x
        } else {
            bounds.right - dp(BUBBLE_SIZE_DP) - dp(DEFAULT_MARGIN_DP)
        }
        val initialY = if (settings.y != OverlaySettings.POSITION_UNSET) {
            settings.y
        } else {
            bounds.top + dp(DEFAULT_TOP_MARGIN_DP)
        }

        val root = FrameLayout(context)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }
        root.setOnTouchListener(
            OverlayTouchHandler(
                windowManager = windowManager,
                view = root,
                params = layoutParams,
                boundsProvider = ::safeBounds,
                onTap = ::cycleMode,
                onLongPress = ::showMenu,
                onPositionSettled = ::persistPosition,
            ),
        )

        val added = runCatching { windowManager.addView(root, layoutParams) }
        if (added.isFailure) {
            logger.warn("add overlay view failed", added.exceptionOrNull())
            showRequested = false
            return
        }

        container = root
        params = layoutParams
        applyMode(settings.mode, persist = false)
        root.post { clampAndUpdate(persist = true) }
        startSnapshotUpdates()
        runtime.setOverlayVisible(true)
        scope.launch(Dispatchers.IO) {
            if (!preferences.setOverlayEnabled(true)) {
                logger.warn("persist overlay enabled failed")
            }
        }
    }

    private fun detach() {
        updateJob?.cancel()
        updateJob = null
        menuVisible = false
        val root = container
        if (root != null) {
            val removed = runCatching { windowManager?.removeViewImmediate(root) }
            if (removed.isFailure) {
                logger.warn("remove overlay view failed", removed.exceptionOrNull())
            }
        }
        container = null
        params = null
        bubbleBinding = null
        miniBarBinding = null
        panelBinding = null
        runtime.setOverlayVisible(false)
    }

    private fun startSnapshotUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch(Dispatchers.Main.immediate) {
            engine.snapshot.collect { snapshot -> render(snapshot) }
        }
    }

    private fun cycleMode() {
        if (menuVisible) {
            return
        }
        applyMode(mode.next(), persist = true)
    }

    private fun applyMode(target: OverlayMode, persist: Boolean) {
        val root = container ?: return
        val windowManager = windowManager ?: return
        root.removeAllViews()
        root.addView(createChild(target))
        mode = target
        menuVisible = false
        params?.let { layoutParams ->
            val updated = runCatching { windowManager.updateViewLayout(root, layoutParams) }
            if (updated.isFailure) {
                logger.warn("update overlay layout failed", updated.exceptionOrNull())
            }
        }
        root.post { clampAndUpdate(persist = false) }
        render(engine.snapshot.value)
        if (persist) {
            scope.launch(Dispatchers.IO) {
                if (!preferences.setOverlayMode(target)) {
                    logger.warn("persist overlay mode failed")
                }
            }
        }
    }

    private fun createChild(target: OverlayMode): View {
        val inflater = LayoutInflater.from(context)
        bubbleBinding = null
        miniBarBinding = null
        panelBinding = null
        return when (target) {
            OverlayMode.BUBBLE -> OverlayBubbleBinding.inflate(inflater)
                .also { bubbleBinding = it }
                .root

            OverlayMode.MINI_BAR -> OverlayMiniBarBinding.inflate(inflater)
                .also { miniBarBinding = it }
                .root

            OverlayMode.PANEL -> OverlayPanelBinding.inflate(inflater)
                .also { panelBinding = it }
                .root
        }
    }

    private fun render(snapshot: MonitorSnapshot) {
        val percentText = FormatUtils.formatPercent(snapshot.battery?.percent?.toFloat())
        val temperatureText = FormatUtils.formatTemperature(snapshot.battery?.temperatureC)

        bubbleBinding?.let { binding ->
            binding.bubbleValue.text = percentText
            binding.bubbleTemperature.text =
                FormatUtils.formatTemperatureCompact(snapshot.battery?.temperatureC)
        }
        miniBarBinding?.let { binding ->
            binding.miniBattery.text = percentText
            binding.miniTemperature.text = temperatureText
            binding.miniCpu.text = FormatUtils.formatPercent(snapshot.cpu?.totalUsagePercent)
            binding.miniMemory.text = FormatUtils.formatPercent(snapshot.memory?.usedPercent)
        }
        panelBinding?.let { binding ->
            binding.panelBattery.text = percentText
            binding.panelTemperature.text = temperatureText
            binding.panelCpu.text = FormatUtils.formatPercent(snapshot.cpu?.totalUsagePercent)
            binding.panelMemory.text = FormatUtils.formatPercent(snapshot.memory?.usedPercent)
            binding.panelStorage.text = FormatUtils.formatPercent(snapshot.storage?.usedPercent)
            binding.panelVoltage.text = FormatUtils.formatVoltage(snapshot.battery?.voltageMv)
            binding.panelCurrent.text = FormatUtils.formatCurrent(snapshot.battery?.currentMicroAmps)
            binding.panelMaxFrequency.text = FormatUtils.formatFrequency(snapshot.cpu?.maxFrequencyKHz)
            binding.panelUpdated.text = FormatUtils.formatTimeOfDay(snapshot.timestampMillis)
        }
        container?.contentDescription = context.getString(
            R.string.overlay_content_description,
            percentText,
            temperatureText,
        )
    }

    /** 长按菜单直接作为悬浮窗内容展示，避免用 PopupWindow 依赖窗口 token。 */
    private fun showMenu() {
        val root = container ?: return
        if (menuVisible) {
            return
        }
        menuVisible = true
        val menuBinding = OverlayMenuBinding.inflate(LayoutInflater.from(context))
        menuBinding.menuOpenApp.setOnClickListener {
            dismissMenu()
            openMainActivity()
        }
        menuBinding.menuHideOverlay.setOnClickListener {
            dismissMenu()
            hide()
        }
        menuBinding.menuStopService.setOnClickListener {
            dismissMenu()
            context.stopService(Intent(context, MonitorService::class.java))
        }
        val wrapper = FrameLayout(context).apply {
            setPadding(dp(MENU_WRAPPER_PADDING_DP), dp(MENU_WRAPPER_PADDING_DP), dp(MENU_WRAPPER_PADDING_DP), dp(MENU_WRAPPER_PADDING_DP))
            addView(menuBinding.root)
            setOnClickListener { dismissMenu() }
        }
        root.removeAllViews()
        root.addView(wrapper)
        root.post { clampAndUpdate(persist = false) }
    }

    private fun dismissMenu() {
        if (!menuVisible) {
            return
        }
        menuVisible = false
        applyMode(mode, persist = false)
    }

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val started = runCatching { context.startActivity(intent) }
        if (started.isFailure) {
            logger.warn("open main activity failed", started.exceptionOrNull())
        }
    }

    private fun clampAndUpdate(persist: Boolean) {
        val root = container ?: return
        val layoutParams = params ?: return
        val windowManager = windowManager ?: return
        val bounds = safeBounds()
        val maxX = (bounds.right - root.width).coerceAtLeast(bounds.left)
        val maxY = (bounds.bottom - root.height).coerceAtLeast(bounds.top)
        layoutParams.x = layoutParams.x.coerceIn(bounds.left, maxX)
        layoutParams.y = layoutParams.y.coerceIn(bounds.top, maxY)
        val updated = runCatching { windowManager.updateViewLayout(root, layoutParams) }
        if (updated.isFailure) {
            logger.warn("clamp overlay layout failed", updated.exceptionOrNull())
        }
        if (persist) {
            persistPosition(layoutParams.x, layoutParams.y)
        }
    }

    private fun persistPosition(x: Int, y: Int) {
        scope.launch(Dispatchers.IO) {
            if (!preferences.setOverlayPosition(x, y)) {
                logger.warn("persist overlay position failed")
            }
        }
    }

    /** 计算可放置悬浮窗的安全区域（避开状态栏、导航栏与挖孔）。 */
    @SuppressLint("DiscouragedApi")
    @Suppress("DEPRECATION")
    private fun safeBounds(): Rect {
        val windowManager = windowManager ?: return Rect(0, 0, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val bounds = metrics.bounds
            return Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom,
            )
        }
        val display = windowManager.defaultDisplay
        val screenSize = Point()
        display.getRealSize(screenSize)
        val topInset = systemDimension("status_bar_height")
        val bottomInset = systemDimension("navigation_bar_height")
        return Rect(0, topInset, screenSize.x, (screenSize.y - bottomInset).coerceAtLeast(topInset))
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemDimension(name: String): Int {
        val resourceId = context.resources.getIdentifier(name, "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun notifyPermissionMissing() {
        runCatching {
            Toast.makeText(context, R.string.overlay_permission_missing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val BUBBLE_SIZE_DP = 64
        const val DEFAULT_MARGIN_DP = 16
        const val DEFAULT_TOP_MARGIN_DP = 120
        const val MENU_WRAPPER_PADDING_DP = 8
    }
}
