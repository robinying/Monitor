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
 * 通过 Shizuku 的 IShizukuService 启动远程进程。
 *
 * Shizuku 13.x 未公开 public 的 newProcess，因此使用官方发布的 AIDL；接口变化时返回 null，
 * 由上层降级为功能不可用。
 */
@Singleton
class ShizukuCommandLauncher @Inject constructor(
    private val logger: MonitorLogger,
) : RemoteCommandLauncher {

    override fun launch(args: Array<String>): RemoteCommandProcess? = runCatching {
        val binder = Shizuku.getBinder() ?: return@runCatching null
        val service = IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
        val remoteProcess = service.newProcess(args, null, null)
        ShizukuProcess(remoteProcess)
    }.onFailure { error ->
        logger.warn("shizuku process launch failed", error)
    }.getOrNull()

    private class ShizukuProcess(
        private val remoteProcess: IRemoteProcess,
    ) : RemoteCommandProcess {

        override fun readStdout(): String =
            ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.inputStream)
                .bufferedReader()
                .use { reader -> reader.readText() }

        override fun waitFor(): Int = remoteProcess.waitFor()

        override fun destroy() {
            runCatching { remoteProcess.destroy() }
        }
    }
}
