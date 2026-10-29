package com.t2m.monitor.domain.export

import android.content.Context
import com.t2m.monitor.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 读取当前应用语言的 CSV 表头。 */
@Singleton
class AndroidCsvHeaderProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CsvHeaderProvider {

    override fun header(): String = context.getString(R.string.csv_header)
}
