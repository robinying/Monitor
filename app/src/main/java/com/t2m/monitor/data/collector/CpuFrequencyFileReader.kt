package com.t2m.monitor.data.collector

/**
 * CPU 频率文件读取器。
 *
 * 离线的核心或不可读的 cpufreq 节点只会在 TTL 内被跳过一次，避免采集循环每秒
 * 对同一批路径重复抛异常；节点恢复后 TTL 到期自动重试。
 */
internal class CpuFrequencyFileReader(
    private val readInt: (String) -> Int?,
    private val elapsedMillis: () -> Long,
    private val retryAfterMillis: Long = RETRY_AFTER_MILLIS,
) {

    private val failedAtMillis = mutableMapOf<String, Long>()

    @Synchronized
    fun read(path: String): Int? {
        val now = elapsedMillis()
        val failedAt = failedAtMillis[path]
        if (failedAt != null && now - failedAt in 0 until retryAfterMillis) {
            return null
        }
        val value = try {
            readInt(path)
        } catch (error: Exception) {
            null
        }
        if (value == null) {
            failedAtMillis[path] = now
        } else {
            failedAtMillis.remove(path)
        }
        return value
    }

    internal companion object {
        const val RETRY_AFTER_MILLIS = 60_000L
    }
}
