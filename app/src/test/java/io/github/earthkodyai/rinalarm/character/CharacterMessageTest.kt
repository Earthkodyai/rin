package io.github.earthkodyai.rinalarm.character

import io.github.earthkodyai.rinalarm.mission.CupsAct
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
  fun cups_bothWays() {
    assertEquals(
      CharacterMessage.CupsShown(true, listOf(0.3, 0.5, 0.7)),
      CharacterMessage.parse("""{"v":1,"type":"cups","shown":true,"x":[0.3,0.5,0.7]}"""),
    )
    // Acts go out on the page's clock (elapsed + offset), in web/character/src/cups.ts parseAct's shape.
    val offset = 1_700_000_000_000
    val shuffle = CupsAct.Shuffle(ball = 2, at = 5_000, swaps = listOf(0 to 1, 2 to 0), leadMs = 300, swapMs = 450, gapMs = 150, exitMs = 250)
    assertEquals(
      """{"type":"cups","act":{"ball":2,"at":1700000005000,"cups":3,"kind":"shuffle","swaps":[[0,1],[2,0]],"leadMs":300,"swapMs":450,"gapMs":150,"exitMs":250}}""",
      CharacterCommand.Cups(shuffle, offset).json,
    )
    val lift = CupsAct.Lift(1, 10, listOf(0, 1), listOf(1), leadMs = 300, upMs = 250, holdMs = null, downMs = 250, exitMs = 250)
    assertEquals(
      """{"type":"cups","act":{"ball":1,"at":1700000000010,"cups":3,"kind":"lift","lift":[0,1],"hands":[1],"leadMs":300,"upMs":250,"holdMs":null,"downMs":250,"exitMs":250}}""",
      CharacterCommand.Cups(lift, offset).json,
    )
    assertEquals("""{"type":"cups","act":null}""", CharacterCommand.Cups(null, offset).json)
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
  @Test
  fun gestureStarted_isParsed() {
    val started = CharacterMessage.parse("""{"v":1,"type":"gesture","name":"wave","ok":true}""")
    assertEquals(CharacterMessage.GestureStarted("wave", true), started)
  }

  @Test
  fun gestureSpeakAndHush_matchWhatThePageReads() {
    assertEquals("""{"type":"gesture","name":"yawn"}""", CharacterCommand.PlayGesture(Gesture.YAWN).json)
    assertEquals("""{"type":"hush"}""", CharacterCommand.Hush.json)
    val speak = Json.parseToJsonElement(CharacterCommand.Speak(MouthTrack(30, "a5-0"), at = 42L).json).jsonObject
    assertEquals("speak", speak["type"]!!.jsonPrimitive.content)
    assertEquals("42", speak["at"]!!.jsonPrimitive.content)
    assertEquals("""{"v":1,"fps":30,"f":"a5-0"}""", speak["mouth"].toString())
  }

  @Test
  fun stats_isParsed() {
    val json =
      """{"v":1,"type":"stats","frames":900,"seconds":30,"avgFps":30,"p1LowFps":29.4,"over50":0,"maxMs":34.1,
        |"fpsCap":30,"hitches":[{"at":1200,"ms":66.7}]}"""
        .trimMargin()

    assertEquals(
      CharacterMessage.Stats(900, 30.0, 30.0, 29.4, 0, 34.1, 30.0, listOf(CharacterMessage.Hitch(1200, 66.7))),
      CharacterMessage.parse(json),
    )
  }

  @Test
  fun measureAndFpsCap_matchWhatThePageReads() {
    assertEquals("""{"type":"stats","ms":30000}""", CharacterCommand.MeasureFrames(30_000).json)
    assertEquals("""{"type":"fps","cap":120}""", CharacterCommand.FpsCap(120).json)
  }
}
