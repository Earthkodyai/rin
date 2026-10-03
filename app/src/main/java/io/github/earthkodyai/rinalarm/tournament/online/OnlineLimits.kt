package io.github.earthkodyai.rinalarm.tournament.online

import io.github.earthkodyai.rinalarm.mission.TournamentScore
import io.github.earthkodyai.rinalarm.tournament.Universities

/**
 * What the leaderboard's security rules accept (firebase/firestore.rules, G.6). The app checks the same limits before
 * it posts, so a row the rules would refuse is never sent; OnlineLimitsTest keeps the two files in step.
 */
object OnlineLimits {
  const val MAX_LEVELS = 500
  /** Rin's demo alone takes longer than this at every level (OnlineLimitsTest), so no real run is refused. */
  const val MIN_MS_PER_LEVEL = 1_000L
  const val MAX_TIME_MS = 86_400_000L
  /** Seconds between two changes of one row. */
  const val UPDATE_GAP_S = 5
  /** Rows on the board (each is a read, out of Spark's 50,000 a day). */
  const val TOP = 50

  /** A university the rules accept: one of [Universities.all], or none ("Not listed"). */
  fun university(id: String?): String? = Universities[id]?.id

  fun postable(score: TournamentScore): Boolean =
    score.levels in 1..MAX_LEVELS && score.timeMs >= score.levels * MIN_MS_PER_LEVEL && score.timeMs <= MAX_TIME_MS
}
