package com.t2m.monitor.domain

import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.BatteryStatus
import com.t2m.monitor.data.model.MonitorSnapshot
import com.t2m.monitor.data.model.PlugState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 聚合器并发安全：暂停/停止时 flushNow 与采样 add 可能来自不同协程。
 */
class MetricAggregatorConcurrencyTest {

    @Test
    fun `concurrent add and flush do not throw`() {
        val aggregator = MetricAggregator { 0L }
        val executor = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)

        val writer = executor.submit {
            start.await()
            repeat(2_000) { aggregator.add(snapshot(it)) }
        }
        val flusher = executor.submit {
            start.await()
            repeat(2_000) { aggregator.flush(it.toLong()) }
        }
        val reader = executor.submit {
            start.await()
            repeat(2_000) { aggregator.shouldFlush() }
        }

        start.countDown()
        listOf(writer, flusher, reader).forEach { task ->
            try {
                task.get(5, TimeUnit.SECONDS)
            } catch (error: Exception) {
                failure.set(error)
            }
        }
        executor.shutdownNow()

        assertNull(failure.get())
        // 最后可能仍有未 flush 的写入，只验证调用本身不再抛异常。
        aggregator.flush(0L)
    }

    private fun snapshot(percent: Int): MonitorSnapshot = MonitorSnapshot(
        battery = BatterySnapshot(
            percent = percent % 100,
            temperatureC = 30.0,
            currentMicroAmps = -100_000,
            voltageMv = 4000,
            status = BatteryStatus.DISCHARGING,
            plugged = PlugState.NONE,
            health = 2,
        ),
    )
}
