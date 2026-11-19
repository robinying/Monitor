package com.t2m.monitor.data.repository

import com.t2m.monitor.data.db.AlertDao
import com.t2m.monitor.data.db.MetricDao
import com.t2m.monitor.data.db.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全部监控数据的一键清空。
 *
 * 原始样本、小时聚合与告警事件在同一事务内删除，任一步失败整体回滚，
 * 避免出现“提示失败但部分历史已丢失”的状态。
 */
@Singleton
class DataResetRepository @Inject constructor(
    private val metricDao: MetricDao,
    private val alertDao: AlertDao,
    private val transactionRunner: TransactionRunner,
) {

    suspend fun clearAll() {
        transactionRunner.run {
            metricDao.clearSamples()
            metricDao.clearHourly()
            alertDao.clear()
        }
    }
}
