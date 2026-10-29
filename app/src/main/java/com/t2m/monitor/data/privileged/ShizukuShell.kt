package com.t2m.monitor.data.privileged

import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通过 Shizuku 执行 shell 命令。
 *
 * 命令以参数数组传入（不经过 `sh -c`），避免字符串拼接带来的注入风险。
 * 阻塞读取由看门狗在超时后 `destroy()` 远程进程，防止 `dumpsys` 卡死拖住采样线程。
 */
@Singleton
class ShizukuShell @Inject constructor(
    private val access: ShizukuAccess,
    private val launcher: RemoteCommandLauncher,
    private val logger: MonitorLogger,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    fun isGranted(): Boolean = access.isGranted()

    /**
     * @return 标准输出；未授权、退出码非 0、超时或执行异常时返回 null
     */
    fun exec(vararg command: String): String? {
        if (!access.isGranted()) {
            return null
        }
        val process = launcher.launch(arrayOf(*command)) ?: return null
        val finished = AtomicBoolean(false)
        val timedOut = AtomicBoolean(false)
        val watchdog = scope.launch(Dispatchers.IO) {
            delay(TIMEOUT_MILLIS)
            if (finished.compareAndSet(false, true)) {
                timedOut.set(true)
                logger.warn("shizuku command timed out: ${command.firstOrNull()}")
                process.destroy()
            }
        }

        return try {
            val output = process.readStdout()
            val exitCode = process.waitFor()
            if (timedOut.get() || exitCode != 0) null else output
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logger.warn("shizuku command failed: ${command.firstOrNull()}", error)
            null
        } finally {
            finished.set(true)
            watchdog.cancel()
            process.destroy()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 400L
    }
}
