package io.github.earthkodyai.rinalarm.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterMessageTest {
  @Test
  fun ready_isParsed_andUnknownFieldsAreIgnored() {
    val json =
      """{"v":1,"type":"ready","ms":{"pageToFirstFrame":820,"fetch":90,"parse":310,"compile":380,
        |"nativeToFirstFrame":1150,"later":1},"model":{"bytes":18400000,"meshes":18,"triangles":37000,"textures":37,
        |"expressions":["happy","blink"]},"pixelRatio":2,"fpsCap":30,"added":"in a later page"}"""
        .trimMargin()

    val ready = CharacterMessage.parse(json) as CharacterMessage.Ready

    assertEquals(1150L, ready.ms.nativeToFirstFrame)
    assertEquals(listOf("happy", "blink"), ready.model.expressions)
    assertEquals(2.0, ready.pixelRatio, 0.0)
    assertEquals(30, ready.fpsCap)
  }

  @Test
  fun ready_withoutANativeTimestamp_isParsed() {
    val json =
      """{"type":"ready","ms":{"pageToFirstFrame":1,"fetch":1,"parse":1,"compile":1,"nativeToFirstFrame":null},
        |"model":{"bytes":1,"meshes":1,"triangles":1,"textures":1,"expressions":[]},"pixelRatio":1.5,"fpsCap":60}"""
        .trimMargin()

    assertNull((CharacterMessage.parse(json) as CharacterMessage.Ready).ms.nativeToFirstFrame)
  }

  @Test
  fun error_isParsed() {
    assertEquals(CharacterMessage.Error("fetch model/dev.vrm: 404"), CharacterMessage.parse("""{"type":"error","message":"fetch model/dev.vrm: 404"}"""))
  }

  @Test
  fun unknownTypes_andBrokenMessages_areIgnored() {
    assertNull(CharacterMessage.parse("""{"type":"stats","fps":30}"""))
    assertNull(CharacterMessage.parse("""{"message":"no type"}"""))
    assertNull(CharacterMessage.parse("not json"))
    assertNull(CharacterMessage.parse("[1,2]"))
    assertNull(CharacterMessage.parse("""{"type":"ready","ms":"wrong shape"}"""))
    assertNull(CharacterMessage.parse("""{"type":{"nested":true}}"""))
  }
}
