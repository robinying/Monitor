package com.robin.monitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.monitor.ui.dashboard.DashboardRoute
import com.robin.monitor.ui.diagnostics.DiagnosticsRoute
import com.robin.monitor.ui.history.HistoryRoute
import com.robin.monitor.ui.permission.PermissionGuideScreen
import com.robin.monitor.ui.settings.SettingsRoute

/**
 * 应用根界面：首次启动进入权限引导，之后进入仪表盘。
 */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    viewModel: AppRootViewModel = viewModel(),
) {
    val guideSeen by viewModel.guideSeen.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf<AppScreen?>(null) }

    LaunchedEffect(guideSeen) {
        if (screen == null && guideSeen != null) {
            screen = if (guideSeen == true) AppScreen.DASHBOARD else AppScreen.GUIDE
        }
    }

    when (screen) {
        AppScreen.GUIDE -> PermissionGuideScreen(
            onFinished = {
                viewModel.markGuideSeen()
                screen = AppScreen.DASHBOARD
            },
            modifier = modifier,
        )

        AppScreen.DASHBOARD -> DashboardRoute(
            onOpenGuide = { screen = AppScreen.GUIDE },
            onOpenHistory = { screen = AppScreen.HISTORY },
            onOpenDiagnostics = { screen = AppScreen.DIAGNOSTICS },
            onOpenSettings = { screen = AppScreen.SETTINGS },
            modifier = modifier,
        )

        AppScreen.HISTORY -> HistoryRoute(
            onBack = { screen = AppScreen.DASHBOARD },
            modifier = modifier,
        )

        AppScreen.DIAGNOSTICS -> DiagnosticsRoute(
            onBack = { screen = AppScreen.DASHBOARD },
            modifier = modifier,
        )

        AppScreen.SETTINGS -> SettingsRoute(
            onBack = { screen = AppScreen.DASHBOARD },
            modifier = modifier,
        )

        null -> Box(modifier = modifier.fillMaxSize())
    }
}

/** 应用内页面：权限引导、仪表盘、历史、诊断与设置。 */
private enum class AppScreen {
    GUIDE,
    DASHBOARD,
    HISTORY,
    DIAGNOSTICS,
    SETTINGS,
}
