package io.github.earthkodyai.rinalarm.ui.qrsetup

import io.github.earthkodyai.rinalarm.data.StickerStore
import io.github.earthkodyai.rinalarm.mission.CodeFormat
import io.github.earthkodyai.rinalarm.mission.QrScanPolicy
import io.github.earthkodyai.rinalarm.mission.QrSticker
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.mission.SeenCode
import io.github.earthkodyai.rinalarm.mission.StickerKey
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QrSetupViewModelTest {
  @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

  private val store = FakeStickerStore()
  private val now = Instant.parse("2026-09-28T12:00:00Z")

  private fun setup() = QrSetupViewModel(store, FixedTimeSource(now, ZoneOffset.UTC))

  private fun code(content: String, fraction: Float, format: CodeFormat = CodeFormat.QR) = SeenCode(content, format, fraction)

  private val close = QrScanPolicy.MIN_FRACTION + 0.1f
  private val far = QrScanPolicy.MIN_FRACTION - 0.1f

  /** One spot of the bed check, reading [frames] frames of [codes] over its whole length. */
  private fun kotlinx.coroutines.test.TestScope.runBedSpot(setup: QrSetupViewModel, codes: List<SeenCode>, frames: Int = 30) {
    setup.startBedCheck()
    runCurrent()
    repeat(frames) { setup.onBedCodes(codes) }
    advanceTimeBy(QrScanPolicy.BED_CHECK.toMillis() + 1)
  }

  /** All three spots, each seeing [codes]. */
  private fun kotlinx.coroutines.test.TestScope.runBedCheck(setup: QrSetupViewModel, codes: List<SeenCode>) {
    repeat(BedSpot.entries.size) { runBedSpot(setup, codes) }
  }

  @Test
  fun theBedCheck_walksThroughThreeSpots_andAPassAtTheEdgeFailsIt() =
    runTest(main.dispatcher) {
      val setup = setup()
      setup.useExisting()
      setup.onRegisterCodes(listOf(code("door", close)))
      setup.confirmCandidate()

      // The 3.2 dev data: fine lying down and sitting up, passes from the edge of the bed.
      runBedSpot(setup, listOf(code("door", 0.07f)))
      assertEquals(SetupStep.BED_INTRO to 1, setup.uiState.value.let { it.step to it.bedSpot })
      runBedSpot(setup, listOf(code("door", 0.13f)))
      assertEquals(SetupStep.BED_INTRO to 2, setup.uiState.value.let { it.step to it.bedSpot })
      setup.startBedCheck()
      runCurrent()
      setup.onBedCodes(listOf(code("door", 0.25f)))
      assertEquals(SetupStep.BED_FAILED, setup.uiState.value.step)
      assertNull(store.saved)

      // Moved out of the bedroom: every spot again, from the first.
      setup.retryBedCheck()
      assertEquals(SetupStep.BED_INTRO to 0, setup.uiState.value.let { it.step to it.bedSpot })
      runBedSpot(setup, listOf(code("door", 0.04f)))
      runBedSpot(setup, emptyList())
      runBedSpot(setup, listOf(code("door", 0.06f)))
      assertEquals(SetupStep.DONE, setup.uiState.value.step)
      // The largest size across the spots of the passing check, not the failed one.
      assertEquals(0.06f, store.saved!!.bedFraction)
    }

  @Test
  fun generatedSticker_registersOnlyItself_thenSavesOnceTheBedCheckPasses() =
    runTest(main.dispatcher) {
      val setup = setup()
      setup.makeSticker()
      runCurrent()
      val payload = setup.uiState.value.payload!!
      assertEquals(SetupStep.STICKER, setup.uiState.value.step)
      assertEquals(payload, store.pending)

      setup.stickerPlaced()
      setup.onRegisterCodes(listOf(code("cereal box", 0.9f, CodeFormat.BARCODE)))
      assertEquals(ScanVerdict.OTHER, setup.uiState.value.hint)
      setup.onRegisterCodes(listOf(code(payload, far)))
      assertEquals(ScanVerdict.TOO_FAR, setup.uiState.value.hint)
      setup.onRegisterCodes(listOf(code(payload, close)))
      assertEquals(SetupStep.BED_INTRO, setup.uiState.value.step)
      assertNull(store.saved)

      // Seen from bed, but too small to pass: that is the placement we want.
      runBedCheck(setup, listOf(code(payload, 0.07f)))
      val saved = store.saved!!
      assertEquals(SetupStep.DONE, setup.uiState.value.step)
      assertEquals(QrSticker.Source.GENERATED, saved.source)
      assertEquals(payload, saved.payload)
      assertTrue(saved.matches(payload))
      assertEquals(close, saved.closeFraction)
      assertEquals(0.07f, saved.bedFraction)
      assertEquals(now, saved.setAt)
    }

  @Test
  fun leavingHalfway_andComingBack_offersTheSameSticker() =
    runTest(main.dispatcher) {
      val first = setup()
      first.makeSticker()
      runCurrent()
      val second = setup()
      second.makeSticker()
      assertEquals(first.uiState.value.payload, second.uiState.value.payload)
    }

  @Test
  fun existingCode_needsAConfirm_andKeepsOnlyItsHash() =
    runTest(main.dispatcher) {
      val setup = setup()
      setup.useExisting()
      setup.onRegisterCodes(listOf(code("8850000000001", far, CodeFormat.BARCODE)))
      assertEquals(ScanVerdict.TOO_FAR, setup.uiState.value.hint)
      setup.onRegisterCodes(listOf(code("8850000000001", close, CodeFormat.BARCODE)))
      assertEquals(SetupStep.CONFIRM, setup.uiState.value.step)

      setup.rescan()
      assertEquals(SetupStep.REGISTER, setup.uiState.value.step)
      setup.onRegisterCodes(listOf(code("8850000000001", close, CodeFormat.BARCODE)))
      setup.confirmCandidate()
      runBedCheck(setup, emptyList())

      val saved = store.saved!!
      assertEquals(QrSticker.Source.EXISTING, saved.source)
      assertEquals(CodeFormat.BARCODE, saved.format)
      assertNull(saved.payload)
      assertNull(saved.bedFraction)
      assertFalse(saved.hash.contains("8850000000001"))
      assertTrue(saved.matches("8850000000001"))
    }

  @Test
  fun passingFromBed_failsTheCheck_andNothingIsSaved_untilItIsMoved() =
    runTest(main.dispatcher) {
      val setup = setup()
      setup.useExisting()
      setup.onRegisterCodes(listOf(code("door", close)))
      setup.confirmCandidate()

      setup.startBedCheck()
      runCurrent()
      setup.onBedCodes(listOf(code("door", QrScanPolicy.MIN_FRACTION)))
      assertEquals(SetupStep.BED_FAILED, setup.uiState.value.step)
      advanceTimeBy(QrScanPolicy.BED_CHECK.toMillis() + 1)
      assertEquals(SetupStep.BED_FAILED, setup.uiState.value.step)
      assertNull(store.saved)

      // Moved farther away: checked again from bed.
      setup.retryBedCheck()
      runBedCheck(setup, listOf(code("door", 0.05f)))
      assertEquals(SetupStep.DONE, setup.uiState.value.step)
    }

  @Test
  fun iMovedIt_setsUpTheSameCodeAgain_withNothingToReprint() =
    runTest(main.dispatcher) {
      val salt = "ab"
      val payload = "RINALARM:TQXPLAV2WE6JSN3F"
      val old =
        QrSticker(salt, StickerKey.hash(salt, payload), QrSticker.Source.GENERATED, CodeFormat.QR, payload, 0.2f, 0.06f, Instant.EPOCH)
      store.saved = old
      val setup = setup()

      setup.recheck()
      assertEquals(SetupStep.REGISTER, setup.uiState.value.step)
      setup.onRegisterCodes(listOf(code("cereal box", 0.9f)))
      assertEquals(ScanVerdict.OTHER, setup.uiState.value.hint)
      setup.onRegisterCodes(listOf(code(payload, close)))
      assertEquals(SetupStep.BED_INTRO, setup.uiState.value.step)
      runBedCheck(setup, emptyList())

      val saved = store.saved!!
      assertTrue(saved.matches(payload))
      assertEquals(payload, saved.payload)
      assertEquals(QrSticker.Source.GENERATED, saved.source)
      assertNull(saved.bedFraction)
      assertEquals(now, saved.setAt)
    }

  @Test
  fun aBedCheckWithoutCameraFrames_provesNothing() =
    runTest(main.dispatcher) {
      val setup = setup()
      setup.useExisting()
      setup.onRegisterCodes(listOf(code("door", close)))
      setup.confirmCandidate()

      runBedSpot(setup, emptyList(), frames = QrSetupViewModel.MIN_BED_FRAMES - 1)
      assertEquals(SetupStep.BED_INTRO to 0, setup.uiState.value.let { it.step to it.bedSpot })
      assertTrue(setup.uiState.value.bedProblem)
      assertNull(store.saved)
    }

  @Test
  fun replacing_keepsTheOldStickerUntilTheNewOnePasses() =
    runTest(main.dispatcher) {
      val old = QrSticker("aa", "hash", QrSticker.Source.EXISTING, CodeFormat.QR, null, 0.3f, null, Instant.EPOCH)
      store.saved = old
      val setup = setup()
      assertEquals(old, setup.uiState.value.existing)

      setup.useExisting()
      setup.onRegisterCodes(listOf(code("new", close)))
      assertEquals(old, store.saved)
      setup.confirmCandidate()
      runBedCheck(setup, emptyList())
      assertNotEquals(old, store.saved)
      assertTrue(store.saved!!.matches("new"))
    }

  private class FakeStickerStore : StickerStore {
    var saved: QrSticker? = null
    var pending: String? = null

    override fun current() = saved

    override suspend fun save(sticker: QrSticker) {
      saved = sticker
      pending = null
    }

    override fun pendingPayload() = pending

    override suspend fun setPendingPayload(payload: String) {
      pending = payload
    }
  }
}
