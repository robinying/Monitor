package com.t2m.monitor.domain.export

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.t2m.monitor.core.MonitorLogger
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生产导出目标：在真实 ContentResolver/FileProvider 上写入并清理。
 */
@RunWith(AndroidJUnit4::class)
class AndroidExportTargetFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logger = object : MonitorLogger {
        override fun warn(message: String, throwable: Throwable?) = Unit
    }

    @Test
    fun writesAndCompletesMediaStoreTarget() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val factory = AndroidExportTargetFactory(context, logger)
        val target = factory.create("monitor-test-${System.nanoTime()}.csv")
        try {
            target.openStream().bufferedWriter().use { writer -> writer.write("header\nrow\n") }
            target.markComplete()
            val text = context.contentResolver.openInputStream(Uri.parse(target.uri))
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
            assertEquals("header\nrow\n", text)
        } finally {
            target.abort()
        }
    }
}
