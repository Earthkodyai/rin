package io.github.earthkodyai.rinalarm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.alarm.ring.AssetMusicCatalog
import io.github.earthkodyai.rinalarm.alarm.ring.MusicCatalog
import io.github.earthkodyai.rinalarm.mission.AndroidMissionFactory
import io.github.earthkodyai.rinalarm.mission.AndroidMissionReadiness
import io.github.earthkodyai.rinalarm.mission.MissionFactory
import io.github.earthkodyai.rinalarm.mission.MissionReadiness

@Module
@InstallIn(SingletonComponent::class)
abstract class MissionModule {
  @Binds abstract fun missionReadiness(impl: AndroidMissionReadiness): MissionReadiness

  @Binds abstract fun missionFactory(impl: AndroidMissionFactory): MissionFactory

  /** The alarm themes (UX.7): here with the games, the other thing an alarm picks per ring. */
  @Binds abstract fun musicCatalog(impl: AssetMusicCatalog): MusicCatalog
}
