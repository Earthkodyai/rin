package io.github.earthkodyai.rinalarm.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * Who plays the tournament and how they did (G.5), and whether their bests go to the online leaderboard (G.6).
 *
 * @property name optional, as typed (trimmed, at most [NAME_MAX] characters); "" when not given.
 * @property university an id from [io.github.earthkodyai.rinalarm.tournament.Universities], or null for "Not listed".
 * @property agreed the version of the rules the user accepted; the start page asks again when [RULES_VERSION] is newer
 *   (2: posting online is mentioned; 3: university names and logos, not affiliated, G.7).
 * @property online the user's consent to post their name, university and bests to the leaderboard (off by default).
 */
data class TournamentEntry(
  val name: String = "",
  val university: String? = null,
  val agreed: Int = 0,
  val online: Boolean = false,
) {
  val agreedToCurrent: Boolean
    get() = agreed >= RULES_VERSION

  companion object {
    const val NAME_MAX = 20
    const val RULES_VERSION = 3
  }
}

interface TournamentStore {
  val entry: Flow<TournamentEntry>

  suspend fun setEntry(entry: TournamentEntry)

  fun best(game: TournamentGame): Flow<TournamentScore?>

  /** Keeps [score] if it beats the best so far; true when it did (a new best). */
  suspend fun record(score: TournamentScore): Boolean

  /** What was last posted to the leaderboard for [game] ([io.github.earthkodyai.rinalarm.tournament.online.LeaderboardSync.signature]), or null. */
  suspend fun posted(game: TournamentGame): String?

  suspend fun markPosted(game: TournamentGame, signature: String)

  /** Posting switched off: [TournamentEntry.online] goes false, and the rows online are owed a delete until [withdrawn]. */
  suspend fun stopPosting()

  suspend fun owesWithdraw(): Boolean

  /** The rows online are gone. */
  suspend fun withdrawn()
}

@Singleton
class DataStoreTournamentStore @Inject constructor(private val store: DataStore<Preferences>) : TournamentStore {
  override val entry: Flow<TournamentEntry> =
    store.data.map { TournamentEntry(it[NAME] ?: "", it[UNIVERSITY], it[AGREED] ?: 0, it[ONLINE] ?: false) }

  override suspend fun setEntry(entry: TournamentEntry) {
    store.edit {
      it[NAME] = entry.name.trim().take(TournamentEntry.NAME_MAX)
      val university = entry.university
      if (university == null) it.remove(UNIVERSITY) else it[UNIVERSITY] = university
      it[AGREED] = entry.agreed
      it[ONLINE] = entry.online
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

  override suspend fun posted(game: TournamentGame): String? = store.data.first()[postedKey(game)]

  override suspend fun markPosted(game: TournamentGame, signature: String) {
    store.edit { it[postedKey(game)] = signature }
  }

  override suspend fun stopPosting() {
    store.edit {
      it[ONLINE] = false
      it[WITHDRAW] = true
      TournamentGame.entries.forEach { game -> it.remove(postedKey(game)) }
    }
  }

  override suspend fun owesWithdraw(): Boolean = store.data.first()[WITHDRAW] ?: false

  override suspend fun withdrawn() {
    store.edit { it.remove(WITHDRAW) }
  }

  private companion object {
    val NAME = stringPreferencesKey("tournament_name")
    val UNIVERSITY = stringPreferencesKey("tournament_university")
    val AGREED = intPreferencesKey("tournament_agreed")
    val ONLINE = booleanPreferencesKey("tournament_online")
    val WITHDRAW = booleanPreferencesKey("tournament_withdraw")

    fun postedKey(game: TournamentGame) = stringPreferencesKey("tournament_posted_${game.stored}")

    fun levelsKey(game: TournamentGame) = intPreferencesKey("tournament_best_${game.stored}_levels")

    fun timeKey(game: TournamentGame) = longPreferencesKey("tournament_best_${game.stored}_time_ms")
  }
}
