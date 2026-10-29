package com.t2m.monitor.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t2m.monitor.R
import com.t2m.monitor.core.MonitorClock
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.HistoryRange
import com.t2m.monitor.data.model.MetricPoint
import com.t2m.monitor.data.model.MetricType
import com.t2m.monitor.data.model.AlertRecord
import com.t2m.monitor.data.repository.AlertRepository
import com.t2m.monitor.data.repository.MetricRepository
import com.t2m.monitor.domain.export.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 历史数据 ViewModel：范围/指标切换、统计、导出与清空。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: MetricRepository,
    private val alertRepository: AlertRepository,
    private val exporter: CsvExporter,
    private val clock: MonitorClock,
    private val logger: MonitorLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())

    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun selectRange(range: HistoryRange) {
        if (_uiState.value.range == range) {
            return
        }
        _uiState.update { it.copy(range = range) }
        load()
    }

    fun selectMetric(metric: MetricType) {
        if (_uiState.value.metric == metric) {
            return
        }
        _uiState.update { it.copy(metric = metric) }
        load()
    }

    fun reload() {
        load()
    }

    fun export() {
        val state = _uiState.value
        if (state.exporting || state.points.isEmpty()) {
            return
        }
        val now = clock.currentTimeMillis()
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            val result = exporter.export(now - state.range.durationMillis, now)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { file ->
                        current.copy(
                            exporting = false,
                            exportEvent = file,
                            messageRes = R.string.history_export_success,
                        )
                    },
                    onFailure = {
                        current.copy(
                            exporting = false,
                            messageRes = R.string.history_export_failed,
                        )
                    },
                )
            }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            val result = runCatching {
                repository.clearAll()
                alertRepository.clearAll()
            }
            _uiState.update { state ->
                state.copy(
                    messageRes = if (result.isSuccess) {
                        R.string.history_cleared
                    } else {
                        R.string.history_clear_failed
                    },
                )
            }
            load()
        }
    }

    fun consumeExportEvent() {
        _uiState.update { it.copy(exportEvent = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(messageRes = null) }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val state = _uiState.value
            val now = clock.currentTimeMillis()
            val result = runCatching {
                val points = repository.history(
                    metric = state.metric.key,
                    fromMillis = now - state.range.durationMillis,
                    toMillis = now,
                    bucketMillis = state.range.bucketMillis,
                )
                val count = repository.sampleCount()
                val oldest = repository.oldestTimestampMillis()
                val latest = repository.latestTimestampMillis()
                val alerts = alertRepository.recent(ALERT_LIMIT)
                LoadResult(points, count, oldest, latest, alerts)
            }
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) {
                // 新一次加载取消旧任务时不提示错误，也不覆盖新状态。
                return@launch
            }
            if (failure != null) {
                logger.warn("load history failed", failure)
            }
            _uiState.update { current ->
                val data = result.getOrNull()
                if (data == null) {
                    current.copy(loading = false, messageRes = R.string.history_load_failed)
                } else {
                    val values = data.points.map { point -> point.value }
                    current.copy(
                        points = data.points,
                        minValue = values.minOrNull(),
                        maxValue = values.maxOrNull(),
                        avgValue = if (values.isEmpty()) null else values.average().toFloat(),
                        sampleCount = data.sampleCount,
                        oldestTimestampMillis = data.oldestTimestampMillis,
                        latestTimestampMillis = data.latestTimestampMillis,
                        alerts = data.alerts,
                        loading = false,
                    )
                }
            }
        }
    }

    private data class LoadResult(
        val points: List<MetricPoint>,
        val sampleCount: Long,
        val oldestTimestampMillis: Long?,
        val latestTimestampMillis: Long?,
        val alerts: List<AlertRecord>,
    )

    private companion object {
        const val ALERT_LIMIT = 10
    }
}
