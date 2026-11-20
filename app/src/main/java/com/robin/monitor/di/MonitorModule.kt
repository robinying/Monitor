package com.robin.monitor.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.robin.monitor.core.AndroidMonitorLogger
import com.robin.monitor.core.FeatureConfig
import com.robin.monitor.core.MonitorClock
import com.robin.monitor.core.MonitorLogger
import com.robin.monitor.core.SystemMonitorClock
import com.robin.monitor.data.collector.AndroidBatterySource
import com.robin.monitor.data.db.AlertDao
import com.robin.monitor.data.db.MetricDao
import com.robin.monitor.data.db.MonitorDatabase
import com.robin.monitor.data.db.RoomTransactionRunner
import com.robin.monitor.data.db.TransactionRunner
import com.robin.monitor.data.collector.AndroidCpuStatSource
import com.robin.monitor.data.collector.AndroidMemorySource
import com.robin.monitor.data.collector.AndroidStorageSource
import com.robin.monitor.data.collector.AndroidThermalSource
import com.robin.monitor.data.collector.AndroidUsageSource
import com.robin.monitor.data.collector.ThermalCollector
import com.robin.monitor.data.collector.ThermalSource
import com.robin.monitor.data.collector.UsageCollector
import com.robin.monitor.data.collector.UsageSource
import com.robin.monitor.data.collector.AppStorageCollector
import com.robin.monitor.data.collector.BatteryCollector
import com.robin.monitor.data.collector.BatterySource
import com.robin.monitor.data.collector.CollectorRegistry
import com.robin.monitor.data.collector.CollectorSelection
import com.robin.monitor.data.collector.CpuCollector
import com.robin.monitor.data.collector.CpuStatSource
import com.robin.monitor.data.collector.FpsCollector
import com.robin.monitor.data.collector.MemoryCollector
import com.robin.monitor.data.collector.MemorySource
import com.robin.monitor.data.collector.StorageCollector
import com.robin.monitor.data.collector.StorageSource
import com.robin.monitor.data.privileged.AndroidAppStorageSource
import com.robin.monitor.data.privileged.RemoteCommandLauncher
import com.robin.monitor.data.privileged.ShizukuAccess
import com.robin.monitor.data.privileged.ShizukuCommandLauncher
import com.robin.monitor.data.privileged.ShizukuController
import com.robin.monitor.data.privileged.AppStorageSource
import com.robin.monitor.data.privileged.ShizukuFpsSource
import com.robin.monitor.data.privileged.SystemFpsSource
import com.robin.monitor.domain.export.AndroidCsvHeaderProvider
import com.robin.monitor.domain.export.AndroidExportTargetFactory
import com.robin.monitor.domain.export.CsvHeaderProvider
import com.robin.monitor.domain.export.ExportTargetFactory
import com.robin.monitor.domain.SamplingScheduler
import com.robin.monitor.domain.alert.ThresholdEvaluator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 采集层依赖装配。
 *
 * 数据源均通过接口注入，单元测试可用 Fake 替换。
 */
@Module
@InstallIn(SingletonComponent::class)
object MonitorModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
    )

    @Provides
    @Singleton
    fun provideMonitorDatabase(
        @ApplicationContext context: Context,
    ): MonitorDatabase = Room.databaseBuilder(
        context,
        MonitorDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(MonitorDatabase.MIGRATION_1_2).build()

    @Provides
    fun provideMetricDao(database: MonitorDatabase): MetricDao = database.metricDao()

    @Provides
    fun provideAlertDao(database: MonitorDatabase): AlertDao = database.alertDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(impl: RoomTransactionRunner): TransactionRunner = impl

    @Provides
    @Singleton
    fun provideFeatureConfig(): FeatureConfig = FeatureConfig()

    @Provides
    @Singleton
    fun provideMonitorClock(): MonitorClock = SystemMonitorClock()

    @Provides
    @Singleton
    fun provideMonitorLogger(): MonitorLogger = AndroidMonitorLogger()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideBatterySource(impl: AndroidBatterySource): BatterySource = impl

    @Provides
    @Singleton
    fun provideCpuStatSource(impl: AndroidCpuStatSource): CpuStatSource = impl

    @Provides
    @Singleton
    fun provideMemorySource(impl: AndroidMemorySource): MemorySource = impl

    @Provides
    @Singleton
    fun provideStorageSource(impl: AndroidStorageSource): StorageSource = impl

    @Provides
    @Singleton
    fun provideThermalSource(impl: AndroidThermalSource): ThermalSource = impl

    @Provides
    @Singleton
    fun provideUsageSource(impl: AndroidUsageSource): UsageSource = impl

    @Provides
    @Singleton
    fun provideShizukuAccess(impl: ShizukuController): ShizukuAccess = impl

    @Provides
    @Singleton
    fun provideRemoteCommandLauncher(impl: ShizukuCommandLauncher): RemoteCommandLauncher = impl

    @Provides
    @Singleton
    fun provideSystemFpsSource(impl: ShizukuFpsSource): SystemFpsSource = impl

    @Provides
    @Singleton
    fun provideAppStorageSource(impl: AndroidAppStorageSource): AppStorageSource = impl

    @Provides
    @Singleton
    fun provideCollectorRegistry(
        batteryCollector: BatteryCollector,
        cpuCollector: CpuCollector,
        memoryCollector: MemoryCollector,
        storageCollector: StorageCollector,
        thermalCollector: ThermalCollector,
        usageCollector: UsageCollector,
        fpsCollector: FpsCollector,
        appStorageCollector: AppStorageCollector,
        features: FeatureConfig,
    ): CollectorRegistry = CollectorRegistry(
        collectors = CollectorSelection.select(
            collectors = listOf(
                batteryCollector,
                cpuCollector,
                memoryCollector,
                storageCollector,
                thermalCollector,
                usageCollector,
                fpsCollector,
                appStorageCollector,
            ),
            features = features,
        ),
    )

    @Provides
    @Singleton
    fun provideExportTargetFactory(impl: AndroidExportTargetFactory): ExportTargetFactory = impl

    @Provides
    @Singleton
    fun provideCsvHeaderProvider(impl: AndroidCsvHeaderProvider): CsvHeaderProvider = impl

    @Provides
    @Singleton
    fun provideSamplingScheduler(): SamplingScheduler = SamplingScheduler()

    @Provides
    @Singleton
    fun provideThresholdEvaluator(): ThresholdEvaluator = ThresholdEvaluator()

    private const val PREFERENCES_FILE_NAME = "monitor_preferences"
    private const val DATABASE_NAME = "monitor.db"
}
