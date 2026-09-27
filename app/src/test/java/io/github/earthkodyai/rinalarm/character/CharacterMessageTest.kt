package io.github.earthkodyai.rinalarm.character

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
  fun emotionShown_andTap_areParsed() {
    assertEquals(
      CharacterMessage.EmotionShown("pouty", CharacterMessage.EmotionTimings(toPage = 4, total = 231)),
      CharacterMessage.parse("""{"v":1,"type":"emotion","mood":"pouty","ms":{"toPage":4,"total":231}}"""),
    )
    assertEquals(CharacterMessage.Tap("head"), CharacterMessage.parse("""{"v":1,"type":"tap","part":"head"}"""))
    assertNull(CharacterMessage.parse("""{"type":"emotion","mood":"pouty"}""")) // no timings
  }

  @Test
  fun commands_matchWhatThePageReads() {
    assertEquals("""{"type":"pause"}""", CharacterCommand.Pause.json)
    assertEquals("""{"type":"resume"}""", CharacterCommand.Resume.json)
    val emotion = Json.parseToJsonElement(CharacterCommand.Emotion(Mood.SULKY, 1.5f, 1_700_000_000_123).json).jsonObject

    assertEquals("emotion", emotion["type"]!!.jsonPrimitive.content)
    assertEquals("sulky", emotion["mood"]!!.jsonPrimitive.content)
    assertEquals("1.0", emotion["intensity"]!!.jsonPrimitive.content) // clamped
    assertEquals("1700000000123", emotion["at"]!!.jsonPrimitive.content)
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
