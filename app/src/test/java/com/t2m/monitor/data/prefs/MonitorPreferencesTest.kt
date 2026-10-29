package com.t2m.monitor.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.t2m.monitor.core.MonitorLogger
import com.t2m.monitor.data.model.OverlayMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 偏好存储：正常读写与 DataStore 失败时的降级行为。
 */
class MonitorPreferencesTest {

    private val logger = RecordingLogger()

    @Test
    fun `reads and writes overlay settings`() = runTest {
        val preferences = MonitorPreferences(InMemoryDataStore(), logger)

        assertFalse(preferences.overlayEnabled.first())
        assertTrue(preferences.setOverlayEnabled(true))
        assertTrue(preferences.setOverlayMode(OverlayMode.PANEL))
        assertTrue(preferences.setOverlayPosition(12, 34))

        val settings = preferences.readOverlaySettings()
        assertTrue(preferences.overlayEnabled.first())
        assertEquals(OverlayMode.PANEL, settings.mode)
        assertEquals(12, settings.x)
        assertEquals(34, settings.y)
    }

    @Test
    fun `falls back to defaults when datastore read fails`() = runTest {
        val preferences = MonitorPreferences(FailingDataStore(), logger)

        assertFalse(preferences.overlayEnabled.first())
        assertFalse(preferences.guideSeen.first())
        val settings = preferences.readOverlaySettings()
        assertEquals(OverlayMode.BUBBLE, settings.mode)
        assertEquals(OverlaySettings.POSITION_UNSET, settings.x)
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `returns false when datastore write fails`() = runTest {
        val preferences = MonitorPreferences(FailingDataStore(), logger)
        assertFalse(preferences.setOverlayEnabled(true))
        assertFalse(preferences.setGuideSeen(true))
        assertTrue(logger.messages.isNotEmpty())
    }

    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            throw IOException("broken datastore")
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw IOException("broken datastore")
        }
    }

    private class RecordingLogger : MonitorLogger {
        val messages = mutableListOf<String>()

        override fun warn(message: String, throwable: Throwable?) {
            messages += message
        }
    }
}
