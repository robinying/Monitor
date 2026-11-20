package com.robin.monitor.data.db

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/** 跨 DAO 操作的数据库事务边界，便于测试替换。 */
interface TransactionRunner {

    suspend fun <T> run(block: suspend () -> T): T
}

/** Room 实现：同一事务内串行提交，任一操作失败则整体回滚。 */
@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: MonitorDatabase,
) : TransactionRunner {

    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}
