package com.t2m.monitor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 悬浮窗三态循环顺序。
 */
class OverlayModeTest {

    @Test
    fun `cycles bubble mini bar panel and back`() {
        assertEquals(OverlayMode.MINI_BAR, OverlayMode.BUBBLE.next())
        assertEquals(OverlayMode.PANEL, OverlayMode.MINI_BAR.next())
        assertEquals(OverlayMode.BUBBLE, OverlayMode.PANEL.next())
    }
}
