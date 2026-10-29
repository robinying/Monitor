package com.t2m.monitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.t2m.monitor.ui.theme.T2mMonitorTheme
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
            T2mMonitorTheme {
                AppRoot()
            }
        }
    }
}
