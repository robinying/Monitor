package com.t2m.monitor.ui.history

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.t2m.monitor.R
import com.t2m.monitor.core.FormatUtils
import com.t2m.monitor.data.model.AlertRecord
import com.t2m.monitor.data.model.AlertType
import com.t2m.monitor.data.model.HistoryRange
import com.t2m.monitor.data.model.MetricType
import com.t2m.monitor.domain.export.ExportFile
import com.t2m.monitor.ui.components.PillTone
import com.t2m.monitor.ui.components.StatColumn
import com.t2m.monitor.ui.components.StatusPill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 历史数据页：范围/指标切换、曲线、统计、CSV 导出与清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    // 页面重新可见时刷新，避免告警/历史在停留期间变化后仍显示旧数据。
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.messageRes) {
        val messageRes = uiState.messageRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(messageRes))
        viewModel.consumeMessage()
    }
    LaunchedEffect(uiState.exportEvent) {
        val exportFile = uiState.exportEvent ?: return@LaunchedEffect
        shareCsv(context, exportFile)
        viewModel.consumeExportEvent()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.permission_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RangeSelector(selected = uiState.range, onSelect = viewModel::selectRange)
                    MetricSelector(selected = uiState.metric, onSelect = viewModel::selectMetric)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when {
                        uiState.loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CHART_HEIGHT.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        uiState.points.isEmpty() -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CHART_HEIGHT.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.history_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            HistoryChart(
                                points = uiState.points,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(CHART_HEIGHT.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = formatTimestamp(uiState.points.first().timestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = formatTimestamp(uiState.points.last().timestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.points.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatItem(
                        label = stringResource(R.string.history_min),
                        value = metricValue(uiState.metric, uiState.minValue),
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        label = stringResource(R.string.history_avg),
                        value = metricValue(uiState.metric, uiState.avgValue),
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        label = stringResource(R.string.history_max),
                        value = metricValue(uiState.metric, uiState.maxValue),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                text = if (uiState.sampleCount == 0L) {
                    stringResource(R.string.history_no_samples)
                } else {
                    stringResource(
                        R.string.history_info,
                        uiState.sampleCount,
                        formatTimestamp(uiState.oldestTimestampMillis),
                        formatTimestamp(uiState.latestTimestampMillis),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.alerts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.history_alerts_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.alerts.take(MAX_VISIBLE_ALERTS).forEach { alert ->
                            AlertRow(alert)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = viewModel::export,
                    enabled = !uiState.exporting && uiState.points.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (uiState.exporting) R.string.history_exporting else R.string.history_export,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    enabled = uiState.sampleCount > 0L,
                ) {
                    Text(stringResource(R.string.history_clear))
                }
            }

            Text(
                text = stringResource(R.string.history_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearData()
                    },
                ) {
                    Text(stringResource(R.string.history_confirm_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}

@Composable
private fun RangeSelector(
    selected: HistoryRange,
    onSelect: (HistoryRange) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(rangeLabel(range)) },
            )
        }
    }
}

@Composable
private fun MetricSelector(
    selected: MetricType,
    onSelect: (MetricType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricType.entries.forEach { metric ->
            FilterChip(
                selected = metric == selected,
                onClick = { onSelect(metric) },
                label = { Text(metricLabel(metric)) },
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            StatColumn(label = label, value = value)
        }
    }
}

@Composable
private fun AlertRow(alert: AlertRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alertTypeLabel(alert.type),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatTimestamp(alert.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(
            text = alertValueText(alert),
            tone = PillTone.NEGATIVE,
        )
    }
}

@Composable
private fun alertTypeLabel(type: AlertType): String = stringResource(
    when (type) {
        AlertType.BATTERY_TEMPERATURE_HIGH -> R.string.alert_type_battery_temperature
        AlertType.BATTERY_LOW -> R.string.alert_type_battery_low
        AlertType.MEMORY_LOW -> R.string.alert_type_memory
        AlertType.STORAGE_LOW -> R.string.alert_type_storage
    },
)

@Composable
private fun alertValueText(alert: AlertRecord): String = when (alert.type) {
    AlertType.BATTERY_TEMPERATURE_HIGH -> FormatUtils.formatTemperature(alert.actualValue.toDouble())
    AlertType.BATTERY_LOW, AlertType.MEMORY_LOW -> FormatUtils.formatPercent(alert.actualValue)
    AlertType.STORAGE_LOW -> FormatUtils.formatBytes((alert.actualValue * GIGABYTE_BYTES).toLong())
}

private const val MAX_VISIBLE_ALERTS = 5
private const val GIGABYTE_BYTES = 1024f * 1024f * 1024f

@Composable
private fun rangeLabel(range: HistoryRange): String = stringResource(
    when (range) {
        HistoryRange.ONE_HOUR -> R.string.history_range_1h
        HistoryRange.SIX_HOURS -> R.string.history_range_6h
        HistoryRange.ONE_DAY -> R.string.history_range_24h
        HistoryRange.SEVEN_DAYS -> R.string.history_range_7d
    },
)

@Composable
private fun metricLabel(metric: MetricType): String = stringResource(
    when (metric) {
        MetricType.CPU -> R.string.history_metric_cpu
        MetricType.MEMORY -> R.string.history_metric_memory
        MetricType.BATTERY_LEVEL -> R.string.history_metric_battery
        MetricType.BATTERY_TEMPERATURE -> R.string.history_metric_temperature
        MetricType.STORAGE -> R.string.history_metric_storage
        MetricType.DEVICE_TEMPERATURE -> R.string.history_metric_device_temperature
        MetricType.FPS -> R.string.history_metric_fps
    },
)

@Composable
private fun metricValue(metric: MetricType, value: Float?): String = when (metric) {
    MetricType.BATTERY_TEMPERATURE, MetricType.DEVICE_TEMPERATURE ->
        FormatUtils.formatTemperature(value?.toDouble())

    MetricType.FPS -> FormatUtils.formatFps(value)

    else -> FormatUtils.formatPercent(value)
}

private fun formatTimestamp(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) {
        return "--"
    }
    return AXIS_TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMillis))
}

private fun shareCsv(context: Context, exportFile: ExportFile) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = MIME_TYPE_CSV
        putExtra(Intent.EXTRA_STREAM, exportFile.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.history_share_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (exception: Exception) {
        // 无可用分享目标时忽略，文件已写入下载目录，可在文件管理器中找到。
        Log.w(TAG, "share csv failed", exception)
    }
}

private const val CHART_HEIGHT = 180
private const val MIME_TYPE_CSV = "text/csv"
private const val TAG = "T2mMonitor"
private val AXIS_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
