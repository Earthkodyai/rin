package io.github.earthkodyai.rinalarm.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.alarm.engine.AndroidSystemAlarms
import io.github.earthkodyai.rinalarm.alarm.engine.DeviceStateProbe
import io.github.earthkodyai.rinalarm.alarm.engine.MissedAlarmNotifier
import io.github.earthkodyai.rinalarm.alarm.engine.Ringer
import io.github.earthkodyai.rinalarm.alarm.engine.SystemAlarms
import io.github.earthkodyai.rinalarm.alarm.log.AndroidDeviceState
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.alarm.log.RoomRingLog
import io.github.earthkodyai.rinalarm.alarm.notify.AlarmNotifications
import io.github.earthkodyai.rinalarm.alarm.ring.ServiceRinger
import io.github.earthkodyai.rinalarm.data.db.PendingRingDao
import io.github.earthkodyai.rinalarm.data.db.RinDatabase
import io.github.earthkodyai.rinalarm.data.db.RingEventDao
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A process-wide scope for work that must outlive the component that started it (receivers, the ring service). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AlarmProvidesModule {
  @Provides fun pendingRingDao(database: RinDatabase): PendingRingDao = database.pendingRingDao()

  @Provides fun ringEventDao(database: RinDatabase): RingEventDao = database.ringEventDao()

  @Provides @Singleton @AppScope fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmBindsModule {
  @Binds abstract fun systemAlarms(impl: AndroidSystemAlarms): SystemAlarms

  @Binds abstract fun ringer(impl: ServiceRinger): Ringer

  @Binds abstract fun missedAlarmNotifier(impl: AlarmNotifications): MissedAlarmNotifier

  @Binds abstract fun deviceState(impl: AndroidDeviceState): DeviceStateProbe

  @Binds abstract fun ringLog(impl: RoomRingLog): RingLog
}
