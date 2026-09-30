package io.github.earthkodyai.rinalarm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.dialogue.AndroidLineVoiceFactory
import io.github.earthkodyai.rinalarm.dialogue.AssetLineBook
import io.github.earthkodyai.rinalarm.dialogue.LineBook
import io.github.earthkodyai.rinalarm.dialogue.LineVoiceFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class DialogueModule {
  @Binds abstract fun lineBook(impl: AssetLineBook): LineBook

  @Binds abstract fun lineVoices(impl: AndroidLineVoiceFactory): LineVoiceFactory
}
