package com.t2m.monitor.data.repository

import com.t2m.monitor.data.db.MetricDao
import com.t2m.monitor.data.db.MetricSampleEntity
import com.t2m.monitor.data.model.MetricAggregate
import com.t2m.monitor.data.model.MetricPoint
import com.t2m.monitor.data.model.RetentionPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 时序数据仓库：写入、历史查询、清理与保留策略。
 */
@Singleton
class MetricRepository @Inject constructor(
    private val metricDao: MetricDao,
) {

    suspend fun insert(aggregates: List<MetricAggregate>) {
        if (aggregates.isEmpty()) {
            return
        }
        metricDao.insertAll(
            aggregates.map { aggregate ->
                MetricSampleEntity(
                    metric = aggregate.metric,
                    timestampMillis = aggregate.timestampMillis,
                    value = aggregate.value,
                )
            },
        )
    }

    /** 按时间桶查询平均值，用于历史曲线。 */
    suspend fun history(
        metric: String,
        fromMillis: Long,
        toMillis: Long,
        bucketMillis: Long,
    ): List<MetricPoint> = metricDao
        .queryBuckets(metric, fromMillis, toMillis, bucketMillis)
        .map { row -> MetricPoint(row.bucketTimestampMillis, row.avgValue) }

    suspend fun sampleCount(): Long = metricDao.countSamples()

    suspend fun oldestTimestampMillis(): Long? = metricDao.oldestTimestampMillis()

    suspend fun latestTimestampMillis(): Long? = metricDao.latestTimestampMillis()

    /**
     * 执行保留策略：先把过期原始样本聚合为小时粒度，再删除原始样本与过期聚合。
     *
     * @param nowMillis 当前墙上时间
     */
    suspend fun prune(nowMillis: Long) {
        val rawCutoff = RetentionPolicy.rawCutoff(nowMillis)
        metricDao.rollupHourly(rawCutoff)
        metricDao.deleteSamplesBefore(rawCutoff)
        metricDao.deleteHourlyBefore(RetentionPolicy.hourlyCutoff(nowMillis))
    }

    suspend fun clearAll() {
        metricDao.clearSamples()
        metricDao.clearHourly()
    }
}
