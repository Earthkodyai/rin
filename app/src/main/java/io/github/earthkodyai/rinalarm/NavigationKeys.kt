package io.github.earthkodyai.rinalarm

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** First-launch permission setup (task 1.4). */
@Serializable data object Onboarding : NavKey

@Serializable data object Diagnostics : NavKey

/** Pout off (Phase 5). */
@Serializable data object Settings : NavKey

/** "Try the games": a practice round of each game, no alarm (UX.8). */
@Serializable data object Practice : NavKey

/** How Rin uses your data: PRIVACY.md (6.3). */
@Serializable data object Privacy : NavKey

/** The alarm editor; [alarmId] 0 adds a new alarm. */
@Serializable data class AlarmEditor(val alarmId: Long) : NavKey
