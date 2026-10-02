package io.github.earthkodyai.rinalarm.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Who plays the tournament and how they did, on this phone only (G.5; G.6 sends a best to the leaderboard).
 *
 * @property name optional, as typed (trimmed, at most [NAME_MAX] characters); "" when not given.
 * @property university a university's id, or null for "Not listed" (the list comes in G.7).
 * @property agreed the version of the rules the user accepted; the start page asks again when [RULES_VERSION] is newer
 *   (G.6–G.7 add what goes online and the logos).
 */
data class TournamentEntry(val name: String = "", val university: String? = null, val agreed: Int = 0) {
  val agreedToCurrent: Boolean
    get() = agreed >= RULES_VERSION

  companion object {
    const val NAME_MAX = 20
    const val RULES_VERSION = 1
  }
}

interface TournamentStore {
  val entry: Flow<TournamentEntry>

  suspend fun setEntry(entry: TournamentEntry)

  fun best(game: TournamentGame): Flow<TournamentScore?>

  /** Keeps [score] if it beats the best so far; true when it did (a new best). */
  suspend fun record(score: TournamentScore): Boolean
}

@Singleton
class DataStoreTournamentStore @Inject constructor(private val store: DataStore<Preferences>) : TournamentStore {
  override val entry: Flow<TournamentEntry> =
    store.data.map { TournamentEntry(it[NAME] ?: "", it[UNIVERSITY], it[AGREED] ?: 0) }

  override suspend fun setEntry(entry: TournamentEntry) {
    store.edit {
      it[NAME] = entry.name.trim().take(TournamentEntry.NAME_MAX)
      val university = entry.university
      if (university == null) it.remove(UNIVERSITY) else it[UNIVERSITY] = university
      it[AGREED] = entry.agreed
    }
  }

  override fun best(game: TournamentGame): Flow<TournamentScore?> =
    store.data.map { p ->
      val levels = p[levelsKey(game)] ?: return@map null
      TournamentScore(game, levels, p[timeKey(game)] ?: 0L)
    }

  override suspend fun record(score: TournamentScore): Boolean {
    if (!score.beats(best(score.game).first())) return false
    store.edit {
      it[levelsKey(score.game)] = score.levels
      it[timeKey(score.game)] = score.timeMs
    }
    return true
  }

  private companion object {
    val NAME = stringPreferencesKey("tournament_name")
    val UNIVERSITY = stringPreferencesKey("tournament_university")
    val AGREED = intPreferencesKey("tournament_agreed")

    fun levelsKey(game: TournamentGame) = intPreferencesKey("tournament_best_${game.stored}_levels")

    fun timeKey(game: TournamentGame) = longPreferencesKey("tournament_best_${game.stored}_time_ms")
  }
}
