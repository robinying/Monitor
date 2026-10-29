package com.t2m.monitor.data.collector

import com.t2m.monitor.core.FeatureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 功能开关决定注册哪些采集器。
 */
class CollectorSelectionTest {

    private val collectors = listOf(
        fake(CollectorId.BATTERY),
        fake(CollectorId.CPU),
        fake(CollectorId.MEMORY),
        fake(CollectorId.STORAGE),
        fake(CollectorId.THERMAL),
        fake(CollectorId.USAGE),
        fake(CollectorId.FPS),
        fake(CollectorId.APP_STORAGE),
    )

    @Test
    fun `keeps all collectors when every feature is enabled`() {
        val selected = CollectorSelection.select(collectors, FeatureConfig())
        assertEquals(collectors, selected)
    }

    @Test
    fun `removes usage and storage collectors when usage stats disabled`() {
        val selected = CollectorSelection.select(
            collectors,
            FeatureConfig(usageStats = false),
        )
        assertEquals(
            listOf(
                CollectorId.BATTERY,
                CollectorId.CPU,
                CollectorId.MEMORY,
                CollectorId.STORAGE,
                CollectorId.THERMAL,
                CollectorId.FPS,
            ),
            selected.map { it.id },
        )
    }

    @Test
    fun `removes fps collector when privileged disabled`() {
        val selected = CollectorSelection.select(
            collectors,
            FeatureConfig(privileged = false),
        )
        assertEquals(
            listOf(
                CollectorId.BATTERY,
                CollectorId.CPU,
                CollectorId.MEMORY,
                CollectorId.STORAGE,
                CollectorId.THERMAL,
                CollectorId.USAGE,
                CollectorId.APP_STORAGE,
            ),
            selected.map { it.id },
        )
    }

    @Test
    fun `disabling overlay and alerts does not remove baseline collectors`() {
        val selected = CollectorSelection.select(
            collectors,
            FeatureConfig(overlay = false, alerts = false),
        )
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.any { it.id == CollectorId.BATTERY })
    }

    private fun fake(id: CollectorId): Collector<Int> = object : Collector<Int> {
        override val id: CollectorId = id
        override val intervalMillis: Long = 1_000L
        override suspend fun collect(): Int? = null
    }
}
