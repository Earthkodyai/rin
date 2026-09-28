package io.github.earthkodyai.rinalarm.mission

import io.github.earthkodyai.rinalarm.ui.qrsetup.StickerImage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrStickerTest {
  private fun sticker(content: String, salt: String = StickerKey.newSalt()) =
    QrSticker(salt, StickerKey.hash(salt, content), QrSticker.Source.EXISTING, CodeFormat.BARCODE, null, 0.3f, null, Instant.EPOCH)

  @Test
  fun theStoredHash_matchesOnlyItsOwnCode_andNeverHoldsTheContent() {
    val wifi = "WIFI:S:home;T:WPA;P:secret-password;;"
    val saved = sticker(wifi)
    assertTrue(saved.matches(wifi))
    assertFalse(saved.matches("WIFI:S:home;T:WPA;P:other;;"))
    assertFalse(saved.hash.contains("secret"))
    // Salted: the same code on two phones leaves two different hashes.
    assertNotEquals(sticker(wifi).hash, sticker(wifi).hash)
  }

  @Test
  fun newPayloads_areUnique_alphanumericOnly_andFitASmallQr() {
    val payloads = (1..200).map { StickerKey.newPayload() }
    assertEquals(200, payloads.toSet().size)
    for (payload in payloads) {
      assertTrue(payload, Regex("RINALARM:[A-Z2-7]{16}").matches(payload))
      // Version 2 (25 x 25 modules): large modules that read in a dim room.
      assertEquals(25, StickerImage.encode(payload).size)
    }
  }

  @Test
  fun judge_passesOnlyTheStickerAtTheSizeBar() {
    val mine = { code: SeenCode -> code.content == "mine" }
    fun seen(content: String, fraction: Float) = SeenCode(content, CodeFormat.QR, fraction)

    assertEquals(ScanVerdict.NONE, QrScanPolicy.judge(emptyList(), mine))
    assertEquals(ScanVerdict.OTHER, QrScanPolicy.judge(listOf(seen("cereal box", 0.9f)), mine))
    assertEquals(ScanVerdict.TOO_FAR, QrScanPolicy.judge(listOf(seen("mine", QrScanPolicy.MIN_FRACTION - 0.01f)), mine))
    assertEquals(ScanVerdict.MATCH, QrScanPolicy.judge(listOf(seen("mine", QrScanPolicy.MIN_FRACTION)), mine))
    // Another code next to it changes nothing.
    assertEquals(
      ScanVerdict.MATCH,
      QrScanPolicy.judge(listOf(seen("cereal box", 0.1f), seen("mine", 0.5f)), mine),
    )
  }

  @Test
  fun fraction_isTheLongestSide_overTheShortSideOfTheFrame_howeverItIsTurned() {
    // A 144 px square in a 1280 x 720 frame, upright and at 45 degrees.
    val upright = floatArrayOf(100f, 100f, 244f, 100f, 244f, 244f, 100f, 244f)
    val d = 144f / kotlin.math.sqrt(2f)
    val turned = floatArrayOf(300f, 100f, 300f + d, 100f + d, 300f, 100f + 2 * d, 300f - d, 100f + d)
    assertEquals(0.2f, QrScanPolicy.fraction(upright, 1280, 720), 1e-4f)
    assertEquals(0.2f, QrScanPolicy.fraction(turned, 1280, 720), 1e-4f)
    assertEquals(0.2f, QrScanPolicy.fraction(upright, 720, 1280), 1e-4f)
    assertEquals(0f, QrScanPolicy.fraction(floatArrayOf(), 1280, 720))
  }

  @Test
  fun torch_comesOnAfterFiveDarkFrames_andStaysOn() {
    val torch = TorchControl()
    repeat(TorchControl.DARK_FRAMES - 1) { assertFalse(torch.onFrame(20)) }
    assertFalse(torch.onFrame(200)) // one bright frame starts the count again
    repeat(TorchControl.DARK_FRAMES - 1) { assertFalse(torch.onFrame(20)) }
    assertTrue(torch.onFrame(20))
    assertTrue(torch.on && torch.auto)
    // Lit, the frame looks bright: that must not switch it off.
    assertFalse(torch.onFrame(220))
    assertTrue(torch.on)
  }

  @Test
  fun torch_afterTheSwitch_onlyTheSwitchDecides() {
    val torch = TorchControl()
    assertTrue(torch.toggle())
    assertFalse(torch.toggle())
    repeat(20) { assertFalse(torch.onFrame(0)) }
    assertFalse(torch.on)
  }
}
