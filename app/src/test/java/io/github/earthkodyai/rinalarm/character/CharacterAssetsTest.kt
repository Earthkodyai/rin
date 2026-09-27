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
  fun noModel_meansTheStillImage() {
    assertNull(CharacterAssets.pickModel(emptyList()))
    assertNull(CharacterAssets.pickModel(listOf("other.vrm")))
  }
}
