package com.t2m.monitor.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.t2m.monitor.R
import com.t2m.monitor.data.permission.PermissionStatus
import com.t2m.monitor.ui.components.PillTone
import com.t2m.monitor.ui.components.StatusPill

/**
 * 权限引导页。
 *
 * 逐项展示必需权限的授权状态并跳转系统设置；通知权限在 Android 13+ 直接发起运行时申请。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionGuideViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val shizukuAvailable by viewModel.shizukuAvailable.collectAsStateWithLifecycle()
    val shizukuGranted by viewModel.shizukuGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    BackHandler(onBack = onFinished)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permission_guide_title)) },
                navigationIcon = {
                    TextButton(onClick = onFinished) {
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
                text = stringResource(R.string.permission_guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PermissionCard(
                step = 1,
                title = stringResource(R.string.permission_overlay_title),
                description = stringResource(R.string.permission_overlay_desc),
                granted = status.overlayGranted,
                optional = false,
                onAction = viewModel::openOverlaySettings,
            )

            PermissionCard(
                step = 2,
                title = stringResource(R.string.permission_notification_title),
                description = stringResource(R.string.permission_notification_desc),
                granted = status.notificationGranted,
                optional = false,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.openNotificationSettings()
                    }
                },
            )

            ShizukuCard(
                available = shizukuAvailable,
                granted = shizukuGranted,
                onRequest = viewModel::requestShizukuPermission,
            )

            PermissionCard(
                step = 3,
                title = stringResource(R.string.permission_usage_title),
                description = stringResource(R.string.permission_usage_desc),
                granted = status.usageStatsGranted,
                optional = true,
                onAction = viewModel::openUsageAccessSettings,
            )

            PermissionCard(
                step = 4,
                title = stringResource(R.string.permission_battery_title),
                description = stringResource(R.string.permission_battery_desc),
                granted = status.ignoringBatteryOptimizations,
                optional = true,
                onAction = viewModel::requestBatteryOptimization,
            )

            Button(
                onClick = onFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text(stringResource(R.string.permission_done))
            }
        }
    }
}

@Composable
private fun ShizukuCard(
    available: Boolean,
    granted: Boolean,
    onRequest: () -> Unit,
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
                Text(
                    text = stringResource(R.string.permission_shizuku_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = stringResource(
                        when {
                            granted -> R.string.shizuku_status_granted
                            !available -> R.string.shizuku_status_unavailable
                            else -> R.string.shizuku_status_denied
                        },
                    ),
                    tone = when {
                        granted -> PillTone.POSITIVE
                        available -> PillTone.NEGATIVE
                        else -> PillTone.NEUTRAL
                    },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.permission_shizuku_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!available) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.shizuku_unavailable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onRequest, enabled = available && !granted) {
                Text(stringResource(R.string.shizuku_request))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    step: Int,
    title: String,
    description: String,
    granted: Boolean,
    optional: Boolean,
    onAction: () -> Unit,
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
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = step.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = stringResource(
                        when {
                            granted -> R.string.permission_granted
                            optional -> R.string.permission_optional
                            else -> R.string.permission_denied
                        },
                    ),
                    tone = when {
                        granted -> PillTone.POSITIVE
                        optional -> PillTone.NEUTRAL
                        else -> PillTone.NEGATIVE
                    },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onAction) {
                Text(stringResource(R.string.permission_go_grant))
            }
        }
    }
}
