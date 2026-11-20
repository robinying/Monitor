package com.robin.monitor.data.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 使用情况快照：全空不算成功，部分数据可用时保留并标记 partial。
 */
class UsageSnapshotTest {

    @Test
    fun `all empty snapshot is treated as no data`() {
        assertNull(usageSnapshotOrNull(null, null, null))
    }

    @Test
    fun `foreground only is partial but usable`() {
        val snapshot = usageSnapshotOrNull("com.example.app", "Example", null)

        assertTrue(snapshot!!.partial)
        assertTrue(snapshot.hasData)
        assertEquals("com.example.app", snapshot.foregroundPackage)
    }

    @Test
    fun `network only is partial but usable`() {
        val snapshot = usageSnapshotOrNull(null, null, listOf(1L, 2L, 3L, 4L))

        assertTrue(snapshot!!.partial)
        assertEquals(4L, snapshot.todayRxBytes)
        assertEquals(6L, snapshot.todayTxBytes)
    }

    @Test
    fun `missing one network type marks the snapshot partial`() {
        val snapshot = usageSnapshotOrNull("com.example.app", "Example", listOf(1L, 2L, null, null))

        assertTrue(snapshot!!.partial)
        assertEquals(1L, snapshot.todayRxBytes)
    }

    @Test
    fun `complete snapshot is not partial`() {
        val snapshot = usageSnapshotOrNull(
            "com.example.app",
            "Example",
            listOf(1L, 2L, 3L, 4L),
        )

        assertFalse(snapshot!!.partial)
        assertTrue(snapshot.hasData)
    }

}
