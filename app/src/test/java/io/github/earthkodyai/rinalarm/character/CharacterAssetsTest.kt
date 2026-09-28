package io.github.earthkodyai.rinalarm.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterAssetsTest {
  @Test
  fun rinsOwnModel_winsOverTheDevSample() {
    assertEquals("model/rin.vrm", CharacterAssets.pickModel(listOf("dev.vrm", "rin.vrm")))
  }

  @Test
  fun devSample_isUsedWhenItIsTheOnlyModel() {
    assertEquals("model/dev.vrm", CharacterAssets.pickModel(listOf("dev.vrm", "notes.txt")))
  }

  @Test
  fun stills_areFoundPerMood_andStrayFilesIgnored() {
    val stills = CharacterAssets.pickStills(listOf("cheerful.webp", "sleepy.webp", "cheerful.png", "angry.webp"))

    assertEquals(
      mapOf(Mood.CHEERFUL to "character/stills/cheerful.webp", Mood.SLEEPY to "character/stills/sleepy.webp"),
      stills,
    )
    assertEquals(emptyMap<Mood, String>(), CharacterAssets.pickStills(emptyList()))
  }

  @Test
  fun fullBodyStills_liveInTheirOwnFolder() {
    assertEquals(
      mapOf(Mood.PROUD to "character/stills/full/proud.webp"),
      CharacterAssets.pickStills(listOf("proud.webp"), Framing.FULL),
    )
  }

  @Test
  fun noModel_meansTheStillImage() {
    assertNull(CharacterAssets.pickModel(emptyList()))
    assertNull(CharacterAssets.pickModel(listOf("other.vrm")))
  }
}
