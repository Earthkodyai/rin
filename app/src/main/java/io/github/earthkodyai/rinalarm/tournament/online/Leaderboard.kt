package io.github.earthkodyai.rinalarm.tournament.online

import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore

/** One player's best in one game, as the board shows it. [id] is their anonymous Firebase id. */
data class BoardRow(val id: String, val name: String, val university: String?, val levels: Int, val timeMs: Long)

/**
 * The board for one game: the [top] rows, best first, and this player's own row and rank when they have one ([me] may
 * sit below the top rows; [myRank] counts from 1).
 */
data class BoardPage(val top: List<BoardRow>, val me: BoardRow?, val myRank: Int?)

/**
 * The online leaderboard (G.6, ADR 0008): players are ranked one by one in each game, with their university as a badge
 * (the user's pick, 2026-10-03: no university table). It is the only thing in the app that uses the network, and
 * nothing outside the tournament waits on it. Every call may throw when the board cannot be reached.
 */
interface Leaderboard {
  /** False on builds without a Firebase project (CI, clones): the tournament then plays offline only. */
  val configured: Boolean

  suspend fun page(game: TournamentGame): BoardPage

  /** Posts [score] as this player's row in its game; the rules keep the better of it and the row already there. */
  suspend fun post(score: TournamentScore, name: String, university: String?)

  /** Deletes this player's rows in every game (posting switched off). */
  suspend fun withdraw()

  /** Reports another player's row (a rude name); one report per row and reporter. */
  suspend fun report(game: TournamentGame, rowId: String)
}
