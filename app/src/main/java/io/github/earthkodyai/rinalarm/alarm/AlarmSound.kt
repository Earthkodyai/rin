package io.github.earthkodyai.rinalarm.alarm

import java.time.LocalDate

/**
 * What an alarm plays (UX.7, D30): one of the alarm themes, or the generated beep. "Rin picks" rotates through the
 * themes by date, like her game pick (the user's default, 2026-10-02). Stored as [stored]: a theme by its id, so a
 * theme added later needs no schema change.
 */
sealed interface AlarmSound {
  val stored: String

  data object RinPicks : AlarmSound {
    override val stored = "rin_picks"
  }

  /** The generated beep: no file, so it is also what plays when a theme cannot. */
  data object Beep : AlarmSound {
    override val stored = "beep"
  }

  data class Theme(val id: String) : AlarmSound {
    override val stored: String
      get() = id
  }

  companion object {
    const val DEFAULT_STORED = "rin_picks"

    /** Never fails: anything that is not a theme id reads as Rin picks. */
    fun fromStored(value: String): AlarmSound =
      when (value) {
        RinPicks.stored -> RinPicks
        Beep.stored -> Beep
        else -> if (THEME_ID.matches(value)) Theme(value) else RinPicks
      }

    /**
     * The theme that rings on [day], or null for the beep. A pinned theme this build does not carry (a build without
     * the music, a theme dropped later) rings Rin's pick for the day rather than the beep.
     */
    fun resolve(sound: AlarmSound, themes: List<String>, day: LocalDate): String? =
      when (sound) {
        Beep -> null
        is Theme -> sound.id.takeIf { it in themes } ?: pick(themes, day)
        RinPicks -> pick(themes, day)
      }

    private fun pick(themes: List<String>, day: LocalDate): String? =
      if (themes.isEmpty()) null else themes[Math.floorMod(day.toEpochDay(), themes.size.toLong()).toInt()]

    /** Theme ids are file names: lower-case letters, digits and underscores. */
    val THEME_ID = Regex("[a-z0-9_]{1,32}")
  }
}
