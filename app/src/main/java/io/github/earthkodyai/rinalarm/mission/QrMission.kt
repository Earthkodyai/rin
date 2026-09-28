package io.github.earthkodyai.rinalarm.mission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A mission the ring screen feeds camera frames to. */
interface ScanMission : Mission {
  /** Called from the camera's analysis thread with the codes in one frame. */
  fun onCodes(codes: List<SeenCode>): ScanVerdict

  fun cameraOpened()

  fun torchChanged(on: Boolean, auto: Boolean)
}

/**
 * Scan the registered [sticker] (task 3.2): one close-enough sighting passes ([QrScanPolicy.judge]). The camera lives
 * on the ring screen and opens on a tap, so [start] only counts steps: they are not required (user decision: steps
 * are logged, never gated, to protect the first-try bar), but each one, like each sighting from too far, is
 * [MissionProgress.activity] and keeps the tone quiet on the way to the sticker.
 */
class QrMission(private val sticker: QrSticker, private val steps: StepSource?) : ScanMission {
  override val type = MissionType.QR
  private val state = MutableStateFlow(MissionProgress(0, 1))
  override val progress: StateFlow<MissionProgress> = state.asStateFlow()

  private var counting = false
  @Volatile private var stepCount = 0
  @Volatile private var opens = 0
  @Volatile private var frames = 0
  @Volatile private var others = 0
  @Volatile private var farMax: Float? = null
  @Volatile private var passFraction: Float? = null
  @Volatile private var torch = "off"

  override fun start() {
    if (counting || state.value.state != MissionState.RUNNING) return
    counting = steps?.start(::onStep) == true
  }

  override fun stop() {
    if (!counting) return
    counting = false
    steps?.stop()
  }

  private fun onStep(total: Int) {
    stepCount = total
    state.update { if (it.state == MissionState.RUNNING) it.copy(activity = it.activity + 1) else it }
  }

  override fun onCodes(codes: List<SeenCode>): ScanVerdict {
    if (state.value.state != MissionState.RUNNING) return ScanVerdict.NONE
    frames++
    val verdict = QrScanPolicy.judge(codes) { sticker.matches(it.content) }
    val best = codes.filter { sticker.matches(it.content) }.maxOfOrNull { it.fraction }
    when (verdict) {
      ScanVerdict.MATCH -> {
        passFraction = best
        state.update { it.copy(done = 1, state = MissionState.PASSED) }
        stop()
      }
      ScanVerdict.TOO_FAR -> {
        farMax = maxOf(farMax ?: 0f, best ?: 0f)
        state.update { it.copy(activity = it.activity + 1) }
      }
      ScanVerdict.OTHER -> others++
      ScanVerdict.NONE -> Unit
    }
    return verdict
  }

  override fun cameraOpened() {
    opens++
  }

  override fun torchChanged(on: Boolean, auto: Boolean) {
    torch = if (!on) "off" else if (auto) "auto" else "manual"
  }

  /** opens=1 on a pass is the first-try pass the phase exit counts (plan phase-3). */
  override fun summary(): String =
    "opens=$opens steps=$stepCount frames=$frames others=$others torch=$torch" +
      " fraction=${passFraction.fmt()} farMax=${farMax.fmt()} bar=${QrScanPolicy.MIN_FRACTION.fmt()}"

  private fun Float?.fmt() = this?.let { "%.3f".format(java.util.Locale.ROOT, it) } ?: "-"
}
