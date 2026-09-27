package io.github.earthkodyai.rinalarm

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** First-launch permission setup (task 1.4). */
@Serializable data object Onboarding : NavKey

@Serializable data object Diagnostics : NavKey

/** The alarm editor; [alarmId] 0 adds a new alarm. */
@Serializable data class AlarmEditor(val alarmId: Long) : NavKey
