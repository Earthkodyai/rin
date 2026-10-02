package io.github.earthkodyai.rinalarm.mission

/**
 * How hard an alarm's game is (Phase G, D33). [EASY] is the game as it was frozen for the held-out rings (3.3/3.4), so
 * alarms set before Phase G keep it; a new install starts the editor at [NORMAL]. G.2–G.3 give each level its rules,
 * G.4 locks them. Stored by [stored], so entries may be added but never renamed.
 */
enum class Difficulty(val stored: String) {
  EASY("easy"),
  NORMAL("normal"),
  HARD("hard"),
  NIGHTMARE("nightmare");

  companion object {
    /** What schema 6 gave the alarms that already existed (AlarmEntityTest checks the column default). */
    const val DEFAULT_STORED = "easy"

    /** The editor's level for a first alarm, before the user has picked one. */
    val NEW_ALARM = NORMAL

    /** Never fails: an unknown value (a newer version's level, then a downgrade) reads as [EASY]. */
    fun fromStored(value: String?): Difficulty = entries.firstOrNull { it.stored == value } ?: EASY
  }
}

/** Whether the alarm's level changes this game: Repeat after Rin has one level, as the mic, not skill, makes it hard. */
val MissionType.hasLevels: Boolean
  get() = this != MissionType.SPEECH
