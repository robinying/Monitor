package com.robin.monitor.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.monitor.R
import com.robin.monitor.data.model.OverlayMetric
import com.robin.monitor.data.model.OverlayMode
import com.robin.monitor.ui.components.MetricCard
import kotlin.math.roundToInt

/**
 * 设置页：悬浮窗形态、透明度、指标可见性与位置重置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            MetricCard(title = stringResource(R.string.settings_overlay_section)) {
                Text(
                    text = stringResource(R.string.settings_overlay_mode),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            label = { Text(overlayModeLabel(mode)) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_opacity),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                var opacity by remember(settings.opacityPercent) {
                    mutableFloatStateOf(settings.opacityPercent.toFloat())
                }
                Slider(
                    value = opacity,
                    onValueChange = { value -> opacity = value },
                    onValueChangeFinished = { viewModel.setOpacity(opacity.roundToInt()) },
                    valueRange = 20f..100f,
                )

                Text(
                    text = stringResource(R.string.settings_overlay_metrics),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OverlayMetric.entries.forEach { metric ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = metricLabel(metric),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = settings.isVisible(metric),
                            onCheckedChange = { visible -> viewModel.setMetricVisible(metric, visible) },
                        )
                    }
                }

                OutlinedButton(
                    onClick = viewModel::resetPosition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_reset_position))
                }
            }
        }
    }
}

@Composable
private fun overlayModeLabel(mode: OverlayMode): String = stringResource(
    when (mode) {
        OverlayMode.BUBBLE -> R.string.overlay_mode_bubble
        OverlayMode.MINI_BAR -> R.string.overlay_mode_mini_bar
        OverlayMode.PANEL -> R.string.overlay_mode_panel
    },
)

@Composable
private fun metricLabel(metric: OverlayMetric): String = stringResource(
    when (metric) {
        OverlayMetric.BATTERY -> R.string.settings_overlay_battery
        OverlayMetric.TEMPERATURE -> R.string.settings_overlay_temperature
        OverlayMetric.CPU -> R.string.settings_overlay_cpu
        OverlayMetric.MEMORY -> R.string.settings_overlay_memory
        OverlayMetric.STORAGE -> R.string.settings_overlay_storage
        OverlayMetric.FPS -> R.string.settings_overlay_fps
    },
)
