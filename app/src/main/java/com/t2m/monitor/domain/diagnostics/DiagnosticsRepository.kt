package com.t2m.monitor.domain.diagnostics

import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.data.collector.CollectorResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 单个采集器的运行统计。
 *
 * @param successCount 成功次数（返回非 null 且无异常）
 * @param failureCount 失败次数（返回 null 或抛出异常）
 * @param lastDurationMillis 最近一次采集耗时
 * @param lastError 最近一次错误信息
 */
data class CollectorDiagnostic(
    val collectorId: CollectorId,
    val successCount: Long = 0L,
    val failureCount: Long = 0L,
    val consecutiveFailures: Int = 0,
    val lastDurationMillis: Long = 0L,
    val lastUpdatedAtMillis: Long = 0L,
    val lastError: String? = null,
) {
    val totalCount: Long
        get() = successCount + failureCount

    /** 成功率 0..100；尚未运行时为 null。 */
    val successRatePercent: Float?
        get() = if (totalCount == 0L) {
            null
        } else {
            (successCount.toDouble() / totalCount * 100.0).toFloat()
        }
}

/**
 * 一条诊断错误记录。
 */
data class DiagnosticError(
    val timestampMillis: Long,
    val collectorId: CollectorId?,
    val message: String,
)

/**
 * 采集诊断统计。
 *
 * 全部数据保存在内存中，进程重启即清空；错误日志采用固定容量的环形缓冲。
 */
@Singleton
class DiagnosticsRepository @Inject constructor(
    private val clock: MonitorClock,
) {

    private val stats = linkedMapOf<CollectorId, CollectorDiagnostic>()

    private val errors = ArrayDeque<DiagnosticError>()

    private val _diagnostics = MutableStateFlow<List<CollectorDiagnostic>>(emptyList())

    val diagnostics: StateFlow<List<CollectorDiagnostic>> = _diagnostics.asStateFlow()

    private val _recentErrors = MutableStateFlow<List<DiagnosticError>>(emptyList())

    val recentErrors: StateFlow<List<DiagnosticError>> = _recentErrors.asStateFlow()

    /** 记录一次采集结果：成功、返回 null 或异常都计入统计。 */
    @Synchronized
    fun record(result: CollectorResult) {
        val previous = stats[result.id] ?: CollectorDiagnostic(collectorId = result.id)
        val failed = result.error != null || result.value == null
        val updated = if (failed) {
            val message = result.error?.message ?: "collector returned no data"
            addError(result.id, message)
            previous.copy(
                failureCount = previous.failureCount + 1,
                consecutiveFailures = previous.consecutiveFailures + 1,
                lastDurationMillis = result.durationMillis,
                lastUpdatedAtMillis = clock.currentTimeMillis(),
                lastError = message,
            )
        } else {
            previous.copy(
                successCount = previous.successCount + 1,
                consecutiveFailures = 0,
                lastDurationMillis = result.durationMillis,
                lastUpdatedAtMillis = clock.currentTimeMillis(),
                lastError = null,
            )
        }
        stats[result.id] = updated
        publish()
    }

    /** 记录不来自采集器的错误（例如导出、数据库）。 */
    @Synchronized
    fun recordError(message: String) {
        addError(collectorId = null, message = message)
        publish()
    }

    @Synchronized
    fun reset() {
        stats.clear()
        errors.clear()
        publish()
    }

    private fun addError(collectorId: CollectorId?, message: String) {
        errors.addFirst(
            DiagnosticError(
                timestampMillis = clock.currentTimeMillis(),
                collectorId = collectorId,
                message = message,
            ),
        )
        while (errors.size > MAX_ERRORS) {
            errors.removeLast()
        }
    }

    private fun publish() {
        _diagnostics.value = stats.values.toList()
        _recentErrors.value = errors.toList()
    }

    private companion object {
        const val MAX_ERRORS = 20
    }
}
