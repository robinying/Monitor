package com.t2m.monitor.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.t2m.monitor.R
import com.t2m.monitor.core.FormatUtils
import com.t2m.monitor.data.collector.CollectorId
import com.t2m.monitor.domain.diagnostics.CollectorDiagnostic
import com.t2m.monitor.domain.diagnostics.DiagnosticError
import com.t2m.monitor.ui.components.MetricCard
import com.t2m.monitor.ui.components.PillTone
import com.t2m.monitor.ui.components.StatusPill
import com.t2m.monitor.ui.components.UsageBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 诊断页：采集成功率、最近耗时与错误环形缓冲。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = viewModel(),
) {
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val errors by viewModel.recentErrors.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
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
            Text(
                text = stringResource(R.string.diagnostics_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = stringResource(R.string.diagnostics_collectors)) {
                if (diagnostics.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diagnostics_never_run),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    diagnostics.forEach { item -> DiagnosticRow(item) }
                }
            }

            SectionCard(title = stringResource(R.string.diagnostics_errors)) {
                if (errors.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diagnostics_no_errors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    errors.forEach { error -> ErrorRow(error) }
                }
            }

            OutlinedButton(
                onClick = viewModel::reset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diagnostics_reset))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    MetricCard(title = title) {
        content()
    }
}

@Composable
private fun DiagnosticRow(diagnostic: CollectorDiagnostic) {
    val rate = diagnostic.successRatePercent
    val rateColor = successRateColor(rate)
    val rateText = rate?.let { value ->
        stringResource(R.string.diagnostics_success_rate, FormatUtils.formatPercent(value))
    } ?: stringResource(R.string.diagnostics_never_run)
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = collectorLabel(diagnostic.collectorId),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = rateText, tone = successRateTone(rate))
        }
        Spacer(modifier = Modifier.height(6.dp))
        UsageBar(percent = rate, color = rateColor, thickness = 6.dp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.diagnostics_counts,
                    diagnostic.successCount,
                    diagnostic.failureCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_last_duration,
                    diagnostic.lastDurationMillis,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        diagnostic.lastError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun successRateColor(rate: Float?): Color = when {
    rate == null -> MaterialTheme.colorScheme.outline
    rate >= 90f -> MaterialTheme.colorScheme.primary
    rate >= 70f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun successRateTone(rate: Float?): PillTone = when {
    rate == null -> PillTone.NEUTRAL
    rate >= 90f -> PillTone.POSITIVE
    rate >= 70f -> PillTone.WARNING
    else -> PillTone.NEGATIVE
}

@Composable
private fun ErrorRow(error: DiagnosticError) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = error.collectorId?.let { id -> collectorLabel(id) }
                    ?: stringResource(R.string.diagnostics_error_source),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ERROR_TIME_FORMATTER.format(Instant.ofEpochMilli(error.timestampMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = error.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
    }
}

@Composable
private fun collectorLabel(id: CollectorId): String = stringResource(
    when (id) {
        CollectorId.BATTERY -> R.string.collector_battery
        CollectorId.CPU -> R.string.collector_cpu
        CollectorId.MEMORY -> R.string.collector_memory
        CollectorId.STORAGE -> R.string.collector_storage
        CollectorId.THERMAL -> R.string.collector_thermal
        CollectorId.USAGE -> R.string.collector_usage
        CollectorId.FPS -> R.string.collector_fps
        CollectorId.APP_STORAGE -> R.string.collector_app_storage
    },
)

private val ERROR_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
