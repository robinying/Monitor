package com.t2m.monitor.data.collector

import java.io.File
import javax.inject.Inject

/** `/proc/stat` 与 `/sys` 频率文件的数据来源，便于测试替换。 */
interface CpuStatSource {

    /** 读取 `/proc/stat` 中以 `cpu` 开头的行；不可读时返回 null。 */
    fun readStatLines(): List<String>?

    /** 读取指定核心的当前频率（kHz）；核心离线或文件不可读时返回 null。 */
    fun readFrequencyKHz(coreIndex: Int): Int?

    /** 读取指定核心的最大频率（kHz）；不可读时返回 null。 */
    fun readMaxFrequencyKHz(coreIndex: Int): Int?
}

/** 生产实现：直接读取内核暴露的 proc/sys 文件。 */
class AndroidCpuStatSource @Inject constructor() : CpuStatSource {

    override fun readStatLines(): List<String>? = runCatching {
        File(PROC_STAT_PATH).useLines { lines ->
            lines.takeWhile { it.startsWith(CPU_LINE_PREFIX) }.toList()
        }
    }.getOrNull()

    override fun readFrequencyKHz(coreIndex: Int): Int? =
        readIntFromFile(coreFrequencyPath(coreIndex, CURRENT_FREQUENCY_FILE))

    override fun readMaxFrequencyKHz(coreIndex: Int): Int? =
        readIntFromFile(coreFrequencyPath(coreIndex, MAX_FREQUENCY_FILE))

    private fun readIntFromFile(path: String): Int? = runCatching {
        File(path).readText().trim().toInt()
    }.getOrNull()

    private fun coreFrequencyPath(coreIndex: Int, fileName: String): String =
        "$CPU_FREQUENCY_DIR/cpu$coreIndex/cpufreq/$fileName"

    private companion object {
        const val PROC_STAT_PATH = "/proc/stat"
        const val CPU_LINE_PREFIX = "cpu"
        const val CPU_FREQUENCY_DIR = "/sys/devices/system/cpu"
        const val CURRENT_FREQUENCY_FILE = "scaling_cur_freq"
        const val MAX_FREQUENCY_FILE = "cpuinfo_max_freq"
    }
}
