package com.t2m.monitor.data.privileged

import android.os.ParcelFileDescriptor
import com.t2m.monitor.core.MonitorLogger
import javax.inject.Inject
import javax.inject.Singleton
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * 通过 Shizuku 执行 shell 命令。
 *
 * 命令以参数数组传入（不经过 `sh -c`），避免字符串拼接带来的注入风险。
 * Shizuku 13.x 未公开 `newProcess`，这里通过 `IShizukuService` + `ShizukuBinderWrapper` 调用。
 */
@Singleton
class ShizukuShell @Inject constructor(
    private val controller: ShizukuController,
    private val logger: MonitorLogger,
) {

    fun isGranted(): Boolean = controller.isGranted()

    /**
     * @return 标准输出；未授权、退出码非 0 或执行异常时返回 null
     */
    fun exec(vararg command: String): String? {
        if (!controller.isGranted()) {
            return null
        }
        val service = openService() ?: return null
        var process: IRemoteProcess? = null
        val result = runCatching {
            val remoteProcess = service.newProcess(arrayOf(*command), null, null)
            process = remoteProcess
            val outputStream = ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.inputStream)
            val output = outputStream.bufferedReader().use { reader -> reader.readText() }
            val exitCode = remoteProcess.waitFor()
            if (exitCode == 0) output else null
        }
        if (result.isFailure) {
            logger.warn("shizuku command failed: ${command.joinToString(" ")}", result.exceptionOrNull())
        }
        runCatching { process?.destroy() }
        return result.getOrNull()
    }

    private fun openService(): IShizukuService? = runCatching {
        val binder = Shizuku.getBinder() ?: return@runCatching null
        IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
    }.getOrNull()
}
