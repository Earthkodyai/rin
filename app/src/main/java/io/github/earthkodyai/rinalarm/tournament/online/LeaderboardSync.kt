package io.github.earthkodyai.rinalarm.tournament.online

import io.github.earthkodyai.rinalarm.data.TournamentEntry
import io.github.earthkodyai.rinalarm.data.TournamentStore
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the phone's bests stand with the board, for the start page's line under the switch. */
enum class SyncStatus {
  /** Posting is off (or there is no board in this build). */
  OFF,
  /** Everything worth posting is on the board. */
  POSTED,
  /** Something is still to post or delete: no connection, or the board refused for now. */
  WAITING,
}

/**
 * Keeps the board in step with the phone (G.6): with posting on ([TournamentEntry.online], the user's consent), each
 * game's best goes up once, and again whenever it, the name or the university changes; with posting switched off, the
 * rows come down. What was sent is remembered ([signature]), so a sync with nothing new sends nothing, and one that
 * fails (offline) is simply tried again at the next [sync]: when the start page shows, and after switching.
 *
 * It runs in the app's scope, so leaving the page does not cut a delete short.
 */
@Singleton
class LeaderboardSync
@Inject
constructor(private val store: TournamentStore, private val board: Leaderboard, @AppScope private val scope: CoroutineScope) {
  private val mutex = Mutex()
  private val _status = MutableStateFlow(SyncStatus.OFF)
  val status: StateFlow<SyncStatus> = _status.asStateFlow()

  /**
   * One pass now, and up to [RETRIES] more [RETRY_MS] apart while something is still waiting: a second change within
   * the rules' 5 s (a name typed right after a post) goes up on the retry instead of at the next visit.
   */
  fun sync() {
    scope.launch {
      syncNow()
      repeat(RETRIES) {
        if (_status.value != SyncStatus.WAITING) return@launch
        delay(RETRY_MS)
        syncNow()
      }
    }
  }

  /** One pass; public for tests. */
  suspend fun syncNow() {
    mutex.withLock { _status.value = pass() }
  }

  private suspend fun pass(): SyncStatus {
    if (!board.configured) return SyncStatus.OFF
    val entry = store.entry.first()
    if (!entry.online) {
      if (store.owesWithdraw()) {
        if (!attempt { board.withdraw() }) return SyncStatus.WAITING
        store.withdrawn()
      }
      return SyncStatus.OFF
    }
    var waiting = false
    for (game in TournamentGame.entries) {
      val best = store.best(game).first() ?: continue
      if (!OnlineLimits.postable(best)) continue
      val name = entry.name.takeIf(NameFilter::allowed).orEmpty()
      val university = entry.university?.takeIf(OnlineLimits.UNIVERSITY_ID::matches)
      val signature = signature(best, name, university)
      if (store.posted(game) == signature) continue
      if (attempt { board.post(best, name, university) }) store.markPosted(game, signature) else waiting = true
    }
    return if (waiting) SyncStatus.WAITING else SyncStatus.POSTED
  }

  private suspend fun attempt(call: suspend () -> Unit): Boolean =
    try {
      call()
      true
    } catch (_: TimeoutCancellationException) {
      false
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // Offline, timed out, or refused (posted again within 5 s): kept for the next sync.
      false
    }

  companion object {
    const val RETRIES = 2
    /** Longer than the rules' 5 s between two changes of one row. */
    const val RETRY_MS = 6_000L

    /** What a posted row holds, as one string: a change to any part of it posts again. */
    fun signature(score: TournamentScore, name: String, university: String?): String =
      "${score.levels}/${score.timeMs}/${university.orEmpty()}/$name"
  }
}
