package com.t2m.monitor.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.t2m.monitor.di.MonitorAppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 前台服务与悬浮窗生命周期（真实 ContentProvider / WindowManager）。
 */
@RunWith(AndroidJUnit4::class)
class MonitorServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val entryPoint: MonitorAppEntryPoint
        get() = EntryPointAccessors.fromApplication(context, MonitorAppEntryPoint::class.java)

    @Before
    fun stopServiceAndDisableOverlay() {
        runBlocking {
            context.stopService(Intent(context, MonitorService::class.java))
            entryPoint.preferences().setOverlayEnabled(false)
            withTimeout(TIMEOUT_MILLIS) {
                entryPoint.runtime().serviceRunning.first { running -> !running }
            }
        }
    }

    @After
    fun cleanup() {
        runBlocking {
            entryPoint.overlayController().release()
            context.stopService(Intent(context, MonitorService::class.java))
            entryPoint.preferences().setOverlayEnabled(false)
            withTimeout(TIMEOUT_MILLIS) {
                entryPoint.runtime().serviceRunning.first { running -> !running }
            }
        }
    }

    @Test
    fun startsAndStopsForegroundService() {
        runBlocking {
            startService()

            withTimeout(TIMEOUT_MILLIS) { entryPoint.runtime().serviceRunning.first { it } }
            context.stopService(Intent(context, MonitorService::class.java))
            withTimeout(TIMEOUT_MILLIS) { entryPoint.runtime().serviceRunning.first { !it } }
            assertTrue(true)
        }
    }

    @Test
    fun showsAndHidesOverlayWhenPermitted() {
        runBlocking {
            assumeTrue("overlay permission not granted", Settings.canDrawOverlays(context))
            entryPoint.preferences().setOverlayEnabled(true)
            startService()

            val runtime = entryPoint.runtime()
            withTimeout(TIMEOUT_MILLIS) { runtime.overlayVisible.first { it } }

            entryPoint.overlayController().hide()
            withTimeout(TIMEOUT_MILLIS) { runtime.overlayVisible.first { !it } }

            entryPoint.overlayController().show()
            withTimeout(TIMEOUT_MILLIS) { runtime.overlayVisible.first { it } }
        }
    }

    private fun startService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_START),
        )
    }

    private companion object {
        const val TIMEOUT_MILLIS = 8_000L
    }
}
