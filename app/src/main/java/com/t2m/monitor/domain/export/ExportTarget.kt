package com.t2m.monitor.domain.export

import java.io.OutputStream

/**
 * 一次导出的目标文件。
 *
 * 实现负责打开写入流、成功收尾与失败清理；[abort] 必须在任何失败路径调用，
 * 避免 MediaStore 留下 pending 半成品或应用目录留下空文件。
 */
interface ExportTarget {

    /** 可分享的 URI 字符串（MediaStore content URI 或 FileProvider URI）。 */
    val uri: String

    fun openStream(): OutputStream

    /** 写入成功后调用；MediaStore 实现需要清除 IS_PENDING。 */
    fun markComplete()

    /** 失败时清理半成品；实现必须幂等且不抛异常。 */
    fun abort()
}

/** 导出目标工厂。 */
fun interface ExportTargetFactory {

    fun create(fileName: String): ExportTarget
}

/** CSV 表头提供者，便于隔离 Android 资源读取。 */
fun interface CsvHeaderProvider {

    fun header(): String
}

/**
 * 导出结果。
 *
 * @param uri 可分享 URI 的字符串形式，界面层负责转换为 android.net.Uri
 */
data class ExportFile(
    val uri: String,
    val displayName: String,
)
