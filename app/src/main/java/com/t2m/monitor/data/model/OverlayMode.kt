package com.t2m.monitor.data.model

/**
 * 悬浮窗展示形态。
 *
 * 单击悬浮窗按 [next] 顺序循环切换。
 */
enum class OverlayMode {
    /** 悬浮球：仅显示电量与温度。 */
    BUBBLE,

    /** 迷你条：电量/温度/CPU/内存单行展示。 */
    MINI_BAR,

    /** 完整面板：8 项指标网格。 */
    PANEL;

    fun next(): OverlayMode = when (this) {
        BUBBLE -> MINI_BAR
        MINI_BAR -> PANEL
        PANEL -> BUBBLE
    }
}
