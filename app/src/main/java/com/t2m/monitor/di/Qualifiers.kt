package com.t2m.monitor.di

import javax.inject.Qualifier

/** 应用级协程作用域：生命周期与进程一致，子任务由各自持有者取消。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
