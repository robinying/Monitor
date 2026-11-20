package com.robin.monitor.data.db

import com.robin.monitor.data.model.MetricKeys

/**
 * 时序 DAO 的测试替身：记录写入/清理调用，并允许注入写入行为。
 */
class TestMetricDao : MetricDao {

    val inserted = mutableListOf<MetricSampleEntity>()

    val rollupCutoffs = mutableListOf<Long>()

    val deletedSampleCutoffs = mutableListOf<Long>()

    val deletedHourlyCutoffs = mutableListOf<Long>()

    var statisticsRow: MetricStatsRow = MetricStatsRow(null, null, null)

    var insertBehavior: suspend (List<MetricSampleEntity>) -> Unit = { samples ->
        inserted += samples
    }

    var clearSamplesCount: Int = 0

    var clearHourlyCount: Int = 0

    override suspend fun insertAll(samples: List<MetricSampleEntity>) {
        insertBehavior(samples)
    }

    override suspend fun queryBuckets(
        metric: String,
        fromMillis: Long,
        toMillis: Long,
        bucketMillis: Long,
    ): List<MetricBucketRow> = emptyList()

    override suspend fun queryRange(fromMillis: Long, toMillis: Long): List<MetricSampleEntity> =
        emptyList()

    override suspend fun statistics(
        metric: String,
        fromMillis: Long,
        toMillis: Long,
    ): MetricStatsRow = statisticsRow

    override suspend fun countSamples(): Long = inserted.size.toLong()

    override suspend fun oldestTimestampMillis(): Long? = inserted.minOfOrNull { it.timestampMillis }

    override suspend fun latestTimestampMillis(): Long? = inserted.maxOfOrNull { it.timestampMillis }

    override suspend fun deleteSamplesBefore(cutoffMillis: Long): Int {
        deletedSampleCutoffs += cutoffMillis
        return 0
    }

    override suspend fun clearSamples(): Int {
        clearSamplesCount++
        inserted.clear()
        return 0
    }

    override suspend fun clearHourly(): Int {
        clearHourlyCount++
        return 0
    }

    override suspend fun deleteHourlyBefore(cutoffMillis: Long): Int {
        deletedHourlyCutoffs += cutoffMillis
        return 0
    }

    override suspend fun rollupHourly(cutoffMillis: Long): Long {
        rollupCutoffs += cutoffMillis
        return 0L
    }

    companion object {
        fun sample(
            metric: String = MetricKeys.MEMORY_USED_PERCENT,
            timestampMillis: Long = 1_000L,
            value: Float = 50f,
        ): MetricSampleEntity = MetricSampleEntity(
            metric = metric,
            timestampMillis = timestampMillis,
            value = value,
        )
    }
}
