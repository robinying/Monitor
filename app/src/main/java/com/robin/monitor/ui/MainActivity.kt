package com.robin.monitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.robin.monitor.ui.theme.RobinMonitorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主界面。
 *
 * 承载实时状态仪表盘；M2 起前台服务接管采样，界面仅做展示。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RobinMonitorTheme {
                AppRoot()
            }
        }
    }
}
