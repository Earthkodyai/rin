package dev.rinalarm.spike.s3

/**
 * Suggested-reply mode (the Phase 4 design after the S3 dev-set NO-GO): Rin's line shows three short
 * replies and the user says one of them. Frozen before recording; this set is held out from all tuning.
 */
data class SuggItem(val id: String, val question: String, val chips: List<String>, val target: Int, val cond: String)

object Suggest {
    private val SCREENS = listOf(
        "Good morning! Are you awake?" to listOf("I'm up!", "Five more minutes", "I feel sick"),
        "Time to get up!" to listOf("Okay, I'm getting up", "Ten more minutes", "I'm so tired"),
        "How are you feeling today?" to listOf("I'm good", "I'm tired", "Not well"),
        "Good morning, sleepyhead!" to listOf("Good morning!", "Let me sleep", "Hi Rin"),
        "Mission complete! Great job!" to listOf("Thank you!", "That was easy", "I'm still sleepy"),
        "Want to hear a fun fact?" to listOf("Yes, please", "No, thanks", "Maybe later"),
        "Ready for the morning mission?" to listOf("Let's go!", "Not yet", "I can't today"),
        "Did you sleep well?" to listOf("Yes, I did", "Not really", "I had a bad dream"),
    )

    /**
     * What to say on the 6 off-list items. Added 2026-09-26 after the first take, where the tester read
     * "say something not on the screen" as "say one of these". Off-list items only; nothing else changed.
     */
    val OFF_LIST = mapOf(
        "s1" to "What time is it?", "s2" to "Where is my phone?", "s3" to "Is it raining outside?",
        "s4" to "Turn off the light.", "s5" to "What's for breakfast?", "s6" to "I had a weird dream.",
    )

    fun offListLine(item: SuggItem) = OFF_LIST[item.id.substringBefore('-')] ?: "What time is it?"

    /** 8 screens x targets [0,1,2,0,1,2]; on screens 1-6 the last slot becomes "off-list" (-1): 42 + 6. */
    val ALL: List<SuggItem> = run {
        val conds = List(24) { "normal" } + List(16) { "sleepy" } + List(8) { "far" }
        val items = SCREENS.flatMapIndexed { s, (q, chips) ->
            listOf(0, 1, 2, 0, 1, 2).mapIndexed { k, t ->
                val target = if (k == 5 && s < 6) -1 else t
                Triple(s, k, q to (chips to target))
            }
        }.shuffled(java.util.Random(7))
        items.mapIndexed { i, (s, k, qc) ->
            SuggItem("s${s + 1}-${k + 1}", qc.first, qc.second.first, qc.second.second, conds[i])
        }
    }
}

/** Picks which on-screen reply was said, or null (off-list / unclear). */
object ChipMatcher {
    private val FILLER = setOf("i", "am", "a", "the", "so", "is", "it", "rin", "um", "uh", "oh")

    fun content(chip: String) = IntentMatcher.normalize(chip).filter { it !in FILLER }

    /** Each chip scores the share of its content words heard; the best wins if >= 0.5 and not tied. */
    fun match(text: String?, chips: List<String>, phonetic: Boolean = true): Int? {
        val tokens = IntentMatcher.normalize(text ?: return null)
        if (tokens.isEmpty()) return null
        val scores = chips.map { c ->
            val words = content(c)
            words.count { w -> tokens.any { IntentMatcher.wordMatches(w, it, phonetic) } }.toDouble() / words.size.coerceAtLeast(1)
        }
        val best = scores.indices.maxBy { scores[it] }
        if (scores[best] < 0.5 || scores.count { it == scores[best] } > 1) return null
        return best
    }

    /** Vosk grammar for one screen: the chips' words as spoken, plus [unk] for anything else. */
    fun grammar(chips: List<String>): List<String> =
        chips.flatMap { c -> c.lowercase().split(Regex("[^a-z']+")).filter { it.isNotBlank() } }.distinct() + "[unk]"
}
