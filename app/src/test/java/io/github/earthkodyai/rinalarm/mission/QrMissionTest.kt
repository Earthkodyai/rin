package io.github.earthkodyai.rinalarm.mission

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMissionTest {
  private val salt = "00ff"
  private val sticker =
    QrSticker(salt, StickerKey.hash(salt, "RINALARM:ABC"), QrSticker.Source.GENERATED, CodeFormat.QR, "RINALARM:ABC", 0.3f, null, Instant.EPOCH)
  private val steps = FakeSteps()
  private val mission = QrMission(sticker, steps)

  private fun code(content: String, fraction: Float) = SeenCode(content, CodeFormat.QR, fraction)

  @Test
  fun stepsAndSightingsFromTooFar_areActivity_andACloseScanPasses() {
    mission.start()
    assertTrue(steps.running)

    steps.step(1)
    steps.step(2)
    assertEquals(2, mission.progress.value.activity)
    assertEquals(ScanVerdict.OTHER, mission.onCodes(listOf(code("cereal box", 0.5f))))
    assertEquals(2, mission.progress.value.activity)
    assertEquals(ScanVerdict.TOO_FAR, mission.onCodes(listOf(code("RINALARM:ABC", 0.08f))))
    assertEquals(3, mission.progress.value.activity)
    assertEquals(MissionState.RUNNING, mission.progress.value.state)

    assertEquals(ScanVerdict.MATCH, mission.onCodes(listOf(code("RINALARM:ABC", 0.31f))))
    assertEquals(MissionProgress(1, 1, MissionState.PASSED, 3), mission.progress.value)
    assertFalse(steps.running)
    // After the pass, frames still in flight change nothing.
    assertEquals(ScanVerdict.NONE, mission.onCodes(listOf(code("RINALARM:ABC", 0.9f))))
  }

  @Test
  fun summary_holdsWhatTheSizeBarIsTunedOn() {
    mission.start()
    mission.cameraOpened()
    mission.torchChanged(on = true, auto = true)
    steps.step(14)
    mission.onCodes(emptyList())
    mission.onCodes(listOf(code("RINALARM:ABC", 0.12f)))
    mission.onCodes(listOf(code("RINALARM:ABC", 0.345f)))

    assertEquals(
      "opens=1 steps=14 frames=3 others=0 torch=auto fraction=0.345 farMax=0.120 bar=0.200",
      mission.summary(),
    )
  }

  @Test
  fun withoutMotionSensors_itStillRuns_onTheCameraAlone() {
    val noSensors = QrMission(sticker, steps = null)
    noSensors.start()
    assertEquals(MissionState.RUNNING, noSensors.progress.value.state)
    noSensors.onCodes(listOf(code("RINALARM:ABC", 0.4f)))
    assertEquals(MissionState.PASSED, noSensors.progress.value.state)
  }

  private class FakeSteps : StepSource {
    private var onStep: ((Int) -> Unit)? = null
    val running
      get() = onStep != null

    override fun start(onStep: (Int) -> Unit): Boolean {
      this.onStep = onStep
      return true
    }

    override fun stop() {
      onStep = null
    }

    fun step(total: Int) = onStep?.invoke(total)
  }
}
