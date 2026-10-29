package com.t2m.monitor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 时序数据访问。
 *
 * 历史查询按时间桶取平均值，避免把 7 天的原始样本全部加载到内存绘图。
 */
@Dao
interface MetricDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<MetricSampleEntity>)

    @Query(
        """
        SELECT (ts / :bucketMillis) * :bucketMillis AS bucket_ts, AVG(value) AS avg_value
        FROM metric_sample
        WHERE metric = :metric AND ts >= :fromMillis AND ts <= :toMillis
        GROUP BY bucket_ts
        ORDER BY bucket_ts ASC
        """,
    )
    suspend fun queryBuckets(
        metric: String,
        fromMillis: Long,
        toMillis: Long,
        bucketMillis: Long,
    ): List<MetricBucketRow>

    @Query(
        """
        SELECT * FROM metric_sample
        WHERE ts >= :fromMillis AND ts < :toMillis
        ORDER BY ts ASC, metric ASC
        """,
    )
    suspend fun queryRange(fromMillis: Long, toMillis: Long): List<MetricSampleEntity>

    @Query("SELECT COUNT(*) FROM metric_sample")
    suspend fun countSamples(): Long

    @Query("SELECT MIN(ts) FROM metric_sample")
    suspend fun oldestTimestampMillis(): Long?

    @Query("SELECT MAX(ts) FROM metric_sample")
    suspend fun latestTimestampMillis(): Long?

    @Query("DELETE FROM metric_sample WHERE ts < :cutoffMillis")
    suspend fun deleteSamplesBefore(cutoffMillis: Long): Int

    @Query("DELETE FROM metric_sample")
    suspend fun clearSamples(): Int

    @Query("DELETE FROM metric_hourly")
    suspend fun clearHourly(): Int

    @Query("DELETE FROM metric_hourly WHERE hour_ts < :cutoffMillis")
    suspend fun deleteHourlyBefore(cutoffMillis: Long): Int

    /** 将指定时间之前的原始样本聚合为小时粒度。 */
    @Query(
        """
        INSERT OR REPLACE INTO metric_hourly(metric, hour_ts, min_value, max_value, avg_value, sample_count)
        SELECT metric, (ts / 3600000) * 3600000, MIN(value), MAX(value), AVG(value), COUNT(*)
        FROM metric_sample
        WHERE ts < :cutoffMillis
        GROUP BY metric, (ts / 3600000)
        """,
    )
    suspend fun rollupHourly(cutoffMillis: Long): Long
}
