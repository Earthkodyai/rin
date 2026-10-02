package io.github.earthkodyai.rinalarm

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** First-launch permission setup (task 1.4). */
@Serializable data object Onboarding : NavKey

@Serializable data object Diagnostics : NavKey

/** The look (day, night, by the time) and the home tour. */
@Serializable data object Settings : NavKey

/** "Try the games": a practice round of each game, no alarm (UX.8). */
@Serializable data object Practice : NavKey

/** The tournament's start page (G.5): game, name, university, rules. */
@Serializable data object TournamentStart : NavKey

/** How Rin uses your data: PRIVACY.md (6.3). */
@Serializable data object Privacy : NavKey

/** The alarm editor; [alarmId] 0 adds a new alarm. [guided]: the home tour's walkthrough of the first alarm (UX.8). */
@Serializable data class AlarmEditor(val alarmId: Long, val guided: Boolean = false) : NavKey
