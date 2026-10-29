package com.t2m.monitor.domain.export

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 旧系统导出目录选择：外部目录与缓存兜底必须分别落在 file_paths 声明的根下。
 */
class ExportPathsTest {

    @Test
    fun `uses external Download directory when available`() {
        val external = File("/storage/emulated/0/Android/data/pkg/files/Download")
        val cache = File("/data/user/0/pkg/cache")

        assertEquals(
            File(external, "T2MMonitor").path,
            ExportPaths.legacyDirectory(external, cache).path,
        )
    }

    @Test
    fun `falls back to cache exports directory matching file provider config`() {
        val cache = File("/data/user/0/pkg/cache")

        assertEquals(
            File(cache, "exports/T2MMonitor").path,
            ExportPaths.legacyDirectory(null, cache).path,
        )
    }
}
