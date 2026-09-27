package io.github.earthkodyai.rinalarm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.time.SystemTimeSource
import io.github.earthkodyai.rinalarm.time.TimeSource

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
  @Binds abstract fun timeSource(impl: SystemTimeSource): TimeSource
}
