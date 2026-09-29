package io.github.earthkodyai.rinalarm.mission

/**
 * What stops an alarm (D17, ADR 0001): games with Rin. Task 3.3 added colour pads, 3.4 the cup shuffle; repeat after
 * Rin (3.5) joins when it is built. The QR sticker (3.2) stays in the code behind [MissionFlags.QR_STICKER].
 * Walking (3.1) was removed in 3.3: a stored "walk" reads as Rin picks. Stored by [stored], so entries may be added but
 * never renamed. Rin picks rotates through them in this order.
 */
enum class MissionType(val stored: String) {
  PADS("pads"),
  QR("qr"),
  CUPS("cups");

  /** Shown in the editor and planned for rings; a hidden type reads as Rin picks. */
  val offered: Boolean
    get() = this != QR || MissionFlags.QR_STICKER

  companion object {
    val offeredEntries: List<MissionType>
      get() = entries.filter { it.offered }

    fun fromStored(value: String): MissionType? = entries.firstOrNull { it.stored == value && it.offered }
  }
}

object MissionFlags {
  /**
   * The QR sticker mission (task 3.2) found no valid size bar and the setup was too much work (ADR 0001). Off until a
   * placement passes its frozen rule; QrLabActivity (debug) still works.
   */
  const val QR_STICKER = false
}

/** What an alarm asks for (user decision D15: per alarm, "Rin picks" by default, None for naps and the test alarm). */
sealed interface MissionChoice {
  val stored: String

  /** Rotates daily among the missions that are ready on this phone. */
  data object RinPicks : MissionChoice {
    override val stored = "rin_picks"
  }

  /** A plain Dismiss button, as before Phase 3. */
  data object None : MissionChoice {
    override val stored = "none"
  }

  data class Only(val type: MissionType) : MissionChoice {
    override val stored = type.stored
  }

  companion object {
    const val DEFAULT_STORED = "rin_picks"

    /** Never fails: an unknown value (a newer version's mission, then a downgrade) reads as Rin picks. */
    fun fromStored(value: String): MissionChoice =
      when (value) {
        RinPicks.stored -> RinPicks
        None.stored -> None
        else -> MissionType.fromStored(value)?.let(::Only) ?: RinPicks
      }
  }
}

/** Whether a mission can run on this phone right now. */
enum class Readiness(val reason: String) {
  READY("ready"),
  /** The runtime permission it needs was never granted, or was taken back. */
  NO_PERMISSION("no_permission"),
  /** The phone lacks the sensor (e.g. no step counter). */
  NO_SENSOR("no_sensor"),
  /** It needs a one-time setup first (the QR sticker: registered and checked from bed). */
  NOT_SET_UP("not_set_up"),
  /** The phone has not been unlocked since it booted, and the mission needs credential-protected parts. */
  BEFORE_UNLOCK("before_unlock"),
}
