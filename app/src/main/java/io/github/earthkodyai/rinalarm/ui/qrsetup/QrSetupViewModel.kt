package io.github.earthkodyai.rinalarm.ui.qrsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.data.StickerStore
import io.github.earthkodyai.rinalarm.mission.QrScanPolicy
import io.github.earthkodyai.rinalarm.mission.QrSticker
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.mission.SeenCode
import io.github.earthkodyai.rinalarm.mission.StickerKey
import io.github.earthkodyai.rinalarm.time.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * QR sticker setup (task 3.2; 05c: "the scan from bed must fail at setup"). Choose the app's sticker or a code already
 * in the home, scan it up close, then aim at it from each [BedSpot] for [QrScanPolicy.BED_CHECK]: it is saved only if
 * it never passed from any of them. Nothing is replaced until then, so leaving halfway keeps the old sticker working.
 *
 * The scan callbacks come from the camera's analysis thread; every step change is an atomic [update] on [state].
 */
@HiltViewModel
class QrSetupViewModel @Inject constructor(private val store: StickerStore, private val time: TimeSource) : ViewModel() {
  private val state = MutableStateFlow(QrSetupUiState(existing = store.current()))
  val uiState: StateFlow<QrSetupUiState> = state.asStateFlow()

  private var bedJob: Job? = null

  /** The app's own sticker: the one setup already showed if it was left halfway, or a new one. */
  fun makeSticker() {
    val payload = store.pendingPayload() ?: StickerKey.newPayload().also { viewModelScope.launch { store.setPendingPayload(it) } }
    state.update {
      it.copy(step = SetupStep.STICKER, source = QrSticker.Source.GENERATED, payload = payload, hint = null, recheck = false)
    }
  }

  fun useExisting() {
    state.update {
      it.copy(step = SetupStep.REGISTER, source = QrSticker.Source.EXISTING, payload = null, hint = null, recheck = false)
    }
  }

  /**
   * "I moved it": the saved code, scanned at its new place and checked from bed again, with no new sticker to print.
   * Matched by its hash, so this works for a code already in the home too.
   */
  fun recheck() {
    state.update { s ->
      val existing = s.existing ?: return@update s
      s.copy(step = SetupStep.REGISTER, source = existing.source, payload = existing.payload, hint = null, recheck = true)
    }
  }

  /** "It's up": scan the printed sticker where it now hangs. */
  fun stickerPlaced() {
    state.update { if (it.step == SetupStep.STICKER) it.copy(step = SetupStep.REGISTER, hint = null) else it }
  }

  fun onRegisterCodes(codes: List<SeenCode>) {
    if (codes.isEmpty()) return
    state.update { s ->
      if (s.step != SetupStep.REGISTER) return@update s
      // Only a known code (the sticker just made, or the saved one being rechecked): nothing else in view slips in.
      val known: ((SeenCode) -> Boolean)? =
        when {
          s.recheck -> s.existing?.let { existing -> { code: SeenCode -> existing.matches(code.content) } }
          s.source == QrSticker.Source.GENERATED -> { code: SeenCode -> code.content == s.payload }
          else -> null
        }
      when {
        known != null -> {
          when (QrScanPolicy.judge(codes, known)) {
            ScanVerdict.MATCH -> s.copy(step = SetupStep.BED_INTRO, candidate = codes.filter(known).maxBy { it.fraction }, hint = null)
            ScanVerdict.TOO_FAR -> s.copy(hint = ScanVerdict.TOO_FAR)
            else -> s.copy(hint = ScanVerdict.OTHER)
          }
        }
        // Any code, close enough; the user confirms it, since several codes can be in view.
        s.source == QrSticker.Source.EXISTING -> {
          val best = codes.maxBy { it.fraction }
          if (best.fraction >= QrScanPolicy.MIN_FRACTION) s.copy(step = SetupStep.CONFIRM, candidate = best, hint = null)
          else s.copy(hint = ScanVerdict.TOO_FAR)
        }
        else -> s
      }
    }
  }

  fun confirmCandidate() {
    state.update { if (it.step == SetupStep.CONFIRM) it.copy(step = SetupStep.BED_INTRO) else it }
  }

  fun rescan() {
    bedJob?.cancel()
    state.update { it.copy(step = SetupStep.REGISTER, candidate = null, hint = null) }
  }

  /**
   * From the current [BedSpot]: aim at the sticker; it must not pass once in [QrScanPolicy.BED_CHECK]. The first spot
   * starts a fresh check; later spots keep the largest size seen so far.
   */
  fun startBedCheck() {
    val before = state.getAndUpdate {
      if (it.step == SetupStep.BED_INTRO) {
        it.copy(
          step = SetupStep.BED_RUNNING,
          bedFraction = if (it.bedSpot == 0) null else it.bedFraction,
          bedFrames = 0,
          bedSecondsLeft = BED_SECONDS,
          bedProblem = false,
        )
      } else it
    }
    if (before.step != SetupStep.BED_INTRO) return
    bedJob?.cancel()
    bedJob =
      viewModelScope.launch {
        for (left in BED_SECONDS downTo 1) {
          state.update { if (it.step == SetupStep.BED_RUNNING) it.copy(bedSecondsLeft = left) else it }
          delay(1_000)
        }
        finishBedCheck()
      }
  }

  fun onBedCodes(codes: List<SeenCode>) {
    state.update { s ->
      if (s.step != SetupStep.BED_RUNNING) return@update s
      val content = s.candidate?.content
      val seen = codes.filter { it.content == content }.maxOfOrNull { it.fraction }
      val farthest = listOfNotNull(s.bedFraction, seen).maxOrNull()
      when {
        seen != null && seen >= QrScanPolicy.MIN_FRACTION -> s.copy(step = SetupStep.BED_FAILED, bedFraction = farthest)
        else -> s.copy(bedFraction = farthest, bedFrames = s.bedFrames + 1)
      }
    }
    if (state.value.step == SetupStep.BED_FAILED) bedJob?.cancel()
  }

  /** After a pass from bed and a move: the whole check again, from the first spot. */
  fun retryBedCheck() {
    state.update { if (it.step == SetupStep.BED_FAILED) it.copy(step = SetupStep.BED_INTRO, bedSpot = 0) else it }
  }

  private suspend fun finishBedCheck() {
    val last = BedSpot.entries.lastIndex
    val before = state.getAndUpdate {
      when {
        it.step != SetupStep.BED_RUNNING -> it
        // A camera that never ran proves nothing: ask again rather than pass.
        it.bedFrames < MIN_BED_FRAMES -> it.copy(step = SetupStep.BED_INTRO, bedProblem = true)
        it.bedSpot < last -> it.copy(step = SetupStep.BED_INTRO, bedSpot = it.bedSpot + 1)
        else -> it.copy(step = SetupStep.SAVING)
      }
    }
    if (before.step != SetupStep.BED_RUNNING || before.bedFrames < MIN_BED_FRAMES || before.bedSpot < last) return
    val candidate = checkNotNull(before.candidate)
    val salt = StickerKey.newSalt()
    val sticker =
      QrSticker(
        salt = salt,
        hash = StickerKey.hash(salt, candidate.content),
        source = checkNotNull(before.source),
        format = candidate.format,
        payload = before.payload.takeIf { before.source == QrSticker.Source.GENERATED },
        closeFraction = candidate.fraction,
        bedFraction = before.bedFraction,
        setAt = time.now(),
      )
    store.save(sticker)
    state.update { it.copy(step = SetupStep.DONE, saved = sticker, existing = sticker, candidate = null) }
  }

  /** The camera would not start (or permission was taken back mid-way): back to the step before it. */
  fun onCameraError() {
    bedJob?.cancel()
    state.update {
      when (it.step) {
        SetupStep.BED_RUNNING -> it.copy(step = SetupStep.BED_INTRO, bedProblem = true)
        else -> it
      }
    }
  }

  companion object {
    val BED_SECONDS = QrScanPolicy.BED_CHECK.seconds.toInt()

    /** Frames the bed check must read (about 2 s of camera) before its silence means anything. */
    const val MIN_BED_FRAMES = 20
  }
}

/**
 * Where the bed check aims from, in order (3.2 dev data: lying down read the sticker at 0.07, the edge of the bed at
 * 0.25, above the 0.20 bar, so a check from one spot proved nothing about the others).
 */
enum class BedSpot {
  LYING,
  SITTING_UP,
  EDGE,
}

enum class SetupStep {
  START,
  /** The generated sticker, to share and print. */
  STICKER,
  /** Scan it up close where it hangs. */
  REGISTER,
  /** An existing code was found: use it? */
  CONFIRM,
  BED_INTRO,
  BED_RUNNING,
  /** It passed from bed: move it. */
  BED_FAILED,
  SAVING,
  DONE,
}

/**
 * @property existing the sticker saved before (or just now), shown at [SetupStep.START].
 * @property recheck the saved code is being set up again at a new place ("I moved it").
 * @property candidate the code being set up; its content stays in memory and only its hash is saved.
 * @property bedSpot the [BedSpot] (ordinal) being checked; each must pass before the next.
 * @property bedFraction the largest size the sticker was seen at from bed (it may be seen, only never pass).
 * @property bedProblem the last bed check read too few frames to count.
 */
data class QrSetupUiState(
  val step: SetupStep = SetupStep.START,
  val existing: QrSticker? = null,
  val source: QrSticker.Source? = null,
  val payload: String? = null,
  val recheck: Boolean = false,
  val candidate: SeenCode? = null,
  val hint: ScanVerdict? = null,
  val bedSpot: Int = 0,
  val bedSecondsLeft: Int = 0,
  val bedFraction: Float? = null,
  val bedFrames: Int = 0,
  val bedProblem: Boolean = false,
  val saved: QrSticker? = null,
) {
  val cameraOn: Boolean
    get() = step == SetupStep.REGISTER || step == SetupStep.BED_RUNNING
}
