package com.t2m.monitor.overlay

import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.animation.doOnEnd
import kotlin.math.hypot

/**
 * 悬浮窗触摸处理：拖拽、吸边、单击与长按。
 *
 * 触摸判定：
 * 1. 位移超过系统 touch slop 视为拖拽，拖拽过程中不触发单击/长按；
 * 2. 抬手后播放吸边动画，动画结束回调最终坐标用于持久化；
 * 3. 未拖拽时单击切换形态，长按弹出菜单。
 */
class OverlayTouchHandler(
    private val windowManager: WindowManager,
    private val view: View,
    private val params: WindowManager.LayoutParams,
    private val boundsProvider: () -> Rect,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onPositionSettled: (x: Int, y: Int) -> Unit,
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    private var longPressHandled = false

    private val gestureDetector = GestureDetector(
        view.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(event: MotionEvent) {
                if (!dragging) {
                    longPressHandled = true
                    onLongPress()
                }
            }
        },
    )

    override fun onTouch(target: View, event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                longPressHandled = false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                if (!dragging && hypot(deltaX, deltaY) > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    moveWithinBounds(deltaX, deltaY)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    snapToNearestEdge()
                } else if (event.actionMasked == MotionEvent.ACTION_UP && !longPressHandled) {
                    // 保证无障碍服务能收到点击事件；长按后不再触发单击。
                    view.performClick()
                    onTap()
                }
                dragging = false
            }

            else -> Unit
        }
        return true
    }

    private fun moveWithinBounds(deltaX: Float, deltaY: Float) {
        val bounds = boundsProvider()
        val maxX = (bounds.right - view.width).coerceAtLeast(bounds.left)
        val maxY = (bounds.bottom - view.height).coerceAtLeast(bounds.top)
        params.x = (startX + deltaX).toInt().coerceIn(bounds.left, maxX)
        params.y = (startY + deltaY).toInt().coerceIn(bounds.top, maxY)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun snapToNearestEdge() {
        val bounds = boundsProvider()
        val width = view.width
        val centerX = params.x + width / 2
        val targetX = if (centerX <= (bounds.left + bounds.right) / 2) {
            bounds.left
        } else {
            (bounds.right - width).coerceAtLeast(bounds.left)
        }
        animateTo(targetX)
    }

    private fun animateTo(targetX: Int) {
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_DURATION_MILLIS
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            doOnEnd { onPositionSettled(params.x, params.y) }
            start()
        }
    }

    private companion object {
        const val SNAP_DURATION_MILLIS = 180L
    }
}
