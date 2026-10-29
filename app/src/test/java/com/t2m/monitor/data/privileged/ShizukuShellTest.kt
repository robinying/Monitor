package com.t2m.monitor.data.privileged

import com.t2m.monitor.core.MonitorLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shizuku 命令执行：授权、退出码、异常与超时看门狗。
 */
class ShizukuShellTest {

    private val logger = RecordingLogger()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Test
    fun `returns stdout on success`() {
        val process = FakeProcess(output = "ok", exitCode = 0)
        val shell = createShell(process)

        assertEquals("ok", shell.exec("dumpsys", "SurfaceFlinger", "--list"))
        assertTrue(process.destroyed.get() > 0)
    }

    @Test
    fun `returns null on non zero exit code`() {
        val process = FakeProcess(output = "ignored", exitCode = 1)
        val shell = createShell(process)
        assertNull(shell.exec("echo"))
    }

    @Test
    fun `returns null and does not launch when not granted`() {
        val launched = AtomicBoolean(false)
        val shell = ShizukuShell(
            access = FakeAccess(granted = false),
            launcher = RemoteCommandLauncher {
                launched.set(true)
                FakeProcess(output = "x", exitCode = 0)
            },
            logger = logger,
            scope = scope,
        )

        assertNull(shell.exec("echo"))
        assertFalse(launched.get())
    }

    @Test
    fun `times out and destroys a hanging process`() {
        val process = HangingProcess()
        val shell = createShell(process)

        val result = runBlocking {
            withTimeout(3_000L) { shell.exec("dumpsys", "SurfaceFlinger", "--latency", "layer") }
        }

        assertNull(result)
        assertTrue(process.destroyed.get())
        assertTrue(logger.messages.any { it.contains("timed out") })
    }

    private fun createShell(process: RemoteCommandProcess): ShizukuShell = ShizukuShell(
        access = FakeAccess(granted = true),
        launcher = RemoteCommandLauncher { process },
        logger = logger,
        scope = scope,
    )

    private class FakeAccess(private val granted: Boolean) : ShizukuAccess {
        override fun isGranted(): Boolean = granted
    }

    private class FakeProcess(
        private val output: String,
        private val exitCode: Int,
    ) : RemoteCommandProcess {
        val destroyed = AtomicInteger(0)

        override fun readStdout(): String = output

        override fun waitFor(): Int = exitCode

        override fun destroy() {
            destroyed.incrementAndGet()
        }
    }

    private class HangingProcess : RemoteCommandProcess {
        private val release = CountDownLatch(1)
        val destroyed = AtomicBoolean(false)

        override fun readStdout(): String {
            release.await(5, TimeUnit.SECONDS)
            return ""
        }

        override fun waitFor(): Int = -1

        override fun destroy() {
            destroyed.set(true)
            release.countDown()
        }
    }

    private class RecordingLogger : MonitorLogger {
        val messages = mutableListOf<String>()
        override fun warn(message: String, throwable: Throwable?) {
            messages += message
        }
    }
}
