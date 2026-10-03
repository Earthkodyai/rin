package io.github.earthkodyai.rinalarm.tournament.online

import io.github.earthkodyai.rinalarm.data.TournamentEntry
import io.github.earthkodyai.rinalarm.data.TournamentStore
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardSyncTest {
  private class MemoryStore : TournamentStore {
    val entryFlow = MutableStateFlow(TournamentEntry())
    val bests = MutableStateFlow<Map<TournamentGame, TournamentScore>>(emptyMap())
    val posted = mutableMapOf<TournamentGame, String>()
    var owes = false

    override val entry: Flow<TournamentEntry> = entryFlow

    override suspend fun setEntry(entry: TournamentEntry) {
      entryFlow.value = entry
    }

    override fun best(game: TournamentGame): Flow<TournamentScore?> = bests.map { it[game] }

    override suspend fun record(score: TournamentScore): Boolean {
      if (!score.beats(bests.value[score.game])) return false
      bests.value = bests.value + (score.game to score)
      return true
    }

    override suspend fun posted(game: TournamentGame): String? = posted[game]

    override suspend fun markPosted(game: TournamentGame, signature: String) {
      posted[game] = signature
    }

    override suspend fun stopPosting() {
      entryFlow.value = entryFlow.value.copy(online = false)
      owes = true
      posted.clear()
    }

    override suspend fun owesWithdraw(): Boolean = owes

    override suspend fun withdrawn() {
      owes = false
    }
  }

  private class FakeBoard(override val configured: Boolean = true) : Leaderboard {
    val posts = mutableListOf<Triple<TournamentScore, String, String?>>()
    var withdrawals = 0
    var offline = false

    override suspend fun page(game: TournamentGame): BoardPage = BoardPage(emptyList(), null, null)

    override suspend fun post(score: TournamentScore, name: String, university: String?) {
      if (offline) throw IOException("offline")
      posts += Triple(score, name, university)
    }

    override suspend fun withdraw() {
      if (offline) throw IOException("offline")
      withdrawals++
    }

    override suspend fun report(game: TournamentGame, rowId: String) = Unit
  }

  private val store = MemoryStore()
  private val board = FakeBoard()
  private val pads = TournamentScore(TournamentGame.PADS, 6, 70_000)
  private val cups = TournamentScore(TournamentGame.CUPS, 4, 40_000)

  private fun TestScope.sync(on: Leaderboard = board) = LeaderboardSync(store, on, backgroundScope)

  @Test
  fun postingOff_sendsNothing() = runTest {
    store.bests.value = mapOf(TournamentGame.PADS to pads)
    val sync = sync()
    sync.syncNow()
    assertTrue(board.posts.isEmpty())
    assertEquals(SyncStatus.OFF, sync.status.value)
  }

  @Test
  fun postingOn_sendsEachBestOnce() = runTest {
    store.entryFlow.value = TournamentEntry("Earth", "ku", online = true)
    store.bests.value = mapOf(TournamentGame.PADS to pads, TournamentGame.CUPS to cups)
    val sync = sync()
    sync.syncNow()
    sync.syncNow()
    assertEquals(listOf(Triple(pads, "Earth", "ku"), Triple(cups, "Earth", "ku")), board.posts)
    assertEquals(SyncStatus.POSTED, sync.status.value)
  }

  @Test
  fun aNewBestOrANewName_postsAgain() = runTest {
    store.entryFlow.value = TournamentEntry("Earth", null, online = true)
    store.bests.value = mapOf(TournamentGame.PADS to pads)
    val sync = sync()
    sync.syncNow()
    val better = TournamentScore(TournamentGame.PADS, 7, 80_000)
    store.record(better)
    sync.syncNow()
    store.entryFlow.value = store.entryFlow.value.copy(name = "Mint")
    sync.syncNow()
    assertEquals(listOf(Triple(pads, "Earth", null), Triple(better, "Earth", null), Triple(better, "Mint", null)), board.posts)
  }

  @Test
  fun offline_waitsAndPostsOnTheNextSync() = runTest {
    store.entryFlow.value = TournamentEntry(online = true)
    store.bests.value = mapOf(TournamentGame.PADS to pads)
    board.offline = true
    val sync = sync()
    sync.syncNow()
    assertEquals(SyncStatus.WAITING, sync.status.value)
    board.offline = false
    sync.syncNow()
    assertEquals(1, board.posts.size)
    assertEquals(SyncStatus.POSTED, sync.status.value)
  }

  @Test
  fun aRefusedPost_isRetriedAfterTheRulesGap_withoutAnotherVisit() = runTest {
    store.entryFlow.value = TournamentEntry("Earth", online = true)
    store.bests.value = mapOf(TournamentGame.PADS to pads)
    board.offline = true
    val sync = sync()
    sync.sync()
    runCurrent()
    assertEquals(SyncStatus.WAITING, sync.status.value)
    board.offline = false
    advanceTimeBy(LeaderboardSync.RETRY_MS + 1)
    assertEquals(SyncStatus.POSTED, sync.status.value)
    assertEquals(1, board.posts.size)
  }

  @Test
  fun switchingOff_deletesTheRows_evenAfterBeingOffline() = runTest {
    store.entryFlow.value = TournamentEntry(online = true)
    store.bests.value = mapOf(TournamentGame.PADS to pads)
    val sync = sync()
    sync.syncNow()
    store.stopPosting()
    board.offline = true
    sync.syncNow()
    assertEquals(SyncStatus.WAITING, sync.status.value)
    assertTrue(store.owes)
    board.offline = false
    sync.syncNow()
    assertEquals(1, board.withdrawals)
    assertFalse(store.owes)
    assertEquals(SyncStatus.OFF, sync.status.value)
  }

  @Test
  fun aNameTheFilterRefuses_goesUpBlank_andARunTheRulesWouldRefuse_staysHome() = runTest {
    store.entryFlow.value = TournamentEntry("fuck", "NOT AN ID", online = true)
    store.bests.value = mapOf(TournamentGame.PADS to pads, TournamentGame.CUPS to TournamentScore(TournamentGame.CUPS, 5, 10))
    sync().syncNow()
    assertEquals(listOf(Triple(pads, "", null)), board.posts)
  }

  @Test
  fun noBoardInThisBuild_doesNothing() = runTest {
    val none = FakeBoard(configured = false)
    store.entryFlow.value = TournamentEntry(online = true)
    store.bests.value = mapOf(TournamentGame.PADS to pads)
    sync(none).syncNow()
    assertTrue(none.posts.isEmpty())
  }
}
