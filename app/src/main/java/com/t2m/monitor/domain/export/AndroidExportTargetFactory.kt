package com.t2m.monitor.domain.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.t2m.monitor.core.MonitorLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生产导出目标：
 * - Android 10+：MediaStore Downloads（无需存储权限，IS_PENDING 控制可见性）；
 * - Android 8/9：应用专属外部目录，缓存兜底使用 `cache/exports/T2MMonitor`（与 file_paths 一致）。
 */
@Singleton
class AndroidExportTargetFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: MonitorLogger,
) : ExportTargetFactory {

    override fun create(fileName: String): ExportTarget {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStoreTarget(fileName)
        } else {
            createLegacyTarget(fileName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createMediaStoreTarget(fileName: String): ExportTarget {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE_CSV)
            put(MediaStore.Downloads.RELATIVE_PATH, "$EXPORT_DIRECTORY/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: throw IOException("insert download failed")

        return object : ExportTarget {
            override val uri: String = uri.toString()

            override fun openStream(): OutputStream =
                resolver.openOutputStream(uri) ?: throw IOException("open export stream failed")

            override fun markComplete() {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            }

            override fun abort() {
                runCatching { resolver.delete(uri, null, null) }
                    .onFailure { error -> logger.warn("delete pending export failed", error) }
            }
        }
    }

    private fun createLegacyTarget(fileName: String): ExportTarget {
        val directory = ExportPaths.legacyDirectory(
            externalDownloadsBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            cacheBase = context.cacheDir,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("create export directory failed")
        }
        val file = File(directory, fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return object : ExportTarget {
            override val uri: String = uri.toString()

            override fun openStream(): OutputStream = FileOutputStream(file)

            override fun markComplete() = Unit

            override fun abort() {
                runCatching { file.delete() }
                    .onFailure { error -> logger.warn("delete legacy export failed", error) }
            }
        }
    }

    private companion object {
        const val MIME_TYPE_CSV = "text/csv"
        const val EXPORT_DIRECTORY = "Download/T2MMonitor"
    }
}

/**
 * 旧系统导出目录选择（纯函数，便于单元测试）。
 *
 * 外部目录：`<files>/Download/T2MMonitor`，对应 file_paths `external-files-path path="Download/"`；
 * 缓存兜底：`<cache>/exports/T2MMonitor`，对应 `cache-path path="exports/"`。
 */
object ExportPaths {

    fun legacyDirectory(externalDownloadsBase: File?, cacheBase: File): File =
        if (externalDownloadsBase != null) {
            File(externalDownloadsBase, LEGACY_EXPORT_DIRECTORY)
        } else {
            File(cacheBase, CACHE_EXPORT_DIRECTORY)
        }

    private const val LEGACY_EXPORT_DIRECTORY = "T2MMonitor"
    private const val CACHE_EXPORT_DIRECTORY = "exports/T2MMonitor"
}
