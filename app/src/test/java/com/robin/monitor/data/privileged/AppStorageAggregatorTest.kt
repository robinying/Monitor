package com.robin.monitor.data.privileged

import com.robin.monitor.data.model.AppStorageUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 应用存储聚合：单包失败保留其他结果，全部失败返回 null，失败可上报。
 */
class AppStorageAggregatorTest {

    private val failures = mutableListOf<String>()

    @Test
    fun `keeps successful packages when one package fails`() {
        val apps = buildAppStorageList(
            packageNames = listOf("a", "bad", "b"),
            limit = 5,
            queryBytes = { packageName ->
                if (packageName == "bad") throw SecurityException("denied")
                if (packageName == "a") 100L to 300L else 50L to 50L
            },
            label = { packageName -> packageName.uppercase() },
            onFailure = { packageName, _ -> failures += packageName },
        )

        assertEquals(listOf("a", "b"), apps!!.map { it.packageName })
        assertEquals(400L, apps.first().totalBytes)
        assertEquals(listOf("bad"), failures)
    }

    @Test
    fun `returns null when every package fails`() {
        val apps = buildAppStorageList(
            packageNames = listOf("a", "b"),
            limit = 5,
            queryBytes = { throw IllegalStateException("storage unavailable") },
            label = { null },
            onFailure = { packageName, _ -> failures += packageName },
        )

        assertNull(apps)
        assertEquals(listOf("a", "b"), failures)
    }

    @Test
    fun `keeps empty input empty`() {
        assertEquals(
            emptyList<AppStorageUsage>(),
            buildAppStorageList(
                packageNames = emptyList(),
                limit = 5,
                queryBytes = { null },
                label = { null },
                onFailure = { _, _ -> },
            ),
        )
    }
}
