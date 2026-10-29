package com.t2m.monitor.data.privileged

/**
 * 远程命令进程抽象，屏蔽 Shizuku AIDL，便于单元测试注入假实现。
 */
interface RemoteCommandProcess {

    /** 读取标准输出；实现可能阻塞，调用方必须配合超时看门狗。 */
    fun readStdout(): String

    /** 等待进程退出并返回退出码。 */
    fun waitFor(): Int

    /** 强制结束远程进程（超时/清理时调用）。 */
    fun destroy()
}

/** 远程命令启动器。 */
fun interface RemoteCommandLauncher {

    /** 启动命令；失败或未授权时返回 null。 */
    fun launch(args: Array<String>): RemoteCommandProcess?
}

/** Shizuku 授权状态的最小接口。 */
interface ShizukuAccess {

    fun isGranted(): Boolean
}
