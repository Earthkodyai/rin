package io.github.earthkodyai.rinalarm.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.alarm.log.RingHistoryRepository
import io.github.earthkodyai.rinalarm.alarm.log.RoomRingHistory
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.data.DataStoreTournamentStore
import io.github.earthkodyai.rinalarm.data.TournamentStore
import io.github.earthkodyai.rinalarm.data.SettingsRepository
import io.github.earthkodyai.rinalarm.data.RoomAlarmRepository
import io.github.earthkodyai.rinalarm.data.StorageFiles
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.data.db.RinDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
  @Provides
  @Singleton
  fun database(@ApplicationContext context: Context): RinDatabase =
    RinDatabase.open(context, StorageFiles.database(context))

  @Provides fun alarmDao(database: RinDatabase): AlarmDao = database.alarmDao()

  @Provides
  @Singleton
  fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(produceFile = { StorageFiles.settings(context) })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds abstract fun alarmRepository(impl: RoomAlarmRepository): AlarmRepository

  @Binds abstract fun appSettings(impl: SettingsRepository): AppSettings

  @Binds abstract fun tournamentStore(impl: DataStoreTournamentStore): TournamentStore

  @Binds abstract fun ringHistory(impl: RoomRingHistory): RingHistoryRepository
}
