package dev.rinalarm.spike.s3

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentMatcherTest {
    private fun check(expected: Intent, vararg texts: String) =
        texts.forEach { assertEquals("\"$it\"", expected, IntentMatcher.classify(it)) }

    @Test fun affirm() = check(Intent.AFFIRM, "Yeah", "okay okay", "I'm up", "I'm awake now", "Sure, let's go", "I slept well")
    @Test fun deny() = check(Intent.DENY, "No", "nope", "Not really", "No thanks", "I'm not ready", "I don't want to")
    @Test fun snooze() = check(Intent.SNOOZE, "five more minutes", "Let me sleep", "later please", "not yet", "okay just one more minute")
    @Test fun tired() = check(Intent.TIRED, "I'm so tired", "sleepy", "I didn't sleep well", "I stayed up late", "Yeah I'm exhausted")
    @Test fun sick() = check(Intent.SICK, "I feel sick", "I have a headache", "I'm not feeling well", "my stomach hurts")
    @Test fun greet() = check(Intent.GREET, "Good morning", "Morning Rin", "hi", "Hey there")
    @Test fun thanks() = check(Intent.THANKS, "Thank you", "thanks Rin", "Aw, I appreciate it")
    @Test fun unknown() = check(Intent.UNKNOWN, "", "What time is it", "where is my phone", "I don't know", "I'm not tired")
    @Test fun fuzzy() = check(Intent.TIRED, "I'm so tyred")
    @Test fun emptyAndNull() { assertEquals(Intent.UNKNOWN, IntentMatcher.classify(null)) }
}

class PhoneticTest {
    private val on = IntentMatcher.Options(phonetic = true)
    @Test fun thaiVowelLength() {
        assertEquals(Intent.SICK, IntentMatcher.classify("I am seek now", on))
        assertEquals(Intent.UNKNOWN, IntentMatcher.classify("I am seek now"))
    }
    @Test fun stopWordsDontCollide() {
        assertEquals(Intent.UNKNOWN, IntentMatcher.classify("when is it", on))
        assertEquals(Intent.UNKNOWN, IntentMatcher.classify("what time", on))
    }
    @Test fun coverage() {
        assertEquals(Intent.SNOOZE, IntentMatcher.classify("I want to sleep", IntentMatcher.Options(coverage = true)))
    }
}
