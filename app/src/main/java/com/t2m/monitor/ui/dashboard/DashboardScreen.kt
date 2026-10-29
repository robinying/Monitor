package com.t2m.monitor.ui.dashboard

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.t2m.monitor.R
import com.t2m.monitor.core.FormatUtils
import com.t2m.monitor.data.model.AppStorageUsage
import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.BatteryStatus
import com.t2m.monitor.data.model.CpuSnapshot
import com.t2m.monitor.data.model.MemorySnapshot
import com.t2m.monitor.data.model.MonitorSnapshot
import com.t2m.monitor.data.model.PlugState
import com.t2m.monitor.data.model.StorageSnapshot
import com.t2m.monitor.data.model.ThermalSnapshot
import com.t2m.monitor.data.model.ThermalZone
import com.t2m.monitor.data.model.UsageSnapshot
import com.t2m.monitor.data.permission.PermissionStatus
import com.t2m.monitor.ui.components.BatteryRing
import com.t2m.monitor.ui.components.CoreBars
import com.t2m.monitor.ui.components.MetricCard
import com.t2m.monitor.ui.components.PillTone
import com.t2m.monitor.ui.components.SectionHeader
import com.t2m.monitor.ui.components.Sparkline
import com.t2m.monitor.ui.components.StatColumn
import com.t2m.monitor.ui.components.StatusPill
import com.t2m.monitor.ui.components.UsageBar
import com.t2m.monitor.ui.theme.T2mMonitorTheme
import com.t2m.monitor.ui.theme.availableStorageColor
import com.t2m.monitor.ui.theme.batteryLevelColor
import com.t2m.monitor.ui.theme.temperatureColor
import com.t2m.monitor.ui.theme.usageColor

/**
 * 仪表盘入口，负责把 ViewModel 状态接入界面。
 */
@Composable
fun DashboardRoute(
    onOpenGuide: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }
    DashboardScreen(
        uiState = uiState,
        modifier = modifier,
        onStartMonitoring = viewModel::startMonitoring,
        onStopMonitoring = viewModel::stopMonitoring,
        onToggleOverlay = viewModel::toggleOverlay,
        onOpenGuide = onOpenGuide,
        onOpenHistory = onOpenHistory,
        onOpenDiagnostics = onOpenDiagnostics,
    )
}

/**
 * 实时状态仪表盘。
 *
 * 所有指标允许为空，空值统一展示 "--"，颜色按阈值在品牌色/警告色/错误色之间切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier,
    onStartMonitoring: () -> Unit = {},
    onStopMonitoring: () -> Unit = {},
    onToggleOverlay: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    if (uiState.serviceRunning) {
                        SamplingPill(
                            active = uiState.samplingActive,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.dashboard_control_title))
            ControlCard(
                uiState = uiState,
                onStartMonitoring = onStartMonitoring,
                onStopMonitoring = onStopMonitoring,
                onToggleOverlay = onToggleOverlay,
                onOpenGuide = onOpenGuide,
                onOpenHistory = onOpenHistory,
                onOpenDiagnostics = onOpenDiagnostics,
            )
            SectionHeader(title = stringResource(R.string.dashboard_section_live))
            BatteryCard(battery = uiState.snapshot.battery)
            CpuCard(cpu = uiState.snapshot.cpu, history = uiState.cpuHistoryPercent)
            uiState.snapshot.fps?.let { fps -> FpsCard(fps = fps) }
            ThermalCard(thermal = uiState.snapshot.thermal)
            MemoryCard(memory = uiState.snapshot.memory, history = uiState.memoryHistoryPercent)
            StorageCard(storage = uiState.snapshot.storage)
            UsageCard(
                usage = uiState.snapshot.usage,
                usageStatsGranted = uiState.permissions.usageStatsGranted,
                onOpenGuide = onOpenGuide,
            )
            if (!uiState.snapshot.appStorage.isNullOrEmpty()) {
                AppStorageCard(apps = uiState.snapshot.appStorage)
            }
            Text(
                text = stringResource(R.string.m1_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SamplingPill(active: Boolean, modifier: Modifier = Modifier) {
    StatusPill(
        text = stringResource(if (active) R.string.state_running else R.string.state_paused),
        modifier = modifier,
        tone = if (active) PillTone.POSITIVE else PillTone.NEUTRAL,
    )
}

@Composable
private fun ControlCard(
    uiState: DashboardUiState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (uiState.serviceRunning) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (uiState.serviceRunning) {
                                R.string.dashboard_service_running
                            } else {
                                R.string.dashboard_service_stopped
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (uiState.serviceRunning && uiState.samplingPaused) {
                        Text(
                            text = stringResource(R.string.dashboard_sampling_paused),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (uiState.serviceRunning) {
                    StatusPill(
                        text = stringResource(
                            if (uiState.samplingActive) R.string.state_running else R.string.state_paused,
                        ),
                        tone = if (uiState.samplingActive) PillTone.POSITIVE else PillTone.NEUTRAL,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (uiState.serviceRunning) onStopMonitoring else onStartMonitoring,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (uiState.serviceRunning) {
                                R.string.dashboard_stop_service
                            } else {
                                R.string.dashboard_start_service
                            },
                        ),
                    )
                }
                OutlinedButton(
                    onClick = if (uiState.permissions.overlayGranted) onToggleOverlay else onOpenGuide,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.serviceRunning,
                ) {
                    Text(
                        stringResource(
                            if (uiState.overlayVisible) {
                                R.string.dashboard_hide_overlay
                            } else {
                                R.string.dashboard_show_overlay
                            },
                        ),
                    )
                }
            }

            if (!uiState.permissions.overlayGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_overlay_permission_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                TextButton(onClick = onOpenGuide) {
                    Text(stringResource(R.string.dashboard_open_guide))
                }
                TextButton(onClick = onOpenHistory) {
                    Text(stringResource(R.string.dashboard_open_history))
                }
                TextButton(onClick = onOpenDiagnostics) {
                    Text(stringResource(R.string.dashboard_open_diagnostics))
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(battery: BatterySnapshot?) {
    val percent = battery?.percent
    val accent = batteryLevelColor(percent)
    val temperature = battery?.temperatureC
    MetricCard(
        title = stringResource(R.string.battery_title),
        accent = accent,
        chip = battery?.let {
            {
                StatusPill(
                    text = batteryStatusLabel(it.status),
                    tone = if (it.status == BatteryStatus.CHARGING) PillTone.POSITIVE else PillTone.NEUTRAL,
                )
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryRing(
                percent = percent,
                color = accent,
                label = stringResource(R.string.battery_title),
                modifier = Modifier.size(108.dp),
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow(
                    label = stringResource(R.string.label_temperature),
                    value = FormatUtils.formatTemperature(temperature),
                    valueColor = temperatureColor(temperature),
                )
                DetailRow(
                    label = stringResource(R.string.label_current),
                    value = FormatUtils.formatCurrent(battery?.currentMicroAmps),
                )
                DetailRow(
                    label = stringResource(R.string.label_voltage),
                    value = FormatUtils.formatVoltage(battery?.voltageMv),
                )
                battery?.takeIf { it.plugged != PlugState.NONE }?.let {
                    Text(
                        text = plugStateLabel(it.plugged),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CpuCard(cpu: CpuSnapshot?, history: List<Float>) {
    val usage = cpu?.totalUsagePercent
    val accent = usageColor(usage)
    MetricCard(
        title = stringResource(R.string.cpu_title),
        accent = accent,
        chip = cpu?.let {
            {
                StatusPill(
                    text = pluralStringResource(
                        R.plurals.label_cores,
                        it.coreCount,
                        it.coreCount,
                    ),
                    tone = PillTone.NEUTRAL,
                )
            }
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = FormatUtils.formatPercent(usage),
                style = MaterialTheme.typography.displaySmall,
                color = accent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_usage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.label_max_frequency,
                    FormatUtils.formatFrequency(cpu?.maxFrequencyKHz),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (cpu != null && usage == null && cpu.coreCount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dashboard_cpu_usage_limited),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Sparkline(
                values = history,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                lineColor = accent,
            )
        }
        if (cpu?.perCoreUsagePercent?.any { usagePercent -> usagePercent != null } == true) {
            Spacer(modifier = Modifier.height(14.dp))
            CoreBars(
                usages = cpu.perCoreUsagePercent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            )
        }
    }
}

@Composable
private fun FpsCard(fps: Float) {
    MetricCard(
        title = stringResource(R.string.fps_title),
        accent = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = FormatUtils.formatFps(fps),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.fps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThermalCard(thermal: ThermalSnapshot?) {
    val accent = temperatureColor(thermal?.maxTemperatureC?.toDouble())
    MetricCard(
        title = stringResource(R.string.thermal_title),
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = FormatUtils.formatTemperature(thermal?.maxTemperatureC?.toDouble()),
                style = MaterialTheme.typography.displaySmall,
                color = accent,
            )
            Spacer(modifier = Modifier.weight(1f))
            thermal?.let {
                Text(
                    text = stringResource(R.string.thermal_hottest_zone, it.hottestZone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!thermal?.zones.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            thermal.zones.take(MAX_THERMAL_ZONES).forEach { zone ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = zone.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = FormatUtils.formatTemperature(zone.temperatureC.toDouble()),
                        style = MaterialTheme.typography.bodySmall,
                        color = temperatureColor(zone.temperatureC.toDouble()),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: MemorySnapshot?, history: List<Float>) {
    val percent = memory?.usedPercent
    val accent = usageColor(percent)
    MetricCard(
        title = stringResource(R.string.memory_title),
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = FormatUtils.formatPercent(percent),
                style = MaterialTheme.typography.displaySmall,
                color = accent,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.label_used_of_total,
                    FormatUtils.formatBytes(memory?.usedBytes),
                    FormatUtils.formatBytes(memory?.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        UsageBar(percent = percent, color = accent)
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Sparkline(
                values = history,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                lineColor = accent,
            )
        }
    }
}

@Composable
private fun StorageCard(storage: StorageSnapshot?) {
    val percent = storage?.usedPercent
    val accent = availableStorageColor(storage?.availableBytes)
    MetricCard(
        title = stringResource(R.string.storage_title),
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = FormatUtils.formatPercent(percent),
                style = MaterialTheme.typography.displaySmall,
                color = accent,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.label_used_of_total,
                    FormatUtils.formatBytes(storage?.usedBytes),
                    FormatUtils.formatBytes(storage?.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        UsageBar(percent = percent, color = accent)
    }
}

@Composable
private fun UsageCard(
    usage: UsageSnapshot?,
    usageStatsGranted: Boolean,
    onOpenGuide: () -> Unit,
) {
    MetricCard(title = stringResource(R.string.usage_title)) {
        if (!usageStatsGranted) {
            Text(
                text = stringResource(R.string.usage_permission_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onOpenGuide) {
                Text(stringResource(R.string.usage_permission_action))
            }
            return@MetricCard
        }
        StatColumn(
            label = stringResource(R.string.usage_foreground_app),
            value = usage?.foregroundAppLabel
                ?: usage?.foregroundPackage
                ?: stringResource(R.string.value_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.usage_traffic_value,
                FormatUtils.formatBytes(usage?.todayRxBytes),
                FormatUtils.formatBytes(usage?.todayTxBytes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AppStorageCard(apps: List<AppStorageUsage>) {
    val maxBytes = apps.maxOfOrNull { app -> app.totalBytes }?.coerceAtLeast(1L) ?: 1L
    MetricCard(title = stringResource(R.string.app_storage_title)) {
        apps.take(MAX_APP_STORAGE_ROWS).forEach { app ->
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.label ?: app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = FormatUtils.formatBytes(app.totalBytes),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                UsageBar(
                    percent = app.totalBytes * 100f / maxBytes,
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 4.dp,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
private fun batteryStatusLabel(status: BatteryStatus): String = stringResource(
    when (status) {
        BatteryStatus.CHARGING -> R.string.battery_status_charging
        BatteryStatus.DISCHARGING -> R.string.battery_status_discharging
        BatteryStatus.NOT_CHARGING -> R.string.battery_status_not_charging
        BatteryStatus.FULL -> R.string.battery_status_full
        BatteryStatus.UNKNOWN -> R.string.battery_status_unknown
    },
)

@Composable
private fun plugStateLabel(plugState: PlugState): String = stringResource(
    when (plugState) {
        PlugState.NONE -> R.string.plug_none
        PlugState.AC -> R.string.plug_ac
        PlugState.USB -> R.string.plug_usb
        PlugState.WIRELESS -> R.string.plug_wireless
        PlugState.DOCK -> R.string.plug_dock
    },
)

private const val MAX_THERMAL_ZONES = 3
private const val MAX_APP_STORAGE_ROWS = 5

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    T2mMonitorTheme {
        DashboardScreen(uiState = previewUiState())
    }
}

private fun previewUiState(): DashboardUiState = DashboardUiState(
    snapshot = MonitorSnapshot(
        timestampMillis = 0L,
        battery = BatterySnapshot(
            percent = 78,
            temperatureC = 36.4,
            currentMicroAmps = -820_000,
            voltageMv = 4200,
            status = BatteryStatus.DISCHARGING,
            plugged = PlugState.NONE,
            health = 2,
        ),
        cpu = CpuSnapshot(
            totalUsagePercent = 23f,
            perCoreUsagePercent = listOf(12f, 30f, 45f, 18f, 60f, 25f, 10f, 33f),
            perCoreFrequencyKHz = listOf(1_800_000, 2_400_000, 2_800_000, 1_800_000),
            maxFrequencyKHz = 2_800_000,
        ),
        memory = MemorySnapshot(
            totalBytes = 12L * 1024 * 1024 * 1024,
            availBytes = 4L * 1024 * 1024 * 1024,
            lowMemory = false,
        ),
        storage = StorageSnapshot(
            totalBytes = 128L * 1024 * 1024 * 1024,
            availableBytes = 32L * 1024 * 1024 * 1024,
        ),
        thermal = ThermalSnapshot(
            maxTemperatureC = 42.5f,
            hottestZone = "cpu-0-0",
            zones = listOf(
                ThermalZone(name = "cpu-0-0", temperatureC = 42.5f),
                ThermalZone(name = "battery", temperatureC = 36.4f),
            ),
        ),
        usage = UsageSnapshot(
            foregroundPackage = "com.tencent.mm",
            foregroundAppLabel = "Messages",
            todayMobileRxBytes = 120L * 1024 * 1024,
            todayMobileTxBytes = 8L * 1024 * 1024,
            todayWifiRxBytes = 640L * 1024 * 1024,
            todayWifiTxBytes = 32L * 1024 * 1024,
        ),
        fps = 60f,
        appStorage = listOf(
            AppStorageUsage("com.tencent.mm", "Messages", 8L * 1024 * 1024 * 1024),
            AppStorageUsage("com.android.chrome", "Chrome", 3L * 1024 * 1024 * 1024),
        ),
    ),
    cpuHistoryPercent = listOf(18f, 22f, 35f, 28f, 41f, 33f, 25f, 23f),
    memoryHistoryPercent = listOf(60f, 61f, 62f, 62f, 63f, 65f, 64f, 67f),
    serviceRunning = true,
    overlayVisible = true,
    samplingPaused = false,
    permissions = PermissionStatus(
        overlayGranted = true,
        notificationGranted = true,
        ignoringBatteryOptimizations = false,
        usageStatsGranted = true,
    ),
)
